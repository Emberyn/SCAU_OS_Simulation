package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.ProcessState;

import java.util.*;

/**
 * 进程管理器 - 负责进程的创建、终止、调度、状态切换
 * 【修复版】增加了 createProcess 时的唤醒逻辑，并完善了 Idle 进程
 */
public class ProcessManager
{
    private final List<Process> processes;
    private final MemoryManager memoryManager;
    private int nextPid = 0;
    private final List<Process> readyQueue = new ArrayList<>();
    private final Deque<Process> blockedQueue = new ArrayDeque<>();
    private Process running;

    // 闲逛进程
    private final Process idleProcess;

    public ProcessManager(MemoryManager memoryManager)
    {
        this.memoryManager = memoryManager;
        this.processes = new ArrayList<>();

        // 【修改】IDLE 进程不需要实体内存块，传 null
        PCB idlePcb = new PCB(-1, "IDLE (闲逛)", 0, null);
        this.idleProcess = new Process(idlePcb);
        // 手动构造一个死循环指令序列
        Executable idleExec = new Executable(Arrays.asList("x=0", "x++", "x--"));
        // 注意：CPU 执行完最后一条指令通常会结束，但在 Scheduler 中会对 IDLE 特殊处理
        // 或者我们可以让它无限循环（如果指令集支持跳转），这里简单处理即可
        this.idleProcess.setExecutable(idleExec);
    }

    // --- Getter 方法 (加锁保护) ---

    public synchronized List<Process> getProcesses() { return new ArrayList<>(processes); }
    public synchronized List<Process> getReadyQueue() { return new ArrayList<>(readyQueue); }
    public synchronized Deque<Process> getBlockedQueue() { return new ArrayDeque<>(blockedQueue); }
    public synchronized Process getRunning() { return running; }

    // 【新增】供 Scheduler 调用
    public boolean hasIdleProcess() { return idleProcess != null; }
    public Process getIdleProcess() { return idleProcess; }

    /**
     * 创建新进程
     */
    public synchronized Process createProcess(String name, int priority)
    {
        int memorySize = 64;

        // 【修改】获取对象
        org.example.scau_os_simulation.memory.MemoryBlock block = memoryManager.allocateMemory(memorySize);

        if (block == null) // 【修改】判空
        {
            Kernel.getInstance().printToTerminal("创建失败: 内存不足");
            return null;
        }

        // 【修改】传入 block 对象
        PCB pcb = new PCB(nextPid++, name, priority, block);
        Process process = new Process(pcb);

        processes.add(process);
        process.ready();
        readyQueue.add(process);

        // 记录日志
        Map<String, Object> details = new HashMap<>();
        details.put("pid", pcb.getPid());
        details.put("name", name);
        Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_CREATE,
                "创建进程成功", details
        );

        // 【关键修复】唤醒调度器！
        // 如果调度器因为没进程而正在 wait()，这里必须把它叫醒
        if (Kernel.getInstance().getScheduler() != null) {
            Kernel.getInstance().getScheduler().wakeUp();
        }

        return process;
    }

    /**
     * 终止进程
     */
    public synchronized void terminateProcess(int pid)
    {
        Process process = findProcessInternal(pid);
        if (process != null)
        {
            // 【修改】传入 block 对象进行释放，确保能够匹配到搬家后的块
            memoryManager.freeMemory(process.getPcb().getMemoryBlock());
            processes.remove(process);

            // 记录日志
            Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_TERMINATE,
                    "进程终止: " + process.getPcb().getName(), null
            );

            boolean wasRunning = (running == process);
            if (wasRunning) running = null;

            readyQueue.remove(process);
            blockedQueue.remove(process);

            if (wasRunning) scheduleNext();
        }
    }

    public synchronized void terminateAllProcesses()
    {
        List<Process> copy = new ArrayList<>(processes);
        for (Process p : copy) terminateProcess(p.getPcb().getPid());
    }

    public synchronized Process findProcess(int pid)
    {
        return findProcessInternal(pid);
    }

    private Process findProcessInternal(int pid) {
        for (Process p : processes) if (p.getPcb().getPid() == pid) return p;
        return null;
    }

    /**
     * 上下文切换 / 调度下一个
     */
    public synchronized void contextSwitch(Process nextProcess) {
        // 如果有当前进程且没结束，放回就绪队列
        if (running != null && running != idleProcess && running.getPcb().getState() != ProcessState.TERMINATED) {
            running.ready();
            readyQueue.add(running);
        }

        running = nextProcess;
        if (running != null) {
            running.run();
            running.getPcb().resetTimeSlice();
        }
    }



    /**
     * 调度核心逻辑
     */
    public synchronized void scheduleNext()
    {
        // 1. Aging 机制：增加等待进程的优先级
        for (Process p : readyQueue) p.getPcb().incrementWaitingTime();

        // 2. 将当前运行的放回队列（如果它还是运行态且非 IDLE）
        if (running != null && running != idleProcess && running.getPcb().getState() == ProcessState.RUNNING) {
            running.ready();
            readyQueue.add(running);
        }

        // 3. 选择下一个
        if (readyQueue.isEmpty()) {
            // 没进程了，跑闲逛进程
            running = idleProcess;
            // 注意：IDLE 进程通常不需要显式设为 RUNNING，因为它不参与普通状态流转
            // 但为了 UI 显示一致，可以设一下
            if (running != null) {
                running.getPcb().setState(ProcessState.RUNNING);
            }
            return;
        }

        // 按优先级排序 (数值越大优先级越高? 这里假设 priority 越大越高)
        // 使用 stream 查找优先级最高的进程
        Process next = readyQueue.stream()
                .max(Comparator.comparingInt(p -> p.getPcb().getPriority()))
                .orElse(null);

        if (next != null) {
            readyQueue.remove(next);
            running = next;
            running.run();
            running.getPcb().resetTimeSlice();
            running.getPcb().resetWaitingTime();
        }
    }




    public synchronized void onTimeSliceEnd()
    {
        scheduleNext();
    }

    public synchronized void onProcessBlocked(int pid)
    {
        Process p = findProcessInternal(pid);
        if (p == null) return;

        readyQueue.remove(p);
        blockedQueue.addLast(p);
        p.block();

        if (running == p) {
            running = null;
            scheduleNext();
        }
    }

    public synchronized void onDeviceComplete(int pid)
    {
        Process p = findProcessInternal(pid);
        if (p == null) return;

        blockedQueue.remove(p);
        p.ready();
        readyQueue.add(p);

        // 【新增】唤醒调度器，因为有新进程就绪了
        if (Kernel.getInstance().getScheduler() != null) {
            Kernel.getInstance().getScheduler().wakeUp();
        }
    }
}
package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

import java.util.*;

/**
 * 进程管理器 - 操作系统的人力资源部
 */
public class ProcessManager
{
    /**
     * 所有进程的主列表
     */
    private final List<Process> processes;
    /**
     * 内存管理器
     */
    private final MemoryManager memoryManager;
    /**
     * 下一个可分配的 PID 计数器
     */
    private int nextPid = 0;

    /**
     * 【修改点】就绪队列改为 List，不再使用 PriorityQueue。
     * 因为 PriorityQueue 无法感知对象内部 priority 属性的变化（Aging机制失效问题）。
     */
    private final List<Process> readyQueue = new ArrayList<>();

    /**
     * 阻塞队列
     */
    private final Deque<Process> blockedQueue = new ArrayDeque<>();
    /**
     * 当前正在 CPU 上运行的进程
     */
    private Process running;

    public ProcessManager(MemoryManager memoryManager)
    {
        this.memoryManager = memoryManager;
        this.processes = new ArrayList<>();
    }

    public List<Process> getProcesses()
    {
        return processes;
    }

    // 【修改点】返回类型改为 List
    public List<Process> getReadyQueue()
    {
        return readyQueue;
    }

    public Deque<Process> getBlockedQueue()
    {
        return blockedQueue;
    }

    public Process getRunning()
    {
        return running;
    }

    /**
     * 创建一个新进程
     */
    public Process createProcess(String name, int priority)
    {
        int memorySize = 64; // 默认64KB
        int memoryAddress = memoryManager.allocateMemory(memorySize);

        if (memoryAddress < 0)
        {
            System.out.println("内存不足，无法创建进程");
            Map<String, Object> details = new HashMap<>();
            details.put("name", name);
            details.put("priority", priority);
            details.put("memorySize", memorySize);
            Kernel.getInstance().getOperationLogger().error(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_CREATE,
                    "创建进程失败：内存不足",
                    details
            );
            return null;
        }

        PCB pcb = new PCB(nextPid++, name, priority, memoryAddress, memorySize);
        Process process = new Process(pcb);
        processes.add(process);

        process.ready();
        // 【修改点】使用 add 代替 offer
        readyQueue.add(process);

        System.out.println("创建进程: " + name + ", PID: " + pcb.getPid());

        Map<String, Object> details = new HashMap<>();
        details.put("pid", pcb.getPid());
        details.put("name", name);
        details.put("priority", priority);
        details.put("memoryAddress", memoryAddress);
        details.put("memorySize", memorySize);
        Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_CREATE,
                "成功创建进程",
                details
        );

        return process;
    }

    /**
     * 终止一个进程
     */
    public void terminateProcess(int pid)
    {
        Process process = findProcess(pid);
        if (process != null)
        {
            memoryManager.freeMemory(process.getPcb().getMemoryAddress(), process.getPcb().getMemorySize());
            processes.remove(process);
            System.out.println("终止进程: " + process.getPcb().getName() + ", PID: " + pid);

            Map<String, Object> details = new HashMap<>();
            details.put("pid", pid);
            details.put("name", process.getPcb().getName());
            details.put("finalAx", process.getPcb().getAx());
            Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_TERMINATE,
                    "进程终止",
                    details
            );

            if (running == process) running = null;
            readyQueue.remove(process);
            blockedQueue.remove(process);

            boolean wasRunning = (running == process);

            if (wasRunning)
            {
                running = null;
            }

            readyQueue.remove(process);
            blockedQueue.remove(process);

            // 如果刚刚终止的是运行中进程，立即调度下一个
            if (wasRunning)
            {
                scheduleNext();
            }
        }
    }

    public void terminateAllProcesses()
    {
        List<Process> processesCopy = new ArrayList<>(processes);
        for (Process process : processesCopy)
        {
            terminateProcess(process.getPcb().getPid());
        }
    }

    public Process findProcess(int pid)
    {
        for (Process process : processes)
        {
            if (process.getPcb().getPid() == pid)
            {
                return process;
            }
        }
        return null;
    }

    /**
     * 更新等待时间（Aging机制）
     */
    public void updateWaitingTimes()
    {
        for (Process process : readyQueue)
        {
            process.getPcb().incrementWaitingTime();
        }
        for (Process process : blockedQueue)
        {
            process.getPcb().incrementWaitingTime();
        }
    }

    /**
     * 选择并切换到下一个运行的进程
     */
    public void scheduleNext()
    {
        // 1) aging：更新等待时间
        updateWaitingTimes();

        // 2) 若当前有运行中的进程，则将其恢复为就绪态并放回队列
        if (running != null)
        {
            running.ready();
            // 【修改点】使用 add
            readyQueue.add(running);
        }

        if (readyQueue.isEmpty()) return;

        // 【修改点】手动查找优先级最高的进程
        // 规则：优先级高(Priority值大)的优先；如果相同，PID小的优先
        Process next = readyQueue.stream()
                .max((p1, p2) ->
                {
                    int pDiff = p1.getPcb().getPriority() - p2.getPcb().getPriority();
                    if (pDiff != 0) return pDiff;
                    return p2.getPcb().getPid() - p1.getPcb().getPid(); // PID越小越优先，所以反过来减
                })
                .orElse(null);

        if (next == null) return;

        // 从就绪列表中移除选中的进程
        readyQueue.remove(next);

        // 4) 切换运行指针
        running = next;
        running.run();

        // 5) 初始化
        running.getPcb().resetTimeSlice();
        running.getPcb().resetWaitingTime();
    }

    public void onTimeSliceEnd()
    {
        scheduleNext();
    }

    public void onProcessBlocked(int pid)
    {
        Process p = findProcess(pid);
        if (p == null) return;

        // 1. 从就绪队列移除（如果它在那里的话）
        readyQueue.remove(p);

        // 2. 加入阻塞队列
        blockedQueue.addLast(p);

        // [修复点]：如果被阻塞的正是当前运行的进程，必须立刻让出 CPU
        if (running != null && running.getPcb().getPid() == pid) {
            running = null; // 清空当前运行指针
            scheduleNext(); // 立即尝试调度下一个，避免 CPU 空转
        }
    }

    /**
     * 设备完成请求后的处理
     */
    public void onDeviceComplete(int pid)
    {
        Process p = findProcess(pid);
        if (p == null) return;
        blockedQueue.remove(p);
        p.ready();
        // 【修改点】使用 add
        readyQueue.add(p);
    }
}
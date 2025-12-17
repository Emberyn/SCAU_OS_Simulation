package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.ProcessState;

import java.util.*;

/**
 * 进程管理器（ProcessManager）
 * 核心职责：作为操作系统内核的核心模块之一，全权负责进程的全生命周期管理，包括：
 * 1. 进程创建（分配PID、内存、初始化PCB）
 * 2. 进程终止（释放内存、清理队列、更新运行状态）
 * 3. 进程调度（实现就绪队列/阻塞队列管理、CPU上下文切换、闲逛进程兜底）
 * 4. 进程状态切换（就绪→运行、运行→阻塞、阻塞→就绪等）
 * 5. 调度辅助逻辑（时间片耗尽处理、设备完成后的进程唤醒、优先级老化机制）
 */
public class ProcessManager
{
    /**
     * 系统中所有进程的总列表
     */
    private final List<Process> processes;

    /**
     * 内存管理器引用
     * 作用：进程创建时分配内存块，进程终止时释放内存块，依赖内存管理器完成物理内存的管理
     */
    private final MemoryManager memoryManager;

    /**
     * 下一个要分配的进程ID（PID）
     * 作用：保证每个进程的PID唯一，创建进程时自增
     */
    private int nextPid = 0;


    private final List<Process> readyQueue = new ArrayList<>();
    private final Deque<Process> blockedQueue = new ArrayDeque<>();

    private Process running;
    private final Process idleProcess;


    /**
     * 构造函数：初始化进程管理器核心资源
     * @param memoryManager 内存管理器实例（依赖注入，用于进程内存分配/释放）
     */
    public ProcessManager(MemoryManager memoryManager)
    {
        // 初始化内存管理器引用
        this.memoryManager = memoryManager;
        // 初始化系统进程总列表
        this.processes = new ArrayList<>();

        // 初始化闲逛进程：操作系统必备的兜底进程
        // 1. 构造闲逛进程的PCB：PID=-1（特殊标识，区别于用户进程）、名称"IDLE (闲逛)"、优先级0（最低）、内存块null（无需内存）
        PCB idlePcb = new PCB(-1, "IDLE (闲逛)", 0, null);
        // 2. 创建闲逛进程对象
        this.idleProcess = new Process(idlePcb);
        // 3. 为闲逛进程设置空指令序列：模拟CPU空转（死循环执行x=0、x++、x--）
        Executable idleExec = new Executable(Arrays.asList("x=0", "x++", "x--"));
        this.idleProcess.setExecutable(idleExec);
    }


    // --- 线程安全的Getter方法 ---
    /**
     * 获取系统中所有进程的副本（线程安全）
     * 设计原因：返回新ArrayList副本，避免外部修改原列表导致数据混乱
     * @return 所有进程的只读副本列表
     */
    public synchronized List<Process> getProcesses() { return new ArrayList<>(processes); }

    /**
     * 获取就绪队列的副本（线程安全）
     * @return 就绪队列的只读副本列表
     */
    public synchronized List<Process> getReadyQueue() { return new ArrayList<>(readyQueue); }

    /**
     * 获取阻塞队列的副本（线程安全）
     * @return 阻塞队列的只读副本双端队列
     */
    public synchronized Deque<Process> getBlockedQueue() { return new ArrayDeque<>(blockedQueue); }

    /**
     * 获取当前运行的进程（线程安全）
     * @return 当前运行的进程（可能为闲逛进程或null）
     */
    public synchronized Process getRunning() { return running; }

    /**
     * 检查是否存在闲逛进程
     * @return 存在返回true，否则返回false
     */
    public boolean hasIdleProcess() { return idleProcess != null; }


    /**
     * 获取闲逛进程实例
     * @return 闲逛进程对象
     */
    public Process getIdleProcess() { return idleProcess; }


    /**
     * 创建新用户进程（核心方法）
     * 执行流程：1. 分配内存 → 2. 初始化PCB → 3. 创建进程对象 → 4. 加入进程列表 → 5. 加入就绪队列 → 6. 唤醒调度器
     * @param name 进程名称（用户自定义）
     * @param priority 进程优先级（数值越大优先级越高）
     * @return 创建成功返回进程对象，内存不足返回null
     */
    public synchronized Process createProcess(String name, int priority)
    {
        // 步骤1：定义进程默认占用内存大小（64KB）
        int memorySize = 64;

        // 步骤2：调用内存管理器分配64KB内存块
        MemoryBlock block = memoryManager.allocateMemory(memorySize);

        // 步骤3：内存分配失败处理
        if (block == null)
        {
            // 输出终端提示信息
            Kernel.getInstance().printToTerminal("创建失败: 内存不足");
            return null;
        }

        // 步骤4：初始化进程控制块（PCB）
        // nextPid++：分配唯一PID（先使用当前值，再自增）
        PCB pcb = new PCB(nextPid++, name, priority, block);
        // 步骤5：创建进程对象
        Process process = new Process(pcb);

        // 步骤6：将进程加入系统总列表
        processes.add(process);
        // 步骤7：设置进程状态为就绪，并加入就绪队列
        process.ready();
        readyQueue.add(process);

        // 步骤8：记录进程创建日志（用于内核日志展示）
        Map<String, Object> details = new HashMap<>();
        details.put("pid", pcb.getPid());
        details.put("name", name);
        Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_CREATE,
                "创建进程成功", details
        );

        // 步骤9：唤醒调度器（关键）
        // 若调度器因无就绪进程处于等待状态，需唤醒以立即调度新进程
        if (Kernel.getInstance().getScheduler() != null) {
            Kernel.getInstance().getScheduler().wakeUp();
        }

        // 返回创建成功的进程对象
        return process;
    }

    /**
     * 终止指定PID的进程（核心方法）
     * 执行流程：1. 查找进程 → 2. 释放内存 → 3. 清理队列 → 4. 重新调度
     * @param pid 要终止的进程ID
     */
    public synchronized void terminateProcess(int pid)
    {
        // 步骤1：查找要终止的进程（内部私有方法，避免重复逻辑）
        Process process = findProcessInternal(pid);
        if (process != null)
        {
            // 步骤2：释放进程占用的内存块（通过内存管理器）
            memoryManager.freeMemory(process.getPcb().getMemoryBlock());
            // 步骤3：从系统总列表移除进程
            processes.remove(process);

            // 步骤4：记录进程终止日志
            Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_TERMINATE,
                    "进程终止: " + process.getPcb().getName(), null
            );

            // 步骤5：判断被终止的是否是当前运行的进程
            boolean wasRunning = (running == process);
            if (wasRunning) running = null;

            // 步骤6：从就绪队列/阻塞队列中移除进程（无论进程处于哪个队列）
            readyQueue.remove(process);
            blockedQueue.remove(process);

            // 步骤7：若终止的是运行中的进程，立即调度下一个进程
            if (wasRunning) scheduleNext();
        }
    }

    /**
     * 终止系统中所有用户进程（保留闲逛进程）
     * 执行逻辑：遍历进程副本列表，逐个终止（避免遍历原列表时修改导致的并发异常）
     */
    public synchronized void terminateAllProcesses()
    {
        // 创建进程列表副本，避免遍历过程中修改原列表引发ConcurrentModificationException
        List<Process> copy = new ArrayList<>(processes);
        for (Process p : copy) terminateProcess(p.getPcb().getPid());
    }

    /**
     * 对外提供的进程查找方法（线程安全）
     * @param pid 要查找的进程ID
     * @return 找到返回进程对象，否则返回null
     */
    public synchronized Process findProcess(int pid)
    {
        return findProcessInternal(pid);
    }

    /**
     * 内部私有进程查找方法（避免重复逻辑）
     * @param pid 要查找的进程ID
     * @return 找到返回进程对象，否则返回null
     */
    private Process findProcessInternal(int pid) {
        // 遍历系统总进程列表，匹配PID
        for (Process p : processes) if (p.getPcb().getPid() == pid) return p;
        return null;
    }

    /**
     * 执行CPU上下文切换（核心调度方法）
     * 作用：将当前运行进程切换为目标进程，保证CPU执行权的平稳交接
     * @param nextProcess 要切换到的目标进程
     */
    public synchronized void contextSwitch(Process nextProcess) {
        // 步骤1：处理当前运行的进程（非闲逛进程、未终止）
        if (running != null && running != idleProcess && running.getPcb().getState() != ProcessState.TERMINATED) {
            // 将当前进程状态设为就绪
            running.ready();
            // 放回就绪队列，等待下次调度
            readyQueue.add(running);
        }

        // 步骤2：切换到新进程
        running = nextProcess;
        if (running != null) {
            // 设置新进程状态为运行
            running.run();
            // 重置新进程的时间片（重新开始计时）
            running.getPcb().resetTimeSlice();
        }
    }



    /**
     * 调度核心逻辑（选择下一个要运行的进程）
     * 执行流程：1. 优先级老化 → 2. 处理当前运行进程 → 3. 选择下一个进程（就绪队列优先，无则执行闲逛进程）
     */
    public synchronized void scheduleNext()
    {
        // 步骤1：优先级老化机制（提升长期等待进程的优先级，避免饥饿）
        // 遍历就绪队列，为每个等待的进程增加等待时间（间接提升优先级）
        for (Process p : readyQueue) p.getPcb().incrementWaitingTime();

        // 步骤2：处理当前运行的进程（非闲逛进程、仍处于运行态）
        if (running != null && running != idleProcess && running.getPcb().getState() == ProcessState.RUNNING) {
            // 将当前进程设为就绪状态
            running.ready();
            // 放回就绪队列
            readyQueue.add(running);
        }

        // 步骤3：无就绪进程时，执行闲逛进程
        if (readyQueue.isEmpty()) {
            running = idleProcess;
            // 为了UI显示一致，将闲逛进程设为运行态
            if (running != null) {
                running.getPcb().setState(ProcessState.RUNNING);
            }
            return;
        }

        // 步骤4：从就绪队列中选择优先级最高的进程
        // 按进程优先级（数值越大优先级越高）排序，取最大值
        Process next = readyQueue.stream()
                .max(Comparator.comparingInt(p -> p.getPcb().getPriority()))
                .orElse(null);

        // 步骤5：调度选中的进程
        if (next != null) {
            // 从就绪队列移除该进程（避免重复调度）
            readyQueue.remove(next);
            // 设置为当前运行进程
            running = next;
            // 设为运行状态
            running.run();
            // 重置时间片
            running.getPcb().resetTimeSlice();
            // 重置等待时间（优先级老化值归零）
            running.getPcb().resetWaitingTime();
        }
    }

    /**
     * 时间片耗尽处理方法
     * 触发时机：当前运行进程的时间片用完时，由调度器调用
     * 执行逻辑：直接触发调度，选择下一个进程执行
     */
    public synchronized void onTimeSliceEnd()
    {
        scheduleNext();
    }

    /**
     * 进程阻塞处理方法
     * 触发时机：进程请求资源（如设备IO）未满足时调用
     * @param pid 要阻塞的进程ID
     */
    public synchronized void onProcessBlocked(int pid)
    {
        // 查找要阻塞的进程
        Process p = findProcessInternal(pid);
        if (p == null) return;

        // 从就绪队列移除（若存在）
        readyQueue.remove(p);
        // 加入阻塞队列尾部
        blockedQueue.addLast(p);
        // 设置进程状态为阻塞
        p.block();

        // 若被阻塞的是当前运行进程，立即调度下一个进程
        if (running == p) {
            running = null;
            scheduleNext();
        }
    }

    /**
     * 设备完成后的进程唤醒方法
     * 触发时机：进程等待的设备资源完成（如IO完成）时调用
     * @param pid 要唤醒的进程ID
     */
    public synchronized void onDeviceComplete(int pid)
    {
        // 查找要唤醒的进程
        Process p = findProcessInternal(pid);
        if (p == null) return;

        // 从阻塞队列移除
        blockedQueue.remove(p);
        // 设置进程状态为就绪
        p.ready();
        // 加入就绪队列，等待调度
        readyQueue.add(p);

        // 唤醒调度器，立即处理新就绪的进程
        if (Kernel.getInstance().getScheduler() != null) {
            Kernel.getInstance().getScheduler().wakeUp();
        }
    }
}
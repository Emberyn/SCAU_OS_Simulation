package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.ProcessState; // 【修复】添加导包

import java.util.*;

/**
 * 进程管理器 - 模拟操作系统的进程管理核心模块
 * 负责进程的创建、终止、调度、状态切换（就绪/运行/阻塞）等核心功能
 * 实现了基于优先级的调度算法，并包含Aging（老化）机制以避免低优先级进程饥饿
 */
public class ProcessManager
{
    /**
     * 系统中所有进程的主列表（包含就绪、运行、阻塞状态的所有进程）
     */
    private final List<Process> processes;

    /**
     * 内存管理器实例 - 用于进程内存的分配与释放
     */
    private final MemoryManager memoryManager;

    /**
     * 下一个可分配的PID（进程ID）计数器 - 保证PID唯一性
     */
    private int nextPid = 0;

    /**
     * 就绪队列：等待CPU调度的进程列表
     * 使用List可在每次调度时重新计算优先级，保证Aging机制生效
     */
    private final List<Process> readyQueue = new ArrayList<>();

    /**
     * 阻塞队列：因I/O等事件等待的进程队列（双端队列保证操作效率）
     */
    private final Deque<Process> blockedQueue = new ArrayDeque<>();

    /**
     * 当前正在CPU上运行的进程引用
     */
    private Process running;

    /**
     * 【新增】闲逛进程 (Idle Process)
     * 当就绪队列为空时，CPU 运行此进程，避免“停转”
     */
    private final Process idleProcess;

    /**
     * 构造函数 - 初始化进程管理器
     * @param memoryManager 内存管理器实例，用于进程内存分配/释放
     */
    public ProcessManager(MemoryManager memoryManager)
    {
        this.memoryManager = memoryManager;
        this.processes = new ArrayList<>();

        // 【新增】初始化闲逛进程
        // PID=-1, 优先级=0, 内存=0
        PCB idlePcb = new PCB(-1, "IDLE (闲逛)", 0, 0, 0);
        this.idleProcess = new Process(idlePcb);
    }

    /**
     * 获取系统中所有进程的列表（只读）
     * @return 所有进程的List集合
     */
    public List<Process> getProcesses()
    {
        return processes;
    }

    /**
     * 获取就绪队列（只读）
     * @return 就绪进程的List集合
     */
    public List<Process> getReadyQueue()
    {
        return readyQueue;
    }

    /**
     * 获取阻塞队列（只读）
     * @return 阻塞进程的Deque集合
     */
    public Deque<Process> getBlockedQueue()
    {
        return blockedQueue;
    }

    /**
     * 获取当前运行的进程
     * @return 运行态进程，无则返回null
     */
    public Process getRunning()
    {
        return running;
    }

    /**
     * 创建新进程 - 核心方法
     * 1. 分配内存 2. 创建PCB 3. 初始化进程 4. 加入就绪队列 5. 记录日志
     * @param name 进程名称
     * @param priority 进程优先级（数值越大优先级越高）
     * @return 创建成功的进程实例，内存不足时返回null
     */
    public Process createProcess(String name, int priority)
    {
        // 默认进程内存占用64KB（模拟固定内存大小）
        int memorySize = 64;
        // 向内存管理器申请内存
        int memoryAddress = memoryManager.allocateMemory(memorySize);

        // 内存分配失败处理
        if (memoryAddress < 0)
        {
            System.out.println("内存不足，无法创建进程");
            // 记录创建失败日志
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

        // 1. 创建PCB（进程控制块）- 分配唯一PID
        PCB pcb = new PCB(nextPid++, name, priority, memoryAddress, memorySize);
        // 2. 创建进程实例
        Process process = new Process(pcb);
        // 3. 添加到系统进程主列表
        processes.add(process);

        // 4. 设置进程状态为就绪，并加入就绪队列
        process.ready();
        readyQueue.add(process);

        System.out.println("创建进程: " + name + ", PID: " + pcb.getPid());

        // 记录创建成功日志
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
     * 终止指定PID的进程
     * 1. 释放内存 2. 从所有队列移除 3. 记录日志 4. 若终止的是运行态进程，立即调度下一个
     * @param pid 要终止的进程ID
     */
    public void terminateProcess(int pid)
    {
        // 查找目标进程
        Process process = findProcess(pid);
        if (process != null)
        {
            // 1. 释放进程占用的内存
            memoryManager.freeMemory(process.getPcb().getMemoryAddress(), process.getPcb().getMemorySize());
            // 2. 从系统进程主列表移除
            processes.remove(process);
            System.out.println("终止进程: " + process.getPcb().getName() + ", PID: " + pid);

            // 3. 记录终止日志
            Map<String, Object> details = new HashMap<>();
            details.put("pid", pid);
            details.put("name", process.getPcb().getName());
            details.put("finalAx", process.getPcb().getAx()); // 记录进程终止时的AX寄存器值
            Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.PROCESS_TERMINATE,
                    "进程终止",
                    details
            );

            // 标记是否终止的是当前运行的进程
            boolean wasRunning = (running == process);

            // 4. 清空运行指针（如果终止的是运行态进程）
            if (wasRunning)
            {
                running = null;
            }

            // 5. 从就绪队列和阻塞队列中移除（防止内存泄漏）
            readyQueue.remove(process);
            blockedQueue.remove(process);

            // 6. 如果终止的是运行态进程，立即调度下一个进程
            if (wasRunning)
            {
                scheduleNext();
            }
        }
    }

    /**
     * 终止系统中所有进程 - 用于系统重置/关闭
     */
    public void terminateAllProcesses()
    {
        // 复制进程列表（避免遍历过程中修改原列表导致的并发修改异常）
        List<Process> processesCopy = new ArrayList<>(processes);
        for (Process process : processesCopy)
        {
            terminateProcess(process.getPcb().getPid());
        }
    }

    /**
     * 根据PID查找进程
     * @param pid 进程ID
     * @return 找到的进程实例，无则返回null
     */
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
     * 更新等待时间（Aging机制核心）
     * 为就绪队列和阻塞队列中的进程增加等待时间，用于后续优先级调整
     * （Aging机制：等待时间过长的进程会提高优先级，避免饥饿）
     */
    public void updateWaitingTimes()
    {
        // 就绪队列进程增加等待时间
        for (Process process : readyQueue)
        {
            process.getPcb().incrementWaitingTime();
        }

        // 阻塞队列进程增加等待时间
        for (Process process : blockedQueue)
        {
            process.getPcb().incrementWaitingTime();
        }
    }

    /**
     * 进程调度核心方法 - 选择并切换到下一个运行的进程
     * 调度规则：
     * 1. 执行Aging机制
     * 2. 若当前有运行进程，将其放回就绪队列（除非它是闲逛进程）
     * 3. 优先调度就绪队列中的高优先级进程
     * 4. 若就绪队列为空，调度闲逛进程
     */
    public void scheduleNext()
    {
        // 1) 执行Aging：更新所有等待进程的等待时间
        updateWaitingTimes();

        // 2) 若当前有运行中的进程
        if (running != null)
        {
            // 【关键修改】如果当前运行的是闲逛进程，直接丢弃，不放入就绪队列
            if (running == idleProcess) {
                running = null;
            } else {
                // 正常进程：恢复为就绪态并放回就绪队列
                running.ready();
                readyQueue.add(running);
            }
        }

        // 3) 检查就绪队列是否为空
        if (readyQueue.isEmpty()) {
            // 【关键修改】队列为空，运行闲逛进程
            running = idleProcess;
            running.getPcb().setState(ProcessState.RUNNING);
            // 注意：闲逛进程不需要 resetTimeSlice，它永远跑不完
            return;
        }

        // 4) 手动查找就绪队列中优先级最高的进程
        Process next = readyQueue.stream()
                .max((p1, p2) ->
                {
                    // 先比较优先级
                    int pDiff = p1.getPcb().getPriority() - p2.getPcb().getPriority();
                    if (pDiff != 0) return pDiff;
                    // 优先级相同则比较PID（PID小的优先，所以反向相减）
                    return p2.getPcb().getPid() - p1.getPcb().getPid();
                })
                .orElse(null);

        if (next == null) return;

        // 5) 从就绪队列中移除选中的进程
        readyQueue.remove(next);

        // 6) 切换运行指针，设置进程为运行态
        running = next;
        running.run();

        // 7) 初始化进程的时间片和等待时间
        running.getPcb().resetTimeSlice();
        running.getPcb().resetWaitingTime();
    }

    /**
     * 时间片结束时的处理方法 - 触发进程调度
     * 时间片耗尽后，当前进程让出CPU，调度下一个进程
     */
    public void onTimeSliceEnd()
    {
        scheduleNext();
    }

    /**
     * 进程阻塞处理方法 - 当进程因I/O等事件需要阻塞时调用
     * @param pid 要阻塞的进程ID
     */
    public void onProcessBlocked(int pid)
    {
        Process p = findProcess(pid);
        if (p == null) return;

        // 1. 从就绪队列移除（防止重复调度）
        readyQueue.remove(p);

        // 2. 将进程加入阻塞队列，并设置为阻塞状态
        blockedQueue.addLast(p);
        p.block();

        // 如果被阻塞的是当前运行的进程，必须立即让出CPU
        if (running != null && running.getPcb().getPid() == pid) {
            running = null; // 清空运行指针
            scheduleNext(); // 立即调度下一个进程，避免CPU空转
        }
    }

    /**
     * 设备完成请求后的处理方法 - 唤醒阻塞的进程
     * 当I/O设备完成请求后，将对应的进程从阻塞队列移到就绪队列
     * @param pid 要唤醒的进程ID
     */
    public void onDeviceComplete(int pid)
    {
        Process p = findProcess(pid);
        if (p == null) return;

        // 1. 从阻塞队列移除
        blockedQueue.remove(p);
        // 2. 设置进程为就绪态，并加入就绪队列
        p.ready();
        readyQueue.add(p);
    }
}
package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

import java.util.*;

/**
 * 进程管理器 - 操作系统的人力资源部
 *
 * 这个类是操作系统的核心组件之一，专门负责管理所有进程的生命周期。
 * 把它想象成一个公司的人力资源（HR）部门，它处理员工（进程）的“入职”、“离职”和“工作状态管理”。
 *
 * 主要职责：
 * 1. **创建进程 (入职)**：当需要一个新进程时，它负责分配资源（如内存），创建进程控制块（PCB，即员工档案），并将其放入“准备工作”的队列中。
 * 2. **终止进程 (离职)**：当一个进程完成任务或被强制结束时，它负责回收该进程占用的所有资源，并将其从系统中移除。
 * 3. **状态管理**：它维护着几个重要的队列来跟踪每个进程的当前状态：
 *    - **就绪队列 (Ready Queue)**：存放所有准备好运行、等待CPU分配的进程（已经准备好上班，在等工位的员工）。
 *    - **阻塞队列 (Blocked Queue)**：存放所有因等待某个事件（如等待I/O设备）而暂停的进程（正在出差或等待外部资源的员工）。
 *    - **运行状态 (Running)**：记录当前正在CPU上执行的那个进程（正在工位上工作的员工）。
 * 4. **进程调度 (工作安排)**：它包含简单的调度逻辑（如 `scheduleNext`），用于决定下一个应该由哪个进程来使用CPU。
 */
public class ProcessManager {
    /** 所有进程的主列表（包含 NEW/READY/RUNNING/BLOCKED/TERMINATED 等各种状态） */
    private final List<Process> processes;
    /** 内存管理器：用于创建/终止进程时分配与释放内存 */
    private final MemoryManager memoryManager;
    /** 下一个可分配的 PID 计数器（自增） */
    private int nextPid = 0;
    /** 就绪队列（先进先出）：等待 CPU 执行的进程 */
    private final Deque<Process> readyQueue = new ArrayDeque<>();
    /** 阻塞队列：等待外部事件/设备完成的进程 */
    private final Deque<Process> blockedQueue = new ArrayDeque<>();
    /** 当前正在 CPU 上运行的进程（可能为 null） */
    private Process running;
    
    /**
     * 进程管理器的构造函数
     *
     * @param memoryManager 内存管理器。进程管理器在创建进程时需要它来申请内存，
     *                      在终止进程时需要它来释放内存。
     */
    public ProcessManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.processes = new ArrayList<>();
    }
    
    /**
     * 创建一个新进程
     *
     * 这是新员工的“入职流程”，包含以下步骤：
     * 1. **申请办公空间 (分配内存)**：首先向内存管理器申请一块内存，如果内存不足，则无法创建进程。
     * 2. **建立员工档案 (创建PCB)**：如果内存申请成功，就创建一个进程控制块（PCB），记录进程的ID、名称、优先级和内存信息。
     * 3. **正式入职 (创建进程对象)**：创建进程对象，并将其加入到总的进程列表中。
     * 4. **进入待命状态 (加入就绪队列)**：将新进程的状态设置为“就绪”，并将其放入就绪队列的末尾，等待CPU调度。
     *
     * @param name 进程的名称
     * @param priority 进程的优先级
     * @return 如果创建成功，返回新的进程对象；如果因内存不足等原因失败，返回 null。
     */
    public Process createProcess(String name, int priority) {
        // 分配内存：按默认 64KB 申请一段连续空间
        int memorySize = 64; // 默认64KB
        int memoryAddress = memoryManager.allocateMemory(memorySize);
        
        if (memoryAddress < 0) {
            System.out.println("内存不足，无法创建进程"); // 申请失败：反馈信息
            return null;                                   // 返回空，表示创建失败
        }
        
        // 创建 PCB：分配唯一 PID 与入职档案
        PCB pcb = new PCB(nextPid++, name, priority, memoryAddress, memorySize);
        
        // 创建进程对象并加入主列表
        Process process = new Process(pcb);
        processes.add(process);
        process.ready();                 // 标记为就绪态
        readyQueue.addLast(process);     // 加入就绪队列尾部
        
        System.out.println("创建进程: " + name + ", PID: " + pcb.getPid());
        return process;
    }
    
    /**
     * 终止一个进程
     *
     * 这是员工的“离职流程”，包含以下步骤：
     * 1. **清退办公空间 (释放内存)**：通知内存管理器，回收该进程之前占用的内存空间。
     * 2. **销毁员工档案 (移除进程)**：将该进程从总的进程列表、就绪队列和阻塞队列中彻底移除。
     * 3. **处理特殊情况**：如果被终止的进程当前正在CPU上运行，需要将CPU的当前运行进程设置为空。
     *
     * @param pid 要终止的进程的ID
     */
    public void terminateProcess(int pid) {
        Process process = findProcess(pid);
        if (process != null) {
            // 释放内存：归还该进程占用的连续区域
            memoryManager.freeMemory(process.getPcb().getMemoryAddress(), process.getPcb().getMemorySize());
            
            // 移除进程：从主列表与各队列清理
            processes.remove(process);
            System.out.println("终止进程: " + process.getPcb().getName() + ", PID: " + pid);
            if (running == process) running = null; // 若正在运行则清空运行指针
            readyQueue.remove(process);             // 从就绪队列移除
            blockedQueue.remove(process);           // 从阻塞队列移除
        }
    }
    
    /**
     * 终止所有进程
     *
     * 执行“全员解散”操作，通常在系统关闭或重启时使用。
     * 它会遍历当前所有的进程，并逐个调用 `terminateProcess` 方法来确保每个进程都被干净地终止。
     */
    public void terminateAllProcesses() {
        List<Process> processesCopy = new ArrayList<>(processes);
        for (Process process : processesCopy) {
            terminateProcess(process.getPcb().getPid());
        }
    }
    
    /**
     * 根据PID查找进程
     *
     * 就像在公司的员工花名册中，通过员工号（PID）来查找一个具体的员工（进程对象）。
     *
     * @param pid 要查找的进程的ID
     * @return 如果找到，返回对应的进程对象；否则返回 null。
     */
    public Process findProcess(int pid) {
        for (Process process : processes) {
            if (process.getPcb().getPid() == pid) {
                return process;
            }
        }
        return null;
    }
    
    /**
     * 获取所有进程列表
     * @return 当前系统中存在的所有进程
     */
    public List<Process> getProcesses() {
        return processes;
    }

    /**
     * 获取就绪队列
     * @return 等待CPU调度、可立即运行的进程队列（先进先出）
     */
    public Deque<Process> getReadyQueue() {
        return readyQueue;
    }

    /**
     * 获取阻塞队列
     * @return 因等待外部事件（如设备）而暂不可运行的进程队列
     */
    public Deque<Process> getBlockedQueue() {
        return blockedQueue;
    }

    /**
     * 获取当前正在运行的进程
     * @return 正在CPU上执行的进程；若无则为null
     */
    public Process getRunning() {
        return running;
    }

    /**
     * 选择并切换到下一个运行的进程
     *
     * 简化的时间片轮转策略：
     * 1. 若当前有运行进程，则将其状态改为就绪并放回队列尾部
     * 2. 从就绪队列取出队头进程作为新的运行进程，置为运行态
     * 3. 重置其时间片计数
     */
    public void scheduleNext() {
        // 时间片轮转：若有当前运行进程，则将其放回就绪队列尾部
        if (running != null) {
            running.ready();
            readyQueue.addLast(running);
        }
        // 取队头作为新的运行进程
        Process next = readyQueue.pollFirst();
        if (next == null) return;    // 队列为空：暂时无进程可运行
        running = next;
        running.run();               // 置为运行态
        running.getPcb().resetTimeSlice(); // 重置其时间片
    }

    /**
     * 时间片用尽时的回调：切换到下一个进程
     */
    public void onTimeSliceEnd() {
        scheduleNext();
    }

    /**
     * 进程阻塞时的处理
     *
     * 将进程从就绪队列移除并加入阻塞队列。
     * @param pid 阻塞进程的PID
     */
    public void onProcessBlocked(int pid) {
        Process p = findProcess(pid);
        if (p == null) return;
        readyQueue.remove(p);
        blockedQueue.addLast(p);
    }

    /**
     * 设备完成请求后的处理
     *
     * 当进程的设备请求完成后：
     * 1. 从阻塞队列移除该进程
     * 2. 置其为就绪并加入就绪队列尾部
     * @param pid 完成设备请求的进程PID
     */
    public void onDeviceComplete(int pid) {
        Process p = findProcess(pid);
        if (p == null) return;
        blockedQueue.remove(p);
        p.ready();
        readyQueue.addLast(p);
    }
}

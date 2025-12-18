package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.Process;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 系统调度器（Scheduler）- 操作系统内核核心模块
 * 核心职责：
 * 1. 进程调度：实现基于时间片的抢占式调度，优先执行就绪队列中的用户进程，无进程时执行IDLE闲逛进程
 * 2. 系统时钟管理：推进系统时钟（模拟CPU硬件时钟周期）
 * 3. CPU指令执行：触发CPU执行当前运行进程的单条指令
 * 4. 设备状态更新：周期更新设备管理器状态（如IO设备的完成进度）
 * 5. 性能监控：记录每个调度周期的CPU利用率、内存使用率等系统指标
 */
public class Scheduler
{

    private final ProcessManager processManager;

    private final DeviceManager deviceManager;

    /**
     * 定时任务线程池
     * 作用：执行固定周期的调度循环（核心调度逻辑）
     * 特性：单线程（newSingleThreadScheduledExecutor），保证调度逻辑串行执行，避免并发问题
     */
    private ScheduledExecutorService exec;

    /**
     * 调度器运行状态标记
     * true：调度器正在运行；false：调度器已停止
     */
    private boolean running = false;

    /**
     * 系统时钟（模拟CPU硬件时钟）
     * 计数单位：调度周期数（每200ms+1）
     */
    private long systemClock = 0;

    /**
     * CPU实例引用
     * 作用：调用CPU的executeOne()方法执行当前进程的单条指令
     * 初始化方式：动态获取（避免调度器启动时CPU尚未初始化导致空指针）
     */
    private CPU cpu;

    /**
     * 构造函数：初始化调度器核心依赖
     * @param processManager 进程管理器实例（必须）
     * @param deviceManager 设备管理器实例（必须）
     */
    public Scheduler(ProcessManager processManager, DeviceManager deviceManager)
    {
        this.processManager = processManager;
        this.deviceManager = deviceManager;
    }


    /**
     * 启动调度器（核心方法）
     * 执行逻辑：
     * 1. 检查运行状态，避免重复启动
     * 2. 创建单线程定时任务池
     * 3. 启动固定周期的调度循环（延迟0ms，周期200ms）
     */
    public void start()
    {
        // 防止重复启动：若调度器已运行，直接返回
        if (running) return;
        // 标记调度器为运行状态
        running = true;

        // 创建单线程定时任务池：保证调度逻辑串行执行，避免并发冲突
        exec = Executors.newSingleThreadScheduledExecutor();

        // 启动固定周期的调度循环
        // 参数1：调度核心逻辑（Runnable）
        // 参数2：初始延迟（0ms）- 调度器启动后立即执行第一次循环
        // 参数3：周期（200ms）- 每200ms执行一次调度循环
        // 参数4：时间单位（MILLISECONDS）- 周期单位为毫秒
        exec.scheduleAtFixedRate(() ->
        {
            try
            {
                // 步骤1：推进系统时钟（每调度一次，时钟+1）
                systemClock++;

                // 步骤2：动态获取CPU实例（懒加载）
                // 原因：调度器启动时，Kernel的CPU实例可能尚未初始化，此处动态获取避免空指针
                if (cpu == null) {
                    if (Kernel.getInstance() != null) {
                        cpu = Kernel.getInstance().getCPU();
                    }
                }

                // -----------------------------------------------------------
                // 核心修复：抢占IDLE进程的调度逻辑（避免CPU空转）
                // 设计目的：只要就绪队列有用户进程，就立即抢占IDLE进程的CPU执行权
                // -----------------------------------------------------------
                // 获取当前正在运行的进程
                Process current = processManager.getRunning();

                // 判断条件1：CPU完全空闲（无任何进程运行）
                boolean isCpuEmpty = (current == null);

                // 判断条件2：CPU正在执行IDLE闲逛进程（PID=-1是IDLE进程的特殊标识）
                boolean isRunningIdle = (current != null && current.getPcb().getPid() == -1);

                // 判断条件3：就绪队列中有等待执行的用户进程
                boolean hasReadyProcess = !processManager.getReadyQueue().isEmpty();

                // 调度决策：满足以下任一条件则立即触发进程调度
                // - CPU空转（无进程运行）
                // - 正在执行IDLE进程且有用户进程等待
                if (isCpuEmpty || (isRunningIdle && hasReadyProcess))
                {
                    // 调用进程管理器的调度逻辑，选择下一个要执行的进程
                    processManager.scheduleNext();
                    // 刷新当前运行进程引用，确保后续指令执行的是新调度的进程
                    current = processManager.getRunning();
                }

                // -----------------------------------------------------------
                // 执行当前进程的单条指令
                // -----------------------------------------------------------
                // 仅当有运行进程且CPU实例有效时，执行指令
                if (current != null && cpu != null)
                {
                    // 调用CPU执行一条指令（模拟CPU的指令执行周期）
                    cpu.executeOne();
                }

                // -----------------------------------------------------------
                // 周期更新设备状态
                // -----------------------------------------------------------
                // 推进设备管理器的状态（如IO设备计时、完成的IO任务唤醒阻塞进程）
                deviceManager.tick();


                // -----------------------------------------------------------
                // 性能监控：记录当前调度周期的系统状态
                // -----------------------------------------------------------
                if (Kernel.getInstance() != null && Kernel.getInstance().getPerformanceMonitor() != null) {
                    // 计算CPU利用率：
                    // - 执行用户进程时，CPU利用率=1.0（100%）
                    // - 执行IDLE进程/无进程时，CPU利用率=0.0（空闲）
                    double cpuUtil = (current != null && current.getPcb().getPid() != -1) ? 1.0 : 0.0;

                    // 计算内存使用率：从内存管理器获取（未初始化则为0.0）
                    double memUsage = 0.0;
                    if (Kernel.getInstance().getMemoryManager() != null) {
                        memUsage = Kernel.getInstance().getMemoryManager().getMemoryUsageRate();
                    }

                    // 记录性能快照：包含CPU利用率、内存使用率、各类进程数量
                    Kernel.getInstance().getPerformanceMonitor().recordSnapshot(
                            cpuUtil,                  // CPU利用率
                            memUsage,                 // 内存使用率
                            processManager.getProcesses().size(),    // 系统总进程数
                            processManager.getReadyQueue().size(),   // 就绪队列进程数
                            processManager.getBlockedQueue().size()  // 阻塞队列进程数
                    );
                }

            } catch (Exception e)
            {
                // 捕获调度循环中的所有异常，避免定时任务终止
                e.printStackTrace(); // 打印异常堆栈，便于调试
                System.err.println("调度器错误: " + e.getMessage()); // 输出错误信息到控制台
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止调度器
     * 执行逻辑：
     * 1. 标记调度器为停止状态
     * 2. 关闭定时任务线程池，终止调度循环
     */
    public void stop()
    {
        // 标记调度器为停止状态
        running = false;
        // 关闭定时任务线程池（若已创建）
        if (exec != null) {
            exec.shutdown();
        }
    }

    /**
     * 获取调度器运行状态
     * @return true：调度器运行中；false：调度器已停止
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 获取系统时钟值（调度周期数）
     * @return 系统时钟当前值（每200ms+1）
     */
    public long getSystemClock() {
        return systemClock;
    }

    /**
     * 唤醒调度器（供进程管理器调用）
     * 设计说明：
     * 本次修复中无需额外逻辑，因为调度器是固定周期轮询（每200ms检查一次就绪队列），
     * 即使新进程加入就绪队列，下一个调度周期会自动检测并触发调度。
     * 若需实现“即时唤醒”，可在此方法中添加触发调度的逻辑（如调用processManager.scheduleNext()）。
     */
    public void wakeUp() {
        // 唤醒方法 (本次修复不需要额外逻辑，定时器会自动轮询)
    }
}
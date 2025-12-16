package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.Process;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler
{
    private final ProcessManager processManager;
    private final DeviceManager deviceManager;
    private ScheduledExecutorService exec;
    private boolean running = false;
    private long systemClock = 0;
    private CPU cpu;

    public Scheduler(ProcessManager processManager, DeviceManager deviceManager)
    {
        this.processManager = processManager;
        this.deviceManager = deviceManager;
    }

    public void start()
    {
        if (running) return;
        running = true;

        exec = Executors.newSingleThreadScheduledExecutor();

        exec.scheduleAtFixedRate(() ->
        {
            try
            {
                systemClock++;

                // 动态获取 CPU 实例
                if (cpu == null) {
                    if (Kernel.getInstance() != null) {
                        cpu = Kernel.getInstance().getCPU();
                    }
                }

                // -----------------------------------------------------------
                // 【核心修复】抢占 IDLE 进程的逻辑
                // -----------------------------------------------------------
                Process current = processManager.getRunning();

                // 判断条件 1: CPU 当前完全空闲 (null)
                boolean isCpuEmpty = (current == null);

                // 判断条件 2: CPU 正在跑 IDLE 闲逛进程 (PID == -1)
                boolean isRunningIdle = (current != null && current.getPcb().getPid() == -1);

                // 判断条件 3: 就绪队列里有正经进程在排队
                boolean hasReadyProcess = !processManager.getReadyQueue().isEmpty();

                // 决策：如果 (CPU空) 或者 (正在闲逛 且 有人排队)，则立即触发调度
                if (isCpuEmpty || (isRunningIdle && hasReadyProcess))
                {
                    processManager.scheduleNext();
                    // 调度后，刷新一下 current 变量，确保下面执行的是新进程
                    current = processManager.getRunning();
                }

                // -----------------------------------------------------------
                // 执行指令
                // -----------------------------------------------------------
                if (current != null && cpu != null)
                {
                    cpu.executeOne();
                }

                // 更新设备状态
                deviceManager.tick();

                // 性能监控
                if (Kernel.getInstance() != null && Kernel.getInstance().getPerformanceMonitor() != null) {
                    // 如果是 IDLE 进程，CPU 利用率记为 0.0，否则记为 1.0
                    double cpuUtil = (current != null && current.getPcb().getPid() != -1) ? 1.0 : 0.0;

                    double memUsage = 0.0;
                    if (Kernel.getInstance().getMemoryManager() != null) {
                        memUsage = Kernel.getInstance().getMemoryManager().getMemoryUsageRate();
                    }

                    Kernel.getInstance().getPerformanceMonitor().recordSnapshot(
                            cpuUtil,
                            memUsage,
                            processManager.getProcesses().size(),
                            processManager.getReadyQueue().size(),
                            processManager.getBlockedQueue().size()
                    );
                }

            } catch (Exception e)
            {
                e.printStackTrace();
                System.err.println("调度器错误: " + e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    public void stop()
    {
        running = false;
        if (exec != null) {
            exec.shutdown();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public long getSystemClock() {
        return systemClock;
    }

    public void wakeUp() {
        // 唤醒方法 (本次修复不需要额外逻辑，定时器会自动轮询)
    }
}
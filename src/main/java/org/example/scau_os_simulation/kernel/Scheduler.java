package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.Process;

/**
 * 处理器调度器 - 负责 CPU 时间片的分配与流转
 * 【修正版】整合了 DeviceManager，支持 wait/notify 机制，解决新建进程无反应问题
 */
public class Scheduler implements Runnable {
    private final ProcessManager processManager;
    private final DeviceManager deviceManager;
    private final int timeSlice = 100; // 默认时间片 100ms
    private volatile boolean running = false;
    private Thread thread;

    // 锁对象，用于线程的 wait/notify
    private final Object lock = new Object();

    // 系统时钟（逻辑时钟）
    private long systemClock = 0;

    // 内部维护 CPU 实例
    private CPU cpu;

    /**
     * 构造函数
     * @param pm 进程管理器
     * @param dm 设备管理器（用于推进设备时间）
     */
    public Scheduler(ProcessManager pm, DeviceManager dm) {
        this.processManager = pm;
        this.deviceManager = dm;
        // 在内部初始化 CPU，确保依赖关系正确
        this.cpu = new CPU(pm, dm);
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "Scheduler-Thread");
        thread.start();
    }

    public void stop() {
        running = false;
        wakeUp(); // 停止时唤醒，防止卡死在 wait
    }

    /**
     * 唤醒调度器 (供 ProcessManager 创建新进程时调用)
     * 解决 ProcessManager 中的 "无法解析方法 wakeUp" 报错
     */
    public void wakeUp() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public long getSystemClock() {
        return systemClock;
    }

    // 获取 CPU 实例（供 Kernel 使用）
    public CPU getCPU() {
        return cpu;
    }


    @Override
    public void run() {
        while (running) {
            try {
                // 1. 检查是否需要休眠 (无进程且无 IDLE)
                // 如果就绪队列空，且没在运行，且没 IDLE 进程，则等待新进程创建
                synchronized (lock) {
                    while (processManager.getReadyQueue().isEmpty()
                            && processManager.getRunning() == null
                            && !processManager.hasIdleProcess()
                            && running) {
                        // 真的没事干了，进入休眠，等待 createProcess 调用 wakeUp()
                        lock.wait();
                    }
                }

                if (!running) break;

                // 2. 推进系统与设备时间
                systemClock++;
                if (deviceManager != null) {
                    deviceManager.tick();
                }

                // 3. 执行调度逻辑
                Process current = processManager.getRunning();
                boolean hasNewProcess = !processManager.getReadyQueue().isEmpty();

                // 判断当前运行的是否是 IDLE 进程
                boolean isIdleRunning = (current != null && current.getPcb().getPid() == -1);

                // 调度决策：如果没进程跑，或者跑完了，或者跑的是 IDLE 且有新进程来了 -> 切换
                if (current == null || current.isFinished() || (isIdleRunning && hasNewProcess)) {
                    if (hasNewProcess) {
                        // 有新进程，进行调度（ProcessManager.scheduleNext 会处理）
                        processManager.scheduleNext();
                    } else if (current == null || current.isFinished()) {
                        // 没新进程，跑 IDLE
                        if (processManager.hasIdleProcess()) {
                            processManager.contextSwitch(processManager.getIdleProcess());
                            // 让 CPU 执行 IDLE 的指令，避免空转
                            cpu.executeOne();
                        }
                    }
                } else {
                    // 正常运行当前进程
                    // 注意：这里调用 CPU 执行指令
                    if (cpu != null) {
                        cpu.executeOne();
                    }
                }

                // 4. 模拟时间流逝
                Thread.sleep(timeSlice);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
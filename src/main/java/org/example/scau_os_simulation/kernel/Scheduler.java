package org.example.scau_os_simulation.kernel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 操作系统调度器 - 多任务处理的心脏
 *
 * 职责：统一推进“时间片”，协调 CPU 与设备更新，并维护系统时钟。
 * - 周期任务（每 200ms）：systemClock++、CPU 执行一步、设备 tick。
 * - 提供 `start/stop` 控制系统的运行与暂停。
 */
public class Scheduler {
    /** 进程管理器：用于时间片轮转与获取当前运行进程 */
    private final ProcessManager processManager;
    /** 设备管理器：用于周期性推进设备计时与完成处理 */
    private final DeviceManager deviceManager;
    /** CPU 执行器：每个时间片执行一条指令 */
    private final CPU cpu;
    /** 调度线程池：单线程定时器，周期性推进系统时钟 */
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    /** 系统时钟（毫秒片计数，不是真实时间） */
    private long systemClock = 0;
    /** 是否处于运行状态 */
    private boolean running = false;

    /**
     * 调度器的构造函数
     *
     * @param pm 进程管理器，调度器需要通过它来了解和控制所有进程。
     * @param dm 设备管理器，调度器需要通过它来更新设备状态。
     */
    public Scheduler(ProcessManager pm, DeviceManager dm) {
        this.processManager = pm;
        this.deviceManager = dm;
        this.cpu = new CPU(pm, dm);
    }

    /**
     * 启动调度器
     *
     * 这个方法会启动一个无限循环的定时任务，模拟操作系统的运行。
     * 它就像按下了一个“开始工作”的按钮，整个调度中心开始运作。
     *
     * `scheduleAtFixedRate` 方法会创建一个周期性任务：
     * - `() -\u003e { ... }`：这是要执行的任务，代表一个“时间片”内发生的事情。
     *   - `systemClock++`：系统时钟前进一格。
     *   - `cpu.executeOne()`：CPU执行一条指令。
     *   - `deviceManager.tick()`：所有设备更新状态。
     * - `0`：初始延迟为0，即立即开始。
     * - `200`：每隔200毫秒执行一次任务。
     * - `TimeUnit.MILLISECONDS`：时间单位为毫秒。
     */
    public void start() {
        if (running) return;              // 避免重复启动
        running = true;
        processManager.scheduleNext();    // 启动前先挑选一个运行进程
        // 每 200ms 推进一次系统：执行一条指令并推进设备
        exec.scheduleAtFixedRate(() -> {
            try {
                systemClock++;            // 系统时钟步进（逻辑时钟）
                cpu.executeOne();         // CPU 执行一条指令（可能导致终止/阻塞/轮转）
                deviceManager.tick();     // 推进设备时间片并处理完成事件
            } catch (Exception ignored) {}
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止调度器
     *
     * 这个方法会立即停止调度循环，让整个系统暂停。
     * 就像按下了“紧急停止”按钮。
     */
    public void stop() {
        running = false;          // 修改运行标记
        exec.shutdownNow();       // 立即停止调度线程
    }

    /**
     * 获取当前系统时钟
     *
     * @return 系统时钟的当前值
     */
    public long getSystemClock() {
        return systemClock;
    }
}

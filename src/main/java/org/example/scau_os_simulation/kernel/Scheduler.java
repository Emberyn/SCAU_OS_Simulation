package org.example.scau_os_simulation.kernel;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    private final ProcessManager processManager;
    private final DeviceManager deviceManager;
    private final CPU cpu;
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    private long systemClock = 0;
    private boolean running = false;

    public Scheduler(ProcessManager pm, DeviceManager dm) {
        this.processManager = pm;
        this.deviceManager = dm;
        this.cpu = new CPU(pm, dm);
    }

    public void start() {
        if (running) return;
        running = true;
        processManager.scheduleNext();
        exec.scheduleAtFixedRate(() -> {
            try {
                systemClock++;
                cpu.executeOne();
                deviceManager.tick();
            } catch (Exception ignored) {}
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        running = false;
        exec.shutdownNow();
    }

    public long getSystemClock() {
        return systemClock;
    }
}


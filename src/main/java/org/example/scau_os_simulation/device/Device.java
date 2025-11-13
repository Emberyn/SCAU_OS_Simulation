package org.example.scau_os_simulation.device;

public class Device {
    private final DeviceType type;
    private boolean inUse;
    private int usedByPid;
    private int remainingTime;

    public Device(DeviceType type) {
        this.type = type;
        this.inUse = false;
        this.usedByPid = -1;
        this.remainingTime = 0;
    }

    public DeviceType getType() {
        return type;
    }

    public boolean isInUse() {
        return inUse;
    }

    public int getUsedByPid() {
        return usedByPid;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public boolean allocate(int pid, int time) {
        if (inUse) return false;
        inUse = true;
        usedByPid = pid;
        remainingTime = Math.max(0, time);
        return true;
    }

    public void tick() {
        if (!inUse) return;
        if (remainingTime > 0) remainingTime--;
    }

    public boolean isComplete() {
        return inUse && remainingTime <= 0;
    }

    public void release() {
        inUse = false;
        usedByPid = -1;
        remainingTime = 0;
    }
}


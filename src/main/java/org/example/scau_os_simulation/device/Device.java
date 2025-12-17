package org.example.scau_os_simulation.device;

/**
 * 设备 - 可被进程占用的有限资源
 * - 设备是系统中的共享硬件资源（如打印机、磁盘），同一时间只能被一个进程占用。
 * - 当设备被占用后，会在若干时间片内保持“忙碌”，时间片消耗为 0 时表示完成。
 */
public class Device
{
    private final DeviceType type;
    private boolean inUse;
    private int usedByPid;
    private int remainingTime;

    /**
     * 构造一个设备实例
     * @param type 设备类型（A/B/C）
     */
    public Device(DeviceType type)
    {
        this.type = type;
        this.inUse = false;
        this.usedByPid = -1;
        this.remainingTime = 0;
    }

    /**
     * 获取设备类型
     */
    public DeviceType getType()
    {
        return type;
    }

    /**
     * 设备是否被占用
     */
    public boolean isInUse()
    {
        return inUse;
    }

    public boolean isBusy()
    {
        return inUse;
    }

    /**
     * 当前占用设备的进程PID（未占用时为 -1）
     */
    public int getUsedByPid()
    {
        return usedByPid;
    }

    public int getCurrentUserPid()
    {
        return usedByPid;
    }

    /**
     * 当前占用还需的时间片数
     */
    public int getRemainingTime()
    {
        return remainingTime;
    }

    /**
     * 占用设备
     * @param pid  占用进程PID
     * @param time 预计占用时间片数
     * @return 是否成功占用（若设备已在使用中则返回 false）
     */
    public boolean allocate(int pid, int time)
    {
        if (inUse) return false;
        inUse = true;
        usedByPid = pid;
        remainingTime = Math.max(0, time);
        return true;
    }

    /**
     * 时间推进（剩余时间片递减）
     */
    public void tick()
    {
        if (!inUse) return;
        if (remainingTime > 0) remainingTime--;
    }

    /**
     * 是否完成（已占用且剩余时间不大于0）
     */
    public boolean isComplete()
    {
        return inUse && remainingTime <= 0;
    }

    /**
     * 释放设备占用
     */
    public void release()
    {
        inUse = false;
        usedByPid = -1;
        remainingTime = 0;
    }
}

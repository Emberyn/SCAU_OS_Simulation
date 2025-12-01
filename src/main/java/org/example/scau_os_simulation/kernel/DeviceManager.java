package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.process.PCB;

import java.util.*;

/**
 * 设备管理器 - 管理设备与请求队列
 * <p>
 * 职责总览：
 * - 管理不同类型（A/B/C）的设备集合与其等待队列；同类设备可能有多个。
 * - 提供统一的 `requestDevice/tick` 入口以支撑调度循环。
 * - 与 `ProcessManager` 协作，负责在设备占用/完成时更新进程状态与队列。
 */
public class DeviceManager
{
    /**
     * 每类设备的实体列表（同类可能有多个实例）
     */
    private final Map<DeviceType, List<Device>> devices = new EnumMap<>(DeviceType.class);
    /**
     * 每类设备的等待队列（FIFO），存放未能立即分配的请求
     */
    private final Map<DeviceType, Deque<DeviceRequest>> waitQueues = new EnumMap<>(DeviceType.class);
    /**
     * 进程管理器：用于更新进程的阻塞/就绪状态与队列
     */
    private final ProcessManager processManager;

    /**
     * 构造函数：初始化设备与等待队列
     */
    public DeviceManager(ProcessManager processManager)
    {
        this.processManager = processManager;
        // 初始化三类设备的存储容器
        devices.put(DeviceType.A, new ArrayList<>());
        devices.put(DeviceType.B, new ArrayList<>());
        devices.put(DeviceType.C, new ArrayList<>());
        // 初始化对应的等待队列
        waitQueues.put(DeviceType.A, new ArrayDeque<>());
        waitQueues.put(DeviceType.B, new ArrayDeque<>());
        waitQueues.put(DeviceType.C, new ArrayDeque<>());
        // 预置设备数量：A 类 2 台，B 类 3 台，C 类 3 台
        for (int i = 0; i < 2; i++) devices.get(DeviceType.A).add(new Device(DeviceType.A));
        for (int i = 0; i < 3; i++) devices.get(DeviceType.B).add(new Device(DeviceType.B));
        for (int i = 0; i < 3; i++) devices.get(DeviceType.C).add(new Device(DeviceType.C));
    }

    /**
     * 申请设备
     * 若存在空闲设备则立即分配并将进程置为阻塞；否则加入等待队列。
     *
     * @param pid       申请进程PID
     * @param type      设备类型
     * @param timeUnits 预计使用时间片数量
     * @return 是否成功立即分配
     */
    public boolean requestDevice(int pid, DeviceType type, int timeUnits)
    {
        // 尝试立即分配同类中的空闲设备
        for (Device d : devices.get(type))
        {
            if (!d.isInUse())
            {
                d.allocate(pid, timeUnits);                       // 标记设备被该进程占用
                PCB pcb = processManager.findProcess(pid).getPcb();
                pcb.setState(org.example.scau_os_simulation.process.ProcessState.BLOCKED); // 进程阻塞，等待设备完成
                pcb.setBlockReason(type.name());
                processManager.onProcessBlocked(pid);             // 从就绪队列移至阻塞队列
                return true;                                      // 立即分配成功
            }
        }
        // 无空闲设备：入队等待
        waitQueues.get(type).addLast(new DeviceRequest(pid, type, timeUnits));
        PCB pcb = processManager.findProcess(pid).getPcb();
        pcb.setState(org.example.scau_os_simulation.process.ProcessState.BLOCKED);
        pcb.setBlockReason(type.name());
        processManager.onProcessBlocked(pid);
        return false;                                             // 暂未分配（进入等待队列）
    }

    /**
     * 时间推进
     * <p>
     * 所有设备 remainingTime 递减；完成时触发进程解阻并尝试分配等待队列。
     */
    public void tick()
    {
        // 时间推进：遍历所有设备，递减占用剩余时间；完成后释放并尝试从等待队列分配
        for (DeviceType t : devices.keySet())
        {
            for (Device d : devices.get(t))
            {
                d.tick();
                if (d.isComplete())
                {
                    int pid = d.getUsedByPid();   // 记录完成的进程 PID
                    d.release();                  // 释放设备占用
                    allocateFromQueue(t);         // 尝试从对应等待队列分配给下一位请求者
                    processManager.onDeviceComplete(pid); // 使进程解阻并回到就绪队列
                }
            }
        }
    }

    /**
     * 从等待队列尝试分配空闲设备
     */
    private void allocateFromQueue(DeviceType type)
    {
        // 尝试为指定类型的空闲设备分配队头请求（若存在）
        Deque<DeviceRequest> q = waitQueues.get(type);
        for (Device d : devices.get(type))
        {
            if (!d.isInUse())
            {
                DeviceRequest req = q.pollFirst(); // 取队头请求（FIFO）
                if (req != null)
                {
                    d.allocate(req.pid(), req.timeUnits()); // 分配设备给该请求
                }
            }
        }
    }


    /**
     * 获取设备清单
     */
    public Map<DeviceType, List<Device>> getDevices()
    {
        return devices;
    }


    /**
     * 获取设备等待队列
     */
    public Map<DeviceType, Deque<DeviceRequest>> getWaitQueues()
    {
        return waitQueues;
    }

    public java.util.List<Device> getAllDevices()
    {
        java.util.List<Device> list = new java.util.ArrayList<>();
        for (java.util.List<Device> ds : devices.values()) list.addAll(ds);
        return list;
    }

    public java.util.Deque<DeviceRequest> getWaitingQueue(DeviceType type)
    {
        return waitQueues.get(type);
    }
}

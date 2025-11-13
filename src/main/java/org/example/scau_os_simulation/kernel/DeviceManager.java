package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.process.PCB;

import java.util.*;

public class DeviceManager {
    private final Map<DeviceType, List<Device>> devices = new EnumMap<>(DeviceType.class);
    private final Map<DeviceType, Deque<DeviceRequest>> waitQueues = new EnumMap<>(DeviceType.class);
    private final ProcessManager processManager;

    public DeviceManager(ProcessManager processManager) {
        this.processManager = processManager;
        devices.put(DeviceType.A, new ArrayList<>());
        devices.put(DeviceType.B, new ArrayList<>());
        devices.put(DeviceType.C, new ArrayList<>());
        waitQueues.put(DeviceType.A, new ArrayDeque<>());
        waitQueues.put(DeviceType.B, new ArrayDeque<>());
        waitQueues.put(DeviceType.C, new ArrayDeque<>());
        for (int i = 0; i < 2; i++) devices.get(DeviceType.A).add(new Device(DeviceType.A));
        for (int i = 0; i < 3; i++) devices.get(DeviceType.B).add(new Device(DeviceType.B));
        for (int i = 0; i < 3; i++) devices.get(DeviceType.C).add(new Device(DeviceType.C));
    }

    public boolean requestDevice(int pid, DeviceType type, int timeUnits) {
        for (Device d : devices.get(type)) {
            if (!d.isInUse()) {
                d.allocate(pid, timeUnits);
                PCB pcb = processManager.findProcess(pid).getPcb();
                pcb.setState(org.example.scau_os_simulation.process.ProcessState.BLOCKED);
                pcb.setBlockReason(type.name());
                processManager.onProcessBlocked(pid);
                return true;
            }
        }
        waitQueues.get(type).addLast(new DeviceRequest(pid, type, timeUnits));
        PCB pcb = processManager.findProcess(pid).getPcb();
        pcb.setState(org.example.scau_os_simulation.process.ProcessState.BLOCKED);
        pcb.setBlockReason(type.name());
        processManager.onProcessBlocked(pid);
        return false;
    }

    public void tick() {
        for (DeviceType t : devices.keySet()) {
            for (Device d : devices.get(t)) {
                d.tick();
                if (d.isComplete()) {
                    int pid = d.getUsedByPid();
                    d.release();
                    allocateFromQueue(t);
                    processManager.onDeviceComplete(pid);
                }
            }
        }
    }

    private void allocateFromQueue(DeviceType type) {
        Deque<DeviceRequest> q = waitQueues.get(type);
        for (Device d : devices.get(type)) {
            if (!d.isInUse()) {
                DeviceRequest req = q.pollFirst();
                if (req != null) {
                    d.allocate(req.getPid(), req.getTimeUnits());
                }
            }
        }
    }

    public Map<DeviceType, List<Device>> getDevices() {
        return devices;
    }

    public Map<DeviceType, Deque<DeviceRequest>> getWaitQueues() {
        return waitQueues;
    }
}


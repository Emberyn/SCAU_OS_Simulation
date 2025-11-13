package org.example.scau_os_simulation.device;

public class DeviceRequest {
    private final int pid;
    private final DeviceType type;
    private final int timeUnits;

    public DeviceRequest(int pid, DeviceType type, int timeUnits) {
        this.pid = pid;
        this.type = type;
        this.timeUnits = timeUnits;
    }

    public int getPid() {
        return pid;
    }

    public DeviceType getType() {
        return type;
    }

    public int getTimeUnits() {
        return timeUnits;
    }
}


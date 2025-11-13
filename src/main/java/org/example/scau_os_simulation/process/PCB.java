package org.example.scau_os_simulation.process;

public class PCB {
    private int pid;
    private String name;
    private int priority;
    private ProcessState state;
    private int memoryAddress;
    private int memorySize;
    private int ax;
    private int pc;
    private String ir;
    private int timeSlice;
    private String blockReason;
    
    public PCB(int pid, String name, int priority, int memoryAddress, int memorySize) {
        this.pid = pid;
        this.name = name;
        this.priority = priority;
        this.state = ProcessState.NEW;
        this.memoryAddress = memoryAddress;
        this.memorySize = memorySize;
        this.ax = 0;
        this.pc = 0;
        this.ir = "";
        this.timeSlice = 6;
        this.blockReason = "";
    }
    
    public int getPid() {
        return pid;
    }
    
    public String getName() {
        return name;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public ProcessState getState() {
        return state;
    }
    
    public void setState(ProcessState state) {
        this.state = state;
    }
    
    public int getMemoryAddress() {
        return memoryAddress;
    }
    
    public int getMemorySize() {
        return memorySize;
    }

    public int getAx() {
        return ax;
    }

    public void setAx(int ax) {
        this.ax = Math.max(0, Math.min(255, ax));
    }

    public int getPc() {
        return pc;
    }

    public void setPc(int pc) {
        this.pc = pc;
    }

    public String getIr() {
        return ir;
    }

    public void setIr(String ir) {
        this.ir = ir;
    }

    public int getTimeSlice() {
        return timeSlice;
    }

    public void resetTimeSlice() {
        this.timeSlice = 6;
    }

    public void decTimeSlice() {
        this.timeSlice = Math.max(0, this.timeSlice - 1);
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }
}

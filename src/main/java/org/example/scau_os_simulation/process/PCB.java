package org.example.scau_os_simulation.process;

public class PCB {
    private int pid;
    private String name;
    private int priority;
    private ProcessState state;
    private int memoryAddress;
    private int memorySize;
    
    public PCB(int pid, String name, int priority, int memoryAddress, int memorySize) {
        this.pid = pid;
        this.name = name;
        this.priority = priority;
        this.state = ProcessState.NEW;
        this.memoryAddress = memoryAddress;
        this.memorySize = memorySize;
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
}
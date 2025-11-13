package org.example.scau_os_simulation.process;

public class Process {
    private final PCB pcb;
    private Executable executable;
    
    public Process(PCB pcb) {
        this.pcb = pcb;
        this.executable = null;
    }

    public void setExecutable(Executable executable) {
        this.executable = executable;
    }

    public Executable getExecutable() {
        return executable;
    }
    
    public void run() {
        pcb.setState(ProcessState.RUNNING);
    }
    
    public void block() {
        pcb.setState(ProcessState.BLOCKED);
    }
    
    public void ready() {
        pcb.setState(ProcessState.READY);
    }
    
    public void terminate() {
        pcb.setState(ProcessState.TERMINATED);
    }
    
    public PCB getPcb() {
        return pcb;
    }
}

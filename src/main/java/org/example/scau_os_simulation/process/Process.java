package org.example.scau_os_simulation.process;

public class Process {
    private PCB pcb;
    
    public Process(PCB pcb) {
        this.pcb = pcb;
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
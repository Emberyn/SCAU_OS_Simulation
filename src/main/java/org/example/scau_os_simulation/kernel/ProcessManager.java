package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

import java.util.*;

public class ProcessManager {
    private final List<Process> processes;
    private final MemoryManager memoryManager;
    private int nextPid = 0;
    private final Deque<Process> readyQueue = new ArrayDeque<>();
    private final Deque<Process> blockedQueue = new ArrayDeque<>();
    private Process running;
    
    public ProcessManager(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        this.processes = new ArrayList<>();
    }
    
    public Process createProcess(String name, int priority) {
        // 分配内存
        int memorySize = 64; // 默认64KB
        int memoryAddress = memoryManager.allocateMemory(memorySize);
        
        if (memoryAddress < 0) {
            System.out.println("内存不足，无法创建进程");
            return null;
        }
        
        // 创建PCB
        PCB pcb = new PCB(nextPid++, name, priority, memoryAddress, memorySize);
        
        // 创建进程
        Process process = new Process(pcb);
        processes.add(process);
        process.ready();
        readyQueue.addLast(process);
        
        System.out.println("创建进程: " + name + ", PID: " + pcb.getPid());
        return process;
    }
    
    public void terminateProcess(int pid) {
        Process process = findProcess(pid);
        if (process != null) {
            // 释放内存
            memoryManager.freeMemory(process.getPcb().getMemoryAddress(), process.getPcb().getMemorySize());
            
            // 移除进程
            processes.remove(process);
            System.out.println("终止进程: " + process.getPcb().getName() + ", PID: " + pid);
            if (running == process) running = null;
            readyQueue.remove(process);
            blockedQueue.remove(process);
        }
    }
    
    public void terminateAllProcesses() {
        List<Process> processesCopy = new ArrayList<>(processes);
        for (Process process : processesCopy) {
            terminateProcess(process.getPcb().getPid());
        }
    }
    
    public Process findProcess(int pid) {
        for (Process process : processes) {
            if (process.getPcb().getPid() == pid) {
                return process;
            }
        }
        return null;
    }
    
    public List<Process> getProcesses() {
        return processes;
    }

    public Deque<Process> getReadyQueue() {
        return readyQueue;
    }

    public Deque<Process> getBlockedQueue() {
        return blockedQueue;
    }

    public Process getRunning() {
        return running;
    }

    public void scheduleNext() {
        if (running != null) {
            running.ready();
            readyQueue.addLast(running);
        }
        Process next = readyQueue.pollFirst();
        if (next == null) return;
        running = next;
        running.run();
        running.getPcb().resetTimeSlice();
    }

    public void onTimeSliceEnd() {
        scheduleNext();
    }

    public void onProcessBlocked(int pid) {
        Process p = findProcess(pid);
        if (p == null) return;
        readyQueue.remove(p);
        blockedQueue.addLast(p);
    }

    public void onDeviceComplete(int pid) {
        Process p = findProcess(pid);
        if (p == null) return;
        blockedQueue.remove(p);
        p.ready();
        readyQueue.addLast(p);
    }
}

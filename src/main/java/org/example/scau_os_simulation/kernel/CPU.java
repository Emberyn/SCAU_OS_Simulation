package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

public class CPU {
    private final ProcessManager processManager;
    private final DeviceManager deviceManager;

    public CPU(ProcessManager pm, DeviceManager dm) {
        this.processManager = pm;
        this.deviceManager = dm;
    }

    public void executeOne() {
        Process running = processManager.getRunning();
        if (running == null) return;
        PCB pcb = running.getPcb();
        String instr = running.getExecutable() == null ? "end" : running.getExecutable().fetch(pcb.getPc());
        pcb.setIr(instr);
        if (instr.startsWith("end")) {
            processManager.terminateProcess(pcb.getPid());
            processManager.scheduleNext();
            return;
        }
        if (instr.contains("=") && instr.matches("[a-zA-Z]=\\d{1,2}")) {
            String num = instr.substring(instr.indexOf('=') + 1);
            try { pcb.setAx(Integer.parseInt(num)); } catch (Exception ignored) {}
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }
        if (instr.endsWith("++")) {
            pcb.setAx(pcb.getAx() + 1);
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }
        if (instr.endsWith("--")) {
            pcb.setAx(pcb.getAx() - 1);
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }
        if (instr.matches("!.[0-9]")) {
            char dev = instr.charAt(1);
            int t = Character.digit(instr.charAt(2), 10);
            DeviceType type = dev == 'A' ? DeviceType.A : dev == 'B' ? DeviceType.B : DeviceType.C;
            deviceManager.requestDevice(pcb.getPid(), type, t);
            pcb.setPc(pcb.getPc() + 1);
            processManager.scheduleNext();
            return;
        }
        pcb.setPc(pcb.getPc() + 1);
        pcb.decTimeSlice();
        if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
    }
}


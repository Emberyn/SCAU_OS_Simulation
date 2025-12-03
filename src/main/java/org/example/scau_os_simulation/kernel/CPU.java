package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

/**
 * CPU模拟器 - 执行指令并驱动调度
 * <p>
 * 使用说明（面向初学者）：
 * - 这里的 CPU 不是真实硬件，而是一个“解释器”，它根据当前运行进程的脚本逐条执行。
 * - 指令是纯文本格式（见下方集合），CPU 会根据 PCB 的 `pc`（程序计数器）从可执行文件取指。
 * - 每执行一条指令，都会更新寄存器（AX/IR/PC）与时间片（timeSlice），必要时触发调度器切换进程。
 * <p>
 * 简化的指令集合与语义：
 * - `end`            结束进程（立刻终止并从就绪/运行队列移除，随后调用调度选择下一个进程）
 * - `x=NN`           将 AX 寄存器置为数值 NN （0-99），随后 PC+1、时间片-1（耗费一次执行）
 * - `x++` / `x--`    AX 自增/自减，随后 PC+1、时间片-1；时间片用尽会触发进程轮转
 * - `!A3` / `!B2`    发起设备请求（A/B/C），占用对应时间片数；请求后进程进入阻塞状态并切换到下一个就绪进程
 * - 其他文本         视为“空操作”或未识别指令：仅 PC+1、时间片-1（用于占位与演示）
 * <p>
 * 执行流程小结：
 * 1. 获取当前运行进程与其 PCB；若不存在运行进程，直接返回（CPU空转）。
 * 2. 依据 PCB 的 PC 从可执行文件取指并写入 IR（当前指令文本）。
 * 3. 匹配指令类型并执行对应动作（更新 AX/PC/时间片、终止或阻塞）。
 * 4. 当时间片耗尽（降为 0）时，通知进程管理器进行时间片轮转（切换下一个进程）。
 */
public class CPU
{
    /**
     * 进程管理器：用于查询/切换当前运行进程与维护队列
     */
    private final ProcessManager processManager;
    /**
     * 设备管理器：用于处理设备请求与阻塞/解阻流程
     */
    private final DeviceManager deviceManager;

    /**
     * 构造函数
     *
     * @param pm 进程管理器（CPU 需要通过它获取当前运行进程并进行调度）
     * @param dm 设备管理器（CPU 在遇到设备指令时需要发起设备请求）
     */
    public CPU(ProcessManager pm, DeviceManager dm)
    {
        this.processManager = pm;
        this.deviceManager = dm;
    }

    /**
     * 执行一条指令
     * <p>
     * 详细步骤：
     * - 读取当前运行进程，若无则直接返回（系统可能暂时空闲）。
     * - 根据 PCB 的 PC 从可执行文件 `Executable` 中取指，将文本写入 PCB 的 IR。
     * - 解析 IR：按上文指令集合规则执行，对 AX/PC/时间片进行更新；
     * - 终止指令会调用进程终止并立刻调度下一个进程；
     * - 设备指令会发起设备请求并阻塞当前进程，然后调度下一个进程；
     * - 普通计算或空操作会消耗一个时间片，时间片用尽触发切换。
     */
    /**
     * 执行一条指令（修复版）
     */
    public void executeOne()
    {
        // 读取当前运行进程；若系统暂时没有运行进程（队列为空），直接返回
        Process running = processManager.getRunning();
        if (running == null) return;

        PCB pcb = running.getPcb();
        String instr = running.getExecutable() == null ? "end" : running.getExecutable().fetch(pcb.getPc());

        pcb.setIr(instr); // 更新 IR 显示

        if (instr.startsWith("end"))
        {
            Kernel.getInstance().logOutput("进程 PID=" + pcb.getPid() + " 结束，AX=" + pcb.getAx());
            processManager.terminateProcess(pcb.getPid());
            // 注意：terminateProcess 内部通常会调用 scheduleNext，但为了保险这里也可以显式调用
            // processManager.scheduleNext();
            return;
        }

        // 【修复点】正则改为 \\d{1,3} 以支持 0-255 的数值 (例如 x=100)
        if (instr.contains("=") && instr.matches("[a-zA-Z]=\\d{1,3}"))
        {
            String num = instr.substring(instr.indexOf('=') + 1);
            try
            {
                pcb.setAx(Integer.parseInt(num));
            } catch (Exception ignored)
            {
            }
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }

        if (instr.endsWith("++"))
        {
            pcb.setAx(pcb.getAx() + 1);
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }

        if (instr.endsWith("--"))
        {
            pcb.setAx(pcb.getAx() - 1);
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }

        if (instr.matches("!.[0-9]"))
        {
            char dev = instr.charAt(1);
            int t = Character.digit(instr.charAt(2), 10);
            DeviceType type = dev == 'A' ? DeviceType.A : dev == 'B' ? DeviceType.B : DeviceType.C;

            // 请求设备
            boolean success = deviceManager.requestDevice(pcb.getPid(), type, t);

            pcb.setPc(pcb.getPc() + 1); // 无论是否立即成功，PC都前移，防止死循环执行同一条请求指令

            // 如果没有立即分配成功（进入等待队列），或者为了模拟阻塞效果，这里通常会切出 CPU
            // 根据你的逻辑，requestDevice 内部会把进程设为 BLOCKED
            processManager.scheduleNext();
            return;
        }

        // 信号量操作
        if (instr.startsWith("wait(") && instr.endsWith(")"))
        {
            String semaphoreName = instr.substring(5, instr.length() - 1);
            boolean acquired = Kernel.getInstance().getSyncManager().wait(semaphoreName, pcb.getPid());
            if (!acquired)
            {
                pcb.setBlockReason("等待信号量:" + semaphoreName);
                processManager.onProcessBlocked(pcb.getPid());
                return; // 阻塞后直接返回，不由 CPU 继续执行后续逻辑
            }
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }

        if (instr.startsWith("signal(") && instr.endsWith(")"))
        {
            String semaphoreName = instr.substring(7, instr.length() - 1);
            int awakenedPid = Kernel.getInstance().getSyncManager().signal(semaphoreName);
            if (awakenedPid != -1)
            {
                processManager.onDeviceComplete(awakenedPid);
            }
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
            return;
        }

        // 默认操作
        pcb.setPc(pcb.getPc() + 1);
        pcb.decTimeSlice();
        if (pcb.getTimeSlice() == 0) processManager.onTimeSliceEnd();
    }
}

package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.process.Process;

/**
 * CPU模拟器类
 * 功能：模拟CPU执行指令的逻辑，包括指令解析、寄存器操作、时间片管理、进程调度触发以及设备请求/信号量同步处理
 * 设计思路：采用"解释型"执行方式，逐条读取并执行进程的指令，通过与进程管理器/设备管理器/同步管理器的交互完成OS核心调度逻辑
 */
public class CPU
{
    /**
     * 进程管理器引用：用于获取当前运行进程、执行进程终止/阻塞/调度等操作
     * CPU本身不管理进程队列，仅通过进程管理器完成进程状态的修改和切换
     */
    private final ProcessManager processManager;

    /**
     * 设备管理器引用：用于处理设备请求（如I/O请求），完成进程与设备的交互逻辑
     * 当CPU执行设备指令时，通过设备管理器发起请求并处理阻塞逻辑
     */
    private final DeviceManager deviceManager;

    /**
     * CPU构造函数：初始化CPU与进程管理器、设备管理器的关联
     * @param pm 进程管理器实例（由内核初始化并传入，保证系统内唯一）
     * @param dm 设备管理器实例（由内核初始化并传入，保证系统内唯一）
     */
    public CPU(ProcessManager pm, DeviceManager dm)
    {
        this.processManager = pm;
        this.deviceManager = dm;
    }

    /**
     * 核心方法：执行一条CPU指令（单次指令周期）
     * 执行流程：
     * 1. 获取当前运行进程，无进程则CPU空转返回
     * 2. 从进程的可执行文件中读取PC指向的指令，写入PCB的IR寄存器
     * 3. 按指令类型解析执行：处理进程终止、数据操作、设备请求、信号量同步等逻辑
     * 4. 管理时间片消耗，时间片耗尽时触发进程轮转调度
     * 5. 处理进程阻塞/终止后的调度切换，保证CPU持续工作
     */
    public void executeOne()
    {
        // 1. 获取当前运行进程：若系统无运行进程（如所有进程阻塞/终止），CPU空转
        Process running = processManager.getRunning();
        if (running == null) {
            return;
        }

        // 2. 读取进程的PCB和当前指令：PC（程序计数器）指向待执行指令的位置
        PCB pcb = running.getPcb();
        // 容错处理：若进程无执行文件，默认执行end指令终止进程
        String instr = running.getExecutable() == null ? "end" : running.getExecutable().fetch(pcb.getPc());
        pcb.setIr(instr); // 将当前指令写入IR（指令寄存器），用于调试和状态显示

        // 3. 指令解析与执行：按指令类型分支处理
        // 3.1 终止指令：end - 终止当前进程并触发调度
        if (instr.startsWith("end"))
        {
            // 记录进程终止日志，包含PID和最终AX寄存器值
            Kernel.getInstance().logOutput("进程 PID=" + pcb.getPid() + " 结束，AX=" + pcb.getAx());
            // 通知进程管理器终止该进程（移除队列、释放资源）
            processManager.terminateProcess(pcb.getPid());
            // 终止进程后直接返回，进程管理器内部会触发下一个进程调度
            return;
        }

        // 3.2 赋值指令：x=NN（支持0-255的数值）- 设置AX寄存器值
        // 正则匹配规则：[a-zA-Z]=\\d{1,3} 匹配字母=数字（1-3位），如x=12、a=255
        if (instr.contains("=") && instr.matches("[a-zA-Z]=\\d{1,3}"))
        {
            // 提取等号后的数值字符串
            String num = instr.substring(instr.indexOf('=') + 1);
            try
            {
                // 将数值转换为整数并设置AX寄存器
                pcb.setAx(Integer.parseInt(num));
            } catch (Exception ignored)
            {
                // 数值转换失败时忽略（容错处理：视为空操作）
            }
            pcb.setPc(pcb.getPc() + 1); // PC+1：指向下一条指令
            pcb.decTimeSlice(); // 时间片-1：消耗一个CPU周期
            // 时间片耗尽时，通知进程管理器执行轮转调度
            if (pcb.getTimeSlice() == 0) {
                processManager.onTimeSliceEnd();
            }
            return;
        }

        // 3.3 自增指令：x++ - AX寄存器值+1
        if (instr.endsWith("++"))
        {
            pcb.setAx(pcb.getAx() + 1); // AX寄存器自增
            pcb.setPc(pcb.getPc() + 1); // PC指向下一条指令
            pcb.decTimeSlice(); // 消耗时间片
            // 时间片耗尽触发调度
            if (pcb.getTimeSlice() == 0) {
                processManager.onTimeSliceEnd();
            }
            return;
        }

        // 3.4 自减指令：x-- - AX寄存器值-1
        if (instr.endsWith("--"))
        {
            pcb.setAx(pcb.getAx() - 1); // AX寄存器自减
            pcb.setPc(pcb.getPc() + 1); // PC指向下一条指令
            pcb.decTimeSlice(); // 消耗时间片
            // 时间片耗尽触发调度
            if (pcb.getTimeSlice() == 0) {
                processManager.onTimeSliceEnd();
            }
            return;
        }

        // 3.5 设备请求指令：!A30、!B50等（格式：!+设备类型+占用时间）
        if (instr.matches("!.[0-9]+"))
        {
            // 提取设备类型字符（第2个字符）
            char dev = instr.charAt(1);

            // 【修改】提取占用时间（从第3个字符开始截取到末尾，支持多位数）
            int t = Integer.parseInt(instr.substring(2));

            // 转换为设备类型枚举
            DeviceType type = dev == 'A' ? DeviceType.A : (dev == 'B' ? DeviceType.B : DeviceType.C);

            // 向设备管理器发起设备请求
            boolean success = deviceManager.requestDevice(pcb.getPid(), type, t);

            // 关键：PC+1，防止死循环
            pcb.setPc(pcb.getPc() + 1);

            // 触发进程调度
            processManager.scheduleNext();
            return;
        }

        // 3.6 信号量等待指令：wait(信号量名) - 申请信号量，若不可用则阻塞
        if (instr.startsWith("wait(") && instr.endsWith(")"))
        {
            // 提取信号量名称（去掉wait(和)）
            String semaphoreName = instr.substring(5, instr.length() - 1);
            // 向同步管理器发起wait操作：返回是否成功获取信号量
            boolean acquired = Kernel.getInstance().getSyncManager().wait(semaphoreName, pcb.getPid());
            if (!acquired)
            {
                // 未获取到信号量：设置进程阻塞原因，并通知进程管理器处理阻塞
                pcb.setBlockReason("等待信号量:" + semaphoreName);
                processManager.onProcessBlocked(pcb.getPid());
                return; // 阻塞后直接返回，CPU切换到其他进程
            }
            // 成功获取信号量：执行常规指令处理
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();
            if (pcb.getTimeSlice() == 0) {
                processManager.onTimeSliceEnd();
            }
            return;
        }

        // 3.7 信号量释放指令：signal(信号量名) - 释放信号量，唤醒等待进程
        if (instr.startsWith("signal(") && instr.endsWith(")"))
        {
            // 提取信号量名称（去掉signal(和)）
            String semaphoreName = instr.substring(7, instr.length() - 1);
            // 向同步管理器发起signal操作：返回被唤醒的进程PID（无则为-1）
            int awakenedPid = Kernel.getInstance().getSyncManager().signal(semaphoreName);
            if (awakenedPid != -1)
            {
                // 唤醒进程：通知进程管理器将其从阻塞态转为就绪态
                processManager.onDeviceComplete(awakenedPid);
            }
            // 释放信号量后执行常规指令处理
            pcb.setPc(pcb.getPc() + 1);
            pcb.decTimeSlice();

            if (pcb.getTimeSlice() == 0) {
                processManager.onTimeSliceEnd();
            }
            return;
        }

        // 3.8 默认操作：未识别指令（空操作）- 仅消耗时间片和PC前移
        pcb.setPc(pcb.getPc() + 1); // PC指向下一条指令
        pcb.decTimeSlice(); // 消耗时间片
        // 时间片耗尽触发调度
        if (pcb.getTimeSlice() == 0) {
            processManager.onTimeSliceEnd();
        }
    }
}
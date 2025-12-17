package org.example.scau_os_simulation.process;

/**
 * 进程对象 - 封装PCB与可执行文件
 * <p>
 * 说明：
 * - 进程是操作系统调度的基本单位；本类持有其“档案”（PCB）和“任务脚本”（Executable）。
 * - 提供若干状态切换方法，供调度器/CPU/设备管理器驱动。
 */
public class Process
{
    private final PCB pcb;
    private Executable executable;

    /**
     * 构造函数：绑定一份 PCB 档案
     */
    public Process(PCB pcb)
    {
        this.pcb = pcb;
        this.executable = null;
    }

    /**
     * 绑定可执行文件（指令序列）
     */
    public void setExecutable(Executable executable)
    {
        this.executable = executable;
    }

    /**
     * 获取可执行文件
     */
    public Executable getExecutable()
    {
        return executable;
    }

    /**
     * 判断进程是否已结束
     */
    public boolean isFinished()
    {
        return pcb.getState() == ProcessState.TERMINATED;
    }

    /**
     * 计算进程剩余需要的总时间片（估算值）
     * 逻辑：遍历 PC 之后的所有指令
     * - 普通指令 (x++)：算 1 个时间片
     * - IO 指令 (!A10)：算 IO 持续时间 (10)
     */
    public int getRemainingTime() {
        // 如果没有加载可执行文件，或者已经结束
        if (executable == null || pcb == null) {
            return 0;
        }

        int currentPC = pcb.getPc();
        int totalInstructions = executable.length();

        // 如果已经执行完了
        if (currentPC >= totalInstructions) {
            return 0;
        }

        int estimatedTime = 0;

        // 预判未来：从当前指令开始扫描到最后
        for (int i = currentPC; i < totalInstructions; i++) {
            String instruction = executable.fetch(i);

            if (instruction == null) continue;

            // 检查是否是 IO 指令 (以 '!' 开头，例如 "!A20")
            if (instruction.startsWith("!")) {
                try {
                    // 解析设备指令后面的数字
                    // 假设格式是 !<设备号><时间>，如 !A20
                    // 去掉 '!' 和 设备标识符(1位)，截取后面的数字
                    if (instruction.length() > 2) {
                        String durationStr = instruction.substring(2);
                        int duration = Integer.parseInt(durationStr);
                        estimatedTime += duration;
                    } else {
                        estimatedTime += 1; // 格式不对当作普通指令
                    }
                } catch (NumberFormatException e) {
                    estimatedTime += 1; // 解析失败当作普通指令
                }
            }
            else if (instruction.equals("end")) {
                break; // 遇到 end 就不算了
            }
            else {
                // 普通 CPU 指令，消耗 1 个时间片
                estimatedTime += 1;
            }
        }

        return estimatedTime;
    }





    /**
     * 置为运行态
     */
    public void run()
    {
        pcb.setState(ProcessState.RUNNING);
    }

    /**
     * 置为阻塞态
     */
    public void block()
    {
        pcb.setState(ProcessState.BLOCKED);
    }

    /**
     * 置为就绪态
     */
    public void ready()
    {
        pcb.setState(ProcessState.READY);
    }

    /**
     * 置为终止态
     */
    public void terminate()
    {
        pcb.setState(ProcessState.TERMINATED);
    }

    /**
     * 获取 PCB 档案
     */
    public PCB getPcb()
    {
        return pcb;
    }
}

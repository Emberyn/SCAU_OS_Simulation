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

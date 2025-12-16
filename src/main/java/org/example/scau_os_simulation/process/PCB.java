package org.example.scau_os_simulation.process;

/**
 * 进程控制块（PCB）- 记录进程运行所需的全部状态
 * <p>
 * 作用与结构：
 * - 这是进程的“身份证 + 档案袋”，调度器与CPU依据 PCB 决定运行、阻塞与切换。
 * - 寄存器模拟：`ax`（累加器）、`pc`（程序计数器）、`ir`（当前指令文本）。
 * - 调度参数：`priority`（优先级）、`timeSlice`（剩余时间片）。
 * - 资源占用：`memoryAddress/memorySize` 描述该进程在物理内存中的占位。
 */
public class PCB
{
    /**
     * 进程唯一标识
     */
    private int pid;
    /**
     * 进程名（用于 UI 展示）
     */
    private final String name;
    /**
     * 调度优先级（示例用途）
     */
    private final int basePriority;
    /**
     * 当前优先级（动态变化）
     */
    private int currentPriority;
    /**
     * 等待时间（用于动态优先级 aging）
     */
    private int waitingTime;
    /**
     * 当前状态（NEW/READY/RUNNING/BLOCKED/TERMINATED）
     */
    private ProcessState state;
    /**
     * 占用内存的起始地址（字节）
     */
    private final int memoryAddress;
    /**
     * 占用内存大小（KB）
     */
    private final int memorySize;
    /**
     * 模拟累加器寄存器 AX（限定 0-255）
     */
    private int ax;
    /**
     * 程序计数器（当前指令索引，0-based）
     */
    private int pc;
    /**
     * 当前指令文本（IR：Instruction Register）
     */
    private String ir;
    /**
     * 剩余时间片（降为 0 时触发轮转）
     */
    private int timeSlice;
    /**
     * 阻塞原因（设备类型等，用于 UI 展示）
     */
    private String blockReason;

    /**
     * 构造函数：初始化默认寄存器与时间片
     */
    public PCB(int pid, String name, int priority, int memoryAddress, int memorySize)
    {
        this.pid = pid;
        this.name = name;
        this.basePriority = priority;
        this.currentPriority = priority;
        this.waitingTime = 0;
        this.state = ProcessState.NEW;
        this.memoryAddress = memoryAddress;
        this.memorySize = memorySize;
        this.ax = 0;
        this.pc = 0;
        this.ir = "";
        this.timeSlice = 50;
        this.blockReason = "";
    }

    public int getPid()
    {
        return pid;
    }

    public String getName()
    {
        return name;
    }

    public int getPriority()
    {
        return currentPriority;
    }

    public int getBasePriority()
    {
        return basePriority;
    }

    public int getWaitingTime()
    {
        return waitingTime;
    }

    /**
     * 增加等待时间，并根据等待时间调整优先级（aging机制）
     * 每等待10个时间单位，优先级增加1，最高不超过基础优先级+5
     */
    public void incrementWaitingTime()
    {
        waitingTime++;
        // 每10个时间单位增加一次优先级
        if (waitingTime % 10 == 0)
        {
            currentPriority = Math.min(basePriority + 5, currentPriority + 1);
        }
    }

    /**
     * 重置等待时间和优先级（当进程开始运行时调用）
     */
    public void resetWaitingTime()
    {
        waitingTime = 0;
        currentPriority = basePriority;
    }

    public ProcessState getState()
    {
        return state;
    }

    public void setState(ProcessState state)
    {
        this.state = state;
    }

    public int getMemoryAddress()
    {
        return memoryAddress;
    }

    public int getMemorySize()
    {
        return memorySize;
    }

    public int getAx()
    {
        return ax;
    }

    /**
     * 设置累加器 AX（限定在 0-255）
     */
    public void setAx(int ax)
    {
        this.ax = Math.max(0, Math.min(255, ax));
    }

    public int getPc()
    {
        return pc;
    }

    public void setPc(int pc)
    {
        this.pc = pc;
    }

    public String getIr()
    {
        return ir;
    }

    /**
     * 设置当前指令文本（供 UI 展示与调试）
     */
    public void setIr(String ir)
    {
        this.ir = ir;
    }

    public int getTimeSlice()
    {
        return timeSlice;
    }

    /**
     * 重置时间片计数（默认 6）
     */
    public void resetTimeSlice()
    {
        this.timeSlice = 6;
    }

    /**
     * 时间片递减（不低于 0）
     */
    public void decTimeSlice()
    {
        this.timeSlice = Math.max(0, this.timeSlice - 1);
    }

    public String getBlockReason()
    {
        return blockReason;
    }

    public void setBlockReason(String blockReason)
    {
        this.blockReason = blockReason;
    }

    public javafx.beans.property.IntegerProperty pidProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(pid);
    }

    public javafx.beans.property.StringProperty nameProperty()
    {
        return new javafx.beans.property.SimpleStringProperty(name);
    }

    public javafx.beans.property.StringProperty stateProperty()
    {
        return new javafx.beans.property.SimpleStringProperty(state.name());
    }

    public javafx.beans.property.IntegerProperty priorityProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(currentPriority);
    }

    public javafx.beans.property.IntegerProperty memoryAddressProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(memoryAddress);
    }

    public javafx.beans.property.IntegerProperty memorySizeProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(memorySize);
    }

    // 【新增】时间片属性访问器
    public javafx.beans.property.IntegerProperty timeSliceProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(timeSlice);
    }
}

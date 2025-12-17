package org.example.scau_os_simulation.process;

import org.example.scau_os_simulation.memory.MemoryBlock;

/**
 * 进程控制块（PCB）- 记录进程运行所需的全部状态
 */
public class PCB
{
    private int pid;
    private final String name;
    private final int basePriority;
    private int currentPriority;
    private int waitingTime;
    private ProcessState state;
    // 【修改】替换原有的 int memoryAddress, memorySize
    private final MemoryBlock memoryBlock;
    private int ax;
    private int pc;
    private String ir;
    private int timeSlice;
    private String blockReason;

    /**
     * 总剩余运行时间（由外部计算后填入）
     */
    private int totalRemainingTime;

    /**
     * 构造函数：初始化默认寄存器与时间片
     */
    public PCB(int pid, String name, int priority, MemoryBlock memoryBlock)
    {
        this.pid = pid;
        this.name = name;
        this.basePriority = priority;
        this.currentPriority = priority;
        this.waitingTime = 0;
        this.state = ProcessState.NEW;
        this.memoryBlock = memoryBlock; // 【关键】保存引用


        this.ax = 0;
        this.pc = 0;
        this.ir = "";
        this.timeSlice = 10;
        this.blockReason = "";
        this.totalRemainingTime = 0;

    }

    // --- 新增：为了修复 MainController 报错必须添加的方法 ---

    /**
     * 获取总剩余时间数值
     */
    public int getTotalRemainingTime() {
        return totalRemainingTime;
    }

    /**
     * 【修复点 1】设置总剩余时间，供 MainController.java 第 1006 行调用
     */
    public void setTotalRemainingTime(int totalRemainingTime) {
        this.totalRemainingTime = totalRemainingTime;
    }

    /**
     * 【修复点 2】提供属性绑定，供 MainController.java 第 1186 行调用
     */
    public javafx.beans.property.IntegerProperty totalRemainingTimeProperty() {
        return new javafx.beans.property.SimpleIntegerProperty(totalRemainingTime);
    }

    // --- 原有方法保持不变 ---

    public int getPid() { return pid; }
    public String getName() { return name; }
    public int getPriority() { return currentPriority; }
    public int getBasePriority() { return basePriority; }
    public int getWaitingTime() { return waitingTime; }

    public void incrementWaitingTime() {
        waitingTime++;
        if (waitingTime % 10 == 0) {
            currentPriority = Math.min(basePriority + 5, currentPriority + 1);
        }
    }

    public void resetWaitingTime() {
        waitingTime = 0;
        currentPriority = basePriority;
    }

    public ProcessState getState() { return state; }
    public void setState(ProcessState state) { this.state = state; }
    // 【修改】从 Block 对象获取实时地址
    public int getMemoryAddress() {
        return memoryBlock != null ? memoryBlock.getStartAddress() : -1;
    }
    public int getMemorySize() {
        return memoryBlock != null ? memoryBlock.getSize() : 0;
    }
    // 新增 getter
    public MemoryBlock getMemoryBlock() {
        return memoryBlock;
    }
    public int getAx() { return ax; }
    public void setAx(int ax) { this.ax = Math.max(0, Math.min(255, ax)); }
    public int getPc() { return pc; }
    public void setPc(int pc) { this.pc = pc; }
    public String getIr() { return ir; }
    public void setIr(String ir) { this.ir = ir; }
    public int getTimeSlice() { return timeSlice; }
    public void resetTimeSlice() { this.timeSlice = 10; }
    public void decTimeSlice() { this.timeSlice = Math.max(0, this.timeSlice - 1); }
    public String getBlockReason() { return blockReason; }
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }

    public javafx.beans.property.IntegerProperty pidProperty() {
        return new javafx.beans.property.SimpleIntegerProperty(pid);
    }
    public javafx.beans.property.StringProperty nameProperty() {
        return new javafx.beans.property.SimpleStringProperty(name);
    }
    public javafx.beans.property.StringProperty stateProperty() {
        return new javafx.beans.property.SimpleStringProperty(state.name());
    }
    public javafx.beans.property.IntegerProperty priorityProperty() {
        return new javafx.beans.property.SimpleIntegerProperty(currentPriority);
    }
    // 【关键优化】直接返回 Block 的 Property
    // 这样当 defragment 修改了 Block 的地址时，UI 上的表格会自动更新！
    public javafx.beans.property.IntegerProperty memoryAddressProperty() {
        if (memoryBlock != null) {
            return memoryBlock.startAddressProperty();
        }
        return new javafx.beans.property.SimpleIntegerProperty(-1);
    }

    public javafx.beans.property.IntegerProperty memorySizeProperty() {
        if (memoryBlock != null) {
            return memoryBlock.sizeProperty();
        }
        return new javafx.beans.property.SimpleIntegerProperty(0);
    }
    public javafx.beans.property.IntegerProperty timeSliceProperty() {
        return new javafx.beans.property.SimpleIntegerProperty(timeSlice);
    }
}
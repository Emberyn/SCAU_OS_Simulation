package org.example.scau_os_simulation.process;

import org.example.scau_os_simulation.memory.MemoryBlock;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * 进程控制块（Process Control Block, PCB）
 * 核心设计：
 * 1. 关联物理内存块（MemoryBlock）：替代原有的内存地址/大小字段，支持内存碎片整理后的地址自动同步
 * 2. 支持JavaFX属性绑定：所有UI展示的字段都提供Property方法，实现数据变→UI自动更
 * 3. 优先级老化机制：避免低优先级进程长期饥饿（等待时间越长，优先级越高）
 * 4. 寄存器模拟：模拟CPU寄存器（AX通用寄存器、PC程序计数器、IR指令寄存器）
 */
public class PCB {
    private int pid;
    private final String name;

    /** 基准优先级：进程创建时设定的初始优先级，不可变 */
    private final int basePriority;
    /** 当前优先级：支持动态调整（优先级老化），决定进程调度顺序（数值越大优先级越高） */
    private int currentPriority;
    /** 等待时间：进程在就绪队列的等待时长，用于触发优先级老化 */
    private int waitingTime;

    /** 进程状态：NEW(新建)/READY(就绪)/RUNNING(运行)/BLOCKED(阻塞)/TERMINATED(终止) */
    private ProcessState state;

    /** 进程关联的物理内存块
     *  优势：内存碎片整理时，内存块地址变化会自动同步到UI */
    private final MemoryBlock memoryBlock;

    private int ax;
    private int pc;
    /** IR指令寄存器：存储当前正在执行的指令 */
    private String ir;

    /** 时间片剩余时长：进程每次占用CPU的最大时间，用完后触发调度（初始值10） */
    private int timeSlice;
    /** 阻塞原因：记录进程阻塞的具体原因（如"等待磁盘IO"、"等待内存分配"） */
    private String blockReason;

    // -------------------------- 扩展调度字段 --------------------------
    /** 总剩余运行时间：用于调度算法（如SJF短作业优先），记录进程还需运行的总时长 */
    private int totalRemainingTime;

    /**
     * 构造函数：初始化PCB的核心状态，完成进程基础信息的配置
     */
    public PCB(int pid, String name, int priority, MemoryBlock memoryBlock) {
        // 基础标识初始化
        this.pid = pid;
        this.name = name;
        this.basePriority = priority;
        this.currentPriority = priority; // 初始当前优先级=基准优先级
        this.waitingTime = 0;

        // 状态初始化：新建进程默认NEW状态
        this.state = ProcessState.NEW;

        this.memoryBlock = memoryBlock;

        this.ax = 0;         // 通用寄存器初始值0
        this.pc = 0;         // 程序计数器初始指向第一条指令（索引0）
        this.ir = "";        // 指令寄存器初始为空

        // 调度字段初始化
        this.timeSlice = 10; // 初始时间片10个单位
        this.blockReason = "";// 初始无阻塞原因
        this.totalRemainingTime = 0; // 初始剩余运行时间0
    }

    // ========================== 扩展调度字段 - Getter/Setter（修复UI绑定） ==========================
    /**
     * 获取进程总剩余运行时间
     * @return 总剩余运行时间（单位：CPU周期）
     */
    public int getTotalRemainingTime() {
        return totalRemainingTime;
    }

    /**
     * 设置进程总剩余运行时间（供MainController调用）
     * @param totalRemainingTime 新的剩余运行时间
     */
    public void setTotalRemainingTime(int totalRemainingTime) {
        this.totalRemainingTime = totalRemainingTime;
    }



    // ========================== 基础标识字段 - Getter ==========================
    /** 获取进程PID */
    public int getPid() { return pid; }

    /** 获取进程名称（只读，进程创建后名称不变） */
    public String getName() { return name; }

    // ========================== 优先级管理 - Getter/核心逻辑 ==========================
    /** 获取进程当前优先级（调度时使用） */
    public int getPriority() { return currentPriority; }

    /** 获取进程基准优先级（创建时的初始值） */
    public int getBasePriority() { return basePriority; }

    /** 获取进程就绪队列等待时间 */
    public int getWaitingTime() { return waitingTime; }

    /**
     * 优先级老化核心逻辑：增加等待时间，触发优先级提升
     * 规则：每等待10个CPU周期，当前优先级+1（最高不超过基准优先级+5）
     * 目的：避免低优先级进程长期饥饿（永远得不到CPU执行权）
     */
    public void incrementWaitingTime() {
        waitingTime++;
        // 每等待10个周期，提升优先级
        if (waitingTime % 10 == 0) {
            // 优先级上限：基准优先级+5，避免优先级过高抢占核心进程
            currentPriority = Math.min(basePriority + 5, currentPriority + 1);
        }
    }

    /**
     * 重置等待时间和优先级（进程获得CPU执行权时调用）
     * 逻辑：等待时间归零，当前优先级恢复为基准优先级
     */
    public void resetWaitingTime() {
        waitingTime = 0;
        currentPriority = basePriority;
    }

    // ========================== 进程状态管理 - Getter/Setter ==========================
    /** 获取进程当前状态 */
    public ProcessState getState() { return state; }

    /** 设置进程状态（如就绪→运行、运行→阻塞） */
    public void setState(ProcessState state) { this.state = state; }


    /**
     * 获取进程内存起始地址（从MemoryBlock实时获取）
     * @return 内存起始地址（无内存块返回-1）
     */
    public int getMemoryAddress() {
        return memoryBlock != null ? memoryBlock.getStartAddress() : -1;
    }


    /**
     * 获取进程内存大小（从MemoryBlock实时获取）
     * @return 内存大小（无内存块返回0）
     */
    public int getMemorySize() {
        return memoryBlock != null ? memoryBlock.getSize() : 0;
    }


    /** 获取进程关联的内存块对象（供内存管理器释放内存时使用） */
    public MemoryBlock getMemoryBlock() {
        return memoryBlock;
    }


    public int getAx() { return ax; }
    public void setAx(int ax) { this.ax = Math.max(0, Math.min(255, ax)); }

    public int getPc() { return pc; }
    public void setPc(int pc) { this.pc = pc; }

    public String getIr() { return ir; }
    public void setIr(String ir) { this.ir = ir; }



    // ========================== 时间片管理 - Getter/核心逻辑 ==========================
    /** 获取剩余时间片时长 */
    public int getTimeSlice() { return timeSlice; }
    /** 重置时间片（进程获得CPU执行权时，恢复为初始值10） */
    public void resetTimeSlice() { this.timeSlice = 10; }
    /** 减少时间片（CPU执行一个周期后调用，最小为0） */
    public void decTimeSlice() { this.timeSlice = Math.max(0, this.timeSlice - 1); }


    // ========================== 阻塞管理 - Getter/Setter ==========================
    /** 获取进程阻塞原因 */
    public String getBlockReason() { return blockReason; }
    /** 设置进程阻塞原因（如"等待磁盘IO完成"） */
    public void setBlockReason(String blockReason) { this.blockReason = blockReason; }



    // ========================== JavaFX属性绑定 - 供UI表格自动更新 ==========================
    // PID的JavaFX属性（供UI绑定）
    public IntegerProperty pidProperty() {
        return new SimpleIntegerProperty(pid);
    }
    /** 进程名称的JavaFX属性（名称只读，无需更新） */
    public StringProperty nameProperty() {
        return new SimpleStringProperty(name);
    }
    /** 进程状态的JavaFX属性（状态变化时UI自动更新） */
    public StringProperty stateProperty() {
        return new SimpleStringProperty(state.name());
    }
    /** 进程当前优先级的JavaFX属性（优先级变化时UI自动更新） */
    public IntegerProperty priorityProperty() {
        return new SimpleIntegerProperty(currentPriority);
    }
    //总剩余运行时间的JavaFX属性（供UI绑定）
    public IntegerProperty totalRemainingTimeProperty() {
        return new SimpleIntegerProperty(totalRemainingTime);
    }
    // 内存地址的JavaFX属性（核心优化）
    public IntegerProperty memoryAddressProperty() {
        if (memoryBlock != null) {
            return memoryBlock.startAddressProperty();
        }
        return new SimpleIntegerProperty(-1);
    }
    // 内存大小的JavaFX属性（复用MemoryBlock的sizeProperty）
    public IntegerProperty memorySizeProperty() {
        if (memoryBlock != null) {
            return memoryBlock.sizeProperty();
        }
        return new SimpleIntegerProperty(0);
    }
    /** 剩余时间片的JavaFX属性（时间片变化时UI自动更新） */
    public IntegerProperty timeSliceProperty() {
        return new SimpleIntegerProperty(timeSlice);
    }
}
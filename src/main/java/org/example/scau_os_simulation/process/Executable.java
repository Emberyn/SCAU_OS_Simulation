package org.example.scau_os_simulation.process;

import java.util.ArrayList;
import java.util.List;

/**
 * 可执行文件模型 - 按序存储指令文本
 * 说明：
 * - `instructions` 保存了进程的“脚本”，CPU 会根据 PCB 的 `pc` 取指执行。
 * - 为简化演示，指令为纯文本形式（如 `x++`、`!A3`、`end`）。
 */
public class Executable {
    private final List<String> instructions = new ArrayList<>();
    
    /** 默认构造函数 */
    public Executable() {
    }
    
    /** 构造函数：初始化指令列表（允许传入 null） */
    public Executable(List<String> lines) {
        if (lines != null) instructions.addAll(lines);
    }
    
    /**
     * 设置指令列表
     * @param lines 指令列表
     */
    protected void setInstructions(List<String> lines) {
        instructions.clear();
        if (lines != null) instructions.addAll(lines);
    }

    /**
     * 取指
     * @param pc 程序计数器位置（0-based）
     * @return 当前指令；越界时返回 "end"
     */
    public String fetch(int pc) {
        if (pc < 0 || pc >= instructions.size()) return "end";
        return instructions.get(pc);
    }

    /** 指令数量 */
    public int length() {
        return instructions.size();
    }
}

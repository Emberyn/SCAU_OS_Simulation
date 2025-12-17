package org.example.scau_os_simulation.process;

import java.util.ArrayList;
import java.util.List;

/**
 * 可执行文件模型 - 按序存储指令文本
 */
public class Executable
{
    private final List<String> instructions = new ArrayList<>();

    public Executable()
    {
    }

    public Executable(List<String> lines)
    {
        if (lines != null) instructions.addAll(lines);
    }

    /**
     * 设置指令列表
     * @param lines 指令列表
     */
    protected void setInstructions(List<String> lines)
    {
        instructions.clear();
        if (lines != null) instructions.addAll(lines);
    }

    /**
     * 取指
     * @param pc 程序计数器位置（0-based）
     * @return 当前指令；越界时返回 "end"
     */
    public String fetch(int pc)
    {
        if (pc < 0 || pc >= instructions.size()) return "end";
        return instructions.get(pc);
    }

    /**
     * 指令数量
     */
    public int length()
    {
        return instructions.size();
    }
}

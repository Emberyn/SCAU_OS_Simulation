package org.example.scau_os_simulation.memory;

/**
 * 内存块 - 描述一段连续的内存区间
 * <p>
 * 概念：
 * - `startAddress` 表示起始字节地址（通常由上层保证按KB对齐）。
 * - `size` 表示块大小（KB），便于与 UI 的容量展示保持一致。
 * <p>
 * 用途：
 * - 被 `MemoryManager` 用来记录当前已分配的连续区域。
 * - 在 UI 的“内存块列表”中展示每个块的起止与归属进程。
 */
public class MemoryBlock
{
    private int startAddress;
    private final int size;

    /**
     * 构造函数
     *
     * @param startAddress 起始地址（字节/KB对齐由上层保证）
     * @param size         大小（KB）
     */
    public MemoryBlock(int startAddress, int size)
    {
        this.startAddress = startAddress;
        this.size = size;
    }

    /**
     * 获取起始地址（字节）
     */
    public int getStartAddress()
    {
        return startAddress;
    }

    /**
     * 设置起始地址（用于内存整理）
     */
    public void setStartAddress(int startAddress)
    {
        this.startAddress = startAddress;
    }

    /**
     * 获取大小（KB）
     */
    public int getSize()
    {
        return size;
    }

    /**
     * 结束地址（闭区间）
     */
    public int getEndAddress()
    {
        return startAddress + size - 1;
    }

    public javafx.beans.property.IntegerProperty startAddressProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(startAddress);
    }

    public javafx.beans.property.IntegerProperty sizeProperty()
    {
        return new javafx.beans.property.SimpleIntegerProperty(size);
    }
}

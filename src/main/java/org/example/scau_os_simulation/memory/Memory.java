package org.example.scau_os_simulation.memory;

/**
 * 物理内存模型 - 提供字节级读写
 */
public class Memory
{
    private final int size;

    /**
     * 构造函数
     * @param size 总容量（KB）
     */
    public Memory(int size)
    {
        this.size = size;
    }

    /**
     * 获取总容量（KB）
     */
    public int getSize()
    {
        return size;
    }
}

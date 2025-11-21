package org.example.scau_os_simulation.memory;

/**
 * 物理内存模型 - 提供字节级读写
 *
 * 概念解释：
 * - 以“KB”为单位初始化总大小（`size`），内部使用 `byte[] data` 存储具体字节。
 * - 提供 `write/read` 方法，支持从指定地址读写一段数据，边界越界将抛出异常。
 *
 * 教学取舍：
 * - 不实现页表/段表等复杂机制，仅演示连续地址空间的读写行为。
 */
public class Memory {
    private final int size;
    private final byte[] data;
    
    /**
     * 构造函数
     * @param size 总容量（KB）
     */
    public Memory(int size) {
        this.size = size;
        this.data = new byte[size * 1024];
    }
    
    /** 获取总容量（KB） */
    public int getSize() {
        return size;
    }
    
    /** 直接获取底层数据缓冲区（只读引用） */
    public byte[] getData() {
        return data;
    }
    
    /**
     * 写入数据
     * @param address 起始地址（字节）
     * @param data 要写入的数据
     * @throws IndexOutOfBoundsException 若写入范围越过内存边界
     */
    public void write(int address, byte[] data) {
        if (address + data.length > this.data.length) {
            throw new IndexOutOfBoundsException("内存访问越界");
        }
        
        System.arraycopy(data, 0, this.data, address, data.length);
    }
    
    /**
     * 读取数据
     * @param address 起始地址（字节）
     * @param length 长度（字节）
     * @return 读取到的字节数组
     * @throws IndexOutOfBoundsException 若读取范围越过内存边界
     */
    public byte[] read(int address, int length) {
        if (address + length > this.data.length) {
            throw new IndexOutOfBoundsException("内存访问越界");
        }
        
        byte[] result = new byte[length];
        System.arraycopy(this.data, address, result, 0, length);
        return result;
    }
}

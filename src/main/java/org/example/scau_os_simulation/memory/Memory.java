package org.example.scau_os_simulation.memory;

public class Memory {
    private int size; // 内存大小，单位KB
    private byte[] data;
    
    public Memory(int size) {
        this.size = size;
        this.data = new byte[size * 1024]; // 转换为字节
    }
    
    public int getSize() {
        return size;
    }
    
    public byte[] getData() {
        return data;
    }
    
    public void write(int address, byte[] data) {
        if (address + data.length > this.data.length) {
            throw new IndexOutOfBoundsException("内存访问越界");
        }
        
        System.arraycopy(data, 0, this.data, address, data.length);
    }
    
    public byte[] read(int address, int length) {
        if (address + length > this.data.length) {
            throw new IndexOutOfBoundsException("内存访问越界");
        }
        
        byte[] result = new byte[length];
        System.arraycopy(this.data, address, result, 0, length);
        return result;
    }
}
package org.example.scau_os_simulation.memory;

public class MemoryBlock {
    private final int startAddress;
    private final int size;
    
    public MemoryBlock(int startAddress, int size) {
        this.startAddress = startAddress;
        this.size = size;
    }
    
    public int getStartAddress() {
        return startAddress;
    }
    
    public int getSize() {
        return size;
    }
    
    public int getEndAddress() {
        return startAddress + size - 1;
    }
}

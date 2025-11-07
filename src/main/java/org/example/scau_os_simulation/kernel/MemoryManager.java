package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.memory.MemoryBlock;

import java.util.ArrayList;
import java.util.List;

public class MemoryManager {
    private Memory memory;
    private List<MemoryBlock> allocatedBlocks;
    
    public MemoryManager(Memory memory) {
        this.memory = memory;
        this.allocatedBlocks = new ArrayList<>();
    }
    
    public int allocateMemory(int size) {
        // 首次适应算法
        int currentAddress = 0;
        
        while (currentAddress < memory.getSize()) {
            // 检查当前位置是否已被分配
            boolean isAllocated = false;
            int availableSize = 0;
            
            for (MemoryBlock block : allocatedBlocks) {
                if (currentAddress >= block.getStartAddress() && 
                    currentAddress < block.getStartAddress() + block.getSize()) {
                    // 当前位置已被分配
                    isAllocated = true;
                    currentAddress = block.getStartAddress() + block.getSize();
                    break;
                }
            }
            
            if (!isAllocated) {
                // 计算可用空间大小
                availableSize = memory.getSize() - currentAddress;
                for (MemoryBlock block : allocatedBlocks) {
                    if (block.getStartAddress() > currentAddress) {
                        int possibleSize = block.getStartAddress() - currentAddress;
                        if (possibleSize < availableSize) {
                            availableSize = possibleSize;
                        }
                    }
                }
                
                if (availableSize >= size) {
                    // 找到足够的空间
                    MemoryBlock newBlock = new MemoryBlock(currentAddress, size);
                    allocatedBlocks.add(newBlock);
                    return currentAddress;
                } else {
                    // 继续寻找下一个可用空间
                    currentAddress += availableSize;
                }
            }
        }
        
        // 没有足够的内存
        return -1;
    }
    
    public void freeMemory(int address, int size) {
        MemoryBlock blockToRemove = null;
        
        for (MemoryBlock block : allocatedBlocks) {
            if (block.getStartAddress() == address && block.getSize() == size) {
                blockToRemove = block;
                break;
            }
        }
        
        if (blockToRemove != null) {
            allocatedBlocks.remove(blockToRemove);
        }
    }
    
    public List<MemoryBlock> getAllocatedBlocks() {
        return allocatedBlocks;
    }
    
    public Memory getMemory() {
        return memory;
    }
}
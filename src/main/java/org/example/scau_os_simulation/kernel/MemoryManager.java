package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.memory.MemoryBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存管理器 - 负责分配与回收内存
 *
 * 功能概览：
 * - 按“首次适应”策略查找可用连续内存块并分配
 * - 记录所有已分配的内存块，支持精确释放
 * - 提供内存总体信息以供UI展示
 */
public class MemoryManager {
    private final Memory memory;
    private final List<MemoryBlock> allocatedBlocks;
    
    /**
     * 构造函数
     * @param memory 物理内存模型
     */
    public MemoryManager(Memory memory) {
        this.memory = memory;
        this.allocatedBlocks = new ArrayList<>();
    }
    
    /**
     * 分配一块连续内存（首次适应算法）
     *
     * 从地址0开始，寻找第一个能容纳size大小的空闲区域。
     * 若成功，记录该块并返回其起始地址；否则返回-1。
     *
     * @param size 申请的大小（KB）
     * @return 起始地址；失败返回-1
     */
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
    
    /**
     * 释放一块已分配的内存
     *
     * 通过地址与大小精确匹配对应的内存块并移除记录。
     * @param address 起始地址
     * @param size 大小（KB）
     */
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
    
    /**
     * 获取已分配的内存块列表
     */
    public List<MemoryBlock> getAllocatedBlocks() {
        return allocatedBlocks;
    }
    
    /**
     * 获取内存总览模型
     */
    public Memory getMemory() {
        return memory;
    }
}

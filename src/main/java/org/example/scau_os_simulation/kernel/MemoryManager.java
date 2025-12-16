package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.memory.MemoryBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 内存管理器 - 负责分配与回收内存
 * <p>
 * 功能概览：
 * - 按“首次适应”策略查找可用连续内存块并分配
 * - 记录所有已分配的内存块，支持精确释放
 * - 提供内存总体信息以供UI展示
 *
 * 【线程安全修复】增加 synchronized 和防御性复制
 */
public class MemoryManager
{
    private final Memory memory;
    private final List<MemoryBlock> allocatedBlocks;

    /**
     * 构造函数
     * @param memory 物理内存模型
     */
    public MemoryManager(Memory memory)
    {
        this.memory = memory;
        this.allocatedBlocks = new ArrayList<>();
    }

    public Memory getMemory()
    {
        return memory;
    }

    /**
     * 获取已分配内存块列表（副本）
     * 【修复】返回副本
     */
    public synchronized List<MemoryBlock> getAllocatedBlocks()
    {
        return new ArrayList<>(allocatedBlocks);
    }

    /**
     * 分配一块连续内存（修复版 - 标准首次适应算法）
     *
     * @param size 申请的大小（KB）
     * @return 起始地址；失败返回-1
     */
    public synchronized int allocateMemory(int size)
    {
        // 1. 对已分配块按起始地址排序，这是正确计算空隙的前提
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        // 模拟系统区保留：假设前 128KB 是系统区，用户只能从 128 开始用
        int SYSTEM_RESERVED_SIZE = 128;
        int candidateAddress = SYSTEM_RESERVED_SIZE;

        // 2. 遍历所有已分配块，检查"当前候选地址"到"该块起始地址"之间的空隙
        for (MemoryBlock block : allocatedBlocks)
        {
            // 如果空隙足够大 (block.start - candidate >= size)
            if (candidateAddress + size <= block.getStartAddress())
            {
                // 找到合适位置，跳出循环进行分配
                break;
            }
            // 否则，候选地址移动到当前块的末尾之后
            candidateAddress = block.getStartAddress() + block.getSize();
        }

        // 3. 检查最后一个块之后（或者如果内存全空）是否有足够空间
        if (candidateAddress + size <= memory.getSize())
        {
            // 分配成功
            MemoryBlock newBlock = new MemoryBlock(candidateAddress, size);
            allocatedBlocks.add(newBlock);

            // 记录日志
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("address", candidateAddress);
            details.put("size", size);
            org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_ALLOCATE,
                    "内存分配成功",
                    details
            );

            return candidateAddress;
        }

        // 4. 分配失败
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("requestedSize", size);
        details.put("availableSize", memory.getSize() - getTotalUsedMemory());
        org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_ALLOCATE,
                "内存分配失败：空间不足",
                details
        );

        return -1;
    }

    /**
     * 释放一块已分配的内存
     * 通过地址与大小精确匹配对应的内存块并移除记录。
     *
     * @param address 起始地址
     * @param size    大小（KB）
     */
    public synchronized void freeMemory(int address, int size)
    {
        MemoryBlock blockToRemove = null;

        for (MemoryBlock block : allocatedBlocks)
        {
            if (block.getStartAddress() == address && block.getSize() == size)
            {
                blockToRemove = block;
                break;
            }
        }

        if (blockToRemove != null)
        {
            allocatedBlocks.remove(blockToRemove);

            // 记录日志
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("address", address);
            details.put("size", size);
            org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_FREE,
                    "内存释放成功",
                    details
            );
        }
    }


    /**
     * 内存碎片整理 - 压缩空闲内存
     * <p>
     * 将所有已分配的内存块移动到内存前端，消除碎片
     * 同时更新相关进程的内存地址信息
     */
    public synchronized void defragmentMemory()
    {
        if (allocatedBlocks.isEmpty()) return;

        // 记录日志
        double beforeFragmentation = getFragmentationRate();

        // 按起始地址排序
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        int currentAddress = 0;
        for (MemoryBlock block : allocatedBlocks)
        {
            if (block.getStartAddress() != currentAddress)
            {
                // 移动内存块
                block.setStartAddress(currentAddress);
            }
            currentAddress += block.getSize();
        }

        // 记录日志
        double afterFragmentation = getFragmentationRate();
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("beforeFragmentation", String.format("%.1f%%", beforeFragmentation));
        details.put("afterFragmentation", String.format("%.1f%%", afterFragmentation));
        org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_DEFRAGMENT,
                "内存碎片整理完成",
                details
        );
    }

    public synchronized void defragment()
    {
        defragmentMemory();
    }

    /**
     * 【修复】获取内存碎片率
     * 算法：1 - (最大连续空闲块 / 总空闲内存)
     * 只有这样计算，内存整理前后这个数值才会发生剧烈变化。
     */
    public synchronized double getFragmentationRate() {
        int totalMemory = memory.getSize();
        int usedMemory = getTotalUsedMemory();
        int freeMemory = totalMemory - usedMemory;

        // 如果没有空闲内存，或者全是空闲内存(没有被切割)，则认为没有碎片
        if (freeMemory == 0) {
            return 0.0;
        }

        // 如果完全没有分配内存，最大空闲块就是总内存，碎片率也应为0
        if (allocatedBlocks.isEmpty()) {
            return 0.0;
        }

        // 获取最大的连续空闲块
        int maxFreeBlock = getMaxFreeBlockSize();

        // 计算碎片率
        return 1.0 - ((double) maxFreeBlock / freeMemory);
    }




    /**
     * 获取内存使用率
     *
     * @return 使用率百分比 (0-100)
     */
    public synchronized double getMemoryUsageRate()
    {
        if (memory.getSize() == 0) return 0.0;

        int totalUsed = 0;
        for (MemoryBlock block : allocatedBlocks)
        {
            totalUsed += block.getSize();
        }

        return (double) totalUsed / memory.getSize();
    }




    /**
     * 【新增辅助方法】计算当前最大的连续空闲块大小
     */
    public synchronized int getMaxFreeBlockSize() {
        if (allocatedBlocks.isEmpty()) {
            return memory.getSize();
        }

        // 1. 必须先按地址排序，这是计算缝隙的前提
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        int maxFree = 0;
        int currentAddr = 0; // 假设内存从0开始

        // 2. 扫描块与块之间的缝隙
        for (MemoryBlock block : allocatedBlocks) {
            // 如果当前块的起始地址 > 指针地址，说明中间有空隙
            if (block.getStartAddress() > currentAddr) {
                int gap = block.getStartAddress() - currentAddr;
                if (gap > maxFree) {
                    maxFree = gap;
                }
            }
            // 移动指针到当前块的末尾
            currentAddr = block.getStartAddress() + block.getSize();
        }

        // 3. 扫描最后一个块到内存末尾的缝隙
        if (currentAddr < memory.getSize()) {
            int tailGap = memory.getSize() - currentAddr;
            if (tailGap > maxFree) {
                maxFree = tailGap;
            }
        }

        return maxFree;
    }




    /**
     * 获取内存统计信息
     *
     * @return 包含各种统计信息的映射
     */
    public synchronized Map<String, Object> getMemoryStatistics()
    {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", memory.getSize());
        stats.put("usedSize", getTotalUsedMemory());
        stats.put("freeSize", memory.getSize() - getTotalUsedMemory());
        stats.put("usageRate", getMemoryUsageRate());
        stats.put("fragmentationRate", getFragmentationRate());
        stats.put("maxFreeBlockSize", getMaxFreeBlockSize());
        stats.put("allocatedBlocks", allocatedBlocks.size());
        return stats;
    }

    /**
     * 获取已使用内存总量
     *
     * @return 已使用内存大小
     */
    public synchronized int getTotalUsedMemory()
    {
        int total = 0;
        for (MemoryBlock block : allocatedBlocks)
        {
            total += block.getSize();
        }
        return total;
    }
}
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
 */
public class MemoryManager
{
    private final Memory memory;
    private final List<MemoryBlock> allocatedBlocks;

    /**
     * 构造函数
     *
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

    public List<MemoryBlock> getAllocatedBlocks()
    {
        return allocatedBlocks;
    }

    /**
     * 分配一块连续内存（修复版 - 标准首次适应算法）
     *
     * @param size 申请的大小（KB）
     * @return 起始地址；失败返回-1
     */
    public int allocateMemory(int size)
    {
        // 1. 对已分配块按起始地址排序，这是正确计算空隙的前提
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        int candidateAddress = 0;

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
    public void freeMemory(int address, int size)
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
    public void defragmentMemory()
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

    public void defragment()
    {
        defragmentMemory();
    }

    /**
     * 获取内存碎片率
     *
     * @return 碎片率百分比 (0-100)
     */
    public double getFragmentationRate()
    {
        if (allocatedBlocks.isEmpty()) return 0.0;

        int totalUsed = 0;

        for (MemoryBlock block : allocatedBlocks)
        {
            totalUsed += block.getSize();
        }

        // 计算外部碎片
        int externalFragment = 0;
        int currentAddress = 0;

        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        for (MemoryBlock block : allocatedBlocks)
        {
            if (block.getStartAddress() > currentAddress)
            {
                externalFragment += block.getStartAddress() - currentAddress;
            }
            currentAddress = block.getStartAddress() + block.getSize();
        }

        if (currentAddress < memory.getSize())
        {
            externalFragment += memory.getSize() - currentAddress;
        }

        return totalUsed == 0 ? 0.0 : (double) externalFragment / (totalUsed + externalFragment);
    }

    /**
     * 获取内存使用率
     *
     * @return 使用率百分比 (0-100)
     */
    public double getMemoryUsageRate()
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
     * 获取最大可用连续内存块大小
     *
     * @return 最大连续空闲内存大小
     */
    public int getMaxFreeBlockSize()
    {
        if (allocatedBlocks.isEmpty()) return memory.getSize();

        int maxFreeBlock = 0;
        int currentAddress = 0;

        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        for (MemoryBlock block : allocatedBlocks)
        {
            if (block.getStartAddress() > currentAddress)
            {
                int freeSize = block.getStartAddress() - currentAddress;
                maxFreeBlock = Math.max(maxFreeBlock, freeSize);
            }
            currentAddress = block.getStartAddress() + block.getSize();
        }

        if (currentAddress < memory.getSize())
        {
            int freeSize = memory.getSize() - currentAddress;
            maxFreeBlock = Math.max(maxFreeBlock, freeSize);
        }

        return maxFreeBlock;
    }

    /**
     * 获取内存统计信息
     *
     * @return 包含各种统计信息的映射
     */
    public Map<String, Object> getMemoryStatistics()
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
    public int getTotalUsedMemory()
    {
        int total = 0;
        for (MemoryBlock block : allocatedBlocks)
        {
            total += block.getSize();
        }
        return total;
    }
}

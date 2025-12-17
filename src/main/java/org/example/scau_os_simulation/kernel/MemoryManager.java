package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.memory.MemoryBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存管理器（MemoryManager）
 * 核心职责：
 * 1. 基于“首次适应（First Fit）”算法完成内存块的分配与精准回收
 * 2. 管理系统保留内存区（前128KB），避免用户进程占用系统核心空间
 * 3. 提供内存碎片整理功能，压缩空闲内存消除碎片，提升内存利用率
 * 4. 统计内存核心指标（使用率、碎片率、最大空闲块等），支撑UI展示
 */
public class MemoryManager
{
    // 物理内存模型（提供内存总大小、内存操作基础能力）
    private final Memory memory;
    // 已分配内存块列表（包含系统保留块和用户进程块，核心管理容器）
    private final List<MemoryBlock> allocatedBlocks;

    /**
     * 构造函数 - 初始化内存管理器
     * @param memory 物理内存模型实例（提供内存总容量等基础信息）
     */
    public MemoryManager(Memory memory)
    {
        this.memory = memory;
        this.allocatedBlocks = new ArrayList<>();

        // 初始化系统保留内存：占用0-128KB地址空间，标记为已分配
        // 该块会参与碎片整理的地址计算，确保系统空间始终固定在内存起始位置
        this.allocatedBlocks.add(new MemoryBlock(0, 128));
    }

    /**
     * 获取物理内存模型实例
     * @return 物理内存对象
     */
    public Memory getMemory()
    {
        return memory;
    }

    /**
     * 获取已分配内存块列表（防御性副本）
     * @return 已分配内存块的副本列表
     */
    public synchronized List<MemoryBlock> getAllocatedBlocks()
    {
        return new ArrayList<>(allocatedBlocks);
    }

    /**
     * 分配连续内存块（标准首次适应算法实现）
     * 首次适应规则：从内存起始地址开始，找到第一个能容纳申请大小的空闲区间
     * @param size 申请的内存大小（单位：KB）
     * @return 分配成功返回MemoryBlock对象（包含起始地址和大小）；失败返回null
     */
    public synchronized MemoryBlock allocateMemory(int size)
    {
        // 1. 排序已分配块：按起始地址升序，确保遍历顺序符合首次适应的查找逻辑
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        // 候选分配地址：初始为0（系统保留块已占0-128，后续遍历会自动跳过该区间）
        int candidateAddress = 0;

        // 2. 遍历已分配块，查找可用空闲区间
        for (MemoryBlock block : allocatedBlocks)
        {
            // 若候选地址 + 申请大小 ≤ 当前块起始地址 → 该区间可分配
            if (candidateAddress + size <= block.getStartAddress())
            {
                break; // 找到可用区间，终止遍历
            }
            // 否则，将候选地址移到当前块末尾，继续查找下一个空闲区间
            candidateAddress = block.getStartAddress() + block.getSize();
        }

        // 3. 校验并完成分配：候选地址+申请大小未超出总内存容量
        if (candidateAddress + size <= memory.getSize())
        {
            MemoryBlock newBlock = new MemoryBlock(candidateAddress, size);
            allocatedBlocks.add(newBlock);

            // 记录内存分配成功日志（包含地址、大小）
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("address", candidateAddress);
            details.put("size", size);
            org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_ALLOCATE,
                    "内存分配成功",
                    details
            );

            return newBlock; // 返回分配的内存块对象（供后续释放使用）
        }

        // 4. 分配失败：记录失败日志（包含申请大小、可用空闲大小）
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("requestedSize", size);
        details.put("availableSize", memory.getSize() - getTotalUsedMemory());
        org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_ALLOCATE,
                "内存分配失败：空间不足",
                details
        );

        return null; // 分配失败返回null
    }

    /**
     * 精准释放内存块（通过MemoryBlock对象）
     * @param block 待释放的内存块对象（必须是allocateMemory返回的实例）
     */
    public synchronized void freeMemory(MemoryBlock block)
    {
        // 校验块非空且存在于已分配列表中（避免空指针/无效释放）
        if (block != null && allocatedBlocks.contains(block))
        {
            allocatedBlocks.remove(block);

            // 记录内存释放成功日志（包含地址、大小）
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            details.put("address", block.getStartAddress());
            details.put("size", block.getSize());
            org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_FREE,
                    "内存释放成功",
                    details
            );
        }
    }

    /**
     * 内存碎片整理（压缩算法）
     * 核心逻辑：
     * 1. 将所有已分配块按地址排序后，紧凑排列到内存起始位置
     * 2. 系统保留块（0-128KB）固定在起始位置，后续块自动衔接
     * 3. 消除块间空闲碎片，将所有空闲内存合并为一个连续区间
     */
    public synchronized void defragmentMemory()
    {
        // 无已分配块时无需整理
        if (allocatedBlocks.isEmpty()) return;

        // 记录整理前的碎片率，用于日志对比展示整理效果
        double beforeFragmentation = getFragmentationRate();

        // 1. 按起始地址排序，确保紧凑排列的顺序正确
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        // 起始地址指针：从0开始（系统保留块会先占据0-128，后续块自动从128开始）
        int currentAddress = 0;

        // 2. 遍历所有块，重新分配起始地址（紧凑排列）
        for (MemoryBlock block : allocatedBlocks)
        {
            // 若块的当前地址与目标地址不一致，更新地址
            if (block.getStartAddress() != currentAddress)
            {
                block.setStartAddress(currentAddress);
            }
            // 指针移动到当前块末尾，为下一个块预留连续空间
            currentAddress += block.getSize();
        }

        // 记录整理后的碎片率，输出对比日志
        double afterFragmentation = getFragmentationRate();
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("beforeFragmentation", String.format("%.1f%%", beforeFragmentation * 100));
        details.put("afterFragmentation", String.format("%.1f%%", afterFragmentation * 100));
        org.example.scau_os_simulation.kernel.Kernel.getInstance().getOperationLogger().info(
                org.example.scau_os_simulation.logging.OperationLogger.OperationType.MEMORY_DEFRAGMENT,
                "内存碎片整理完成",
                details
        );
    }

    /**
     * 内存碎片整理的便捷方法（简化接口）
     * 底层直接调用defragmentMemory()，提供更简洁的调用入口
     */
    public synchronized void defragment()
    {
        defragmentMemory();
    }

    /**
     * 计算内存碎片率（精准算法）
     * 算法公式：1 - (最大连续空闲块大小 / 总空闲内存大小)
     * 碎片率含义：
     * - 0%：无碎片（所有空闲内存为一个连续区间）
     * - 100%：完全碎片化（空闲内存被分割为无数不可用的小块）
     *
     * @return 碎片率（0.0 ~ 1.0，转换为百分比需×100）
     */
    public synchronized double getFragmentationRate() {
        int totalMemory = memory.getSize();
        int usedMemory = getTotalUsedMemory();
        int freeMemory = totalMemory - usedMemory;

        // 边界条件1：无空闲内存 → 无碎片
        if (freeMemory == 0) {
            return 0.0;
        }

        // 边界条件2：无已分配内存 → 所有空闲为连续区间，无碎片
        if (allocatedBlocks.isEmpty()) {
            return 0.0;
        }

        // 获取最大连续空闲块大小
        int maxFreeBlock = getMaxFreeBlockSize();

        // 计算碎片率：值越大表示碎片越严重
        return 1.0 - ((double) maxFreeBlock / freeMemory);
    }

    /**
     * 计算内存使用率
     * @return 使用率（0.0 ~ 1.0，转换为百分比需×100）
     */
    public synchronized double getMemoryUsageRate()
    {
        // 边界防护：内存总大小为0时返回0
        if (memory.getSize() == 0) return 0.0;

        // 累加所有已分配块的大小
        int totalUsed = 0;
        for (MemoryBlock block : allocatedBlocks)
        {
            totalUsed += block.getSize();
        }

        // 使用率 = 已用内存大小 / 总内存大小
        return (double) totalUsed / memory.getSize();
    }

    /**
     * 【核心辅助方法】计算当前最大的连续空闲内存块大小
     * 核心逻辑：
     * 1. 排序已分配块，遍历块间空闲区间
     * 2. 统计所有空闲区间的大小，记录最大值
     * 3. 包含最后一个块到内存末尾的空闲区间
     *
     * @return 最大连续空闲块大小（KB）
     */
    public synchronized int getMaxFreeBlockSize() {
        // 无已分配块时，最大空闲块为总内存大小
        if (allocatedBlocks.isEmpty()) {
            return memory.getSize();
        }

        // 1. 先排序：按起始地址升序，确保遍历顺序正确
        allocatedBlocks.sort((a, b) -> a.getStartAddress() - b.getStartAddress());

        int maxFree = 0; // 最大空闲块大小
        int currentAddr = 0; // 内存遍历指针（从起始地址0开始）

        // 2. 遍历已分配块，计算块间空闲区间
        for (MemoryBlock block : allocatedBlocks) {
            // 当前块起始地址 > 遍历指针 → 存在空闲区间
            if (block.getStartAddress() > currentAddr) {
                int gap = block.getStartAddress() - currentAddr;
                maxFree = Math.max(maxFree, gap); // 更新最大空闲块
            }
            // 移动指针到当前块末尾，继续遍历
            currentAddr = block.getStartAddress() + block.getSize();
        }

        // 3. 计算最后一个块到内存末尾的空闲区间
        if (currentAddr < memory.getSize()) {
            int tailGap = memory.getSize() - currentAddr;
            maxFree = Math.max(maxFree, tailGap);
        }

        return maxFree;
    }


    /**
     * 计算已使用内存总量
     * @return 已使用内存大小（KB）
     */
    public synchronized int getTotalUsedMemory()
    {
        int total = 0;
        // 累加所有已分配块的大小（包含系统保留块）
        for (MemoryBlock block : allocatedBlocks)
        {
            total += block.getSize();
        }
        return total;
    }
}
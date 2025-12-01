package org.example.scau_os_simulation.filesystem;

/**
 * 文件系统存储模型 - 管理磁盘空间
 * <p>
 * 作用说明：
 * - 用于记录“磁盘”的总容量（`totalSize`，单位KB）与已使用容量（`usedSize`）。
 * - 提供“申请空间（allocateSpace）”与“释放空间（freeSpace）”接口，供文件创建与删除调用。
 * - 在 UI 层可通过 `getUsedSize/getTotalSize` 展示磁盘使用率。
 * <p>
 * 设计取舍：
 * - 使用整型的 KB 计量，避免字节级管理的复杂性；适合教学演示。
 * - 不记录碎片/块表等高级细节，仅关注总量的加减变化。
 */
public class FileSystem
{
    private final int totalSize; // 总大小，单位KB
    private int usedSize; // 已使用大小，单位KB

    /**
     * 构造一个文件系统存储模型
     *
     * @param totalSize 总容量（KB）
     */
    public FileSystem(int totalSize)
    {
        this.totalSize = totalSize;
        this.usedSize = 0;
    }

    /**
     * 申请空间
     *
     * @param size 大小（KB）
     * @return 是否分配成功（若超出总容量则失败）
     */
    public boolean allocateSpace(int size)
    {
        if (usedSize + size > totalSize)
        {
            return false;
        }

        usedSize += size;
        return true;
    }

    /**
     * 释放空间
     *
     * @param size 大小（KB）
     *             <p>
     *             注意：如果释放后出现负数，系统会将其校正为0，避免无意义的负使用量。
     */
    public void freeSpace(int size)
    {
        usedSize -= size;
        if (usedSize < 0)
        {
            usedSize = 0;
        }
    }

    /**
     * 获取总容量（KB）
     */
    public int getTotalSize()
    {
        return totalSize;
    }

    /**
     * 获取已使用容量（KB）
     */
    public int getUsedSize()
    {
        return usedSize;
    }

    /**
     * 获取剩余容量（KB）
     */
    public int getFreeSize()
    {
        return totalSize - usedSize;
    }
}

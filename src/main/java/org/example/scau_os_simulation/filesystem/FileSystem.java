package org.example.scau_os_simulation.filesystem;

/**
 * 文件系统存储模型 - 管理磁盘空间
 */
public class FileSystem
{
    private final int totalSize; // 总大小，单位KB
    private int usedSize; // 已使用大小，单位KB

    /**
     * 构造一个文件系统存储模型
     */
    public FileSystem(int totalSize)
    {
        this.totalSize = totalSize;
        this.usedSize = 0;
    }

    /**
     * 申请空间
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
}

package org.example.scau_os_simulation.filesystem;

public class FileSystem {
    private int totalSize; // 总大小，单位KB
    private int usedSize; // 已使用大小，单位KB
    
    public FileSystem(int totalSize) {
        this.totalSize = totalSize;
        this.usedSize = 0;
    }
    
    public boolean allocateSpace(int size) {
        if (usedSize + size > totalSize) {
            return false;
        }
        
        usedSize += size;
        return true;
    }
    
    public void freeSpace(int size) {
        usedSize -= size;
        if (usedSize < 0) {
            usedSize = 0;
        }
    }
    
    public int getTotalSize() {
        return totalSize;
    }
    
    public int getUsedSize() {
        return usedSize;
    }
    
    public int getFreeSize() {
        return totalSize - usedSize;
    }
}
package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.FileSystem;
import org.example.scau_os_simulation.memory.Memory;

public class Kernel {
    private static Kernel instance;
    
    private ProcessManager processManager;
    private MemoryManager memoryManager;
    private FileSystemManager fileSystemManager;
    
    public Kernel() {
        instance = this;
    }
    
    public static Kernel getInstance() {
        return instance;
    }
    
    public void initialize() {
        // 初始化内存
        Memory memory = new Memory(1024); // 1024KB内存
        memoryManager = new MemoryManager(memory);
        
        // 初始化文件系统
        FileSystem fileSystem = new FileSystem(2048); // 2048KB磁盘空间
        fileSystemManager = new FileSystemManager(fileSystem);
        
        // 初始化进程管理器
        processManager = new ProcessManager(memoryManager);
        
        // 创建初始进程
        processManager.createProcess("系统进程", 0);
    }
    
    public void shutdown() {
        // 关闭所有资源
        processManager.terminateAllProcesses();
    }
    
    public ProcessManager getProcessManager() {
        return processManager;
    }
    
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }
    
    public FileSystemManager getFileSystemManager() {
        return fileSystemManager;
    }
}
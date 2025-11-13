package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.FileSystem;
import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.memory.Memory;

public class Kernel {
    private static Kernel instance;
    
    private ProcessManager processManager;
    private MemoryManager memoryManager;
    private FileSystemManager fileSystemManager;
    private DeviceManager deviceManager;
    private Scheduler scheduler;
    
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
        
        // 初始化设备管理器
        deviceManager = new DeviceManager(processManager);
        
        // 创建可执行文件
        for (int i = 1; i <= 10; i++) {
            java.util.List<String> ins = new java.util.ArrayList<>();
            ins.add("x=" + (i * 3 % 99));
            ins.add("x++");
            ins.add("x--");
            ins.add("!A" + (i % 5));
            ins.add("x++");
            ins.add("!B" + (i % 4));
            ins.add("x++");
            ins.add("!C" + (i % 3));
            ins.add("x++");
            ins.add("end");
            fileSystemManager.createExecutable("/system/exec", "p" + i + ".e", ins);
        }
        
        // 创建进程并加载可执行文件
        for (int i = 1; i <= 10; i++) {
            org.example.scau_os_simulation.process.Process p = processManager.createProcess("进程" + i, 1);
            Executable exec = fileSystemManager.loadExecutable("/system/exec/p" + i + ".e");
            if (p != null) p.setExecutable(exec);
        }
        
        // 启动调度器
        scheduler = new Scheduler(processManager, deviceManager);
        scheduler.start();
    }
    
    public void shutdown() {
        // 关闭所有资源
        processManager.terminateAllProcesses();
        if (scheduler != null) scheduler.stop();
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
    
    public DeviceManager getDeviceManager() {
        return deviceManager;
    }
    
    public Scheduler getScheduler() {
        return scheduler;
    }
}

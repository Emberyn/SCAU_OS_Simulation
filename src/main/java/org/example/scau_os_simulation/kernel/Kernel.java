package org.example.scau_os_simulation.kernel;

// 移除可能引起歧义的 Process 导入，改用全限定名
import org.example.scau_os_simulation.filesystem.FileSystem;
import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.sync.SyncManager;
import org.example.scau_os_simulation.logging.OperationLogger;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.ProducerConsumerExecutable;
import org.example.scau_os_simulation.cli.CommandExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Kernel
{
    private static Kernel instance;

    private ProcessManager processManager;
    private MemoryManager memoryManager;
    private FileSystemManager fileSystemManager;
    private DeviceManager deviceManager;
    private Scheduler scheduler;
    private SyncManager syncManager;
    private OperationLogger operationLogger;
    private PerformanceMonitor performanceMonitor;
    private CommandExecutor commandExecutor;

    private CPU cpu;

    private Consumer<String> terminalListener;
    private final List<String> outputLogs = new ArrayList<>();

    public Kernel()
    {
        instance = this;
        this.commandExecutor = new CommandExecutor();
    }

    public static Kernel getInstance()
    {
        return instance;
    }

    public void initialize()
    {
        try
        {
            // 1. 初始化基础设施
            Memory memory = new Memory(2048);
            memoryManager = new MemoryManager(memory);
            FileSystem fileSystem = new FileSystem(4096);
            fileSystemManager = new FileSystemManager(fileSystem);

            // 2. 初始化核心管理器 (注意顺序！)
            processManager = new ProcessManager(memoryManager);
            deviceManager = new DeviceManager(processManager);

            // 【修复 1】CPU 初始化移到这里，并传入正确的参数
            // 根据报错，CPU 需要 ProcessManager 和 DeviceManager
            cpu = new CPU(processManager, deviceManager);

            syncManager = new SyncManager();
            operationLogger = new OperationLogger();
            performanceMonitor = new PerformanceMonitor(100);

            // 3. 创建目录
            fileSystemManager.createDirectory("/system", "exec");
            fileSystemManager.createDirectory("/user", "data");

            // 4. 创建演示文件
            for (int i = 0; i < 3; i++)
            {
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

                // CPU 密集型
                List<String> insCpu = new ArrayList<>();
                insCpu.add("x=0");
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 40; k++) insCpu.add("x++");
                    insCpu.add("!" + devCode + "20");
                }
                insCpu.add("end");
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 1) + "_" + devCode + "_CPU.e", insCpu);

                // IO 密集型
                List<String> insIo = new ArrayList<>();
                insIo.add("x=0");
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 20; k++) insIo.add("x++");
                    insIo.add("!" + devCode + "40");
                }
                insIo.add("end");
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 2) + "_" + devCode + "_IO.e", insIo);
            }

            // 5. 补充同步演示
            syncManager.createSemaphore("mutex", 1);
            syncManager.createSemaphore("empty", 5);
            syncManager.createSemaphore("full", 0);
            fileSystemManager.createExecutable("/system/exec", "producer.e", new ProducerConsumerExecutable("producer", 1, 50));
            fileSystemManager.createExecutable("/system/exec", "consumer.e", new ProducerConsumerExecutable("consumer", 1, 50));

            // 6. 启动进程 (使用全限定名解决报错)
            for (int i = 0; i < 3; i++)
            {
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

                // 【修复 2】强制使用全限定名 org.example...Process
                String nameCpu = "p" + (i * 2 + 1) + "_" + devCode + "_CPU.e";
                org.example.scau_os_simulation.process.Process p1 =
                        processManager.createProcess("计算型_" + devCode, 1);

                Executable exec1 = fileSystemManager.loadExecutable("/system/exec/" + nameCpu);
                if (p1 != null) p1.setExecutable(exec1);

                String nameIo = "p" + (i * 2 + 2) + "_" + devCode + "_IO.e";
                org.example.scau_os_simulation.process.Process p2 =
                        processManager.createProcess("阻塞型_" + devCode, 2);

                Executable exec2 = fileSystemManager.loadExecutable("/system/exec/" + nameIo);
                if (p2 != null) p2.setExecutable(exec2);
            }

            // 7. 初始化调度器
            scheduler = new Scheduler(processManager, deviceManager);

            logOutput("内核初始化完成 (6个演示进程已就绪)");

        } catch (Exception e)
        {
            e.printStackTrace();
            System.err.println("内核初始化失败: " + e.getMessage());
        }
    }

    public void start()
    {
        if (scheduler != null) {
            scheduler.start();
            if (operationLogger != null) operationLogger.info(OperationLogger.OperationType.SYSTEM, "系统内核已启动调度", null);
        } else {
            System.err.println("严重错误: 调度器未初始化！");
        }
    }

    public void shutdown()
    {
        processManager.terminateAllProcesses();
        if (scheduler != null) scheduler.stop();
    }

    // Getters
    public CPU getCPU() { return cpu; }
    public ProcessManager getProcessManager() { return processManager; }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public FileSystemManager getFileSystemManager() { return fileSystemManager; }
    public DeviceManager getDeviceManager() { return deviceManager; }
    public Scheduler getScheduler() { return scheduler; }
    public SyncManager getSyncManager() { return syncManager; }
    public OperationLogger getOperationLogger() { return operationLogger; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }

    public void setTerminalListener(Consumer<String> listener) { this.terminalListener = listener; }
    public void logOutput(String s) { outputLogs.add(s); }
    public void printToTerminal(String s) {
        outputLogs.add(s);
        if (terminalListener != null) javafx.application.Platform.runLater(() -> terminalListener.accept(s));
    }
    public List<String> getOutputLogs() { return outputLogs; }

    public long getSystemClock() {
        return scheduler == null ? 0 : scheduler.getSystemClock();
    }

    public int getTimeSlice() {
        // 【修复 3】同样使用全限定名
        org.example.scau_os_simulation.process.Process p = processManager == null ? null : processManager.getRunning();
        return p == null ? 0 : p.getPcb().getTimeSlice();
    }

    public double getCpuUtilization() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getCpuUtilization();
    }

    public double getMemoryUtilization() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getMemoryUsage();
    }

    public double getSystemLoad() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getSystemLoad();
    }
}
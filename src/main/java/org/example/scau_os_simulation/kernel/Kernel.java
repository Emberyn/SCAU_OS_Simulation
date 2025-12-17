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
            // 稍微把磁盘改大一点点，防止塞了这么多文件后爆满 (或者保持 64 也可以，只要这些文件很小)
            FileSystem fileSystem = new FileSystem(256);
            fileSystemManager = new FileSystemManager(fileSystem);

            // 2. 初始化核心管理器
            processManager = new ProcessManager(memoryManager);
            deviceManager = new DeviceManager(processManager);

            // CPU 初始化
            cpu = new CPU(processManager, deviceManager);

            syncManager = new SyncManager();
            operationLogger = new OperationLogger();
            performanceMonitor = new PerformanceMonitor(100);

            // =================================================================
            // 3. 创建目录结构 (包含演示搜索用的 docs)
            // =================================================================

            // 【修复 Turn 15 问题】先检查是否存在，避免重复创建 exec(1)
            if (fileSystemManager.getDirectory("/system/exec") == null) {
                fileSystemManager.createDirectory("/system", "exec");
            }
            if (fileSystemManager.getDirectory("/user/data") == null) {
                fileSystemManager.createDirectory("/user", "data");
            }

            // 【新增】创建 docs 目录及子目录
            if (fileSystemManager.getDirectory("/docs") == null) {
                fileSystemManager.createDirectory("/", "docs");          // /docs
                fileSystemManager.createDirectory("/docs", "work");     // /docs/work
                fileSystemManager.createDirectory("/docs", "personal"); // /docs/personal
            }

            // 【新增】在 docs 下创建各种“假”文件用于搜索演示
            // 我们利用 createExecutable 写入简单的文本内容
            java.util.List<String> dummyContent = new java.util.ArrayList<>();
            dummyContent.add("This is a test file.");

            // 根文档
            fileSystemManager.createExecutable("/docs", "readme.txt", dummyContent);
            fileSystemManager.createExecutable("/docs", "secret.log", dummyContent);

            // 工作文档
            fileSystemManager.createExecutable("/docs/work", "plan.doc", dummyContent);
            fileSystemManager.createExecutable("/docs/work", "budget.xls", dummyContent);

            // 个人文档
            fileSystemManager.createExecutable("/docs/personal", "photo.jpg", dummyContent);
            fileSystemManager.createExecutable("/docs/personal", "diary.txt", dummyContent);


            // =================================================================
            // 4. 创建演示文件 (CPU/IO 对比)
            // =================================================================
            for (int i = 0; i < 3; i++)
            {
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

                // CPU 密集型
                java.util.List<String> insCpu = new java.util.ArrayList<>();
                insCpu.add("x=0");
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 40; k++) insCpu.add("x++");
                    insCpu.add("!" + devCode + "20");
                }
                insCpu.add("end");
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 1) + "_" + devCode + "_CPU.e", insCpu);

                // IO 密集型
                java.util.List<String> insIo = new java.util.ArrayList<>();
                insIo.add("x=0");
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 20; k++) insIo.add("x++");
                    insIo.add("!" + devCode + "40");
                }
                insIo.add("end");
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 2) + "_" + devCode + "_IO.e", insIo);
            }

            // 5. 补充同步演示 (生产者消费者)
            syncManager.createSemaphore("mutex", 1);
            syncManager.createSemaphore("empty", 5);
            syncManager.createSemaphore("full", 0);
            fileSystemManager.createExecutable("/system/exec", "producer.e", new ProducerConsumerExecutable("producer", 1, 50));
            fileSystemManager.createExecutable("/system/exec", "consumer.e", new ProducerConsumerExecutable("consumer", 1, 50));

            // 6. 启动初始进程
            for (int i = 0; i < 3; i++)
            {
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

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

            logOutput("内核初始化完成 (已创建 docs 目录供搜索演示)");

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
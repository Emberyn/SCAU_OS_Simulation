package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.FileSystem;
import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.sync.SyncManager;
import org.example.scau_os_simulation.logging.OperationLogger;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.ProducerConsumerExecutable;
import org.example.scau_os_simulation.cli.CommandExecutor;
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 操作系统内核核心类（Kernel）
 * 核心职责：
 * 1. 单例模式设计，作为整个模拟系统的全局入口和核心管控中心
 * 2. 初始化所有核心子系统（内存、文件系统、进程、设备、调度、同步等）
 * 3. 预置演示用的目录结构和可执行文件（支撑CPU/IO对比、文件搜索、进程同步演示）
 * 4. 提供系统级操作接口（启动/关闭内核、终端输出、系统状态查询）
 */
public class Kernel
{
    // 内核单例实例（全局唯一，通过getInstance获取）
    private static Kernel instance;

    // 进程管理器：负责进程的创建、销毁、状态管理、内存映射
    private ProcessManager processManager;
    // 内存管理器：负责内存块分配、释放、碎片整理
    private MemoryManager memoryManager;
    // 文件系统管理器：负责文件/目录的创建、删除、复制、查找
    private FileSystemManager fileSystemManager;
    // 设备管理器：负责IO设备管理、进程IO阻塞/唤醒、设备请求处理
    private DeviceManager deviceManager;
    // 调度器：实现进程调度算法，分配CPU执行权
    private Scheduler scheduler;
    // 同步管理器：负责信号量创建/操作，支撑进程同步（如生产者消费者模型）
    private SyncManager syncManager;
    // 操作日志器：记录系统关键操作（内存分配、进程调度、IO操作等）
    private OperationLogger operationLogger;
    // 性能监控器：实时监控CPU利用率、内存使用率、系统负载等指标（监控间隔100ms）
    private PerformanceMonitor performanceMonitor;
    // 命令执行器：解析并执行CLI终端输入的命令
    private CommandExecutor commandExecutor;

    // CPU模拟核心：执行进程指令，处理算术运算和IO请求
    private CPU cpu;

    // 终端输出监听器：内核输出内容到终端的回调函数（UI层实现）
    private Consumer<String> terminalListener;
    // 输出日志缓存：存储内核所有输出信息，用于回溯和展示
    private final List<String> outputLogs = new ArrayList<>();

    /**
     * 构造函数 - 初始化内核基础组件
     * 单例模式初始化，创建命令执行器实例
     */
    public Kernel()
    {
        instance = this;
        this.commandExecutor = new CommandExecutor(); // 初始化CLI命令执行器
    }

    /**
     * 获取内核单例实例
     * @return 全局唯一的Kernel实例
     */
    public static Kernel getInstance()
    {
        return instance;
    }

    /**
     * 内核初始化（核心方法）
     */
    public void initialize()
    {
        try
        {
            // 1. 初始化基础设施（内存、文件系统）
            Memory memory = new Memory(2048); // 创建2048KB的物理内存实例
            memoryManager = new MemoryManager(memory); // 初始化内存管理器
            // 创建256KB的文件系统
            FileSystem fileSystem = new FileSystem(1024);
            fileSystemManager = new FileSystemManager(fileSystem); // 初始化文件系统管理器

            // 2. 初始化核心管理器
            processManager = new ProcessManager(memoryManager); // 进程管理器依赖内存管理器
            deviceManager = new DeviceManager(processManager); // 设备管理器依赖进程管理器

            // CPU初始化：关联进程管理器（获取待执行进程）和设备管理器（处理IO请求）
            cpu = new CPU(processManager, deviceManager);

            // 初始化同步、日志、性能监控组件
            syncManager = new SyncManager();
            operationLogger = new OperationLogger();
            performanceMonitor = new PerformanceMonitor(100); // 性能监控间隔100ms

            // =================================================================
            // 3. 创建演示目录结构 (支撑文件搜索功能演示)
            // =================================================================

            // 先检查目录是否存在，避免重复创建导致命名重复（如exec(1)）
            if (fileSystemManager.getDirectory("/system/exec") == null) {
                fileSystemManager.createDirectory("/system", "exec"); // 可执行文件存储目录
            }
            if (fileSystemManager.getDirectory("/user/data") == null) {
                fileSystemManager.createDirectory("/user", "data"); // 用户数据目录
            }

            // 创建docs目录及子目录（用于文件搜索功能演示）
            if (fileSystemManager.getDirectory("/docs") == null) {
                fileSystemManager.createDirectory("/", "docs");          // 根文档目录
                fileSystemManager.createDirectory("/docs", "work");     // 工作文档子目录
                fileSystemManager.createDirectory("/docs", "personal"); // 个人文档子目录
            }

            // 在docs目录下创建测试文件（用于搜索演示）
            // 构造简单的测试内容（一行文本）
            java.util.List<String> dummyContent = new java.util.ArrayList<>();
            dummyContent.add("This is a test file.");

            // 根文档目录下的测试文件
            fileSystemManager.createExecutable("/docs", "readme.txt", dummyContent);
            fileSystemManager.createExecutable("/docs", "secret.log", dummyContent);

            // 工作文档子目录下的测试文件
            fileSystemManager.createExecutable("/docs/work", "plan.doc", dummyContent);
            fileSystemManager.createExecutable("/docs/work", "budget.xls", dummyContent);

            // 个人文档子目录下的测试文件
            fileSystemManager.createExecutable("/docs/personal", "photo.jpg", dummyContent);
            fileSystemManager.createExecutable("/docs/personal", "diary.txt", dummyContent);



            // =================================================================
            // 4. 创建演示用可执行文件 (CPU密集型/IO密集型对比)
            // =================================================================
            // 循环创建3组（对应设备A/B/C）的CPU/IO密集型程序
            for (int i = 0; i < 3; i++)
            {
                // 设备编码映射：0→A，1→B，2→C
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

                // 构建CPU密集型程序指令：多运算 + 少IO
                java.util.List<String> insCpu = new java.util.ArrayList<>();
                insCpu.add("x=0"); // 初始化变量x
                // 循环10次：40次x++（CPU密集） + 20ms IO延迟
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 40; k++) insCpu.add("x++"); // CPU运算指令
                    insCpu.add("!" + devCode + "20"); // IO指令（设备devCode，延迟20ms）
                }
                insCpu.add("end"); // 程序结束指令
                // 创建CPU密集型可执行文件：/system/exec/p[奇数]_[devCode]_CPU.e
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 1) + "_" + devCode + "_CPU.e", insCpu);

                // 构建IO密集型程序指令：少运算 + 多IO
                java.util.List<String> insIo = new java.util.ArrayList<>();
                insIo.add("x=0"); // 初始化变量x
                // 循环10次：20次x++（少量CPU） + 40ms IO延迟
                for (int j = 0; j < 10; j++) {
                    for (int k = 0; k < 20; k++) insIo.add("x++"); // 少量CPU运算
                    insIo.add("!" + devCode + "40"); // IO指令（设备devCode，延迟40ms）
                }
                insIo.add("end"); // 程序结束指令
                // 创建IO密集型可执行文件：/system/exec/p[偶数]_[devCode]_IO.e
                fileSystemManager.createExecutable("/system/exec", "p" + (i * 2 + 2) + "_" + devCode + "_IO.e", insIo);
            }

            // 5. 初始化进程同步演示资源（生产者消费者模型）
            // 创建核心信号量：mutex（互斥锁）、empty（空缓冲区数）、full（满缓冲区数）
            syncManager.createSemaphore("mutex", 1);   // 互斥访问缓冲区（初始1）
            syncManager.createSemaphore("empty", 5);   // 空缓冲区数量（初始5）
            syncManager.createSemaphore("full", 0);    // 满缓冲区数量（初始0）
            // 创建生产者可执行文件：/system/exec/producer.e
            fileSystemManager.createExecutable("/system/exec", "producer.e", new ProducerConsumerExecutable("producer", 1, 50));
            // 创建消费者可执行文件：/system/exec/consumer.e
            fileSystemManager.createExecutable("/system/exec", "consumer.e", new ProducerConsumerExecutable("consumer", 1, 50));

            // 6. 启动初始进程（加载CPU/IO密集型程序）
            for (int i = 0; i < 3; i++)
            {
                String devCode = (i == 0) ? "A" : (i == 1) ? "B" : "C";

                // 加载CPU密集型程序并创建进程（优先级1）
                String nameCpu = "p" + (i * 2 + 1) + "_" + devCode + "_CPU.e";
                // 因为上面已经强制导入了，这里直接写 Process 即可，编译器知道是指你写的那个
                Process p1 = processManager.createProcess("计算型_" + devCode, 1);
                Executable exec1 = fileSystemManager.loadExecutable("/system/exec/" + nameCpu);
                if (p1 != null) p1.setExecutable(exec1);

                // 加载IO密集型程序并创建进程（优先级2）
                String nameIo = "p" + (i * 2 + 2) + "_" + devCode + "_IO.e";
                org.example.scau_os_simulation.process.Process p2 =
                        processManager.createProcess("阻塞型_" + devCode, 2); // 创建进程（名称+优先级）
                org.example.scau_os_simulation.process.Executable exec2 = fileSystemManager.loadExecutable("/system/exec/" + nameIo);
                if (p2 != null) p2.setExecutable(exec2);
            }

            // 7. 初始化调度器（关联进程管理器和设备管理器）
            scheduler = new Scheduler(processManager, deviceManager);

            // 记录内核初始化完成日志
            logOutput("内核初始化完成 (已创建 docs 目录供搜索演示)");

        } catch (Exception e)
        {
            e.printStackTrace(); // 打印异常堆栈，便于调试
            System.err.println("内核初始化失败: " + e.getMessage()); // 输出初始化失败原因
        }
    }

    /**
     * 启动内核（系统核心入口）
     * 启动调度器，开始进程调度和CPU指令执行
     */
    public void start()
    {
        if (scheduler != null) {
            scheduler.start(); // 启动调度器，开始进程调度循环
            // 记录调度器启动日志
            if (operationLogger != null) operationLogger.info(OperationLogger.OperationType.SYSTEM, "系统内核已启动调度", null);
        } else {
            System.err.println("严重错误: 调度器未初始化！"); // 调度器未初始化的错误提示
        }
    }

    /**
     * 关闭内核（系统退出入口）
     * 终止所有运行进程，停止调度器，释放系统资源
     */
    public void shutdown()
    {
        processManager.terminateAllProcesses(); // 终止所有进程
        if (scheduler != null) scheduler.stop(); // 停止调度器循环
    }

    // ==================== 核心组件Getter方法 ====================
    /**
     * 获取CPU模拟核心实例
     * @return CPU实例（负责指令执行）
     */
    public CPU getCPU() { return cpu; }

    /**
     * 获取进程管理器实例
     * @return 进程管理器（负责进程生命周期管理）
     */
    public ProcessManager getProcessManager() { return processManager; }

    /**
     * 获取内存管理器实例
     * @return 内存管理器（负责内存分配/释放）
     */
    public MemoryManager getMemoryManager() { return memoryManager; }

    /**
     * 获取文件系统管理器实例
     * @return 文件系统管理器（负责文件/目录操作）
     */
    public FileSystemManager getFileSystemManager() { return fileSystemManager; }

    /**
     * 获取设备管理器实例
     * @return 设备管理器（负责IO设备管理）
     */
    public DeviceManager getDeviceManager() { return deviceManager; }

    /**
     * 获取调度器实例
     * @return 调度器（负责进程调度）
     */
    public Scheduler getScheduler() { return scheduler; }

    /**
     * 获取同步管理器实例
     * @return 同步管理器（负责信号量操作）
     */
    public SyncManager getSyncManager() { return syncManager; }

    /**
     * 获取操作日志器实例
     * @return 操作日志器（记录系统关键操作）
     */
    public OperationLogger getOperationLogger() { return operationLogger; }

    /**
     * 获取性能监控器实例
     * @return 性能监控器（监控系统资源使用情况）
     */
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }

    /**
     * 获取命令执行器实例
     * @return 命令执行器（解析CLI命令）
     */
    public CommandExecutor getCommandExecutor() { return commandExecutor; }

    // ==================== 终端输出相关方法 ====================
    /**
     * 设置终端输出监听器
     * @param listener 输出回调函数（接收内核输出字符串）
     */
    public void setTerminalListener(Consumer<String> listener) { this.terminalListener = listener; }

    /**
     * 记录内核输出日志（仅缓存，不输出到终端）
     * @param s 要记录的输出内容
     */
    public void logOutput(String s) { outputLogs.add(s); }

    /**
     * 输出内容到终端（线程安全）
     * 确保在JavaFX应用线程执行，避免UI线程异常
     * @param s 要输出到终端的内容
     */
    public void printToTerminal(String s) {
        outputLogs.add(s); // 先缓存到日志列表
        // 若终端监听器存在，在JavaFX应用线程执行回调
        if (terminalListener != null) javafx.application.Platform.runLater(() -> terminalListener.accept(s));
    }

    /**
     * 获取内核输出日志列表
     * @return 所有内核输出日志的列表
     */
    public List<String> getOutputLogs() { return outputLogs; }

    // ==================== 系统状态查询方法 ====================
    /**
     * 获取系统时钟（调度器时钟周期）
     * @return 系统时钟值（毫秒/时钟周期），调度器未初始化返回0
     */
    public long getSystemClock() {
        return scheduler == null ? 0 : scheduler.getSystemClock();
    }

    /**
     * 获取当前运行进程的时间片大小
     * @return 时间片大小，无运行进程返回0
     */
    public int getTimeSlice() {
        Process p = processManager == null ? null : processManager.getRunning();
        return p == null ? 0 : p.getPcb().getTimeSlice();
    }

    /**
     * 获取CPU利用率
     * @return CPU利用率（0.0~1.0），性能监控器未初始化返回0.0
     */
    public double getCpuUtilization() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getCpuUtilization();
    }

    /**
     * 获取内存利用率
     * @return 内存利用率（0.0~1.0），性能监控器未初始化返回0.0
     */
    public double getMemoryUtilization() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getMemoryUsage();
    }

    /**
     * 获取系统负载
     * @return 系统负载值（0.0~1.0），性能监控器未初始化返回0.0
     */
    public double getSystemLoad() {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getSystemLoad();
    }
}
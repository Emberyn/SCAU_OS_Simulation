/**********************************************************************************************
 * 项目名称：操作系统模拟器
 *
 * 文件说明：
 * 这个文件是操作系统模拟器的核心 - 内核（Kernel）。
 * 内核是操作系统的"大脑"，负责协调和管理所有的系统资源。
 *
 * 对于零基础的同学：
 * - 内核就像一个大型公司的总经理，负责协调各个部门的工作
 * - 它管理进程（员工）、内存（办公室空间）、文件（文件柜）、设备（办公设备）
 * - 它确保所有部门都能高效、有序地工作，不会互相冲突
 *
 * 内核的主要职责：
 * 1. 进程管理：创建、调度、终止进程
 * 2. 内存管理：分配、回收内存空间
 * 3. 文件系统管理：创建、删除、读写文件和目录
 * 4. 设备管理：管理各种硬件设备的使用
 * 5. 系统调度：决定哪个进程在什么时候使用CPU
 **********************************************************************************************/

// 包声明：这个文件属于kernel包，专门存放内核相关的类
package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.cli.OSShellCommand;
import org.example.scau_os_simulation.filesystem.FileSystem;
import org.example.scau_os_simulation.process.Executable;
import org.example.scau_os_simulation.memory.Memory;
import org.example.scau_os_simulation.sync.SyncManager;
import org.example.scau_os_simulation.logging.OperationLogger;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.ProducerConsumerExecutable;

import java.util.function.Consumer;

import org.example.scau_os_simulation.cli.CommandExecutor;

/**
 * 操作系统内核类
 * <p>
 * 这个类是整个操作系统的核心，负责协调和管理所有的系统资源。
 * 它实现了单例模式，确保整个系统中只有一个内核实例。
 * <p>
 * 想象这个类就像一个大型公司的总经理办公室：
 * - 它有各个部门的经理（各个管理器）
 * - 它协调各部门之间的工作
 * - 它确保整个公司高效运转
 */
public class Kernel
{
    /**
     * 内核的单例实例
     * <p>
     * 单例模式确保整个系统中只有一个内核实例
     * 就像一个国家只能有一个中央政府一样
     */
    private static Kernel instance;

    /**
     * 进程管理器：管理创建/终止/调度与队列（HR 部门）
     */
    private ProcessManager processManager;

    /**
     * 内存管理器：负责分配/回收内存（物业部）
     */
    private MemoryManager memoryManager;

    /**
     * 文件系统管理器：负责目录/文件增删与载入（档案部）
     */
    private FileSystemManager fileSystemManager;

    /**
     * 设备管理器：负责设备占用/队列与进程阻塞/解阻（设备部）
     */
    private DeviceManager deviceManager;

    /**
     * 调度器：周期推进系统时钟、驱动 CPU 与设备（排班经理）
     */
    private Scheduler scheduler;

    /**
     * 同步管理器：管理信号量等同步机制
     */
    private SyncManager syncManager;

    /**
     * 操作日志记录器：记录所有系统操作
     */
    private OperationLogger operationLogger;

    /**
     * 性能监控器：监控系统性能指标
     */
    private PerformanceMonitor performanceMonitor;


    // 新增：用于终端输出的回调接口
    private Consumer<String> terminalListener;
    private CommandExecutor commandExecutor;

    /**
     * 执行结果日志：记录进程结束时的 AX 值等信息
     */
    private final java.util.List<String> outputLogs = new java.util.ArrayList<>();

    /**
     * 内核构造函数
     * 当创建内核实例时，会将自己设置为单例实例
     * 这确保了在整个系统中只有一个内核实例存在
     * 想象这就像公司选举总经理，一旦选出，所有人都要知道谁是总经理
     */
    public Kernel()
    {
        instance = this;  // 设置单例实例，确保全局访问

        this.commandExecutor = new CommandExecutor();
    }

    /**
     * 获取内核的单例实例
     * 这是访问内核的全局入口点，任何地方都可以通过这个方法获取内核实例
     * 就像公司的任何人都可以通过总经理办公室找到总经理一样
     *
     * @return 内核的单例实例
     */
    public static Kernel getInstance()
    {
        return instance;  // 返回单例实例
    }

    /**
     * 初始化操作系统内核
     * <p>
     * 这个方法负责创建和初始化所有的系统组件，就像公司开业时的准备工作：
     * 1. 准备办公空间（初始化内存）
     * 2. 准备文件柜（初始化文件系统）
     * 3. 招聘员工（初始化进程管理器）
     * 4. 采购办公设备（初始化设备管理器）
     * 5. 准备办公用品（创建可执行文件）
     * 6. 安排员工上岗（创建进程）
     * 7. 开始营业（启动调度器）
     * 整个过程就像开一家新公司，需要准备各种资源和人员
     */
    public void initialize()
    {
        try
        {
            // 第一步：初始化内存
            // 创建1024KB的内存，就像租了一个1024KB大小的办公空间
            Memory memory = new Memory(1024); // 1024KB内存
            memoryManager = new MemoryManager(memory);

            // 第二步：初始化文件系统
            // 创建2048KB的磁盘空间，就像准备了文件柜来存放公司文件
            FileSystem fileSystem = new FileSystem(2048); // 2048KB磁盘空间
            fileSystemManager = new FileSystemManager(fileSystem);

            // 第三步：初始化进程管理器
            // 创建进程管理器，并让它使用我们准备好的内存管理器
            // 就像成立人力资源部门，并告诉他们可以使用公司的办公空间
            processManager = new ProcessManager(memoryManager);

            // 第四步：初始化设备管理器
            // 创建设备管理器，并让它与进程管理器协作
            // 就像成立设备管理部门，并让他们与人力资源部门配合工作
            deviceManager = new DeviceManager(processManager);

            // 第四步补充：初始化同步管理器
            // 创建同步管理器，用于管理信号量等同步机制
            syncManager = new SyncManager();

            // 第四步补充：初始化操作日志记录器
            // 创建操作日志记录器，用于记录所有系统操作
            operationLogger = new OperationLogger();

            // 第四步补充：初始化性能监控器
            // 创建性能监控器，用于监控系统性能指标
            performanceMonitor = new PerformanceMonitor(100); // 保存最近100个快照

            // 第五步：创建可执行文件
            for (int i = 1; i <= 10; i++)
            {
                java.util.List<String> ins = new java.util.ArrayList<>();
                ins.add("x=" + (i * 3 % 99));  // 设置初始值

                // --- 【修改点】插入大量“注水”指令，延长运行时间 ---
                // 插入 50 条自增指令，让它多跑一会儿
                for (int j = 0; j < 50; j++) {
                    ins.add("x++");
                    // 每隔 10 条指令插入一次设备请求，让它偶尔阻塞一下，演示状态切换
                    if (j % 10 == 0) {
                        ins.add("!A" + (1 + (j % 5))); // 申请设备A，占用 1~5 个时间片
                    }
                }
                // ---------------------------------------------

                ins.add("end");                // 程序结束
                fileSystemManager.createExecutable("/system/exec", "p" + i + ".e", ins);
            }

            // 补充目录与文件：满足“至少5个目录与15个文件”的要求（简化实现）
            fileSystemManager.createDirectory("/user", "docs");
            fileSystemManager.createDirectory("/user", "tmp");
            fileSystemManager.createDirectory("/user", "logs");
            fileSystemManager.createDirectory("/user", "bin");
            fileSystemManager.createDirectory("/user", "data");
            fileSystemManager.createDirectory("/system", "config");

            fileSystemManager.createFile("/user/docs", "readme.txt", 1);
            fileSystemManager.createFile("/user/docs", "notes.txt", 1);
            fileSystemManager.createFile("/user/docs", "manual.txt", 1);
            fileSystemManager.createFile("/user/logs", "app.log", 1);
            fileSystemManager.createFile("/user/logs", "error.log", 1);
            fileSystemManager.createFile("/user/bin", "run.bat", 1);
            fileSystemManager.createFile("/system/config", "sys.ini", 1);
            fileSystemManager.createFile("/user/data", "dataset1.txt", 1);
            fileSystemManager.createFile("/user/data", "dataset2.txt", 1);
            fileSystemManager.createFile("/user/data", "dataset3.txt", 1);

            // 第五步补充：创建生产者消费者演示程序
            // 创建信号量
            syncManager.createSemaphore("mutex", 1);    // 互斥信号量
            syncManager.createSemaphore("empty", 5);    // 空位信号量（缓冲区大小为5）
            syncManager.createSemaphore("full", 0);     // 满位信号量

            // 创建生产者消费者可执行文件
            ProducerConsumerExecutable producer =
                    new ProducerConsumerExecutable("producer", 1, 3);
            ProducerConsumerExecutable consumer =
                    new ProducerConsumerExecutable("consumer", 1, 3);

            fileSystemManager.createExecutable("/system/exec", "producer1.e", producer);
            fileSystemManager.createExecutable("/system/exec", "consumer1.e", consumer);

            // 第六步：创建进程并加载可执行文件
            // 为每个可执行文件创建一个进程，就像为每个工作任务分配一个员工
            for (int i = 1; i <= 10; i++)
            {
                // 创建进程，名称为"进程1"到"进程10"
                org.example.scau_os_simulation.process.Process p = processManager.createProcess("进程" + i, 1);
                // 加载对应的可执行文件
                Executable exec = fileSystemManager.loadExecutable("/system/exec/p" + i + ".e");
                // 如果进程创建成功，就将可执行文件分配给它
                if (p != null) p.setExecutable(exec);
            }

            // 第七步：启动调度器
            scheduler = new Scheduler(processManager, deviceManager);
            logOutput("内核初始化完成，等待启动...");
        } catch (Exception e)
        {
            e.printStackTrace();
            System.err.println("内核初始化失败: " + e.getMessage());
            logOutput("FATAL: 内核初始化失败 - " + e.getMessage());
        }
    }

    // 新增一个公开方法，由 UI 控制何时启动
    public void start()
    {
        if (scheduler != null)
        {
            scheduler.start();
            // 可以记录一条日志
            if (operationLogger != null)
            {
                operationLogger.info(
                        org.example.scau_os_simulation.logging.OperationLogger.OperationType.SYSTEM,
                        "系统内核已启动调度", null
                );
            }
        } else
        {
            // 【关键修改】显式报错
            System.err.println("严重错误: 调度器(scheduler)未初始化！请检查 initialize() 是否抛出了异常。");
            logOutput("错误: 无法启动系统，调度器未初始化。");
        }
    }

    /**
     * 关闭操作系统内核
     * 这个方法负责安全地关闭所有系统资源，就像公司下班时的清理工作：
     * 1. 让所有员工下班（终止所有进程）
     * 2. 关闭排班系统（停止调度器）
     * 这个过程确保系统能够优雅地关闭，不会造成资源泄露或数据丢失
     */
    public void shutdown()
    {
        // 第一步：终止所有进程
        // 就像通知所有员工下班，确保没有人在加班
        processManager.terminateAllProcesses();

        // 第二步：停止调度器
        // 就像关闭排班系统，不再安排新的工作
        if (scheduler != null) scheduler.stop();
    }

    public void setTerminalListener(Consumer<String> listener)
    {
        this.terminalListener = listener;
    }

    /**
     * 记录一条执行结果日志
     */
    public void logOutput(String s)
    {
        outputLogs.add(s);
    }

    /**
     * 用于 CLI 命令（如 ls, pwd, mkdir）向终端窗口输出结果。
     */
    public void printToTerminal(String s)
    {
        // 1. 可选：终端的输出通常也应该记录在系统总日志里，方便回溯
        outputLogs.add(s);

        // 2. 发送给终端窗口
        if (terminalListener != null)
        {
            javafx.application.Platform.runLater(() -> terminalListener.accept(s));
        }
    }

    /**
     * 获取所有执行结果日志（供 UI 展示）
     */
    public java.util.List<String> getOutputLogs()
    {
        return outputLogs;
    }

    /**
     * @return 进程管理器实例，用于管理所有的进程
     */
    public ProcessManager getProcessManager()
    {
        return processManager;  // 返回进程管理器
    }

    /**
     * @return 内存管理器实例，用于管理系统的内存资源
     */
    public MemoryManager getMemoryManager()
    {
        return memoryManager;  // 返回内存管理器
    }

    /**
     * @return 文件系统管理器实例，用于管理文件和目录
     */
    public FileSystemManager getFileSystemManager()
    {
        return fileSystemManager;  // 返回文件系统管理器
    }

    /**
     * @return 设备管理器实例，用于管理各种硬件设备
     */
    public DeviceManager getDeviceManager()
    {
        return deviceManager;  // 返回设备管理器
    }

    /**
     * @return 调度器实例，用于决定哪个进程在什么时候使用CPU
     */
    public Scheduler getScheduler()
    {
        return scheduler;  // 返回调度器
    }

    /**
     * @return 同步管理器实例，用于管理信号量等同步机制
     */
    public SyncManager getSyncManager()
    {
        return syncManager;  // 返回同步管理器
    }

    /**
     * @return 操作日志记录器实例，用于记录所有系统操作
     */
    public OperationLogger getOperationLogger()
    {
        return operationLogger;  // 返回操作日志记录器
    }

    /**
     * @return 性能监控器实例，用于监控系统性能指标
     */
    public PerformanceMonitor getPerformanceMonitor()
    {
        return performanceMonitor;  // 返回性能监控器
    }

    public long getSystemClock()
    {
        return scheduler == null ? 0 : scheduler.getSystemClock();
    }

    public int getTimeSlice()
    {
        org.example.scau_os_simulation.process.Process p = processManager == null ? null : processManager.getRunning();
        return p == null ? 0 : p.getPcb().getTimeSlice();
    }

    public double getCpuUtilization()
    {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getCpuUtilization();
    }

    public double getMemoryUtilization()
    {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getMemoryUsage();
    }

    public double getSystemLoad()
    {
        return performanceMonitor == null ? 0.0 : performanceMonitor.getLatestSnapshot().getSystemLoad();
    }

    public CommandExecutor getCommandExecutor()
    {
        return commandExecutor;
    }
}

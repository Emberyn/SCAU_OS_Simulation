package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.CacheHint;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.control.Tooltip;
import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;
import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.performance.PerformanceChartFX;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.PCB;
// 【关键】使用精准导入，避免与 java.lang.Process 冲突
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.Executable;

import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 操作系统模拟器 - 主界面控制器 (MainController)
 * * 核心职责 (MVC - Controller):
 * 1. 【视图管理】负责加载和管理所有 GUI 组件（桌面、任务栏、窗口、图表）。
 * 2. 【数据桥接】作为 Model (Kernel) 和 View (FXML) 的中间人，定期从内核拉取数据刷新界面。
 * 3. 【交互响应】处理用户的所有点击、拖拽、快捷键事件，并调用内核对应的方法执行操作。
 * 4. 【窗口系统】实现了一套轻量级的内部窗口管理器 (InternalWindow)，模拟真实 OS 的多窗口体验。
 * * 技术架构特点：
 * - 线程安全：使用 Platform.runLater() 确保所有 UI 更新都在 JavaFX 应用程序线程执行。
 * - 定时刷新：使用 ScheduledExecutorService 每 500ms 轮询内核状态，实现动态监控。
 * - 虚拟桌面：通过 StackPane 和 Pane 的组合，实现了图标层、窗口层、任务栏的分层渲染。
 * * @author SCAU OS Team
 * @version 1.0 (Refactored)
 */
public class MainController implements Initializable {
    // =================================================================================
    // 1. FXML 组件注入
    // =================================================================================

    // --- 基础容器 ---
    /** 根容器：最底层，用于层叠背景、桌面图标、窗口层 */
    @FXML private StackPane rootStackPane;
    /** 桌面图标区域：流式布局，放置"我的电脑"等图标 */
    @FXML private FlowPane desktopArea;
    /** 底部任务栏：存放开始按钮、时间、已打开窗口的标签 */
    @FXML private HBox taskBarApps;
    /** 系统时间显示标签 */
    @FXML private Label systemClockLabel;
    /** "开始"菜单按钮 */
    @FXML private Button startMenuBtn;


    // --- 各个功能模块的视图容器 (预加载在 FXML 中，点击图标时放入窗口显示) ---
    @FXML private VBox processViewRoot;      // 进程管理界面
    @FXML private VBox memoryViewRoot;       // 内存管理界面
    @FXML private VBox fileSystemViewRoot;   // 文件资源管理器界面
    @FXML private AnchorPane deviceViewRoot; // 设备管理界面
    @FXML private VBox performanceViewRoot;  // 性能监视器界面
    @FXML private StackPane performanceChartContainer; // 性能折线图容器

    // --- 工具栏控制按钮 (功能区) ---
    @FXML private Button startSystemBtn, stopSystemBtn;          // 系统启停
    @FXML private Button createProcessBtn, terminateProcessBtn;  // 进程控制
    @FXML private Button defragmentBtn;                          // 内存整理
    @FXML private Button createFileBtn, createDirectoryBtn;      // 文件创建
    @FXML private Button deleteFileBtn, copyFileBtn, pasteFileBtn; // 文件操作
    @FXML private Button searchFileBtn;                          // 文件搜索





// =================================================================================
    // 3. 数据绑定组件 (Data Binding Targets)
    // 这些组件通过 JavaFX 的 Property 绑定机制，实时显示内核状态
    // =================================================================================



    // ---------------------------------------------------------
    // [A] 进程管理视图 (Process Management View)
    // 用于展示系统中的所有进程状态、CPU 寄存器值以及调度队列
    // ---------------------------------------------------------

    /** * 进程控制块 (PCB) 表格
     * 数据源：kernel.getProcessManager().getProcesses()
     * 作用：展示系统中所有进程的详细信息列表
     */
    @FXML private TableView<PCB> processTableView;

    /** PID 列：显示进程唯一标识符 */
    @FXML private TableColumn<PCB, Number> pidColumn;
    /** 优先级列：显示进程调度优先级 (1-5) */
    @FXML private TableColumn<PCB, Number> priorityColumn;
    /** 内存基址列：显示进程在物理内存中的起始地址 */
    @FXML private TableColumn<PCB, Number> memoryAddressColumn;
    /** 内存大小列：显示进程占用的内存块大小 (KB) */
    @FXML private TableColumn<PCB, Number> memorySizeColumn;

    /** 进程名称列：显示用户定义的进程名 (如 "计算型_A") */
    @FXML private TableColumn<PCB, String> nameColumn;
    /** 状态列：显示进程当前状态 (NEW, READY, RUNNING, BLOCKED, TERMINATED) */
    @FXML private TableColumn<PCB, String> stateColumn;

    /** * [核心调度指标]
     * timeSliceColumn: 显示当前时间片剩余值 (RR调度算法核心)
     * totalRemainingTimeColumn: 显示进程总剩余运行时间 (用于估算完成进度)
     */
    @FXML private TableColumn<PCB, Number> timeSliceColumn, totalRemainingTimeColumn;


    /** * CPU 状态显示面板
     * runningPidLabel: 当前占用 CPU 的进程 ID
     * irLabel: 指令寄存器 (IR)，显示当前正在执行的指令内容
     * axLabel: 通用寄存器 (AX)，显示运算结果或临时变量
     * tsLabel: 全局时间片计数器状态
     */
    @FXML private Label runningPidLabel, irLabel, axLabel, tsLabel;

    /** * 调度队列视图
     * readyQueueListView: 就绪队列，显示等待 CPU 的进程 (PID + 优先级)
     * blockedQueueListView: 阻塞队列，显示因 IO 等待挂起的进程
     */
    @FXML private ListView<String> readyQueueListView, blockedQueueListView;



    // ---------------------------------------------------------
    // [B] 系统日志视图 (System Logs)
    // ---------------------------------------------------------

    /** * 终端输出日志
     * 显示内核 printToTerminal 的内容，模拟标准输出 (stdout)
     */
    @FXML private ListView<String> outputListView;

    /** * 操作日志
     * 记录关键系统事件 (如 "进程创建 PID=1", "分配内存 64KB")，用于审计和调试
     */
    @FXML private ListView<String> operationLogListView;



    // ---------------------------------------------------------
    // [C] 资源监控仪表盘 (Resource Monitor)
    // ---------------------------------------------------------

    /** 进度条组件：可视化显示资源占用百分比 (0.0 - 1.0) */
    @FXML private ProgressBar memoryUsageBar;   // 物理内存使用率
    @FXML private ProgressBar diskUsageBar;     // 磁盘空间使用率
    @FXML private ProgressBar cpuUtilizationBar;// CPU 实时利用率
    @FXML private ProgressBar systemLoadBar;    // 系统平均负载

    /** 文本标签：显示具体的数值信息 (如 "1024KB / 2048KB") */
    @FXML private Label memoryInfoLabel;
    @FXML private Label diskInfoLabel;
    @FXML private Label fragmentationLabel;     // 内存碎片率显示

    /** 性能统计指标 (Dashboard Metrics) */
    @FXML private Label cpuUtilizationLabel, systemLoadLabel; // 实时值
    @FXML private Label avgCpuLabel, avgMemoryLabel;          // 平均值 (历史统计)
    @FXML private Label peakCpuLabel, peakMemoryLabel;        // 峰值 (历史最高)



    // ---------------------------------------------------------
    // [D] 内存管理视图 (Memory Management)
    // ---------------------------------------------------------

    /** * 内存块分配表
     * 展示物理内存中每一个已分配块的详细信息 (起始地址、大小、所属进程)
     */
    @FXML private TableView<MemoryBlock> memoryBlockTableView;
    @FXML private TableColumn<MemoryBlock, Number> startAddressColumn; // 块起始物理地址
    @FXML private TableColumn<MemoryBlock, Number> blockSizeColumn;    // 块大小
    @FXML private TableColumn<MemoryBlock, String> processColumn;      // 占用该块的 PID

    /** * 内存可视化画布 (核心组件)
     * 动态绘制彩色条带：红色代表系统进程，蓝色代表用户进程，白色代表空闲
     * 高度直观地展示内存碎片化情况
     */
    @FXML private Pane memoryVisualizationPane;

    /** 内存刻度尺：在可视化条带上方绘制地址刻度 (0, 64K, 128K...) */
    @FXML private Pane memoryRulerPane;



    // ---------------------------------------------------------
    // [E] 文件系统视图 (File System)
    // ---------------------------------------------------------

    /** * 目录树视图
     * 展示层级化的文件系统结构，支持展开/折叠、图标显示、多选操作
     */
    @FXML private TreeView<String> fileSystemTreeView;



    // ---------------------------------------------------------
    // [F] 设备管理视图 (Device Management)
    // ---------------------------------------------------------

    /** * 设备状态表
     * 展示所有 IO 设备 (A, B, C) 的当前状态
     */
    @FXML private TableView<Device> deviceTableView;
    @FXML private TableColumn<Device, String> deviceTypeColumn;   // 设备类型
    @FXML private TableColumn<Device, String> deviceInUseColumn;  // 是否忙碌 (Busy/Free)
    @FXML private TableColumn<Device, Number> devicePidColumn;    // 当前占用设备的 PID
    @FXML private TableColumn<Device, Number> deviceRemainColumn; // 当前作业剩余 IO 时间

    /** * 设备等待队列表
     * 展示正在排队等待 IO 资源的进程请求
     */
    @FXML private TableView<WaitRow> waitQueueTableView;
    @FXML private TableColumn<WaitRow, String> waitDeviceColumn;  // 等待的设备类型
    @FXML private TableColumn<WaitRow, Number> waitPidColumn;     // 等待进程 PID
    @FXML private TableColumn<WaitRow, Number> waitTimeColumn;    // 请求所需的 IO 时长






    // =================================================================================
    // 2. 核心成员变量
    // =================================================================================

    /** 操作系统内核引用 (单例模式获取) */
    private Kernel kernel;

    /** UI 刷新调度器：单线程池，用于定时从内核拉取数据 */
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();

    /** 性能图表组件封装 */
    private PerformanceChartFX performanceChart;

    /** 窗口管理器：记录 "内容节点" -> "窗口外壳" 的映射 */
    private final Map<Node, InternalWindow> openWindows = new HashMap<>();

    /** 剪贴板：存储复制的文件/目录对象 */
    private final List<Object> clipboardFiles = new ArrayList<>();

    /** 窗口层：专门用于放置内部窗口 (InternalWindow) 的透明层，覆盖在桌面图标之上 */
    private Pane windowLayer;


    // =================================================================================
    // 3. 初始化逻辑 (Initialize)
    // =================================================================================

    /**
     * JavaFX 控制器入口点
     * 执行顺序：获取内核 -> 绑定数据 -> 初始化子系统 -> 启动定时任务
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. 获取后端业务逻辑核心
        kernel = Kernel.getInstance();

        // 2. 配置表格列与 JavaFX Property 的绑定关系
        initBindings();

        // 3. 初始化图表组件 (CPU/内存曲线)
        initializePerformanceChart();

        // 4. 配置资源管理器的选择模式 (多选)
        fileSystemTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 5. 【关键】初始化窗口层，解决窗口遮挡和点击穿透问题
        initWindowLayer();

        // 6. 注册内核监听器：当文件系统后端发生改变时，通知前端刷新
        kernel.getFileSystemManager().addListener(() -> {
            Platform.runLater(this::updateFileSystemView);
        });

        // 7. 解决 JavaFX 布局坑：面板初始化时宽度可能为0，监听宽度变化以便首次绘制
        if (memoryVisualizationPane != null) {
            memoryVisualizationPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0) {
                    updateMemoryVisualization();
                }
            });
        }

        // 8. 布局自适应调整
        desktopArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // 9. 初始化桌面环境组件
        initDesktop();   // 创建桌面图标
        initStartMenu(); // 创建开始菜单
        startClock();    // 启动右上角时钟

        // 10. 执行第一次全量视图刷新
        updateAllViews();
        updateFileSystemView();

        // 11. 绑定文件系统的交互事件 (双击、右键菜单)
        setupFileSystemEvents();

        // 12. 初始化按钮状态 (未启动系统前，大部分按钮禁用)
        updateControlButtonsState(false);

        // 13. 注册全局快捷键 (Ctrl+F 搜索)
        rootStackPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                onSearchFileClick();
                e.consume(); // 消耗事件，防止进一步传播
            }
        });

        // 14. 【核心循环】启动 UI 定时刷新任务 (500ms 间隔)
        // 注意：数据读取在后台线程，UI 更新必须包裹在 Platform.runLater 中
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();      // 刷新进程列表、CPU 状态
            updateMemoryView();       // 刷新内存条带、碎片率
            updateDeviceView();       // 刷新设备状态、等待队列
            updateOperationLogView(); // 刷新日志输出
            updatePerformanceChart(); // 刷新折线图数据点
            updatePerformanceMetrics(); // 刷新顶部状态栏百分比
        }), 0, 500, TimeUnit.MILLISECONDS);
    }



    /**
     * 初始化浮动窗口层
     * 原理：在 StackPane 最上层覆盖一个透明 Pane，所有 InternalWindow 添加到这个层。
     */
    private void initWindowLayer() {
        this.windowLayer = new Pane();
        this.windowLayer.setPickOnBounds(false); // 关键：允许鼠标点击空白处穿透到下层的桌面图标

        // 绑定大小，跟随根容器，确保覆盖全屏
        this.windowLayer.prefWidthProperty().bind(rootStackPane.widthProperty());
        this.windowLayer.prefHeightProperty().bind(rootStackPane.heightProperty());

        // 设置裁剪，防止窗口拖出边界
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(this.windowLayer.widthProperty());
        clip.heightProperty().bind(this.windowLayer.heightProperty());
        this.windowLayer.setClip(clip);

        // 【关键】直接添加到 rootStackPane 的最上层
        rootStackPane.getChildren().add(this.windowLayer);
    }



    /**
     * 程序退出清理
     */
    public void shutdown() {
        if (!uiExec.isShutdown()) {
            uiExec.shutdownNow();
        }
    }

    // =================================================================================
    // 4. 辅助查找方法
    // =================================================================================

    /** 递归查找第一个可执行文件 (.e) */
    private TreeItem<String> findFirstExecutable(TreeItem<String> node) {
        if (node.getValue() != null && node.getValue().endsWith(".e")) {
            return node;
        }
        for (TreeItem<String> child : node.getChildren()) {
            TreeItem<String> found = findFirstExecutable(child);
            if (found != null) return found;
        }
        return null;
    }

    /** 展开树路径，确保目标可见 */
    private void expandPath(TreeItem<String> item) {
        TreeItem<String> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    // =================================================================================
    // 5. 桌面环境逻辑 (Desktop & Icons)
    // =================================================================================

    /** 初始化桌面图标 */
    private void initDesktop() {
        addDesktopIcon("进程管理", "process.png", processViewRoot, 800, 600);
        addDesktopIcon("内存管理", "memory.png", memoryViewRoot, 700, 500);
        addDesktopIcon("资源管理器", "computer.png", fileSystemViewRoot, 800, 600);
        addDesktopIcon("设备管理", "device.png", deviceViewRoot, 600, 400);
        addDesktopIcon("性能监视器", "monitor.png", performanceViewRoot, 800, 500);
        addDesktopIcon("终端", "terminal.png", null, 600, 400);
    }

    /** 启动右上角时钟 */
    private void startClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        uiExec.scheduleAtFixedRate(() -> {
            String timeText = sdf.format(new Date());
            Platform.runLater(() -> {
                if (systemClockLabel != null) systemClockLabel.setText(timeText);
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    @FXML protected void onDesktopClick() { }



    /**
     * 创建单个桌面图标并添加到桌面区域
     * 核心功能：
     * 1. 构建图标UI结构（图标图片+名称标签）
     * 2. 加载指定图标图片，加载失败时显示emoji回退图标
     * 3. 为图标绑定双击打开窗口/终端的事件
     * 4. 为图标绑定右键菜单（支持“打开”操作）
     * @param contentNode 点击图标后打开的窗口内的内容节点（如进程管理面板）
     */
    private void addDesktopIcon(String name, String iconFileName, Node contentNode, double winWidth, double winHeight) {
        // ========================== 步骤1：创建图标容器（VBox） ==========================
        // VBox：垂直布局容器，用于将图标图片和名称标签上下排列
        VBox iconBox = new VBox(5);
        iconBox.setAlignment(Pos.TOP_CENTER);
        iconBox.getStyleClass().add("desktop-icon");

        // 声明图标图形节点（可能是ImageView图片，也可能是回退的Label文字）
        Node graphicNode;
        try {
            // ========================== 步骤2：加载指定图标图片 ==========================
            String iconPath = "/org/example/scau_os_simulation/icons/" + iconFileName;
            URL resource = getClass().getResource(iconPath);

            // 检查资源是否存在（避免图片文件缺失导致空指针）
            if (resource != null) {
                // 使用try-with-resources语法：自动关闭输入流，避免资源泄漏
                try (InputStream is = resource.openStream()) {
                    // 创建Image对象：从输入流加载图片
                    Image image = new Image(is);
                    // 创建ImageView：用于显示图片的JavaFX节点
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(48);
                    imageView.setFitHeight(48);
                    imageView.setPreserveRatio(true);
                    // 开启图片平滑渲染：让图片显示更清晰，避免锯齿
                    imageView.setSmooth(true);
                    imageView.getStyleClass().add("icon-image-view");
                    graphicNode = imageView;
                }
            } else {
                // 资源不存在时抛出异常，进入catch块使用回退图标
                throw new RuntimeException("Icon not found: " + iconPath);
            }
        } catch (Exception e) {
            // ========================== 步骤3：图标加载失败时的回退方案 ==========================
            // 创建Label作为回退节点：显示emoji字符替代图片
            Label fallbackLabel = new Label();
            // 添加CSS样式类，自定义回退文字的样式（如字体大小、颜色）
            fallbackLabel.getStyleClass().add("icon-label-fallback");

            // 根据图标名称匹配对应的emoji，保证不同功能的图标有辨识度
            switch (name) {
                case "进程管理" -> fallbackLabel.setText("⚙️"); // 齿轮：代表进程/设置
                case "内存管理" -> fallbackLabel.setText("🧠"); // 大脑：代表内存/存储
                case "资源管理器" -> fallbackLabel.setText("📁"); // 文件夹：代表文件/资源
                case "设备管理" -> fallbackLabel.setText("🖨️"); // 打印机：代表硬件设备
                case "性能监视器" -> fallbackLabel.setText("📊"); // 图表：代表性能/数据
                case "终端" -> fallbackLabel.setText("💻"); // 电脑：代表终端/命令行
                default -> fallbackLabel.setText("📄"); // 文档：默认回退图标
            }
            // 将回退Label赋值给图形节点
            graphicNode = fallbackLabel;
        }

        // ========================== 步骤4：创建图标名称标签 ==========================
        // 创建Label显示图标名称（如“进程管理”）
        Label nameLbl = new Label(name);
        // 添加CSS样式类，自定义名称文字的样式（如字体、颜色、大小）
        nameLbl.getStyleClass().add("icon-label");
        // 将图形节点（图片/回退文字）和名称标签添加到图标容器
        iconBox.getChildren().addAll(graphicNode, nameLbl);

        // ========================== 步骤5：绑定双击事件 ==========================
        // 为图标容器绑定鼠标点击事件
        iconBox.setOnMouseClicked(e -> {
            // 判断：双击 + 鼠标左键（排除右键/中键双击、单击）
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                // 终端图标特殊处理：调用终端打开方法
                if ("终端".equals(name)) {
                    onOpenTerminalClick();
                } else {
                    // 其他图标：调用通用窗口打开方法，传入窗口名称、内容、尺寸
                    openWindow(name, contentNode, winWidth, winHeight);
                }
            }
        });

        // ========================== 步骤6：绑定右键菜单 ==========================
        // 创建右键菜单对象
        ContextMenu menu = new ContextMenu();
        // 创建“打开”菜单项
        MenuItem openItem = new MenuItem("打开");
        // 为“打开”菜单项绑定点击事件（逻辑与双击一致）
        openItem.setOnAction(ev -> {
            if ("终端".equals(name)) {
                onOpenTerminalClick();
            } else {
                openWindow(name, contentNode, winWidth, winHeight);
            }
        });
        // 将“打开”菜单项添加到右键菜单
        menu.getItems().add(openItem);

        // 为图标容器绑定右键菜单触发事件：鼠标右键点击时显示菜单
        // ev.getScreenX()/ev.getScreenY()：菜单显示在鼠标右键点击的位置，符合用户习惯
        iconBox.setOnContextMenuRequested(ev -> menu.show(iconBox, ev.getScreenX(), ev.getScreenY()));

        // ========================== 步骤7：将图标添加到桌面区域 ==========================
        // desktopArea：桌面布局容器（如AnchorPane/VBox），所有图标最终显示在该容器中
        desktopArea.getChildren().add(iconBox);
    }




    // =================================================================================
    // 6. 内存可视化逻辑 (Memory Visualization)
    // =================================================================================

    /**
     * 【终极修复版】绘制内存条带与刻度尺
     * 包括：彩色内存块、自适应宽度、智能刻度尺
     */
    private void updateMemoryVisualization() {
        if (memoryVisualizationPane == null || memoryVisualizationPane.getWidth() <= 0) {
            return;
        }

        // 清空画布
        memoryVisualizationPane.getChildren().clear();
        if (memoryRulerPane != null) memoryRulerPane.getChildren().clear();

        double paneWidth = memoryVisualizationPane.getWidth();
        double paneHeight = memoryVisualizationPane.getPrefHeight();

        int totalMemorySize = kernel.getMemoryManager().getMemory().getSize();
        var allocatedBlocks = kernel.getMemoryManager().getAllocatedBlocks();

        // --- A. 绘制内存块 ---
        for (MemoryBlock block : allocatedBlocks) {
            double x = ((double) block.getStartAddress() / totalMemorySize) * paneWidth;
            double w = ((double) block.getSize() / totalMemorySize) * paneWidth;

            // 视觉修正：防止块太小看不见
            if (w < 0.5) w = 0.5;

            Rectangle rect = new Rectangle(x, 0, w, paneHeight);

            // 颜色：系统红，用户蓝
            int pid = findProcessIdForMemoryBlock(block);
            if (pid == -1) rect.setFill(Color.web("#e74c3c"));
            else rect.setFill(Color.web("#3498db"));

            // 描边
            if (paneWidth < 600) rect.setStrokeWidth(0);
            else {
                rect.setStroke(Color.WHITE);
                rect.setStrokeWidth(0.5);
            }

            // Tooltip
            String info = String.format("PID: %s\n地址: %d\n大小: %d KB",
                    (pid == -1 ? "System" : pid), block.getStartAddress(), block.getSize());
            Tooltip.install(rect, new Tooltip(info));

            memoryVisualizationPane.getChildren().add(rect);
        }

        // --- B. 绘制刻度尺 (自适应算法) ---
        if (memoryRulerPane != null) {
            double minPixelsPerLabel = 60.0;
            int maxLabels = (int) (paneWidth / minPixelsPerLabel);
            if (maxLabels < 1) maxLabels = 1;

            int rawStep = totalMemorySize / maxLabels;
            int step = 64;
            while (step < rawStep) step += 64; // 64KB 对齐

            for (int addr = 0; addr <= totalMemorySize; addr += step) {
                double x = ((double) addr / totalMemorySize) * paneWidth;

                javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, 5);
                line.setStroke(Color.GRAY);

                javafx.scene.text.Text text = new javafx.scene.text.Text(String.valueOf(addr));
                text.setStyle("-fx-font-size: 10px; -fx-fill: #666;");

                double textWidthEstimate = text.getText().length() * 6.0;
                double textX = x - (textWidthEstimate / 2);

                // 边界修正
                if (textX < 0) textX = 0;
                if (textX + textWidthEstimate > paneWidth) textX = paneWidth - textWidthEstimate;

                text.setX(textX);
                text.setY(15);

                memoryRulerPane.getChildren().addAll(line, text);
            }
        }
    }

    // =================================================================================
    // 7. 窗口管理核心逻辑 (Window Manager)
    // =================================================================================

    /**
     * 打开或激活一个窗口
     */
    private void openWindow(String title, Node content, double w, double h) {
        if (content == null) return;

        // 强制确保内容可见
        content.setVisible(true);
        content.setManaged(true);

        // 如果已存在，则置顶
        if (openWindows.containsKey(content)) {
            InternalWindow existing = openWindows.get(content);
            existing.toFront();
            if (!windowLayer.getChildren().contains(existing)) {
                windowLayer.getChildren().add(existing);
                addTaskBarItem(existing);
            }
            existing.setVisible(true);
            return;
        }

        // 创建新窗口
        InternalWindow window = new InternalWindow(title, content, w, h);
        double offset = openWindows.size() * 30;
        window.setLayoutX(100 + offset);
        window.setLayoutY(50 + offset);

        openWindows.put(content, window);
        windowLayer.getChildren().add(window);
        addTaskBarItem(window);
    }

    /** 添加任务栏按钮 */
    private void addTaskBarItem(InternalWindow window) {
        Button taskBtn = new Button(window.title);
        taskBtn.getStyleClass().add("task-app-btn");
        taskBtn.setOnAction(e -> {
            if (!window.isVisible()) {
                window.setVisible(true);
                window.toFront();
            } else {
                window.toFront();
            }
        });
        window.onClosed = () -> taskBarApps.getChildren().remove(taskBtn);
        taskBarApps.getChildren().add(taskBtn);
    }


























    /**
     * 内部窗口类 (InternalWindow) - 模拟操作系统窗口系统的核心组件
     * * 核心职责：
     * 1. 提供标准的窗口外观（标题栏、控制按钮、内容区域）。
     * 2. 实现窗口管理器功能（拖拽移动、边缘缩放、最大化/还原、关闭）。
     * 3. 解决 JavaFX 中嵌套窗口的层级和事件冲突问题。
     */
    class InternalWindow extends VBox {
        // ========================= 1. 窗口状态变量 =========================
        // 用于计算拖拽时的位移差值
        private double xOffset = 0, yOffset = 0;
        // 记录操作开始时的窗口状态（用于计算缩放增量）
        private double initX, initY, initW, initH;

        // 状态标记位
        private boolean isDraggingWindow = false; // 是否正在拖动窗口位置
        private boolean isMaximized = false;      // 是否处于最大化状态

        // 记录最大化前的窗口位置/大小，用于点击"还原"按钮时恢复
        private double restoreX, restoreY, restoreW, restoreH;

        // ========================= 2. 常量定义 =========================
        private static final double RESIZE_MARGIN = 10.0; // 鼠标距离边缘多少像素内触发"缩放模式"
        private static final double MIN_WIDTH = 200;      // 窗口最小宽度限制
        private static final double MIN_HEIGHT = 150;     // 窗口最小高度限制

        // ========================= 3. UI 组件 =========================
        String title;
        Runnable onClosed; // 窗口关闭时的回调函数（用于从任务栏移除按钮）
        private final Button maxBtn; // 最大化按钮引用（因为图标会变，所以需要持有引用）

        // 缩放模式枚举：记录鼠标当前位于窗口的哪个边缘/角落
        private ResizeMode currentResizeMode = ResizeMode.NONE;
        private enum ResizeMode { NONE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

        /**
         * 构造函数：初始化窗口外观和事件
         */
        public InternalWindow(String title, Node content, double w, double h) {
            this.setManaged(false); // 关键：取消布局托管，允许手动设置 X/Y 坐标
            this.title = title;
            this.resize(w, h);
            this.setPrefSize(w, h);
            this.getStyleClass().add("window-frame"); // 应用 CSS 样式

            // ---------------------------------------------------------
            // A. 构建标题栏 (TitleBar)
            // ---------------------------------------------------------
            HBox titleBar = new HBox();
            titleBar.getStyleClass().add("window-title-bar");
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setMinHeight(32);
            titleBar.setPrefHeight(32);

            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("window-title");

            // 占位符：将标题挤到左边，按钮挤到右边
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 按钮样式定义 (内联样式用于微调)
            String baseStyle = "-fx-font-weight: bold; -fx-background-color: transparent;";
            String minBtnStyle = baseStyle + "-fx-font-size: 10px; -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;";
            String maxCloseStyle = baseStyle + "-fx-font-size: 12px; -fx-alignment: center; -fx-padding: 0;";
            String hoverBg = "-fx-background-color: #e0e0e0;";

            // [最小化按钮] - 逻辑上只是隐藏窗口
            Button minBtn = new Button("—");
            minBtn.getStyleClass().add("window-close-btn");
            minBtn.setStyle(minBtnStyle);
            minBtn.setPrefSize(30, 20);
            minBtn.setOnAction(e -> this.setVisible(false));
            minBtn.setOnMouseEntered(e -> minBtn.setStyle(minBtnStyle + hoverBg));
            minBtn.setOnMouseExited(e -> minBtn.setStyle(minBtnStyle));

            // [最大化按钮]
            maxBtn = new Button("□");
            maxBtn.getStyleClass().add("window-close-btn");
            maxBtn.setStyle(maxCloseStyle);
            maxBtn.setPrefSize(30, 20);
            maxBtn.setOnAction(e -> toggleMaximize());
            maxBtn.setOnMouseEntered(e -> maxBtn.setStyle(maxCloseStyle + hoverBg));
            maxBtn.setOnMouseExited(e -> maxBtn.setStyle(maxCloseStyle));

            // [关闭按钮]
            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("window-close-btn");
            closeBtn.setStyle(maxCloseStyle);
            closeBtn.setPrefSize(30, 20);
            closeBtn.setOnAction(e -> close()); // 调用清理逻辑

            // 关闭按钮的红色悬停效果
            String closeHover = "-fx-background-color: #e81123; -fx-text-fill: white;";
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(maxCloseStyle + closeHover));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle(maxCloseStyle + "-fx-text-fill: black;"));

            titleBar.getChildren().addAll(titleLbl, spacer, minBtn, maxBtn, closeBtn);

            // ---------------------------------------------------------
            // B. 构建内容容器
            // ---------------------------------------------------------
            VBox contentContainer = new VBox(content);
            contentContainer.setPadding(new Insets(5));
            VBox.setVgrow(contentContainer, Priority.ALWAYS); // 内容区自动撑满剩余空间

            // 强制内容组件填满窗口
            if (content instanceof Region r) r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (content instanceof Control c) c.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(content, Priority.ALWAYS);

            this.getChildren().addAll(titleBar, contentContainer);

            // ---------------------------------------------------------
            // C. 绑定交互事件 (核心)
            // ---------------------------------------------------------
            setupWindowEvents();

            // 性能优化：启用缓存以提高拖动时的渲染帧率
            this.setCache(true);
            this.setCacheHint(CacheHint.SPEED);

            // 确保样式应用后再计算布局
            Platform.runLater(() -> {
                this.requestLayout();
                this.applyCss();
            });
        }

        /**
         * 【核心逻辑】配置窗口的所有鼠标交互事件
         * 这是一个状态机，根据鼠标位置和按下状态在"普通"、"拖拽"、"缩放"模式间切换。
         */
        private void setupWindowEvents() {
            // 1. [鼠标移动]：检测边缘，改变光标形状 (提示用户可以缩放)
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                if (isMaximized) { this.setCursor(Cursor.DEFAULT); return; } // 最大化时不改变光标

                // 计算当前鼠标处于哪个边缘区域
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                // 根据区域设置光标 (例如：右边缘显示 H_RESIZE 箭头)
                setCursorBasedOnMode(mode);
            });

            // 2. [鼠标移出]：还原默认光标
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                // 如果没按住鼠标(非拖动中)，则还原光标
                if (!e.isPrimaryButtonDown()) this.setCursor(Cursor.DEFAULT);
            });

            // 3. [鼠标按下]：决定操作模式 (拖动 vs 缩放 vs 点击)
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                this.toFront(); // 点击窗口即置顶

                // 判定点击区域
                boolean isHeader = e.getY() < 32; // 是否点击了标题栏
                boolean isButtonArea = e.getX() > (this.getWidth() - 90) && isHeader; // 是否点击了按钮区

                // [双击标题栏] -> 触发最大化/还原
                if (isHeader && !isButtonArea && e.getClickCount() == 2) {
                    toggleMaximize();
                    e.consume();
                    return;
                }

                if (isMaximized) return; // 最大化状态下禁止拖动和缩放

                // [边缘检测] -> 进入缩放模式
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                if (mode != ResizeMode.NONE) {
                    currentResizeMode = mode;
                    // 记录初始快照，用于 delta 计算
                    initX = this.getLayoutX(); initY = this.getLayoutY();
                    initW = this.getWidth(); initH = this.getHeight();
                    xOffset = e.getSceneX(); yOffset = e.getSceneY();
                    e.consume();
                    return;
                }

                // [标题栏检测] -> 进入拖动模式
                if (isHeader && !isButtonArea) {
                    currentResizeMode = ResizeMode.NONE;
                    isDraggingWindow = true;
                    // 记录初始位置
                    initX = this.getLayoutX(); initY = this.getLayoutY();
                    xOffset = e.getSceneX(); yOffset = e.getSceneY();
                    e.consume();
                }
            });

            // 4. [鼠标拖拽]：执行实际的位置/大小变更
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                if (isMaximized) return;

                // 场景 A: 调整大小
                if (currentResizeMode != ResizeMode.NONE) {
                    handleResize(e);
                    e.consume();
                }
                // 场景 B: 移动窗口
                else if (isDraggingWindow) {
                    // 新位置 = 初始位置 + (当前鼠标绝对坐标 - 按下时鼠标绝对坐标)
                    this.setLayoutX(initX + (e.getSceneX() - xOffset));
                    this.setLayoutY(initY + (e.getSceneY() - yOffset));
                    e.consume();
                }
            });

            // 5. [鼠标释放]：重置状态
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                isDraggingWindow = false;
                currentResizeMode = ResizeMode.NONE;
            });
        }

        /** 切换窗口最大化/还原状态 */
        private void toggleMaximize() {
            if (getParent() == null) return;
            Region parent = (Region) getParent(); // 获取父容器(WindowLayer)

            if (isMaximized) {
                // [还原]：恢复到之前记录的坐标和尺寸
                this.setLayoutX(restoreX); this.setLayoutY(restoreY);
                this.setPrefSize(restoreW, restoreH); this.resize(restoreW, restoreH);
                maxBtn.setText("□");
                isMaximized = false;
                this.setStyle("-fx-background-radius: 5; -fx-border-radius: 5;"); // 恢复圆角
            } else {
                // [最大化]：记录当前状态，然后填满父容器
                restoreX = this.getLayoutX(); restoreY = this.getLayoutY();
                restoreW = this.getWidth(); restoreH = this.getHeight();
                this.setLayoutX(0); this.setLayoutY(0);
                this.setPrefSize(parent.getWidth(), parent.getHeight());
                this.resize(parent.getWidth(), parent.getHeight());
                maxBtn.setText("❐");
                isMaximized = true;
                this.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;"); // 最大化去除圆角
            }
            this.requestLayout();
        }

        /** 处理窗口缩放的具体数学计算 */
        private void handleResize(javafx.scene.input.MouseEvent e) {
            double deltaX = e.getSceneX() - xOffset;
            double deltaY = e.getSceneY() - yOffset;
            double newX = initX, newY = initY, newW = initW, newH = initH;

            // 根据拉伸方向调整宽/高/XY坐标
            if (isLeft(currentResizeMode)) { newW = initW - deltaX; newX = initX + deltaX; }
            else if (isRight(currentResizeMode)) { newW = initW + deltaX; }

            if (isTop(currentResizeMode)) { newH = initH - deltaY; newY = initY + deltaY; }
            else if (isBottom(currentResizeMode)) { newH = initH + deltaY; }

            // 限制最小尺寸，防止窗口反向
            if (newW < MIN_WIDTH) { newW = MIN_WIDTH; if (isLeft(currentResizeMode)) newX = initX + (initW - MIN_WIDTH); }
            if (newH < MIN_HEIGHT) { newH = MIN_HEIGHT; if (isTop(currentResizeMode)) newY = initY + (initH - MIN_HEIGHT); }

            // 应用新尺寸
            this.resize(newW, newH); this.setPrefSize(newW, newH);
            this.setLayoutX(newX); this.setLayoutY(newY); this.layout();
        }

        // --- 辅助方法：判定鼠标位置 ---
        private ResizeMode getResizeMode(double x, double y) {
            boolean left = x < RESIZE_MARGIN; boolean right = x > this.getWidth() - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN; boolean bottom = y > this.getHeight() - RESIZE_MARGIN;
            if (left && top) return ResizeMode.TOP_LEFT; if (right && top) return ResizeMode.TOP_RIGHT;
            if (left && bottom) return ResizeMode.BOTTOM_LEFT; if (right && bottom) return ResizeMode.BOTTOM_RIGHT;
            if (top) return ResizeMode.TOP; if (bottom) return ResizeMode.BOTTOM;
            if (left) return ResizeMode.LEFT; if (right) return ResizeMode.RIGHT;
            return ResizeMode.NONE;
        }

        // --- 辅助方法：方向判断简写 ---
        private boolean isLeft(ResizeMode m) { return m == ResizeMode.LEFT || m == ResizeMode.TOP_LEFT || m == ResizeMode.BOTTOM_LEFT; }
        private boolean isRight(ResizeMode m) { return m == ResizeMode.RIGHT || m == ResizeMode.TOP_RIGHT || m == ResizeMode.BOTTOM_RIGHT; }
        private boolean isTop(ResizeMode m) { return m == ResizeMode.TOP || m == ResizeMode.TOP_LEFT || m == ResizeMode.TOP_RIGHT; }
        private boolean isBottom(ResizeMode m) { return m == ResizeMode.BOTTOM || m == ResizeMode.BOTTOM_LEFT || m == ResizeMode.BOTTOM_RIGHT; }

        /** 根据模式设置光标样式 */
        private void setCursorBasedOnMode(ResizeMode mode) {
            switch (mode) {
                case TOP, BOTTOM -> this.setCursor(Cursor.V_RESIZE);
                case LEFT, RIGHT -> this.setCursor(Cursor.H_RESIZE);
                case TOP_LEFT, BOTTOM_RIGHT -> this.setCursor(Cursor.NW_RESIZE);
                case TOP_RIGHT, BOTTOM_LEFT -> this.setCursor(Cursor.NE_RESIZE);
                default -> this.setCursor(Cursor.DEFAULT);
            }
        }

        /** 窗口关闭逻辑 */
        public void close() {
            this.setVisible(false);
            if (onClosed != null) onClosed.run(); // 通知 TaskBar 移除图标
            if (getParent() instanceof Pane p) p.getChildren().remove(this); // 从界面移除节点
            openWindows.values().remove(this); // 从控制器缓存移除
        }
    }

    // =================================================================================
    // 8. 全局 UI 初始化方法 (开始菜单、关于窗口等)
    // =================================================================================

    /**
     * 初始化模拟操作系统的“开始”菜单（基于JavaFX ContextMenu实现）
     * 核心功能：
     * 1. 构建开始菜单的UI结构（菜单项+分隔线），添加自定义样式模拟系统开始菜单外观
     * 2. 为每个菜单项绑定功能事件（帮助、打开终端、关闭系统）
     * 3. 实现“开始”按钮的点击逻辑：切换菜单的显示/隐藏状态
     */
    private void initStartMenu() {
        // ========================== 步骤1：创建开始菜单核心容器 ==========================
        // ContextMenu：JavaFX的上下文菜单组件，此处用作“开始”下拉菜单（替代原生菜单栏）
        ContextMenu startMenu = new ContextMenu();
        // 添加CSS样式类，用于自定义开始菜单的外观（如背景色、字体、边距，模拟Windows开始菜单风格）
        startMenu.getStyleClass().add("start-menu");

        // ========================== 步骤2：创建菜单项（带emoji提升辨识度） ==========================
        // 帮助菜单项：emoji❓+文字，用户易识别功能
        MenuItem itemHelp = new MenuItem("❓  关于 / 帮助");
        // 终端菜单项：emoji💻+文字，对应打开系统终端功能
        MenuItem itemTerminal = new MenuItem("💻  终端");
        // 菜单分隔线：用于区分普通功能和系统级功能（帮助/终端 vs 关闭系统），提升菜单可读性
        SeparatorMenuItem separator = new SeparatorMenuItem();
        // 关闭系统菜单项：emoji🔴+文字，醒目提示是高危操作
        MenuItem itemShutdown = new MenuItem("🔴  关闭系统");

        // ========================== 步骤3：为菜单项绑定功能事件 ==========================
        // 帮助项：点击后显示“关于/帮助”窗口（展示系统版本、使用说明等）
        itemHelp.setOnAction(e -> showAboutWindow());
        // 终端项：点击后调用终端打开方法（与桌面终端图标功能一致）
        itemTerminal.setOnAction(e -> onOpenTerminalClick());
        // 关闭系统项：点击后执行系统优雅退出逻辑（避免线程/资源泄漏）
        itemShutdown.setOnAction(e -> {
            // 步骤1：关闭自定义线程池（如调度器、设备管理器的线程），释放资源
            shutdown();
            // 步骤2：停止内核调度器（避免调度线程持续运行，导致程序无法退出）
            if (kernel != null && kernel.getScheduler() != null) {
                kernel.getScheduler().stop();
            }
            // 步骤3：退出JavaFX应用线程（释放UI资源）
            Platform.exit();
            // 步骤4：退出JVM进程（彻底终止程序，0表示正常退出）
            System.exit(0);
        });

        // ========================== 步骤4：将菜单项按顺序添加到开始菜单 ==========================
        // 顺序：帮助 → 终端 → 分隔线 → 关闭系统，符合用户操作习惯（常用功能在前，高危功能在后）
        startMenu.getItems().addAll(itemHelp, itemTerminal, separator, itemShutdown);

        // ========================== 步骤5：绑定“开始”按钮的点击事件（显示/隐藏菜单） ==========================
        // startMenuBtn：界面上的“开始”按钮（如左下角的Windows风格按钮）
        startMenuBtn.setOnAction(e -> {
            // 切换菜单状态：若菜单已显示则隐藏，未显示则显示
            if (startMenu.isShowing()) {
                startMenu.hide(); // 隐藏菜单
            } else {
                // 显示菜单：指定菜单的显示位置（相对于startMenuBtn）
                // 参数说明：
                // - startMenuBtn：菜单锚定的控件（开始按钮）
                // - Side.TOP：菜单显示在按钮的上方（符合Windows开始菜单显示位置）
                // - 0, 0：菜单相对于按钮的偏移量（无偏移，对齐显示）
                startMenu.show(startMenuBtn, Side.TOP, 0, 0);
            }
        });
    }





    /** 显示"关于"窗口 */
    private void showAboutWindow() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        Label title = new Label("SCAU OS Simulation");
        title.getStyleClass().add("about-title");
        Label version = new Label("版本: v1.0.0 Alpha");
        version.getStyleClass().add("about-version");
        Label desc = new Label("这是一个基于 JavaFX 开发的操作系统模拟器。\n包含进程管理、内存分配、文件系统及设备管理等演示。");
        desc.setWrapText(true);
        desc.setMaxWidth(350);
        desc.setAlignment(Pos.CENTER);
        Label author = new Label("© 2024 SCAU OS Team");
        author.getStyleClass().add("about-author");

        Button closeBtn = new Button("确定");
        content.getChildren().addAll(title, version, desc, new Separator(), author, closeBtn);

        // 创建并显示窗口
        InternalWindow aboutWin = new InternalWindow("关于系统", content, 450, 400);
        // 居中显示算法
        double x = (desktopArea.getWidth() - 450) / 2;
        double y = (desktopArea.getHeight() - 400) / 2;
        aboutWin.setLayoutX(x > 0 ? x : 100);
        aboutWin.setLayoutY(y > 0 ? y : 100);

        closeBtn.setOnAction(e -> aboutWin.close());

        windowLayer.getChildren().add(aboutWin);
        aboutWin.toFront();
        addTaskBarItem(aboutWin);
    }

    /** 绑定文件系统的交互事件 (右键菜单、快捷键、双击) */
    private void setupFileSystemEvents() {
        // [鼠标点击]
        fileSystemTreeView.setOnMouseClicked(event -> {
            // 安全检查：系统必须启动后才能操作文件
            if (Kernel.getInstance().getScheduler() == null || !Kernel.getInstance().getScheduler().isRunning()) {
                if (event.getClickCount() == 2) showWarning("系统未启动", "请先点击 [▶ 启动系统] 按钮。");
                return;
            }
            // 双击打开文件
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) openSelectedFile();
        });

        // [右键菜单]
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("编辑 / 查看");
        MenuItem deleteItem = new MenuItem("删除");
        editItem.setOnAction(e -> openSelectedFile());
        deleteItem.setOnAction(e -> onDeleteClick());
        contextMenu.getItems().addAll(editItem, deleteItem);
        fileSystemTreeView.setContextMenu(contextMenu);

        // 菜单显示前检查系统状态，未启动则禁用菜单项
        contextMenu.setOnShowing(e -> {
            boolean isRunning = Kernel.getInstance().getScheduler() != null && Kernel.getInstance().getScheduler().isRunning();
            for (MenuItem item : contextMenu.getItems()) item.setDisable(!isRunning);
        });

        // [键盘快捷键]
        fileSystemTreeView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                onDeleteClick();
                event.consume();
                return;
            }
            if (event.isShortcutDown()) {
                switch (event.getCode()) {
                    case C -> { handleCopyShortcut(); event.consume(); } // Ctrl+C
                    case V -> { handlePasteShortcut(); event.consume(); } // Ctrl+V
                }
            }
        });
    }

    // =================================================================================
    // 9. 剪贴板逻辑 (Clipboard Operations)
    // =================================================================================

    /** 复制选中文件逻辑 */
    private void handleCopyShortcut() {
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) return;

        clipboardFiles.clear();
        StringBuilder names = new StringBuilder();

        // 遍历选中项，从内核获取实际对象并存入剪贴板
        for (TreeItem<String> item : selectedItems) {
            String path = buildPathFromTree(item);
            Object obj = kernel.getFileSystemManager().getObjectByPath(path);
            if (obj != null) {
                clipboardFiles.add(obj);
                names.append(item.getValue()).append(" ");
            }
        }
        if (!clipboardFiles.isEmpty()) {
            showInfo("复制成功", "已复制 " + clipboardFiles.size() + " 个项目:\n" + names);
        }
    }

    /** 粘贴文件逻辑 */
    private void handlePasteShortcut() {
        if (clipboardFiles.isEmpty()) {
            showWarning("剪贴板为空", "请先复制文件或目录。");
            return;
        }

        // 确定粘贴的目标目录
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String targetPath;
        if (selectedItem == null) {
            targetPath = "/"; // 默认粘贴到根目录
        } else {
            String path = buildPathFromTree(selectedItem);
            Object targetNode = kernel.getFileSystemManager().getObjectByPath(path);
            if (targetNode instanceof Directory) {
                targetPath = path; // 如果选中的是目录，粘贴到该目录内
            } else {
                // 如果选中的是文件，粘贴到该文件的父目录
                if (path.contains("/")) targetPath = path.substring(0, path.lastIndexOf('/'));
                else targetPath = "/";
                if (targetPath.isEmpty()) targetPath = "/";
            }
        }

        // 执行粘贴操作
        int successCount = 0;
        for (Object source : clipboardFiles) {
            try {
                kernel.getFileSystemManager().paste(source, targetPath); // 调用内核 API
                successCount++;
            } catch (Exception e) {
                String name = (source instanceof Directory d) ? d.getName() : ((File)source).getName();
                showError("粘贴失败", "无法粘贴 '" + name + "': " + e.getMessage());
            }
        }

        // 刷新视图并展开
        if (successCount > 0) {
            updateFileSystemView();
            expandTreePath(targetPath);
            showInfo("粘贴成功", "已成功粘贴 " + successCount + " 个项目到 " + targetPath);
        }
    }

    /** 辅助方法：展开指定路径的树节点 */
    private void expandTreePath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return;
        TreeItem<String> current = fileSystemTreeView.getRoot();
        if (current == null) return;
        current.setExpanded(true);
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            boolean found = false;
            for (TreeItem<String> child : current.getChildren()) {
                if (child.getValue().equals(part)) {
                    current = child;
                    current.setExpanded(true);
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }
    }

    /** 更新控制按钮状态 (根据系统是否运行) */
    private void updateControlButtonsState(boolean isRunning) {
        boolean disable = !isRunning;
        if (createProcessBtn != null) createProcessBtn.setDisable(disable);
        if (terminateProcessBtn != null) terminateProcessBtn.setDisable(disable);
        if (createFileBtn != null) createFileBtn.setDisable(disable);
        if (createDirectoryBtn != null) createDirectoryBtn.setDisable(disable);
        if (deleteFileBtn != null) deleteFileBtn.setDisable(disable);
        if (copyFileBtn != null) copyFileBtn.setDisable(disable);
        if (pasteFileBtn != null) pasteFileBtn.setDisable(disable);
        if (searchFileBtn != null) searchFileBtn.setDisable(disable);
    }

    // =================================================================================
    // 10. 交互事件处理 (Actions)
    // =================================================================================

    /** 点击"启动系统"按钮 */
    @FXML protected void onStartSystemClick() {
        Kernel.getInstance().start(); // 启动内核线程
        startSystemBtn.setDisable(true);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(false);
        updateControlButtonsState(true); // 激活所有功能按钮
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }

    /**
     * 【核心功能】创建进程按钮处理
     * 特性：
     * 1. 弹出自定义对话框，包含表单。
     * 2. 左侧显示文件树，用于选择可执行文件。
     * 3. 选中文件时，自动填充路径，并根据文件名智能推断进程名。
     * 4. 自动定位并选中系统中的第一个 .e 文件 (提升演示体验)。
     */
    @FXML protected void onCreateProcessClick() {
        // 1. 准备表单控件
        TextField processNameField = new TextField("新进程");
        processNameField.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Integer> priorityBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1); // 默认优先级
        TextField execPathField = new TextField();
        execPathField.setMaxWidth(Double.MAX_VALUE);

        // 2. 构建文件选择树 (仅用于此对话框)
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(150);
        fileTreeView.setMaxWidth(Double.MAX_VALUE);
        fileTreeView.setMaxHeight(Double.MAX_VALUE);

        // 3. 监听选择事件：实现智能填充
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.getValue().endsWith(".e")) {
                execPathField.setText(buildPathFromTree(newVal)); // 自动填路径

                // 智能解析文件名 (例如从 p1_A_CPU.e 推断出 "计算型_A")
                String fileName = newVal.getValue();
                String suggestedName = fileName.replace(".e", "");
                if (fileName.contains("_CPU")) {
                    if (fileName.contains("_A_")) suggestedName = "计算型_A";
                    else if (fileName.contains("_B_")) suggestedName = "计算型_B";
                    else if (fileName.contains("_C_")) suggestedName = "计算型_C";
                } else if (fileName.contains("_IO")) {
                    if (fileName.contains("_A_")) suggestedName = "阻塞型_A";
                    else if (fileName.contains("_B_")) suggestedName = "阻塞型_B";
                    else if (fileName.contains("_C_")) suggestedName = "阻塞型_C";
                }
                processNameField.setText(suggestedName);
            }
        });

        // 4. UX 优化：自动选中第一个可执行文件
        TreeItem<String> firstExec = findFirstExecutable(rootItem);
        if (firstExec != null) {
            expandPath(firstExec);
            fileTreeView.getSelectionModel().select(firstExec);
            // 滚动到选中项
            Platform.runLater(() -> {
                int row = fileTreeView.getRow(firstExec);
                if (row >= 0) fileTreeView.scrollTo(row);
            });
        }

        // 5. 布局构建 (GridPane)
        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        grid.add(new Label("进程名称:"), 0, 0); grid.add(processNameField, 1, 0);
        grid.add(new Label("优先级:"), 0, 1); grid.add(priorityBox, 1, 1);
        grid.add(new Label("文件路径:"), 0, 2); grid.add(execPathField, 1, 2);
        Label selectLabel = new Label("选择文件:"); selectLabel.setAlignment(Pos.TOP_LEFT);
        grid.add(selectLabel, 0, 3); grid.add(fileTreeView, 1, 3);

        ColumnConstraints col1 = new ColumnConstraints();
        ColumnConstraints col2 = new ColumnConstraints(); col2.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col1, col2);
        GridPane.setHgrow(fileTreeView, Priority.ALWAYS); GridPane.setVgrow(fileTreeView, Priority.ALWAYS);
        GridPane.setHgrow(processNameField, Priority.ALWAYS); GridPane.setHgrow(execPathField, Priority.ALWAYS);

        HBox btnBox = new HBox(10); btnBox.setAlignment(Pos.CENTER_RIGHT); btnBox.setPadding(new Insets(0, 20, 10, 20));
        Button okBtn = new Button("创建"); Button cancelBtn = new Button("取消");
        btnBox.getChildren().addAll(okBtn, cancelBtn);

        VBox root = new VBox(grid, btnBox); VBox.setVgrow(grid, Priority.ALWAYS);

        // 创建并显示对话框窗口
        InternalWindow win = new InternalWindow("创建新进程", root, 500, 400);
        double x = (desktopArea.getWidth() - 500) / 2; double y = (desktopArea.getHeight() - 400) / 2;
        win.setLayoutX(x > 0 ? x : 100); win.setLayoutY(y > 0 ? y : 100);

        cancelBtn.setOnAction(e -> win.close());

        // 6. 确定按钮逻辑
        okBtn.setOnAction(e -> {
            String name = processNameField.getText().trim(); if (name.isEmpty()) name = "新进程";
            String path = execPathField.getText().trim(); int priority = priorityBox.getValue();

            // 加载可执行文件
            org.example.scau_os_simulation.process.Executable exec = kernel.getFileSystemManager().loadExecutable(path);
            if (exec != null) {
                // 调用内核创建进程 (使用全限定名避免 Process 类冲突)
                org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess(name, priority);
                if (p != null) {
                    p.setExecutable(exec); // 绑定指令
                    updateProcessView();   // 刷新界面
                    showInfo("成功", "进程已创建");
                    win.close();
                } else showError("失败", "无法创建进程 (可能内存不足)");
            } else showError("文件错误", "无法加载可执行文件");
        });

        windowLayer.getChildren().add(win);
        win.toFront();
    }


























// =================================================================================
    // 11. 进程控制逻辑 (Process Control Actions)
    // =================================================================================

    /**
     * [终止进程] 按钮点击事件
     * 逻辑：
     * 1. 获取当前表格中选中的 PCB。
     * 2. 安全检查：禁止终止 PID=-1 的系统闲逛进程。
     * 3. 弹出确认框，用户确认后调用内核终止进程。
     * 4. 刷新进程视图和内存视图（释放内存后视图需更新）。
     */
    @FXML protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();

        if (selected != null) {
            // 安全保护：防止用户误杀系统核心进程
            if (selected.getPid() == -1) {
                showError("非法操作", "无法结束系统闲逛进程 (IDLE)。");
                return;
            }

            String msg = "确定要强制结束进程 [" + selected.getName() + "] (PID=" + selected.getPid() + ") 吗？\n此操作不可撤销。";

            // 使用自定义内部确认弹窗
            showInternalConfirm("确认终止进程", msg, () -> {
                // 回调：调用内核执行终止
                kernel.getProcessManager().terminateProcess(selected.getPid());
                // 刷新 UI
                updateProcessView();
                updateMemoryView();
            });
        } else {
            showWarning("未选择进程", "请先选择要终止的进程。");
        }

        // 无论是否成功，刷新内存可视化条带（可能选中状态改变）
        updateMemoryVisualization();
    }

    // =================================================================================
    // 12. 文件系统操作逻辑 (File System Actions)
    // =================================================================================

    /**
     * [创建文件] 按钮点击事件
     * 逻辑：
     * 1. 确定父目录：如果选中了文件，则取其父目录；如果选中目录，则取该目录。
     * 2. 弹出输入框询问文件名。
     * 3. 调用内核创建文件，并刷新文件树。
     */
    @FXML protected void onCreateFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/"; // 默认根目录

        // 智能路径判断
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            // 检查当前路径对应的对象
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            // 如果选中的是文件，则在其父目录下创建
            if (node != null) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }

        final String finalPath = path; // Lambda 表达式需要 final 变量

        // 弹出输入框
        showInternalInput("创建文件", "在路径 '" + finalPath + "' 下创建新文件:", "new.txt", (name) -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    // 调用内核 API
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    // 刷新视图并提示
                    updateFileSystemView();
                    showInfo("文件创建成功", "文件 '" + name + "' 创建成功。");
                } catch (Exception e) {
                    showError("文件创建失败", e.getMessage());
                }
            }
        });
    }

    /**
     * [停止系统] 按钮点击事件
     * 作用：暂停 CPU 调度，冻结系统状态。
     */
    @FXML protected void onStopSystemClick() {
        if (Kernel.getInstance().getScheduler() != null) Kernel.getInstance().getScheduler().stop();

        // 切换按钮可用状态
        startSystemBtn.setDisable(false);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(true);
        updateControlButtonsState(false); // 禁用大部分功能按钮

        showInfo("系统已暂停", "CPU 调度已停止。");
    }

    /**
     * [创建目录] 按钮点击事件
     * 逻辑与创建文件类似，但调用的是 createDirectory。
     */
    @FXML protected void onCreateDirectoryClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            // 如果选中的是文件，回退到父目录
            if (kernel.getFileSystemManager().getFileByPath(path) != null) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }
        final String finalPath = path;

        showInternalInput("创建目录", "在路径 '" + finalPath + "' 下创建新目录:", "NewFolder", (name) -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createDirectory(finalPath, name);
                    updateFileSystemView();
                    showInfo("目录创建成功", "目录 '" + name + "' 创建成功。");
                } catch (Exception e) {
                    showError("目录创建失败", e.getMessage());
                }
            }
        });
    }

    /**
     * [删除] 按钮点击事件
     * 支持批量删除，支持删除非空目录（内核层处理递归）。
     */
    @FXML protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;

        // 获取多选列表
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            showWarning("未选择", "请先选择要删除的文件或目录。");
            return;
        }

        List<String> pathsToDelete = new ArrayList<>();
        boolean containsRoot = false;

        // 构建路径列表
        for (TreeItem<String> item : selectedItems) {
            if (item == null || item.getParent() == null) { containsRoot = true; continue; } // 根目录保护
            pathsToDelete.add(buildPathFromTree(item));
        }

        if (pathsToDelete.isEmpty()) {
            if (containsRoot) showError("无法删除", "根目录不可删除。");
            return;
        }

        // 构建提示信息
        String msg = pathsToDelete.size() == 1 ?
                "您确定要删除 '" + pathsToDelete.get(0) + "' 吗？" :
                "您确定要删除选中的 " + pathsToDelete.size() + " 个项目吗？";

        showInternalConfirm("确认删除", msg, () -> {
            int successCount = 0;
            for (String path : pathsToDelete) {
                try {
                    if (kernel.getFileSystemManager().deletePath(path)) successCount++;
                } catch (Exception e) { }
            }

            if (successCount > 0) {
                updateFileSystemView(); // 刷新树
                showInfo("删除成功", "已成功删除 " + successCount + " 个项目。");
            } else {
                showError("删除失败", "未能删除选中目标。");
            }
        });
    }

    // =================================================================================
    // 13. 工具栏其他功能 (Toolbar Utilities)
    // =================================================================================

    /** [内存整理] 按钮：触发紧凑算法 */
    @FXML protected void onDefragmentClick() {
        kernel.getMemoryManager().defragment();
        updateMemoryView();
        showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    // 剪贴板快捷键的 FXML 绑定
    @FXML protected void onCopyFileClick() { handleCopyShortcut(); }
    @FXML protected void onPasteFileClick() { handlePasteShortcut(); }

    /**
     * [文件搜索] 按钮点击事件
     * 特性：实时前缀匹配搜索 + 自动定位树节点
     */
    @FXML protected void onSearchFileClick() {
        // 构建搜索窗口界面
        VBox root = new VBox(10); root.setPadding(new Insets(15));
        Label headerLbl = new Label("输入文件名 (支持前缀匹配，不区分大小写):");

        // 使用 ComboBox 显示搜索建议
        ComboBox<String> searchBox = new ComboBox<>();
        searchBox.setEditable(true); // 允许输入
        searchBox.setPromptText("例如: new...");
        searchBox.setMaxWidth(Double.MAX_VALUE);

        HBox btnBox = new HBox(10); btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button goBtn = new Button("定位");
        Button closeBtn = new Button("关闭");
        btnBox.getChildren().addAll(goBtn, closeBtn);
        root.getChildren().addAll(headerLbl, searchBox, btnBox);

        InternalWindow win = new InternalWindow("智能搜索", root, 350, 180);
        // 窗口居中逻辑
        win.setLayoutX(desktopArea.getWidth() / 2 - 175);
        win.setLayoutY(desktopArea.getHeight() / 2 - 90);

        // [核心] 监听输入变化，实时从文件系统搜索匹配项
        searchBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) { searchBox.hide(); return; }
            if (newVal.equals(searchBox.getSelectionModel().getSelectedItem())) return; // 避免选中时触发

            Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
            List<String> matches = new ArrayList<>();
            // 调用目录递归搜索
            rootDir.searchByPrefix(newVal.trim(), matches, "");

            // 在 UI 线程更新下拉列表
            Platform.runLater(() -> {
                if (!searchBox.getItems().equals(matches)) {
                    searchBox.getItems().setAll(matches);
                    if (!matches.isEmpty()) {
                        if (!searchBox.isShowing()) searchBox.show();
                    } else searchBox.hide();
                }
            });
        });

        // 定位逻辑：根据路径在左侧树中选中对应节点
        Runnable doLocate = () -> {
            String path = searchBox.getEditor().getText();
            if (path != null && !path.trim().isEmpty()) {
                Object target = kernel.getFileSystemManager().getObjectByPath(path);
                if (target != null) selectFileInTree(target);
                else showWarning("未找到", "路径无效或文件不存在。");
            }
        };

        goBtn.setOnAction(e -> doLocate.run());
        searchBox.setOnAction(e -> doLocate.run()); // 回车触发
        closeBtn.setOnAction(e -> win.close());

        windowLayer.getChildren().add(win);
        win.toFront();
        Platform.runLater(searchBox::requestFocus); // 自动聚焦输入框
    }

    // =================================================================================
    // 14. 文件编辑器逻辑 (Simple Text Editor)
    // =================================================================================

    /** 创建编辑器内容节点 (TextArea + Save Button) */
    private VBox createEditorNode(File file) {
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);

        // 读取文件内容 (byte[] -> String)
        if (file.getContent() != null) {
            String content = new String(file.getContent(), 0, file.getActualLength(), java.nio.charset.StandardCharsets.UTF_8);
            textArea.setText(content);
        }

        // 保存逻辑
        Runnable doSave = () -> {
            try {
                // String -> byte[]
                byte[] data = textArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                file.setContent(data);
                showInfo("保存成功", "文件 '" + file.getName() + "' 已保存。");
            } catch (Exception ex) { showError("保存失败", ex.getMessage()); }
        };

        Button saveBtn = new Button("保存");
        saveBtn.setOnAction(e -> doSave.run());

        // Ctrl+S 快捷键
        textArea.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) { doSave.run(); event.consume(); }
        });

        ToolBar toolBar = new ToolBar(saveBtn);
        VBox editorRoot = new VBox(toolBar, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return editorRoot;
    }



    /**
     * 打开文件系统树中当前选中的文件（核心功能：创建文件编辑窗口并展示）
     * 执行流程：
     * 1. 获取树控件中选中的文件/文件夹节点
     * 2. 从选中节点拼接文件完整路径
     * 3. 通过路径从文件系统管理器获取文件对象
     * 4. 构建文件编辑UI节点，创建级联偏移的编辑窗口
     * 5. 将窗口添加到界面图层，更新任务栏和已打开窗口集合
     */
    private void openSelectedFile() {
        // ========================== 步骤1：获取树控件中选中的节点 ==========================
        // fileSystemTreeView：文件系统树状视图（展示目录/文件层级结构）
        // getSelectionModel()：获取树控件的选中模型（管理用户选中的节点）
        // getSelectedItem()：获取当前选中的树节点（可能是文件夹或文件）
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        // 判空处理：用户未选中任何节点时，直接返回，避免空指针异常
        if (selectedItem == null) return;

        // ========================== 步骤2：从选中节点拼接文件完整路径 ==========================
        // buildPathFromTree()：自定义方法，遍历树节点的父节点，拼接出文件的完整路径（如/root/doc.txt）
        String path = buildPathFromTree(selectedItem);
        // 通过文件系统管理器根据路径获取文件对象：内核级的文件查询，保证文件对象的准确性
        File file = kernel.getFileSystemManager().getFileByPath(path);

        // ========================== 步骤3：文件存在时创建编辑窗口 ==========================
        if (file != null) {
            // 创建文件编辑UI节点：createEditorNode()返回包含文本编辑器的VBox（如TextArea+保存按钮）
            VBox editorRoot = createEditorNode(file);

            // 步骤3.1：创建内部窗口（模拟操作系统的应用窗口）
            // InternalWindow：自定义窗口组件，参数说明：
            // - 窗口标题："编辑: " + 文件名（用户清晰知道编辑的是哪个文件）
            // - 窗口内容：editorRoot（文件编辑UI）
            // - 窗口宽度：500像素，高度：400像素（适配文本编辑的常规尺寸）
            InternalWindow editorWin = new InternalWindow("编辑: " + file.getName(), editorRoot, 500, 400);

            // 步骤3.2：设置窗口级联偏移（避免新窗口完全重叠）
            // openWindows：已打开窗口的集合（key=窗口内容根节点，value=窗口对象）
            // 偏移量计算：每打开一个新窗口，X/Y坐标各偏移30像素，形成级联效果（符合Windows多窗口打开的交互习惯）
            double offset = openWindows.size() * 30;
            editorWin.setLayoutX(100 + offset); // 窗口X坐标：基础值100 + 偏移量
            editorWin.setLayoutY(50 + offset);  // 窗口Y坐标：基础值50 + 偏移量

            // 步骤3.3：将窗口添加到界面的窗口图层（windowLayer是承载所有内部窗口的容器）
            windowLayer.getChildren().add(editorWin);
            // 将新窗口置顶显示（避免被其他窗口遮挡，用户能立即看到编辑界面）
            editorWin.toFront();
            // 为新窗口添加任务栏项（模拟Windows任务栏，方便用户切换窗口）
            addTaskBarItem(editorWin);
            // 将新窗口存入已打开窗口集合：用于后续管理（如关闭、切换、计算偏移）
            openWindows.put(editorRoot, editorWin);
        }
    }



    // =================================================================================
    // 15. 视图更新总控 (UI Refresh)
    // =================================================================================

    /** 刷新所有子系统视图 */
    private void updateAllViews() {
        updateProcessView();
        updateMemoryView();
        updateDeviceView();
        updateFileSystemView();
        updateOperationLogView();
        updatePerformanceMetrics();
    }

    /** * 刷新进程视图
     * 逻辑：
     * 1. 遍历进程列表，计算每个进程的剩余时间。
     * 2. 更新 TableView。
     * 3. 更新就绪/阻塞队列 ListView。
     * 4. 更新 CPU 状态面板 (IR, AX, PID)。
     */
    private void updateProcessView() {
        // 更新计算字段
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            int remaining = p.getRemainingTime();
            p.getPcb().setTotalRemainingTime(remaining);
        }

        // 刷新表格数据
        processTableView.getItems().setAll(kernel.getProcessManager().getProcesses().stream().map(org.example.scau_os_simulation.process.Process::getPcb).toList());

        // 刷新队列列表 (简略信息)
        readyQueueListView.getItems().setAll(kernel.getProcessManager().getReadyQueue().stream().map(p -> "PID: " + p.getPcb().getPid() + " (优先级: " + p.getPcb().getPriority() + ")").toList());
        blockedQueueListView.getItems().setAll(kernel.getProcessManager().getBlockedQueue().stream().map(p -> "PID: " + p.getPcb().getPid()).toList());

        // 刷新 CPU 状态栏
        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        if (running != null) {
            PCB pcb = running.getPcb();
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr());
            axLabel.setText("AX: " + pcb.getAx());
            tsLabel.setText("时间片: " + kernel.getTimeSlice());
        } else {
            runningPidLabel.setText("运行中PID: 无");
        }
    }

    /** * 刷新内存视图
     * 逻辑：
     * 1. 更新进度条。
     * 2. 更新内存块表格。
     * 3. 计算并显示碎片率。
     * 4. 重绘内存可视化条带。
     */
    private void updateMemoryView() {
        MemoryManager memoryManager = kernel.getMemoryManager();
        int totalMemory = memoryManager.getMemory().getSize();
        int usedMemory = memoryManager.getTotalUsedMemory();
        double usage = (double) usedMemory / totalMemory;

        memoryUsageBar.setProgress(usage);
        memoryInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", usedMemory, totalMemory));

        memoryBlockTableView.getItems().setAll(memoryManager.getAllocatedBlocks());

        double fragmentationRate = memoryManager.getFragmentationRate();
        fragmentationLabel.setText(String.format("碎片率: %.2f%%", fragmentationRate * 100));

        updateMemoryVisualization(); // 关键：更新彩色条带
    }

    /** 刷新设备视图 */
    private void updateDeviceView() {
        deviceTableView.getItems().setAll(kernel.getDeviceManager().getAllDevices());

        // 转换等待队列数据为 DTO
        List<WaitRow> waitRows = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            for (DeviceRequest request : kernel.getDeviceManager().getWaitingQueue(t)) {
                waitRows.add(new WaitRow(t.toString(), request.getPid(), request.getExecutionTime()));
            }
        }
        waitQueueTableView.getItems().setAll(waitRows);
    }


    /** * 刷新文件系统视图 (树形图)
     * 难点：刷新树会导致所有节点折叠，这里实现了"状态保存与恢复"逻辑。
     */
    private void updateFileSystemView() {
        // 1. 保存当前展开的路径集合
        Set<String> expandedPaths = new HashSet<>();
        if (fileSystemTreeView.getRoot() != null) saveExpansionState(fileSystemTreeView.getRoot(), expandedPaths);

        // 2. 保存当前选中的路径
        String selectedPath = null;
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedPath = buildPathFromTree(selectedItem);
            expandedPaths.add(selectedPath);
        }

        // 3. 重新构建树
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder"));
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        // 4. 恢复展开状态
        restoreExpansionState(rootItem, expandedPaths);
        rootItem.setExpanded(true); // 根目录始终展开

        // 5. 恢复选中状态
        if (selectedPath != null) {
            TreeItem<String> targetItem = findItemByPath(rootItem, selectedPath);
            if (targetItem != null) {
                fileSystemTreeView.getSelectionModel().select(targetItem);
                int row = fileSystemTreeView.getRow(targetItem);
                if (row >= 0) fileSystemTreeView.scrollTo(row);
            }
        }

        // 6. 更新磁盘容量条
        if (kernel.getFileSystemManager().getFileSystem() != null) {
            int total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
            int used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
            double usage = total > 0 ? (double) used / total : 0;
            if (diskUsageBar != null) diskUsageBar.setProgress(usage);
            if (diskInfoLabel != null) diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", used, total));
        }
    }


    /** 辅助：递归保存展开状态 */
    private void saveExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        if (item.isExpanded()) expandedPaths.add(buildPathFromTree(item));
        for (TreeItem<String> child : item.getChildren()) saveExpansionState(child, expandedPaths);
    }

    /** 辅助：递归恢复展开状态 */
    private void restoreExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        String currentPath = buildPathFromTree(item);
        if (expandedPaths.contains(currentPath)) item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) restoreExpansionState(child, expandedPaths);
    }

    /** * 递归填充文件树
     * 根据对象类型 (Directory/File) 设置不同的图标和节点
     */
    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem) {
        for (Object child : parent.getChildren()) {
            if (child instanceof Directory dir) {
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                dirItem.setGraphic(createIcon("folder"));
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem); // 递归
            } else if (child instanceof File f) {
                TreeItem<String> fileItem = new TreeItem<>(f.getName());
                // 根据后缀判断图标
                if (f.getName().endsWith(".e")) fileItem.setGraphic(createIcon("exec"));
                else if (f.getName().endsWith(".txt")) fileItem.setGraphic(createIcon("text"));
                else fileItem.setGraphic(createIcon("file"));
                parentItem.getChildren().add(fileItem);
            }
        }
    }

    /** 工厂方法：创建特定样式的 Label 作为图标 */
    private javafx.scene.control.Label createIcon(String type) {
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label();
        // 使用 Emoji 字体作为轻量级图标
        iconLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Segoe UI Symbol';");
        switch (type) {
            case "folder" -> { iconLabel.setText("📁"); iconLabel.getStyleClass().add("folder-icon"); }
            case "exec" -> { iconLabel.setText("🚀"); iconLabel.getStyleClass().add("exec-icon"); }
            case "text" -> { iconLabel.setText("📝"); iconLabel.getStyleClass().add("file-icon"); }
            default -> { iconLabel.setText("📄"); iconLabel.getStyleClass().add("file-icon"); }
        }
        return iconLabel;
    }

    /** 刷新日志视图 */
    private void updateOperationLogView() {
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        outputListView.getItems().setAll(kernel.getOutputLogs());
    }

    // =================================================================================
    // 16. 性能监控逻辑 (Performance Monitor)
    // =================================================================================

    /** 初始化图表 */
    private void initializePerformanceChart() {
        try {
            performanceChart = new PerformanceChartFX();
            javafx.scene.chart.LineChart<Number, Number> chart = performanceChart.getChart();
            if (performanceChartContainer != null) {
                performanceChartContainer.getChildren().clear();
                performanceChartContainer.getChildren().add(chart);
                // 绑定尺寸
                chart.prefWidthProperty().bind(performanceChartContainer.widthProperty());
                chart.prefHeightProperty().bind(performanceChartContainer.heightProperty());
                chart.setStyle("-fx-background-color: transparent;");
            }
        } catch (Exception e) {
            System.err.println("性能图表初始化失败: " + e.getMessage());
        }
    }


    /** 定时更新图表数据点 */
    private void updatePerformanceChart() {
        if (performanceChart != null && kernel != null) {
            performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
        }
    }


    /** 更新文字性能指标 (平均值、峰值) */
    private void updatePerformanceMetrics() {
        PerformanceMonitor pm = kernel.getPerformanceMonitor();
        double avgCpu = pm.getAverageCpuUtilization();
        double avgMem = pm.getAverageMemoryUtilization();
        double peakCpu = pm.getPeakCpuUtilization();
        double peakMem = pm.getPeakMemoryUtilization();

        avgCpuLabel.setText(String.format("平均CPU: %.2f%%", avgCpu * 100));
        avgMemoryLabel.setText(String.format("平均内存: %.2f%%", avgMem * 100));
        peakCpuLabel.setText(String.format("峰值CPU: %.2f%%", peakCpu * 100));
        peakMemoryLabel.setText(String.format("峰值内存: %.2f%%", peakMem * 100));

        double currentCpu = kernel.getCpuUtilization();
        double currentLoad = kernel.getSystemLoad();
        cpuUtilizationBar.setProgress(currentCpu);
        systemLoadBar.setProgress(currentLoad);
        cpuUtilizationLabel.setText(String.format("CPU: %.2f%%", currentCpu * 100));
        systemLoadLabel.setText(String.format("负载: %.2f", currentLoad));
    }



    // =================================================================================
    // 17. 数据绑定配置 (Data Binding)
    // =================================================================================

    /** 配置表格列与模型属性的映射 */
    private void initBindings() {
        // 进程表绑定
        pidColumn.setCellValueFactory(cellData -> cellData.getValue().pidProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        stateColumn.setCellValueFactory(cellData -> cellData.getValue().stateProperty());
        priorityColumn.setCellValueFactory(cellData -> cellData.getValue().priorityProperty());
        memoryAddressColumn.setCellValueFactory(cellData -> cellData.getValue().memoryAddressProperty());
        memorySizeColumn.setCellValueFactory(cellData -> cellData.getValue().memorySizeProperty());

        // 内存块表绑定
        startAddressColumn.setCellValueFactory(cellData -> cellData.getValue().startAddressProperty());
        blockSizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        processColumn.setCellValueFactory(cellData -> {
            int pid = findProcessIdForMemoryBlock(cellData.getValue());
            return new javafx.beans.property.SimpleStringProperty(pid >= 0 ? String.valueOf(pid) : "N/A");
        });

        // 设备表绑定
        deviceTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType().toString()));
        deviceInUseColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isBusy() ? "是" : "否"));
        devicePidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCurrentUserPid()));
        deviceRemainColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getRemainingTime()));

        // 等待队列表绑定
        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device()));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid()));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time()));

        // 时间统计绑定
        timeSliceColumn.setCellValueFactory(cellData -> cellData.getValue().timeSliceProperty());
        if (totalRemainingTimeColumn != null) {
            totalRemainingTimeColumn.setCellValueFactory(cellData -> cellData.getValue().totalRemainingTimeProperty());
        }
    }

    /** 辅助：反向查找占用某内存块的 PID */
    private int findProcessIdForMemoryBlock(MemoryBlock block) {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize()) {
                return p.getPcb().getPid();
            }
        }
        return -1;
    }



    // =================================================================================
    // 18. 路径与树转换工具 (Path Utilities)
    // =================================================================================

    /** TreeItem -> String 绝对路径 */
    private String buildPathFromTree(TreeItem<String> item) {
        StringBuilder path = new StringBuilder();
        while (item != null && item.getParent() != null) {
            path.insert(0, "/" + item.getValue());
            item = item.getParent();
        }
        return !path.isEmpty() ? path.toString() : "/";
    }

    /** String 绝对路径 -> TreeItem */
    private TreeItem<String> findItemByPath(TreeItem<String> root, String path) {
        if (path.equals("/")) return root;
        String[] parts = path.split("/");
        TreeItem<String> current = root;
        for (int i = 1; i < parts.length; i++) {
            boolean found = false;
            for (TreeItem<String> child : current.getChildren()) {
                if (child.getValue().equals(parts[i])) {
                    current = child; found = true; break;
                }
            }
            if (!found) return null;
        }
        return current;
    }

    /** FileSystem 对象 -> String 绝对路径 */
    private String buildFullPath(Object fileObj) {
        return findObjectPath(fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
    }

    /** 递归查找对象路径 */
    private String findObjectPath(Object target, Directory current, String currentPath) {
        if (current == target) return currentPath.isEmpty() ? "/" : currentPath;
        for (Object child : current.getChildren()) {
            String childName = (child instanceof Directory d) ? d.getName() : ((File)child).getName();
            String childPath = currentPath + (currentPath.equals("/") ? "" : "/") + childName;
            if (child == target) return childPath;
            if (child instanceof Directory subDir) {
                String result = findObjectPath(target, subDir, childPath);
                if (result != null) return result;
            }
        }
        return null;
    }

    /** 在 UI 树中选中指定文件对象 */
    private void selectFileInTree(Object fileObj) {
        String path = buildFullPath(fileObj);
        if (path != null && !path.isEmpty()) {
            TreeItem<String> root = fileSystemTreeView.getRoot();
            TreeItem<String> target = findItemByPath(root, path);
            if (target != null) {
                fileSystemTreeView.getSelectionModel().select(target);
                fileSystemTreeView.scrollTo(fileSystemTreeView.getSelectionModel().getSelectedIndex());
            }
        }
    }

    // 弹窗快捷方法
    private void showInfo(String title, String message) { showInternalAlert("info", title, message); }
    private void showWarning(String title, String message) { showInternalAlert("warning", title, message); }
    private void showError(String title, String message) { showInternalAlert("error", title, message); }

    // 等待队列数据传输对象
    private record WaitRow(String device, int pid, int time) {}

    // =================================================================================
    // 19. 终端与弹窗实现 (Terminal & Dialogs)
    // =================================================================================

    /** 打开终端窗口 */
    @FXML protected void onOpenTerminalClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/scau_os_simulation/terminal_view.fxml"));
            Parent terminalContent = loader.load();
            TerminalController controller = loader.getController();
            InternalWindow termWin = new InternalWindow("终端", terminalContent, 600, 400);

            // 绑定关闭回调
            termWin.onClosed = () -> controller.onClose();
            openWindow("终端", terminalContent, 600, 400);
        } catch (Exception e) {
            System.err.println("无法打开终端");
            showError("错误", "无法打开终端: " + e.getMessage());
        }
    }

    /** * 显示内部消息弹窗
     * 用于替代 JavaFX 原生 Alert，确保弹窗在 WindowLayer 内部显示
     */
    private void showInternalAlert(String type, String title, String content) {
        VBox root = new VBox(10); root.setPadding(new Insets(15)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content); msgLabel.setWrapText(true); msgLabel.setMaxWidth(250);
        Button okBtn = new Button("确定");
        root.getChildren().addAll(msgLabel, okBtn);

        InternalWindow win = new InternalWindow(title, root, 300, 150);
        double x = (desktopArea.getWidth() - 300) / 2;
        double y = (desktopArea.getHeight() - 150) / 2;
        win.setLayoutX(x); win.setLayoutY(y);

        okBtn.setOnAction(e -> win.close());
        windowLayer.getChildren().add(win);
    }

    /** 显示内部确认弹窗 (Yes/No) */
    private void showInternalConfirm(String title, String content, Runnable onConfirm) {
        VBox root = new VBox(20); root.setPadding(new Insets(20)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true); msgLabel.setMaxWidth(300);
        msgLabel.setStyle("-fx-font-size: 14px;");

        HBox btnBox = new HBox(15); btnBox.setAlignment(Pos.CENTER);
        Button yesBtn = new Button("确定"); yesBtn.getStyleClass().add("button");
        yesBtn.setStyle("-fx-background-color: #da1e28; -fx-text-fill: white;"); // 红色警示
        Button noBtn = new Button("取消");

        btnBox.getChildren().addAll(yesBtn, noBtn);
        root.getChildren().addAll(msgLabel, btnBox);

        InternalWindow win = new InternalWindow(title, root, 350, 180);
        win.setLayoutX((desktopArea.getWidth() - 350) / 2);
        win.setLayoutY((desktopArea.getHeight() - 180) / 2);

        yesBtn.setOnAction(e -> { win.close(); if (onConfirm != null) onConfirm.run(); });
        noBtn.setOnAction(e -> win.close());

        windowLayer.getChildren().add(win);
        win.toFront();
    }

    /** 显示内部输入弹窗 */
    private void showInternalInput(String title, String header, String defaultValue, java.util.function.Consumer<String> callback) {
        VBox root = new VBox(10); root.setPadding(new Insets(15));
        Label headerLbl = new Label(header);
        TextField textField = new TextField(defaultValue);

        HBox btnBox = new HBox(10); btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button okBtn = new Button("确定");
        Button cancelBtn = new Button("取消");
        btnBox.getChildren().addAll(okBtn, cancelBtn);

        root.getChildren().addAll(headerLbl, textField, btnBox);

        InternalWindow win = new InternalWindow(title, root, 320, 160);
        win.setLayoutX(200); win.setLayoutY(200);

        cancelBtn.setOnAction(e -> win.close());
        okBtn.setOnAction(e -> {
            String result = textField.getText();
            win.close();
            if (callback != null) callback.accept(result);
        });

        windowLayer.getChildren().add(win);
        win.toFront();
    }
}
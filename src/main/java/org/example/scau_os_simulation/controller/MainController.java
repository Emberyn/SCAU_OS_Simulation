package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.CacheHint; // 【修复 1】导入正确的 CacheHint
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
import org.example.scau_os_simulation.process.Process;
import org.example.scau_os_simulation.process.Executable;

import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.control.Tooltip;

/**
 * 操作系统模拟器主控制器
 * 
 * 该类是整个操作系统模拟器的核心控制器，负责：
 * 1. 管理整个GUI界面的生命周期和状态
 * 2. 协调各个子系统（进程、内存、文件系统、设备、性能监控）的显示和交互
 * 3. 处理桌面环境、窗口管理、系统时钟等基础功能
 * 4. 提供用户操作的入口点（按钮点击、菜单选择等）
 * 
 * 主要设计特点：
 * - 使用JavaFX框架构建GUI界面
 * - 采用MVC架构模式，将业务逻辑委托给Kernel类处理
 * - 通过ScheduledExecutorService定时刷新UI，避免阻塞JavaFX应用线程
 * - 实现了内部窗口系统，模拟真实操作系统的多窗口环境
 * - 使用FXML注解实现视图和控制的分离
 * 
 * 性能优化：
 * - UI更新操作都通过Platform.runLater()在JavaFX线程中执行
 * - 定时任务使用单线程调度器，避免资源竞争
 * - 内存可视化采用增量更新策略
 * 
 * @author 操作系统模拟器开发团队
 * @version 1.0
 * @since 2024
 */
public class MainController implements Initializable {
    // ========================= 桌面环境核心组件 =========================
    // 这些组件构成了操作系统模拟器的主界面框架
    
    /** 根容器，所有界面元素的最顶层容器，用于承载整个桌面环境 */
    @FXML private StackPane rootStackPane;
    
    /** 桌面区域，用于放置桌面图标（如进程管理、内存管理等快捷方式） */
    @FXML private FlowPane desktopArea;
    
    /** 任务栏应用区域，显示当前打开的窗口对应的任务按钮 */
    @FXML private HBox taskBarApps;
    
    /** 系统时钟标签，显示当前系统时间（格式：yyyy-MM-dd HH:mm） */
    @FXML private Label systemClockLabel;
    
    /** 开始菜单按钮，点击可打开系统功能菜单 */
    @FXML private Button startMenuBtn;

    // --- 功能视图容器 ---
    @FXML private VBox processViewRoot;
    @FXML private VBox memoryViewRoot;
    @FXML private VBox fileSystemViewRoot;
    @FXML private AnchorPane deviceViewRoot;
    @FXML private VBox performanceViewRoot;
    @FXML private StackPane performanceChartContainer;

    // --- 功能按钮 ---
    // 【修复 2】移除了未使用的 undoBtn, redoBtn
    @FXML private Button startSystemBtn, stopSystemBtn;
    @FXML private Button createProcessBtn, terminateProcessBtn;
    @FXML private Button defragmentBtn;
    @FXML private Button createFileBtn, createDirectoryBtn;
    @FXML private Button deleteFileBtn, copyFileBtn, pasteFileBtn;
    @FXML private Button searchFileBtn;

    // --- 数据展示组件 ---
    @FXML private TableView<PCB> processTableView;
    @FXML private TableColumn<PCB, Number> pidColumn, priorityColumn, memoryAddressColumn, memorySizeColumn;
    @FXML private TableColumn<PCB, String> nameColumn, stateColumn;
    @FXML private Label runningPidLabel, irLabel, axLabel, tsLabel;
    @FXML private ListView<String> outputListView, readyQueueListView, blockedQueueListView, operationLogListView;
    @FXML private ProgressBar memoryUsageBar, diskUsageBar, cpuUtilizationBar, systemLoadBar;
    @FXML private Label memoryInfoLabel, fragmentationLabel, diskInfoLabel;
    @FXML private Label cpuUtilizationLabel, systemLoadLabel, avgCpuLabel, avgMemoryLabel, peakCpuLabel, peakMemoryLabel;
    @FXML private TableView<MemoryBlock> memoryBlockTableView;
    @FXML private TableColumn<MemoryBlock, Number> startAddressColumn, blockSizeColumn;
    @FXML private TableColumn<MemoryBlock, String> processColumn;
    @FXML private TreeView<String> fileSystemTreeView;
    @FXML private TableView<Device> deviceTableView;
    @FXML private TableColumn<Device, String> deviceTypeColumn, deviceInUseColumn;
    @FXML private TableColumn<Device, Number> devicePidColumn, deviceRemainColumn;
    @FXML private TableView<WaitRow> waitQueueTableView;
    @FXML private TableColumn<WaitRow, String> waitDeviceColumn;
    @FXML private TableColumn<WaitRow, Number> waitPidColumn, waitTimeColumn;
    // 【修改】在最后添加 timeSliceColumn
    @FXML private TableColumn<PCB, Number> timeSliceColumn;
    @FXML private TableColumn<PCB, Number> totalRemainingTimeColumn; // 新增的列
    @FXML private Pane memoryRulerPane; // 新增的刻度尺

    // --- 后端核心对象引用 ---
    private Kernel kernel;
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartFX performanceChart;
    // 【修复 3】移除了未使用的 clipboardFile (单对象)，保留 clipboardFiles (列表)
    private final Map<Node, InternalWindow> openWindows = new HashMap<>();
    private final List<Object> clipboardFiles = new ArrayList<>();
    @FXML
    private Pane memoryVisualizationPane; // 对应 FXML 中新增的 ID

    // 窗口层 (解决闪烁)
    private Pane windowLayer;

    /**
     * 控制器初始化方法，由JavaFX框架在FXML加载完成后自动调用
     * 
     * 该方法负责整个系统界面的初始化工作，包括：
     * 1. 获取内核实例，建立与业务逻辑层的连接
     * 2. 初始化数据绑定和性能图表
     * 3. 设置文件系统树的多选模式
     * 4. 初始化窗口层，为内部窗口系统做准备
     * 5. 注册文件系统监听器，实现文件变化的实时刷新
     * 6. 监听内存视图宽度变化，解决初始显示空白的问题
     * 7. 设置桌面区域自适应大小
     * 8. 初始化桌面图标、开始菜单和系统时钟
     * 9. 执行首次视图更新
     * 10. 设置文件系统事件处理
     * 11. 初始化控制按钮状态
     * 12. 注册全局快捷键（Ctrl+F搜索）
     * 13. 启动定时刷新任务，每500毫秒更新一次UI
     * 
     * 性能考虑：
     * - 所有UI更新操作都通过Platform.runLater()在JavaFX应用线程中执行
     * - 使用单线程调度器避免并发问题
     * - 定时任务间隔设置为500毫秒，平衡实时性和性能
     * 
     * @param location FXML文件的位置（通常不需要手动处理）
     * @param resources 国际化资源包（支持多语言）
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 获取内核单例实例，建立与业务逻辑层的连接
        kernel = Kernel.getInstance();
        
        // 初始化数据绑定关系
        initBindings();
        
        // 初始化性能监控图表组件
        initializePerformanceChart();

        // 设置文件系统树的选择模式为多选，支持同时选择多个文件/目录
        fileSystemTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 初始化窗口层，为内部窗口系统做准备
        initWindowLayer();

        // 注册文件系统监听器，当文件系统发生变化时自动刷新视图
        // 使用Platform.runLater()确保UI更新在JavaFX线程中执行
        kernel.getFileSystemManager().addListener(() -> {
            Platform.runLater(() -> {
                updateFileSystemView();
            });
        });

        // 监听内存视图宽度变化，解决"刚打开是空白"的问题
        // 当内存可视化面板宽度大于0时，立即执行内存可视化更新
        if (memoryVisualizationPane != null) {
            memoryVisualizationPane.widthProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal.doubleValue() > 0) {
                    updateMemoryVisualization();
                }
            });
        }

        // 设置桌面区域自适应大小，根据内容自动调整尺寸
        desktopArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        // 初始化桌面环境，包括桌面图标和交互功能
        initDesktop();
        
        // 初始化开始菜单
        initStartMenu();
        
        // 启动系统时钟，开始更新时间显示
        startClock();

        // 执行首次全面视图更新，确保界面显示最新数据
        updateAllViews();
        
        // 更新文件系统视图
        updateFileSystemView();
        
        // 设置文件系统相关的事件监听器
        setupFileSystemEvents();
        
        // 初始化控制按钮的可用状态
        updateControlButtonsState(false);

        // 注册全局快捷键：Ctrl+F 打开文件搜索功能
        rootStackPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                onSearchFileClick();
                e.consume(); // 消耗事件，防止进一步传播
            }
        });

        // 启动定时刷新任务，每500毫秒更新一次UI视图
        // 使用单线程调度器确保线程安全
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();      // 更新进程视图
            updateMemoryView();       // 更新内存视图
            updateDeviceView();       // 更新设备视图
            updateOperationLogView(); // 更新操作日志视图
            updatePerformanceChart(); // 更新性能图表
            updatePerformanceMetrics(); // 更新性能指标
        }), 0, 500, TimeUnit.MILLISECONDS);
    }

    // 【核心修复】修改初始化逻辑，确保窗口层一定被添加到界面上
    private void initWindowLayer() {
        this.windowLayer = new Pane();
        this.windowLayer.setPickOnBounds(false); // 关键：鼠标点击空白处穿透下去，不阻挡底层图标

        // 绑定大小，跟随根容器，确保覆盖全屏
        this.windowLayer.prefWidthProperty().bind(rootStackPane.widthProperty());
        this.windowLayer.prefHeightProperty().bind(rootStackPane.heightProperty());

        // 设置裁剪，防止窗口拖出边界
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(this.windowLayer.widthProperty());
        clip.heightProperty().bind(this.windowLayer.heightProperty());
        this.windowLayer.setClip(clip);

        // 【修复点】直接添加到 rootStackPane 的最上层
        // 这样无论 desktopArea 被藏在哪里，窗口层永远在最上面
        rootStackPane.getChildren().add(this.windowLayer);
    }

    public void shutdown() {
        if (!uiExec.isShutdown()) {
            uiExec.shutdownNow();
        }
    }

/**
     * 递归查找树形结构中第一个可执行文件（.e后缀）
     * 
     * 该方法用于在文件系统树中自动查找可执行文件，主要用途：
     * 1. 系统启动时自动定位到第一个可执行文件
     * 2. 为用户提供快捷的执行文件入口
     * 3. 演示文件系统的遍历功能
     * 
     * 搜索逻辑：
     * - 深度优先搜索：先检查当前节点，再递归检查子节点
     * - 后缀匹配：查找以".e"结尾的文件名
     * - 返回第一个匹配项：找到后立即返回，不继续搜索
     * 
     * @param node 搜索的起始树节点
     * @return 第一个找到的可执行文件节点，如果未找到返回null
     * 
     * @see TreeItem
     * @see String#endsWith(String)
     */
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

    /**
     * 展开树形节点的父路径（确保选中项可见）
     * 
     * 该方法用于确保指定的树节点在界面中可见，主要应用场景：
     * 1. 自动选择文件后，确保用户能看到选中的文件
     * 2. 搜索功能定位到文件后，展开其父目录
     * 3. 程序启动时展开到默认位置
     * 
     * 实现逻辑：
     * - 自底向上遍历：从目标节点开始，逐级向上查找父节点
     * - 展开所有父节点：将路径上的所有父节点设置为展开状态
     * - 确保可见性：展开后目标节点将在树中可见
     * 
     * 用户体验：
     * - 自动展开避免了用户手动点击展开的操作
     * - 保持展开状态，用户可以看到完整的文件路径
     * - 与选中操作配合使用，提供完整的视觉反馈
     * 
     * @param item 需要展开的目标树节点
     * 
     * @see TreeItem#setExpanded(boolean)
     * @see TreeItem#getParent()
     */
    private void expandPath(TreeItem<String> item) {
        TreeItem<String> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    private void initDesktop() {
        addDesktopIcon("进程管理", "process.png", processViewRoot, 800, 600);
        addDesktopIcon("内存管理", "memory.png", memoryViewRoot, 700, 500);
        addDesktopIcon("资源管理器", "computer.png", fileSystemViewRoot, 800, 600);
        addDesktopIcon("设备管理", "device.png", deviceViewRoot, 600, 400);
        addDesktopIcon("性能监视器", "monitor.png", performanceViewRoot, 800, 500);
        addDesktopIcon("终端", "terminal.png", null, 600, 400);
    }

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

    private void addDesktopIcon(String name, String iconFileName, Node contentNode, double winWidth, double winHeight) {
        VBox iconBox = new VBox(5);
        iconBox.setAlignment(Pos.TOP_CENTER);
        iconBox.getStyleClass().add("desktop-icon");

        Node graphicNode;
        try {
            String iconPath = "/org/example/scau_os_simulation/icons/" + iconFileName;
            URL resource = getClass().getResource(iconPath);
            if (resource != null) {
                try (InputStream is = resource.openStream()) {
                    ImageView imageView = new ImageView(new Image(is));
                    imageView.setFitWidth(48);
                    imageView.setFitHeight(48);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.getStyleClass().add("icon-image-view");
                    graphicNode = imageView;
                }
            } else {
                throw new RuntimeException("Icon not found: " + iconPath);
            }
        } catch (Exception e) {
            Label fallbackLabel = new Label();
            fallbackLabel.getStyleClass().add("icon-label-fallback");
            switch (name) {
                case "进程管理" -> fallbackLabel.setText("⚙️");
                case "内存管理" -> fallbackLabel.setText("🧠");
                case "资源管理器" -> fallbackLabel.setText("📁");
                case "设备管理" -> fallbackLabel.setText("🖨️");
                case "性能监视器" -> fallbackLabel.setText("📊");
                case "终端" -> fallbackLabel.setText("💻");
                default -> fallbackLabel.setText("📄");
            }
            graphicNode = fallbackLabel;
        }

        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("icon-label");
        iconBox.getChildren().addAll(graphicNode, nameLbl);

        iconBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                if ("终端".equals(name)) onOpenTerminalClick();
                else openWindow(name, contentNode, winWidth, winHeight);
            }
        });

        ContextMenu menu = new ContextMenu();
        MenuItem openItem = new MenuItem("打开");
        openItem.setOnAction(ev -> {
            if ("终端".equals(name)) onOpenTerminalClick();
            else openWindow(name, contentNode, winWidth, winHeight);
        });
        menu.getItems().add(openItem);
        iconBox.setOnContextMenuRequested(ev -> menu.show(iconBox, ev.getScreenX(), ev.getScreenY()));

        desktopArea.getChildren().add(iconBox);
    }



/**
     * 【终极修复版】绘制内存条带与刻度尺
     * 
     * 该方法负责在内存可视化面板中绘制内存使用情况的图形表示，包括：
     * 1. 内存块可视化：用彩色矩形表示已分配的内存块
     * 2. 内存刻度尺：在顶部显示内存地址刻度，便于用户了解内存分布
     * 
     * 核心特性：
     * - 自适应布局：根据面板宽度动态调整显示比例
     * - 颜色编码：系统进程使用红色，用户进程使用蓝色
     * - 智能刻度：根据窗口宽度自动计算刻度间隔，防止文字重叠
     * - 工具提示：鼠标悬停显示详细的内存块信息
     * - 边界保护：确保图形元素不会超出面板边界
     * 
     * 性能优化：
     * - 宽度为0时直接返回，避免无效计算
     * - 先清空再重绘，确保显示一致性
     * - 使用估算文字宽度，避免昂贵的文字测量操作
     * 
     * 修复历史：
     * - 修复了宽度为0导致不显示的问题
     * - 修复了刻度尺文字重叠问题
     * - 修复了小窗口下边框过粗的问题
     * 
     * 设计原理：
     * - 采用比例映射：内存地址和大小按比例映射到面板像素
     * - 步长规整化：刻度步长对齐到64KB倍数，显示更规整
     * - 视觉层次：通过颜色、边框、透明度区分不同元素
     */
    private void updateMemoryVisualization() {
        // ========================= 阶段1：安全检查 =========================
        // 确保内存可视化面板存在且有有效宽度，避免后续计算出现除零错误
        if (memoryVisualizationPane == null || memoryVisualizationPane.getWidth() <= 0) {
            return; // 面板未准备好，直接返回
        }

        // ========================= 阶段2：清空画布 =========================
        // 清除之前的绘制内容，确保显示的一致性和正确性
        memoryVisualizationPane.getChildren().clear();
        if (memoryRulerPane != null) memoryRulerPane.getChildren().clear();

        // ========================= 阶段3：获取基础数据 =========================
        // 获取面板的物理尺寸，用于后续的像素映射计算
        double paneWidth = memoryVisualizationPane.getWidth();
        double paneHeight = memoryVisualizationPane.getPrefHeight();
        
        // 获取内存总大小 (KB)，这是所有比例计算的基础
        int totalMemorySize = kernel.getMemoryManager().getMemory().getSize();
        
        // 获取当前已分配的内存块列表，这些是我们要可视化显示的对象
        var allocatedBlocks = kernel.getMemoryManager().getAllocatedBlocks();

        // =========================================================
        // A. 绘制内存块 (彩色方块)
        // =========================================================
        for (MemoryBlock block : allocatedBlocks) {
            // 计算比例位置
            double x = ((double) block.getStartAddress() / totalMemorySize) * paneWidth;
            double w = ((double) block.getSize() / totalMemorySize) * paneWidth;

            // 视觉修正：防止块太小完全消失，但在小窗口下不强制过大导致重叠
            // 使用 0.5px 主要是为了让它在屏幕上至少有一条线
            if (w < 0.5) w = 0.5;

            Rectangle rect = new Rectangle(x, 0, w, paneHeight);

            // 颜色判断：系统进程(PID -1)用红色，普通进程用蓝色
            int pid = findProcessIdForMemoryBlock(block);
            if (pid == -1) {
                rect.setFill(Color.web("#e74c3c")); // 系统红
            } else {
                rect.setFill(Color.web("#3498db")); // 用户蓝
            }

            // 描边设置：在小窗口模式下，去掉描边或设得极细，防止全是边框颜色
            if (paneWidth < 600) {
                rect.setStrokeWidth(0); // 窗口太小时，去掉边框
            } else {
                rect.setStroke(Color.WHITE);
                rect.setStrokeWidth(0.5);
            }

            // Tooltip 详情
            String info = String.format("PID: %s\n地址: %d\n大小: %d KB",
                    (pid == -1 ? "System" : pid),
                    block.getStartAddress(),
                    block.getSize());
            Tooltip.install(rect, new Tooltip(info));

            memoryVisualizationPane.getChildren().add(rect);
        }

        // =========================================================
        // B. 绘制刻度尺 (自适应算法)
        // =========================================================
        if (memoryRulerPane != null) {
            // 【核心修复算法】
            // 1. 设定每个刻度文字至少需要占用 60 像素宽度，才不会重叠
            double minPixelsPerLabel = 60.0;

            // 2. 计算当前宽度最多能容纳多少个标签
            int maxLabels = (int) (paneWidth / minPixelsPerLabel);
            if (maxLabels < 1) maxLabels = 1; // 至少显示结束值

            // 3. 计算理想的内存步长 (KB)
            int rawStep = totalMemorySize / maxLabels;

            // 4. 将步长规整化：向下取整到最近的 64KB 倍数 (因为内存块通常是64的倍数)
            // 这样显示的刻度如 0, 128, 256 会比 0, 143, 286 这种数字好看得多
            int step = 64;
            while (step < rawStep) {
                step += 64; // 步进增加 64，直到满足最小像素间隔
            }

            // 5. 循环绘制
            for (int addr = 0; addr <= totalMemorySize; addr += step) {
                // 计算 X 坐标
                double x = ((double) addr / totalMemorySize) * paneWidth;

                // 画小竖线
                javafx.scene.shape.Line line = new javafx.scene.shape.Line(x, 0, x, 5);
                line.setStroke(Color.GRAY);

                // 画文字
                javafx.scene.text.Text text = new javafx.scene.text.Text(String.valueOf(addr));
                text.setStyle("-fx-font-size: 10px; -fx-fill: #666;");

                // 文字居中修正
                double textWidthEstimate = text.getText().length() * 6.0; // 估算文字宽度
                double textX = x - (textWidthEstimate / 2);

                // 边界修正：最左边不要出界，最右边也不要出界
                if (textX < 0) textX = 0;
                if (textX + textWidthEstimate > paneWidth) textX = paneWidth - textWidthEstimate;

                text.setX(textX);
                text.setY(15); // 距离上方线条的距离

                memoryRulerPane.getChildren().addAll(line, text);
            }
        }
    }



    private void openWindow(String title, Node content, double w, double h) {
        if (content == null) return;

        // 【新增】强制确保内容可见（防止 FXML 中设置了 visible="false"）
        content.setVisible(true);
        content.setManaged(true);

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

        InternalWindow window = new InternalWindow(title, content, w, h);
        double offset = openWindows.size() * 30;
        window.setLayoutX(100 + offset);
        window.setLayoutY(50 + offset);

        openWindows.put(content, window);
        windowLayer.getChildren().add(window);
        addTaskBarItem(window);
    }



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
     * 内部窗口类 - 模拟操作系统窗口系统的核心组件
     * 
     * 该类实现了操作系统模拟器中的窗口管理功能，提供了类似真实操作系统的窗口体验：
     * 1. 窗口拖动：通过标题栏拖动整个窗口
     * 2. 窗口调整：支持八个方向的边框调整
     * 3. 窗口最大化：支持最大化/还原功能
     * 4. 窗口控制：提供最小化、关闭等标准窗口操作
     * 
     * 设计特点：
     * - 继承自VBox，使用JavaFX布局系统
     * - 采用组合模式，包含标题栏和内容区域
     * - 使用枚举定义调整大小的方向
     * - 支持自定义关闭回调函数
     * 
     * 交互体验：
     * - 鼠标悬停时显示相应的调整光标
     * - 拖动过程中实时预览窗口变化
     * - 最大化时记住原始位置和大小
     * - 边界保护，防止窗口过小
     * 
     * 性能考虑：
     * - 使用缓存变量减少重复计算
     * - 事件处理中及时消耗事件，避免冒泡
     * - 使用最小尺寸限制，防止无效状态
     * 
     * @see VBox
     * @see Runnable
     */
    // --- 内部窗口类 ---
    class InternalWindow extends VBox {
        // ========================= 窗口位置状态 =========================
        /** 鼠标按下时相对于窗口的X偏移量，用于拖动计算 */
        private double xOffset = 0, yOffset = 0;
        
        /** 窗口拖动开始时的初始位置和大小，用于恢复和边界计算 */
        private double initX, initY, initW, initH;
        
        /** 标记是否正在拖动窗口，用于区分拖动和其他鼠标操作 */
        private boolean isDraggingWindow = false;
        
        /** 窗口的最大化状态，true表示当前处于最大化状态 */
        private boolean isMaximized = false;
        
        /** 窗口最大化前的位置和大小，用于还原操作 */
        private double restoreX, restoreY, restoreW, restoreH;
        
        /** 调整窗口大时的检测边距，鼠标进入此范围显示调整光标 */
        private static final double RESIZE_MARGIN = 10.0;
        
        /** 窗口最小宽度限制，防止窗口过小影响使用 */
        private static final double MIN_WIDTH = 200;
        
        /** 窗口最小高度限制，确保内容区域有足够空间 */
        private static final double MIN_HEIGHT = 150;

        // ========================= 窗口属性 =========================
        /** 窗口标题，显示在标题栏和任务栏按钮上 */
        String title;
        
        /** 窗口关闭时的回调函数，用于清理资源和更新UI */
        Runnable onClosed;
        
        /** 最大化/还原按钮的引用，用于动态更新按钮图标 */
        private final Button maxBtn;
        
        /** 当前调整大小模式，指示用户正在从哪个方向调整窗口 */
        private ResizeMode currentResizeMode = ResizeMode.NONE;
        
        /** 调整大小方向枚举，定义了八个可能的调整方向 */
        private enum ResizeMode { NONE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

        public InternalWindow(String title, Node content, double w, double h) {
            this.setManaged(false);
            this.title = title;
            this.resize(w, h);
            this.setPrefSize(w, h);
            this.getStyleClass().add("window-frame");

            HBox titleBar = new HBox();
            titleBar.getStyleClass().add("window-title-bar");
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setMinHeight(32);
            titleBar.setPrefHeight(32);

            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("window-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            String baseStyle = "-fx-font-weight: bold; -fx-background-color: transparent;";
            String minBtnStyle = baseStyle + "-fx-font-size: 10px; -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;";
            String maxCloseStyle = baseStyle + "-fx-font-size: 12px; -fx-alignment: center; -fx-padding: 0;";
            String hoverBg = "-fx-background-color: #e0e0e0;";

            Button minBtn = new Button("—");
            minBtn.getStyleClass().add("window-close-btn");
            minBtn.setStyle(minBtnStyle);
            minBtn.setPrefSize(30, 20);
            minBtn.setOnAction(e -> this.setVisible(false));
            minBtn.setOnMouseEntered(e -> minBtn.setStyle(minBtnStyle + hoverBg));
            minBtn.setOnMouseExited(e -> minBtn.setStyle(minBtnStyle));

            maxBtn = new Button("□");
            maxBtn.getStyleClass().add("window-close-btn");
            maxBtn.setStyle(maxCloseStyle);
            maxBtn.setPrefSize(30, 20);
            maxBtn.setOnAction(e -> toggleMaximize());
            maxBtn.setOnMouseEntered(e -> maxBtn.setStyle(maxCloseStyle + hoverBg));
            maxBtn.setOnMouseExited(e -> maxBtn.setStyle(maxCloseStyle));

            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("window-close-btn");
            closeBtn.setStyle(maxCloseStyle);
            closeBtn.setPrefSize(30, 20);
            closeBtn.setOnAction(e -> close());
            String closeHover = "-fx-background-color: #e81123; -fx-text-fill: white;";
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(maxCloseStyle + closeHover));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle(maxCloseStyle + "-fx-text-fill: black;"));

            titleBar.getChildren().addAll(titleLbl, spacer, minBtn, maxBtn, closeBtn);

            VBox contentContainer = new VBox(content);
            contentContainer.setPadding(new Insets(5));
            VBox.setVgrow(contentContainer, Priority.ALWAYS);

            if (content instanceof Region r) r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (content instanceof Control c) c.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(content, Priority.ALWAYS);

            this.getChildren().addAll(titleBar, contentContainer);
            setupWindowEvents();

            // 【修复 1】使用正确的类引用
            this.setCache(true);
            this.setCacheHint(CacheHint.SPEED);

            Platform.runLater(() -> {
                this.requestLayout();
                this.applyCss();
            });
        }

        private void setupWindowEvents() {
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                if (isMaximized) { this.setCursor(Cursor.DEFAULT); return; }
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                setCursorBasedOnMode(mode);
            });
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (!e.isPrimaryButtonDown()) this.setCursor(Cursor.DEFAULT);
            });
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                this.toFront();
                boolean isHeader = e.getY() < 32;
                boolean isButtonArea = e.getX() > (this.getWidth() - 90) && isHeader;
                if (isHeader && !isButtonArea && e.getClickCount() == 2) { toggleMaximize(); e.consume(); return; }
                if (isMaximized) return;
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                if (mode != ResizeMode.NONE) {
                    currentResizeMode = mode; initX = this.getLayoutX(); initY = this.getLayoutY();
                    initW = this.getWidth(); initH = this.getHeight();
                    xOffset = e.getSceneX(); yOffset = e.getSceneY(); e.consume(); return;
                }
                if (isHeader && !isButtonArea) {
                    currentResizeMode = ResizeMode.NONE; isDraggingWindow = true;
                    initX = this.getLayoutX(); initY = this.getLayoutY();
                    xOffset = e.getSceneX(); yOffset = e.getSceneY(); e.consume();
                }
            });
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                if (isMaximized) return;
                if (currentResizeMode != ResizeMode.NONE) { handleResize(e); e.consume(); }
                else if (isDraggingWindow) {
                    this.setLayoutX(initX + (e.getSceneX() - xOffset));
                    this.setLayoutY(initY + (e.getSceneY() - yOffset));
                    e.consume();
                }
            });
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                isDraggingWindow = false; currentResizeMode = ResizeMode.NONE;
            });
        }

        private void toggleMaximize() {
            if (getParent() == null) return;
            Region parent = (Region) getParent();
            if (isMaximized) {
                this.setLayoutX(restoreX); this.setLayoutY(restoreY);
                this.setPrefSize(restoreW, restoreH); this.resize(restoreW, restoreH);
                maxBtn.setText("□"); isMaximized = false;
                this.setStyle("-fx-background-radius: 5; -fx-border-radius: 5;");
            } else {
                restoreX = this.getLayoutX(); restoreY = this.getLayoutY();
                restoreW = this.getWidth(); restoreH = this.getHeight();
                this.setLayoutX(0); this.setLayoutY(0);
                this.setPrefSize(parent.getWidth(), parent.getHeight());
                this.resize(parent.getWidth(), parent.getHeight());
                maxBtn.setText("❐"); isMaximized = true;
                this.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;");
            }
            this.requestLayout();
        }

        private void handleResize(javafx.scene.input.MouseEvent e) {
            double deltaX = e.getSceneX() - xOffset;
            double deltaY = e.getSceneY() - yOffset;
            double newX = initX, newY = initY, newW = initW, newH = initH;
            if (isLeft(currentResizeMode)) { newW = initW - deltaX; newX = initX + deltaX; }
            else if (isRight(currentResizeMode)) { newW = initW + deltaX; }
            if (isTop(currentResizeMode)) { newH = initH - deltaY; newY = initY + deltaY; }
            else if (isBottom(currentResizeMode)) { newH = initH + deltaY; }
            if (newW < MIN_WIDTH) { newW = MIN_WIDTH; if (isLeft(currentResizeMode)) newX = initX + (initW - MIN_WIDTH); }
            if (newH < MIN_HEIGHT) { newH = MIN_HEIGHT; if (isTop(currentResizeMode)) newY = initY + (initH - MIN_HEIGHT); }
            this.resize(newW, newH); this.setPrefSize(newW, newH);
            this.setLayoutX(newX); this.setLayoutY(newY); this.layout();
        }

        private ResizeMode getResizeMode(double x, double y) {
            boolean left = x < RESIZE_MARGIN; boolean right = x > this.getWidth() - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN; boolean bottom = y > this.getHeight() - RESIZE_MARGIN;
            if (left && top) return ResizeMode.TOP_LEFT; if (right && top) return ResizeMode.TOP_RIGHT;
            if (left && bottom) return ResizeMode.BOTTOM_LEFT; if (right && bottom) return ResizeMode.BOTTOM_RIGHT;
            if (top) return ResizeMode.TOP; if (bottom) return ResizeMode.BOTTOM;
            if (left) return ResizeMode.LEFT; if (right) return ResizeMode.RIGHT;
            return ResizeMode.NONE;
        }
        private boolean isLeft(ResizeMode m) { return m == ResizeMode.LEFT || m == ResizeMode.TOP_LEFT || m == ResizeMode.BOTTOM_LEFT; }
        private boolean isRight(ResizeMode m) { return m == ResizeMode.RIGHT || m == ResizeMode.TOP_RIGHT || m == ResizeMode.BOTTOM_RIGHT; }
        private boolean isTop(ResizeMode m) { return m == ResizeMode.TOP || m == ResizeMode.TOP_LEFT || m == ResizeMode.TOP_RIGHT; }
        private boolean isBottom(ResizeMode m) { return m == ResizeMode.BOTTOM || m == ResizeMode.BOTTOM_LEFT || m == ResizeMode.BOTTOM_RIGHT; }
        private void setCursorBasedOnMode(ResizeMode mode) {
            switch (mode) {
                case TOP, BOTTOM -> this.setCursor(Cursor.V_RESIZE);
                case LEFT, RIGHT -> this.setCursor(Cursor.H_RESIZE);
                case TOP_LEFT, BOTTOM_RIGHT -> this.setCursor(Cursor.NW_RESIZE);
                case TOP_RIGHT, BOTTOM_LEFT -> this.setCursor(Cursor.NE_RESIZE);
                default -> this.setCursor(Cursor.DEFAULT);
            }
        }

        public void close() {
            this.setVisible(false);
            if (onClosed != null) onClosed.run();
            if (getParent() instanceof Pane p) p.getChildren().remove(this);
            openWindows.values().remove(this);
        }
    }

    private void initStartMenu() {
        ContextMenu startMenu = new ContextMenu();
        startMenu.getStyleClass().add("start-menu");
        MenuItem itemHelp = new MenuItem("❓  关于 / 帮助");
        MenuItem itemTerminal = new MenuItem("💻  终端");
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem itemShutdown = new MenuItem("🔴  关闭系统");
        itemHelp.setOnAction(e -> showAboutWindow());
        itemTerminal.setOnAction(e -> onOpenTerminalClick());
        itemShutdown.setOnAction(e -> {
            shutdown();
            if (kernel != null && kernel.getScheduler() != null) kernel.getScheduler().stop();
            Platform.exit();
            System.exit(0);
        });
        startMenu.getItems().addAll(itemHelp, itemTerminal, separator, itemShutdown);
        startMenuBtn.setOnAction(e -> {
            if (startMenu.isShowing()) startMenu.hide();
            else startMenu.show(startMenuBtn, Side.TOP, 0, 0);
        });
    }

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

        InternalWindow aboutWin = new InternalWindow("关于系统", content, 450, 400);
        double x = (desktopArea.getWidth() - 450) / 2;
        double y = (desktopArea.getHeight() - 400) / 2;
        aboutWin.setLayoutX(x > 0 ? x : 100);
        aboutWin.setLayoutY(y > 0 ? y : 100);
        closeBtn.setOnAction(e -> aboutWin.close());

        windowLayer.getChildren().add(aboutWin);
        aboutWin.toFront();
        addTaskBarItem(aboutWin);
    }

    private void setupFileSystemEvents() {
        fileSystemTreeView.setOnMouseClicked(event -> {
            if (Kernel.getInstance().getScheduler() == null || !Kernel.getInstance().getScheduler().isRunning()) {
                if (event.getClickCount() == 2) showWarning("系统未启动", "请先点击 [▶ 启动系统] 按钮。");
                return;
            }
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) openSelectedFile();
        });
        ContextMenu contextMenu = new ContextMenu();
        MenuItem editItem = new MenuItem("编辑 / 查看");
        MenuItem deleteItem = new MenuItem("删除");
        editItem.setOnAction(e -> openSelectedFile());
        deleteItem.setOnAction(e -> onDeleteClick());
        contextMenu.getItems().addAll(editItem, deleteItem);
        fileSystemTreeView.setContextMenu(contextMenu);
        contextMenu.setOnShowing(e -> {
            boolean isRunning = Kernel.getInstance().getScheduler() != null && Kernel.getInstance().getScheduler().isRunning();
            for (MenuItem item : contextMenu.getItems()) item.setDisable(!isRunning);
        });

        // 快捷键监听
        fileSystemTreeView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                onDeleteClick();
                event.consume();
                return;
            }
            if (event.isShortcutDown()) {
                switch (event.getCode()) {
                    case C -> { handleCopyShortcut(); event.consume(); }
                    case V -> { handlePasteShortcut(); event.consume(); }
                }
            }
        });
    }

    private void handleCopyShortcut() {
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) return;
        clipboardFiles.clear();
        StringBuilder names = new StringBuilder();
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

    private void handlePasteShortcut() {
        if (clipboardFiles.isEmpty()) {
            showWarning("剪贴板为空", "请先复制文件或目录。");
            return;
        }
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String targetPath;
        if (selectedItem == null) {
            targetPath = "/";
        } else {
            String path = buildPathFromTree(selectedItem);
            Object targetNode = kernel.getFileSystemManager().getObjectByPath(path);
            if (targetNode instanceof Directory) {
                targetPath = path;
            } else {
                if (path.contains("/")) targetPath = path.substring(0, path.lastIndexOf('/'));
                else targetPath = "/";
                if (targetPath.isEmpty()) targetPath = "/";
            }
        }
        int successCount = 0;
        for (Object source : clipboardFiles) {
            try {
                kernel.getFileSystemManager().paste(source, targetPath);
                successCount++;
            } catch (Exception e) {
                String name = (source instanceof Directory d) ? d.getName() : ((File)source).getName();
                showError("粘贴失败", "无法粘贴 '" + name + "': " + e.getMessage());
            }
        }
        if (successCount > 0) {
            updateFileSystemView();
            expandTreePath(targetPath);
            showInfo("粘贴成功", "已成功粘贴 " + successCount + " 个项目到 " + targetPath);
        }
    }

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

    @FXML protected void onStartSystemClick() {
        Kernel.getInstance().start();
        startSystemBtn.setDisable(true);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(false);
        updateControlButtonsState(true);
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }



    @FXML protected void onCreateProcessClick() {
        TextField processNameField = new TextField("新进程");
        processNameField.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Integer> priorityBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1);
        TextField execPathField = new TextField();
        execPathField.setMaxWidth(Double.MAX_VALUE);

        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(150);
        fileTreeView.setMaxWidth(Double.MAX_VALUE);
        fileTreeView.setMaxHeight(Double.MAX_VALUE);

        // ---------------------------------------------------------------
        // 【步骤 1】绑定监听器：包含文件名解析与智能命名逻辑
        // ---------------------------------------------------------------
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.getValue().endsWith(".e")) {
                // 1. 自动填入路径
                execPathField.setText(buildPathFromTree(newVal));

                // 2. 【核心修改】智能解析文件名，生成人性化的进程名
                String fileName = newVal.getValue(); // 例如 "p1_A_CPU.e"
                String suggestedName = fileName.replace(".e", ""); // 默认回退名

                if (fileName.contains("_CPU")) {
                    if (fileName.contains("_A_")) suggestedName = "计算型_A";
                    else if (fileName.contains("_B_")) suggestedName = "计算型_B";
                    else if (fileName.contains("_C_")) suggestedName = "计算型_C";
                } else if (fileName.contains("_IO")) {
                    if (fileName.contains("_A_")) suggestedName = "阻塞型_A";
                    else if (fileName.contains("_B_")) suggestedName = "阻塞型_B";
                    else if (fileName.contains("_C_")) suggestedName = "阻塞型_C";
                }

                // 3. 将解析出的名字填入文本框 (用户依然可以修改它)
                processNameField.setText(suggestedName);
            }
        });

        // ---------------------------------------------------------------
        // 【步骤 2】自动选中第一个可执行文件
        // ---------------------------------------------------------------
        TreeItem<String> firstExec = findFirstExecutable(rootItem);
        if (firstExec != null) {
            expandPath(firstExec);
            // 这一句会触发上面的监听器，自动填好路径和名字
            fileTreeView.getSelectionModel().select(firstExec);
            Platform.runLater(() -> {
                int row = fileTreeView.getRow(firstExec);
                if (row >= 0) fileTreeView.scrollTo(row);
            });
        }

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
        InternalWindow win = new InternalWindow("创建新进程", root, 500, 400);
        double x = (desktopArea.getWidth() - 500) / 2; double y = (desktopArea.getHeight() - 400) / 2;
        win.setLayoutX(x > 0 ? x : 100); win.setLayoutY(y > 0 ? y : 100);

        cancelBtn.setOnAction(e -> win.close());
        okBtn.setOnAction(e -> {
            String name = processNameField.getText().trim(); if (name.isEmpty()) name = "新进程";
            String path = execPathField.getText().trim(); int priority = priorityBox.getValue();
            org.example.scau_os_simulation.process.Executable exec = kernel.getFileSystemManager().loadExecutable(path);
            if (exec != null) {
                // 使用全限定名，防止 Process 类冲突
                org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess(name, priority);
                if (p != null) {
                    p.setExecutable(exec);
                    updateProcessView(); showInfo("成功", "进程已创建"); win.close();
                } else showError("失败", "无法创建进程");
            } else showError("文件错误", "无法加载可执行文件");
        });

        windowLayer.getChildren().add(win);
        win.toFront();
    }




    @FXML protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            if (selected.getPid() == -1) { showError("非法操作", "无法结束系统闲逛进程 (IDLE)。"); return; }
            String msg = "确定要强制结束进程 [" + selected.getName() + "] (PID=" + selected.getPid() + ") 吗？\n此操作不可撤销。";
            showInternalConfirm("确认终止进程", msg, () -> {
                kernel.getProcessManager().terminateProcess(selected.getPid());
                updateProcessView(); updateMemoryView();
            });
        } else showWarning("未选择进程", "请先选择要终止的进程。");

        // 新增这一行：
        updateMemoryVisualization();
    }

    @FXML protected void onCreateFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node != null) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }
        final String finalPath = path;
        showInternalInput("创建文件", "在路径 '" + finalPath + "' 下创建新文件:", "new.txt", (name) -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView(); showInfo("文件创建成功", "文件 '" + name + "' 创建成功。");
                } catch (Exception e) { showError("文件创建失败", e.getMessage()); }
            }
        });
    }

    @FXML protected void onStopSystemClick() {
        if (Kernel.getInstance().getScheduler() != null) Kernel.getInstance().getScheduler().stop();
        startSystemBtn.setDisable(false);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(true);
        updateControlButtonsState(false);
        showInfo("系统已暂停", "CPU 调度已停止。");
    }

    @FXML protected void onCreateDirectoryClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
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
                    updateFileSystemView(); showInfo("目录创建成功", "目录 '" + name + "' 创建成功。");
                } catch (Exception e) { showError("目录创建失败", e.getMessage()); }
            }
        });
    }

    @FXML protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) { showWarning("未选择", "请先选择要删除的文件或目录。"); return; }
        List<String> pathsToDelete = new ArrayList<>();
        boolean containsRoot = false;
        for (TreeItem<String> item : selectedItems) {
            if (item == null || item.getParent() == null) { containsRoot = true; continue; }
            pathsToDelete.add(buildPathFromTree(item));
        }
        if (pathsToDelete.isEmpty()) { if (containsRoot) showError("无法删除", "根目录不可删除。"); return; }
        String msg = pathsToDelete.size() == 1 ? "您确定要删除 '" + pathsToDelete.get(0) + "' 吗？" : "您确定要删除选中的 " + pathsToDelete.size() + " 个项目吗？";

        showInternalConfirm("确认删除", msg, () -> {
            int successCount = 0;
            for (String path : pathsToDelete) {
                try { if (kernel.getFileSystemManager().deletePath(path)) successCount++; }
                catch (Exception e) { }
            }
            if (successCount > 0) {
                updateFileSystemView();
                showInfo("删除成功", "已成功删除 " + successCount + " 个项目。");
            } else {
                showError("删除失败", "未能删除选中目标。");
            }
        });
    }

    @FXML protected void onDefragmentClick() {
        kernel.getMemoryManager().defragment();
        updateMemoryView(); showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    // 【修复 4】删除了 undo/redo 的事件处理方法

    @FXML protected void onCopyFileClick() { handleCopyShortcut(); }
    @FXML protected void onPasteFileClick() { handlePasteShortcut(); }

    @FXML protected void onSearchFileClick() {
        VBox root = new VBox(10); root.setPadding(new Insets(15));
        Label headerLbl = new Label("输入文件名 (支持前缀匹配，不区分大小写):");
        ComboBox<String> searchBox = new ComboBox<>();
        searchBox.setEditable(true); searchBox.setPromptText("例如: new..."); searchBox.setMaxWidth(Double.MAX_VALUE);
        HBox btnBox = new HBox(10); btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button goBtn = new Button("定位"); Button closeBtn = new Button("关闭");
        btnBox.getChildren().addAll(goBtn, closeBtn);
        root.getChildren().addAll(headerLbl, searchBox, btnBox);

        InternalWindow win = new InternalWindow("智能搜索", root, 350, 180);
        win.setLayoutX(desktopArea.getWidth() / 2 - 175); win.setLayoutY(desktopArea.getHeight() / 2 - 90);

        searchBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) { searchBox.hide(); return; }
            if (newVal.equals(searchBox.getSelectionModel().getSelectedItem())) return;
            Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
            List<String> matches = new ArrayList<>();
            rootDir.searchByPrefix(newVal.trim(), matches, "");
            Platform.runLater(() -> {
                if (!searchBox.getItems().equals(matches)) {
                    searchBox.getItems().setAll(matches);
                    if (!matches.isEmpty()) { if (!searchBox.isShowing()) searchBox.show(); }
                    else searchBox.hide();
                }
            });
        });
        Runnable doLocate = () -> {
            String path = searchBox.getEditor().getText();
            if (path != null && !path.trim().isEmpty()) {
                Object target = kernel.getFileSystemManager().getObjectByPath(path);
                if (target != null) selectFileInTree(target); else showWarning("未找到", "路径无效或文件不存在。");
            }
        };
        goBtn.setOnAction(e -> doLocate.run());
        searchBox.setOnAction(e -> doLocate.run());
        closeBtn.setOnAction(e -> win.close());

        windowLayer.getChildren().add(win);
        win.toFront();
        Platform.runLater(searchBox::requestFocus);
    }

    private VBox createEditorNode(File file) {
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        if (file.getContent() != null) {
            String content = new String(file.getContent(), 0, file.getActualLength(), java.nio.charset.StandardCharsets.UTF_8);
            textArea.setText(content);
        }
        Runnable doSave = () -> {
            try {
                byte[] data = textArea.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                file.setContent(data);
                showInfo("保存成功", "文件 '" + file.getName() + "' 已保存。");
            } catch (Exception ex) { showError("保存失败", ex.getMessage()); }
        };
        Button saveBtn = new Button("保存");
        saveBtn.setOnAction(e -> doSave.run());
        textArea.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) { doSave.run(); event.consume(); }
        });
        ToolBar toolBar = new ToolBar(saveBtn);
        VBox editorRoot = new VBox(toolBar, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return editorRoot;
    }

    private void openSelectedFile() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        String path = buildPathFromTree(selectedItem);
        File file = kernel.getFileSystemManager().getFileByPath(path);
        if (file != null) {
            VBox editorRoot = createEditorNode(file);
            InternalWindow editorWin = new InternalWindow("编辑: " + file.getName(), editorRoot, 500, 400);
            double offset = openWindows.size() * 30;
            editorWin.setLayoutX(100 + offset); editorWin.setLayoutY(50 + offset);

            windowLayer.getChildren().add(editorWin);
            editorWin.toFront();
            addTaskBarItem(editorWin);
            openWindows.put(editorRoot, editorWin);
        }
    }

    private void updateAllViews() {
        updateProcessView(); updateMemoryView(); updateDeviceView();
        updateFileSystemView(); updateOperationLogView(); updatePerformanceMetrics();
    }

    private void updateProcessView() {
        // 【核心修改】在刷新表格前，先计算每个进程的总剩余时间
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            // 调用您在 Process.java 中写好的 getRemainingTime()
            int remaining = p.getRemainingTime();
            // 存入 PCB
            p.getPcb().setTotalRemainingTime(remaining);
        }

        processTableView.getItems().setAll(kernel.getProcessManager().getProcesses().stream().map(org.example.scau_os_simulation.process.Process::getPcb).toList());
        readyQueueListView.getItems().setAll(kernel.getProcessManager().getReadyQueue().stream().map(p -> "PID: " + p.getPcb().getPid() + " (优先级: " + p.getPcb().getPriority() + ")").toList());
        blockedQueueListView.getItems().setAll(kernel.getProcessManager().getBlockedQueue().stream().map(p -> "PID: " + p.getPcb().getPid()).toList());
        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        if (running != null) {
            PCB pcb = running.getPcb();
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr());
            axLabel.setText("AX: " + pcb.getAx());
            tsLabel.setText("时间片: " + kernel.getTimeSlice());
        } else runningPidLabel.setText("运行中PID: 无");
    }

    private void updateMemoryView() {
        MemoryManager memoryManager = kernel.getMemoryManager();
        int totalMemory = memoryManager.getMemory().getSize();
        int usedMemory = memoryManager.getTotalUsedMemory();
        double usage = (double) usedMemory / totalMemory;
        memoryUsageBar.setProgress(usage);
        memoryInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", usedMemory, totalMemory));

        // 更新表格
        memoryBlockTableView.getItems().setAll(memoryManager.getAllocatedBlocks());

        // 更新碎片率文字
        double fragmentationRate = memoryManager.getFragmentationRate();
        fragmentationLabel.setText(String.format("碎片率: %.2f%%", fragmentationRate * 100));

        // 【新增】这一行必须加！否则点击整理按钮后，下面的彩色条不会动！
        updateMemoryVisualization();
    }

    private void updateDeviceView() {
        deviceTableView.getItems().setAll(kernel.getDeviceManager().getAllDevices());
        List<WaitRow> waitRows = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            for (DeviceRequest request : kernel.getDeviceManager().getWaitingQueue(t)) {
                waitRows.add(new WaitRow(t.toString(), request.getPid(), request.getExecutionTime()));
            }
        }
        waitQueueTableView.getItems().setAll(waitRows);
    }

    private void updateFileSystemView() {
        Set<String> expandedPaths = new HashSet<>();
        if (fileSystemTreeView.getRoot() != null) saveExpansionState(fileSystemTreeView.getRoot(), expandedPaths);
        String selectedPath = null;
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) { selectedPath = buildPathFromTree(selectedItem); expandedPaths.add(selectedPath); }

        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder"));
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        restoreExpansionState(rootItem, expandedPaths);
        rootItem.setExpanded(true);
        if (selectedPath != null) {
            TreeItem<String> targetItem = findItemByPath(rootItem, selectedPath);
            if (targetItem != null) {
                fileSystemTreeView.getSelectionModel().select(targetItem);
                int row = fileSystemTreeView.getRow(targetItem);
                if (row >= 0) fileSystemTreeView.scrollTo(row);
            }
        }
        if (kernel.getFileSystemManager().getFileSystem() != null) {
            int total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
            int used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
            double usage = total > 0 ? (double) used / total : 0;
            if (diskUsageBar != null) diskUsageBar.setProgress(usage);
            if (diskInfoLabel != null) diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", used, total));
        }
    }

    private void saveExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        if (item.isExpanded()) expandedPaths.add(buildPathFromTree(item));
        for (TreeItem<String> child : item.getChildren()) saveExpansionState(child, expandedPaths);
    }

    private void restoreExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        String currentPath = buildPathFromTree(item);
        if (expandedPaths.contains(currentPath)) item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) restoreExpansionState(child, expandedPaths);
    }

    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem) {
        for (Object child : parent.getChildren()) {
            if (child instanceof Directory dir) {
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                dirItem.setGraphic(createIcon("folder"));
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File f) {
                TreeItem<String> fileItem = new TreeItem<>(f.getName());
                if (f.getName().endsWith(".e")) fileItem.setGraphic(createIcon("exec"));
                else if (f.getName().endsWith(".txt")) fileItem.setGraphic(createIcon("text"));
                else fileItem.setGraphic(createIcon("file"));
                parentItem.getChildren().add(fileItem);
            }
        }
    }

    private javafx.scene.control.Label createIcon(String type) {
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label();
        iconLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Segoe UI Symbol';");
        switch (type) {
            case "folder" -> { iconLabel.setText("📁"); iconLabel.getStyleClass().add("folder-icon"); }
            case "exec" -> { iconLabel.setText("🚀"); iconLabel.getStyleClass().add("exec-icon"); }
            case "text" -> { iconLabel.setText("📝"); iconLabel.getStyleClass().add("file-icon"); }
            default -> { iconLabel.setText("📄"); iconLabel.getStyleClass().add("file-icon"); }
        }
        return iconLabel;
    }

    private void updateOperationLogView() {
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        outputListView.getItems().setAll(kernel.getOutputLogs());
    }

    private void initializePerformanceChart() {
        try {
            performanceChart = new PerformanceChartFX();
            javafx.scene.chart.LineChart<Number, Number> chart = performanceChart.getChart();
            if (performanceChartContainer != null) {
                performanceChartContainer.getChildren().clear();
                performanceChartContainer.getChildren().add(chart);
                chart.prefWidthProperty().bind(performanceChartContainer.widthProperty());
                chart.prefHeightProperty().bind(performanceChartContainer.heightProperty());
                chart.setStyle("-fx-background-color: transparent;");
            }
        } catch (Exception e) {
            System.err.println("性能图表初始化失败: " + e.getMessage());
        }
    }

    private void updatePerformanceChart() {
        if (performanceChart != null && kernel != null) {
            performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
        }
    }

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

    private void initBindings() {
        pidColumn.setCellValueFactory(cellData -> cellData.getValue().pidProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        stateColumn.setCellValueFactory(cellData -> cellData.getValue().stateProperty());
        priorityColumn.setCellValueFactory(cellData -> cellData.getValue().priorityProperty());
        memoryAddressColumn.setCellValueFactory(cellData -> cellData.getValue().memoryAddressProperty());
        memorySizeColumn.setCellValueFactory(cellData -> cellData.getValue().memorySizeProperty());
        startAddressColumn.setCellValueFactory(cellData -> cellData.getValue().startAddressProperty());
        blockSizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        processColumn.setCellValueFactory(cellData -> {
            int pid = findProcessIdForMemoryBlock(cellData.getValue());
            return new javafx.beans.property.SimpleStringProperty(pid >= 0 ? String.valueOf(pid) : "N/A");
        });
        deviceTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType().toString()));
        deviceInUseColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isBusy() ? "是" : "否"));
        devicePidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCurrentUserPid()));
        deviceRemainColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getRemainingTime()));
        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device()));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid()));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time()));
        // 【新增】绑定剩余时间片列
        timeSliceColumn.setCellValueFactory(cellData -> cellData.getValue().timeSliceProperty());
        // 【新增】绑定“总剩余时间”列
        if (totalRemainingTimeColumn != null) {
            totalRemainingTimeColumn.setCellValueFactory(cellData -> cellData.getValue().totalRemainingTimeProperty());
        }
    }

    private int findProcessIdForMemoryBlock(MemoryBlock block) {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize()) {
                return p.getPcb().getPid();
            }
        }
        return -1;
    }

    private String buildPathFromTree(TreeItem<String> item) {
        StringBuilder path = new StringBuilder();
        while (item != null && item.getParent() != null) {
            path.insert(0, "/" + item.getValue());
            item = item.getParent();
        }
        return !path.isEmpty() ? path.toString() : "/";
    }

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

    private String buildFullPath(Object fileObj) {
        return findObjectPath(fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
    }

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

    private void showInfo(String title, String message) { showInternalAlert("info", title, message); }
    private void showWarning(String title, String message) { showInternalAlert("warning", title, message); }
    private void showError(String title, String message) { showInternalAlert("error", title, message); }

    private record WaitRow(String device, int pid, int time) {}

    @FXML protected void onOpenTerminalClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/scau_os_simulation/terminal_view.fxml"));
            Parent terminalContent = loader.load();
            TerminalController controller = loader.getController();
            InternalWindow termWin = new InternalWindow("终端", terminalContent, 600, 400);
            termWin.onClosed = () -> controller.onClose();
            openWindow("终端", terminalContent, 600, 400);
        } catch (Exception e) {
            System.err.println("无法打开终端");
            showError("错误", "无法打开终端: " + e.getMessage());
        }
    }

    private void showInternalAlert(String type, String title, String content) {
        VBox root = new VBox(10); root.setPadding(new Insets(15)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content); msgLabel.setWrapText(true); msgLabel.setMaxWidth(250);
        Button okBtn = new Button("确定");
        root.getChildren().addAll(msgLabel, okBtn);
        InternalWindow win = new InternalWindow(title, root, 300, 150);
        double x = (desktopArea.getWidth() - 300) / 2; double y = (desktopArea.getHeight() - 150) / 2;
        win.setLayoutX(x); win.setLayoutY(y);
        okBtn.setOnAction(e -> win.close());
        windowLayer.getChildren().add(win);
    }

    private void showInternalConfirm(String title, String content, Runnable onConfirm) {
        VBox root = new VBox(20); root.setPadding(new Insets(20)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content); msgLabel.setWrapText(true); msgLabel.setMaxWidth(300); msgLabel.setStyle("-fx-font-size: 14px;");
        HBox btnBox = new HBox(15); btnBox.setAlignment(Pos.CENTER);
        Button yesBtn = new Button("确定"); yesBtn.getStyleClass().add("button");
        yesBtn.setStyle("-fx-background-color: #da1e28; -fx-text-fill: white;");
        Button noBtn = new Button("取消");
        btnBox.getChildren().addAll(yesBtn, noBtn);
        root.getChildren().addAll(msgLabel, btnBox);
        InternalWindow win = new InternalWindow(title, root, 350, 180);
        win.setLayoutX((desktopArea.getWidth() - 350) / 2); win.setLayoutY((desktopArea.getHeight() - 180) / 2);
        yesBtn.setOnAction(e -> { win.close(); if (onConfirm != null) onConfirm.run(); });
        noBtn.setOnAction(e -> win.close());

        windowLayer.getChildren().add(win);
        win.toFront();
    }

    private void showInternalInput(String title, String header, String defaultValue, java.util.function.Consumer<String> callback) {
        VBox root = new VBox(10); root.setPadding(new Insets(15));
        Label headerLbl = new Label(header); TextField textField = new TextField(defaultValue);
        HBox btnBox = new HBox(10); btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button okBtn = new Button("确定"); Button cancelBtn = new Button("取消");
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
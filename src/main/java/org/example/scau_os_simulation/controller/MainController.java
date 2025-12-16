package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
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

import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 操作系统模拟器主控制器
 * 负责UI交互、各功能模块（进程/内存/文件/设备/性能）的视图渲染与事件处理
 */
public class MainController implements Initializable {
    // --- 桌面环境核心组件 ---
    @FXML private StackPane rootStackPane;          // 根布局容器（全局最外层）
    @FXML private FlowPane desktopArea;             // 桌面区域（承载图标和所有内部窗口）
    @FXML private HBox taskBarApps;                 // 任务栏-应用按钮容器（显示已打开窗口的快捷按钮）
    @FXML private Label systemClockLabel;           // 系统时钟标签（实时显示日期时间）
    @FXML private Button startMenuBtn;              // 开始菜单按钮（触发系统菜单弹窗）

    // --- 功能视图容器（进程/内存/文件/设备/性能） ---
    @FXML private VBox processViewRoot;             // 进程管理视图根节点
    @FXML private VBox memoryViewRoot;              // 内存管理视图根节点
    @FXML private VBox fileSystemViewRoot;          // 文件系统视图根节点
    @FXML private AnchorPane deviceViewRoot;        // 设备管理视图根节点
    @FXML private VBox performanceViewRoot;         // 性能监控视图根节点
    @FXML private StackPane performanceChartContainer; // 性能图表容器（承载CPU/内存趋势图）

    // --- 功能按钮（系统控制/进程/文件/内存操作） ---
    @FXML private Button startSystemBtn, stopSystemBtn;          // 系统启停
    @FXML private Button createProcessBtn, terminateProcessBtn;  // 进程创建/终止
    @FXML private Button undoBtn, redoBtn;                        // 操作撤销/重做
    @FXML private Button createFileBtn, createDirectoryBtn;       // 文件/目录创建
    @FXML private Button deleteFileBtn, copyFileBtn, pasteFileBtn;// 文件删除/复制/粘贴
    @FXML private Button searchFileBtn;                           // 文件搜索

    // --- 数据展示组件 ---
    // 进程表格：基础信息列
    @FXML private TableView<PCB> processTableView;
    @FXML private TableColumn<PCB, Number> pidColumn, priorityColumn, memoryAddressColumn, memorySizeColumn;
    @FXML private TableColumn<PCB, String> nameColumn, stateColumn;
    // 进程运行时状态标签（寄存器/时间片）
    @FXML private Label runningPidLabel, irLabel, axLabel, tsLabel;
    // 日志/队列列表：输出日志、就绪队列、阻塞队列、操作日志
    @FXML private ListView<String> outputListView, readyQueueListView, blockedQueueListView, operationLogListView;
    // 资源使用率进度条：内存、磁盘、CPU、系统负载
    @FXML private ProgressBar memoryUsageBar, diskUsageBar, cpuUtilizationBar, systemLoadBar;
    // 内存/磁盘统计标签：已用/总量、碎片率
    @FXML private Label memoryInfoLabel, fragmentationLabel, diskInfoLabel;
    // 性能监控标签：实时/平均/峰值CPU/内存使用率、系统负载
    @FXML private Label cpuUtilizationLabel, systemLoadLabel, avgCpuLabel, avgMemoryLabel, peakCpuLabel, peakMemoryLabel;
    // 内存块表格：地址/大小/所属进程
    @FXML private TableView<MemoryBlock> memoryBlockTableView;
    @FXML private TableColumn<MemoryBlock, Number> startAddressColumn, blockSizeColumn;
    @FXML private TableColumn<MemoryBlock, String> processColumn;
    // 文件系统树形视图（展示目录/文件结构）
    @FXML private TreeView<String> fileSystemTreeView;
    // 设备表格：类型/占用状态/占用PID/剩余时间
    @FXML private TableView<Device> deviceTableView;
    @FXML private TableColumn<Device, String> deviceTypeColumn, deviceInUseColumn;
    @FXML private TableColumn<Device, Number> devicePidColumn, deviceRemainColumn;
    // 设备等待队列：设备类型/等待PID/等待时间
    @FXML private TableView<WaitRow> waitQueueTableView;
    @FXML private TableColumn<WaitRow, String> waitDeviceColumn;
    @FXML private TableColumn<WaitRow, Number> waitPidColumn, waitTimeColumn;

    // --- 后端核心对象引用 ---
    private Kernel kernel;                              // 内核单例（所有核心功能的入口）
    // UI刷新线程池（单线程，避免多线程UI冲突，定时刷新视图）
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartFX performanceChart;        // 性能趋势图表（CPU/内存使用率曲线）
    private Object clipboardFile;                       // 文件剪贴板（暂存复制的文件/目录对象）
    private final Map<Node, InternalWindow> openWindows = new HashMap<>(); // 已打开窗口缓存（Key=内容节点，Value=窗口实例）

    // [修改] 剪贴板升级为列表，支持多选复制
    private final List<Object> clipboardFiles = new ArrayList<>();


    /**
     * 初始化方法（FXML加载完成后执行）
     * 初始化绑定、视图、事件、定时任务等
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        kernel = Kernel.getInstance();
        initBindings();                // 初始化表格列与数据绑定
        initializePerformanceChart();  // 初始化性能图表

        // [新增] 1. 开启文件树的多选模式 (Shift+点击, Ctrl+点击)
        fileSystemTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 桌面区域裁剪（防止内容溢出）
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(desktopArea.widthProperty());
        clip.heightProperty().bind(desktopArea.heightProperty());
        desktopArea.setClip(clip);
        desktopArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        initDesktop();                 // 初始化桌面图标
        initStartMenu();               // 初始化开始菜单
        startClock();                  // 启动系统时钟

        // 3. 初始刷新
        updateAllViews();
        updateFileSystemView(); // 初始化时调用一次即可
        setupFileSystemEvents();
        updateControlButtonsState(false);

        // 全局快捷键：Ctrl+F 触发文件搜索
        rootStackPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                onSearchFileClick();
                e.consume();
            }
        });

        // 4. 定时任务：每 500ms 刷新一次界面
        // 【关键修复】从定时任务中移除 updateFileSystemView()
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
            updateOperationLogView();
            updatePerformanceChart();
            updatePerformanceMetrics();
            // ❌ 删除或注释掉下面这行：
            // updateFileSystemView();
        }), 0, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 关闭资源（程序退出时调用）
     */
    public void shutdown() {
        if (!uiExec.isShutdown()) {
            uiExec.shutdownNow();
        }
    }

    /**
     * 递归查找树形结构中第一个可执行文件（.e后缀）
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
     * 展开树形节点的父路径（方便定位文件）
     */
    private void expandPath(TreeItem<String> item) {
        TreeItem<String> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }

    /**
     * 初始化桌面图标
     */
    private void initDesktop() {
        addDesktopIcon("进程管理", "process.png", processViewRoot, 800, 600);
        addDesktopIcon("内存管理", "memory.png", memoryViewRoot, 700, 500);
        addDesktopIcon("资源管理器", "computer.png", fileSystemViewRoot, 800, 600);
        addDesktopIcon("设备管理", "device.png", deviceViewRoot, 600, 400);
        addDesktopIcon("性能监视器", "monitor.png", performanceViewRoot, 800, 500);
        addDesktopIcon("终端", "terminal.png", null, 600, 400);
    }

    /**
     * 启动系统时钟（每秒刷新）
     */
    private void startClock() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        uiExec.scheduleAtFixedRate(() -> {
            String timeText = sdf.format(new Date());
            Platform.runLater(() -> {
                if (systemClockLabel != null) systemClockLabel.setText(timeText);
            });
        }, 0, 1, TimeUnit.SECONDS);
    }

    @FXML
    protected void onDesktopClick() {
        // 桌面点击事件（预留扩展）
    }

    /**
     * 添加桌面图标
     * @param name 图标名称
     * @param iconFileName 图标文件名
     * @param contentNode 点击打开的视图节点
     * @param winWidth 窗口宽度
     * @param winHeight 窗口高度
     */
    private void addDesktopIcon(String name, String iconFileName, Node contentNode, double winWidth, double winHeight) {
        VBox iconBox = new VBox(5);
        iconBox.setAlignment(Pos.TOP_CENTER);
        iconBox.getStyleClass().add("desktop-icon");

        Node graphicNode;
        try {
            // 加载图标资源
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
            // 图标加载失败时显示占位符
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

        // 双击打开对应窗口/终端
        iconBox.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                if ("终端".equals(name)) onOpenTerminalClick();
                else openWindow(name, contentNode, winWidth, winHeight);
            }
        });

        // 右键菜单（打开）
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
     * 打开内部窗口
     * @param title 窗口标题
     * @param content 窗口内容节点
     * @param w 宽度
     * @param h 高度
     */
    private void openWindow(String title, Node content, double w, double h) {
        if (content == null) return;
        // 窗口已打开则前置显示
        if (openWindows.containsKey(content)) {
            InternalWindow existing = openWindows.get(content);
            existing.toFront();
            if (!desktopArea.getChildren().contains(existing)) {
                desktopArea.getChildren().add(existing);
                addTaskBarItem(existing);
            }
            existing.setVisible(true);
            return;
        }

        // 创建新窗口并设置偏移（避免重叠）
        InternalWindow window = new InternalWindow(title, content, w, h);
        double offset = openWindows.size() * 30;
        window.setLayoutX(100 + offset);
        window.setLayoutY(50 + offset);

        openWindows.put(content, window);
        desktopArea.getChildren().add(window);
        addTaskBarItem(window);
    }

    /**
     * 为窗口添加任务栏按钮
     */
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
        // 窗口关闭时移除任务栏按钮
        window.onClosed = () -> taskBarApps.getChildren().remove(taskBtn);
        taskBarApps.getChildren().add(taskBtn);
    }

    /**
     * 自定义内部窗口组件
     * 支持拖拽、最大化、调整大小、最小化、关闭等功能
     */
    class InternalWindow extends VBox {
        private double xOffset = 0, yOffset = 0;
        private double initX, initY, initW, initH;
        private boolean isDraggingWindow = false;
        private boolean isMaximized = false;
        private double restoreX, restoreY, restoreW, restoreH; // 最大化恢复参数

        private static final double RESIZE_MARGIN = 10.0;    // 调整大小触发边距
        private static final double MIN_WIDTH = 200;         // 最小宽度
        private static final double MIN_HEIGHT = 150;        // 最小高度

        String title;
        Runnable onClosed;                                   // 关闭回调
        private final Button maxBtn;                         // 最大化按钮
        private ResizeMode currentResizeMode = ResizeMode.NONE; // 调整大小模式

        // 调整大小模式枚举
        private enum ResizeMode { NONE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

        public InternalWindow(String title, Node content, double w, double h) {
            this.setManaged(false);
            this.title = title;
            this.resize(w, h);
            this.setPrefSize(w, h);
            this.getStyleClass().add("window-frame");

            // 标题栏
            HBox titleBar = new HBox();
            titleBar.getStyleClass().add("window-title-bar");
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setMinHeight(32);
            titleBar.setPrefHeight(32);

            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("window-title");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 窗口控制按钮（最小化/最大化/关闭）
            String baseStyle = "-fx-font-weight: bold; -fx-background-color: transparent;";
            String minBtnStyle = baseStyle + "-fx-font-size: 10px; -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;";
            String maxCloseStyle = baseStyle + "-fx-font-size: 12px; -fx-alignment: center; -fx-padding: 0;";
            String hoverBg = "-fx-background-color: #e0e0e0;";

            Button minBtn = new Button("—"); // 最小化
            minBtn.getStyleClass().add("window-close-btn");
            minBtn.setStyle(minBtnStyle);
            minBtn.setPrefSize(30, 20);
            minBtn.setOnAction(e -> this.setVisible(false));
            minBtn.setOnMouseEntered(e -> minBtn.setStyle(minBtnStyle + hoverBg));
            minBtn.setOnMouseExited(e -> minBtn.setStyle(minBtnStyle));

            maxBtn = new Button("□"); // 最大化/恢复
            maxBtn.getStyleClass().add("window-close-btn");
            maxBtn.setStyle(maxCloseStyle);
            maxBtn.setPrefSize(30, 20);
            maxBtn.setOnAction(e -> toggleMaximize());
            maxBtn.setOnMouseEntered(e -> maxBtn.setStyle(maxCloseStyle + hoverBg));
            maxBtn.setOnMouseExited(e -> maxBtn.setStyle(maxCloseStyle));

            Button closeBtn = new Button("✕"); // 关闭
            closeBtn.getStyleClass().add("window-close-btn");
            closeBtn.setStyle(maxCloseStyle);
            closeBtn.setPrefSize(30, 20);
            closeBtn.setOnAction(e -> close());
            String closeHover = "-fx-background-color: #e81123; -fx-text-fill: white;";
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(maxCloseStyle + closeHover));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle(maxCloseStyle + "-fx-text-fill: black;"));

            titleBar.getChildren().addAll(titleLbl, spacer, minBtn, maxBtn, closeBtn);

            // 内容容器
            VBox contentContainer = new VBox(content);
            contentContainer.setPadding(new Insets(5));
            VBox.setVgrow(contentContainer, Priority.ALWAYS);

            if (content instanceof Region r) r.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (content instanceof Control c) c.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(content, Priority.ALWAYS);

            this.getChildren().addAll(titleBar, contentContainer);

            setupWindowEvents(); // 绑定窗口交互事件

            Platform.runLater(() -> {
                this.requestLayout();
                this.applyCss();
            });
        }

        /**
         * 绑定窗口交互事件（拖拽、调整大小、最大化等）
         */
        private void setupWindowEvents() {
            // 鼠标移动：更新调整大小光标
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                if (isMaximized) { this.setCursor(Cursor.DEFAULT); return; }
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                setCursorBasedOnMode(mode);
            });
            // 鼠标移出：恢复默认光标
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (!e.isPrimaryButtonDown()) this.setCursor(Cursor.DEFAULT);
            });
            // 鼠标按下：处理拖拽/调整大小/双击最大化
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
            // 鼠标拖拽：处理窗口移动/大小调整
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                if (isMaximized) return;
                if (currentResizeMode != ResizeMode.NONE) { handleResize(e); e.consume(); }
                else if (isDraggingWindow) {
                    this.setLayoutX(initX + (e.getSceneX() - xOffset));
                    this.setLayoutY(initY + (e.getSceneY() - yOffset));
                    e.consume();
                }
            });
            // 鼠标释放：重置状态
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                isDraggingWindow = false; currentResizeMode = ResizeMode.NONE;
            });
        }

        /**
         * 切换窗口最大化/恢复状态
         */
        private void toggleMaximize() {
            if (getParent() == null) return;
            Region parent = (Region) getParent();
            if (isMaximized) {
                // 恢复原尺寸和位置
                this.setLayoutX(restoreX); this.setLayoutY(restoreY);
                this.setPrefSize(restoreW, restoreH); this.resize(restoreW, restoreH);
                maxBtn.setText("□"); isMaximized = false;
                this.setStyle("-fx-background-radius: 5; -fx-border-radius: 5;");
            } else {
                // 保存原参数，最大化窗口
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

        /**
         * 处理窗口大小调整
         */
        private void handleResize(javafx.scene.input.MouseEvent e) {
            double deltaX = e.getSceneX() - xOffset;
            double deltaY = e.getSceneY() - yOffset;
            double newX = initX, newY = initY, newW = initW, newH = initH;

            // 左/右侧调整宽度
            if (isLeft(currentResizeMode)) { newW = initW - deltaX; newX = initX + deltaX; }
            else if (isRight(currentResizeMode)) { newW = initW + deltaX; }

            // 上/下侧调整高度
            if (isTop(currentResizeMode)) { newH = initH - deltaY; newY = initY + deltaY; }
            else if (isBottom(currentResizeMode)) { newH = initH + deltaY; }

            // 限制最小尺寸
            if (newW < MIN_WIDTH) { newW = MIN_WIDTH; if (isLeft(currentResizeMode)) newX = initX + (initW - MIN_WIDTH); }
            if (newH < MIN_HEIGHT) { newH = MIN_HEIGHT; if (isTop(currentResizeMode)) newY = initY + (initH - MIN_HEIGHT); }

            this.resize(newW, newH);
            this.setPrefSize(newW, newH);
            this.setLayoutX(newX);
            this.setLayoutY(newY);
            this.layout();
        }

        /**
         * 判断鼠标位置对应的调整大小模式
         */
        private ResizeMode getResizeMode(double x, double y) {
            boolean left = x < RESIZE_MARGIN; boolean right = x > this.getWidth() - RESIZE_MARGIN;
            boolean top = y < RESIZE_MARGIN; boolean bottom = y > this.getHeight() - RESIZE_MARGIN;
            if (left && top) return ResizeMode.TOP_LEFT; if (right && top) return ResizeMode.TOP_RIGHT;
            if (left && bottom) return ResizeMode.BOTTOM_LEFT; if (right && bottom) return ResizeMode.BOTTOM_RIGHT;
            if (top) return ResizeMode.TOP; if (bottom) return ResizeMode.BOTTOM;
            if (left) return ResizeMode.LEFT; if (right) return ResizeMode.RIGHT;
            return ResizeMode.NONE;
        }

        // 辅助方法：判断是否为左侧调整
        private boolean isLeft(ResizeMode m) { return m == ResizeMode.LEFT || m == ResizeMode.TOP_LEFT || m == ResizeMode.BOTTOM_LEFT; }
        // 辅助方法：判断是否为右侧调整
        private boolean isRight(ResizeMode m) { return m == ResizeMode.RIGHT || m == ResizeMode.TOP_RIGHT || m == ResizeMode.BOTTOM_RIGHT; }
        // 辅助方法：判断是否为上侧调整
        private boolean isTop(ResizeMode m) { return m == ResizeMode.TOP || m == ResizeMode.TOP_LEFT || m == ResizeMode.TOP_RIGHT; }
        // 辅助方法：判断是否为下侧调整
        private boolean isBottom(ResizeMode m) { return m == ResizeMode.BOTTOM || m == ResizeMode.BOTTOM_LEFT || m == ResizeMode.BOTTOM_RIGHT; }

        /**
         * 根据调整模式设置光标样式
         */
        private void setCursorBasedOnMode(ResizeMode mode) {
            switch (mode) {
                case TOP, BOTTOM -> this.setCursor(Cursor.V_RESIZE);
                case LEFT, RIGHT -> this.setCursor(Cursor.H_RESIZE);
                case TOP_LEFT, BOTTOM_RIGHT -> this.setCursor(Cursor.NW_RESIZE);
                case TOP_RIGHT, BOTTOM_LEFT -> this.setCursor(Cursor.NE_RESIZE);
                default -> this.setCursor(Cursor.DEFAULT);
            }
        }

        /**
         * 关闭窗口
         */
        public void close() {
            this.setVisible(false);
            if (onClosed != null) onClosed.run();
            if (getParent() instanceof Pane p) p.getChildren().remove(this);
            openWindows.values().remove(this);
        }
    }

    /**
     * 初始化开始菜单
     */
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

    /**
     * 显示关于窗口
     */
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
        desktopArea.getChildren().add(aboutWin);
        aboutWin.toFront();
        addTaskBarItem(aboutWin);
    }




    /**
     * 绑定文件系统交互事件（双击打开、右键菜单、快捷键）
     */
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

        // [修改] 添加键盘快捷键监听
        fileSystemTreeView.setOnKeyPressed(event -> {
            // 1. 处理 Delete 键 (通常不需要按 Ctrl)
            if (event.getCode() == KeyCode.DELETE) {
                onDeleteClick();
                event.consume();
                return;
            }

            // 2. 处理组合键 (Ctrl/Cmd + C/V)
            if (event.isShortcutDown()) {
                switch (event.getCode()) {
                    case C -> {
                        handleCopyShortcut();
                        event.consume();
                    }
                    case V -> {
                        handlePasteShortcut();
                        event.consume();
                    }
                }
            }
        });
    }





    // --- [新增] 快捷键处理逻辑 ---

    /**
     * 处理 Ctrl+S 保存快捷键
     */
    private void handleSaveShortcut() {
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // 这里只是模拟保存动作，因为内存文件系统实时生效
            // 如果有打开的编辑器，逻辑会更复杂，这里仅响应资源管理器的选中项
            showInfo("保存成功", "文件 '" + selected.getValue() + "' 已保存。");
        }
    }

    /**
     * 处理 Ctrl+C 复制快捷键
     */
    private void handleCopyShortcut() {
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) return;

        clipboardFiles.clear();
        StringBuilder names = new StringBuilder();

        for (TreeItem<String> item : selectedItems) {
            String path = buildPathFromTree(item);
            // 使用之前优化过的 getObjectByPath 获取对象 (支持文件和目录)
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

    /**
     * 处理 Ctrl+V 粘贴快捷键
     * 规则：目标是文件 -> 粘贴到同级；目标是目录 -> 粘贴到子级
     */
    private void handlePasteShortcut() {
        if (clipboardFiles.isEmpty()) {
            showWarning("剪贴板为空", "请先复制文件或目录。");
            return;
        }

        // 1. 确定粘贴的目标路径
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String targetPath;

        if (selectedItem == null) {
            targetPath = "/"; // 没选中任何东西，默认粘贴到根目录
        } else {
            String path = buildPathFromTree(selectedItem);
            Object targetNode = kernel.getFileSystemManager().getObjectByPath(path);

            if (targetNode instanceof Directory) {
                // 规则②：若选中(粘贴位置)是目录，放置在该目录的子层级中
                targetPath = path;
            } else {
                // 规则①：若选中(粘贴位置)是文件，放置在该文件的同级目录下 (即父目录)
                if (path.contains("/")) {
                    targetPath = path.substring(0, path.lastIndexOf('/'));
                } else {
                    targetPath = "/";
                }
                if (targetPath.isEmpty()) targetPath = "/";
            }
        }

        // 2. 执行批量粘贴
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
            // 展开目标目录以便用户看到粘贴结果
            expandTreePath(targetPath);
            showInfo("粘贴成功", "已成功粘贴 " + successCount + " 个项目到 " + targetPath);
        }
    }


    // --- [辅助方法] 展开指定路径的树节点 ---
    /**
     * 展开文件树直到指定路径，确保用户能看到该路径下的内容
     * @param path 要展开的目标文件夹路径 (例如 "/user/docs")
     */
    private void expandTreePath(String path) {
        if (path == null || path.equals("/") || path.isEmpty()) return;

        TreeItem<String> current = fileSystemTreeView.getRoot();
        if (current == null) return;

        // 确保根节点展开
        current.setExpanded(true);

        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;

            boolean found = false;
            // 在当前节点的子节点中寻找匹配项
            for (TreeItem<String> child : current.getChildren()) {
                if (child.getValue().equals(part)) {
                    current = child;
                    // 关键：将沿途经过的目录都设置为展开状态
                    current.setExpanded(true);
                    found = true;
                    break;
                }
            }

            // 如果路径中的某一段没找到，说明树结构可能还没更新或路径无效，停止展开
            if (!found) break;
        }
    }


    /**
     * 更新功能按钮状态（系统启动/停止时禁用/启用）
     */
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

    /**
     * 启动系统按钮点击事件
     */
    @FXML protected void onStartSystemClick() {
        Kernel.getInstance().start();
        startSystemBtn.setDisable(true);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(false);
        updateControlButtonsState(true);
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }

    /**
     * 创建进程按钮点击事件
     */
    @FXML protected void onCreateProcessClick() {
        TextField processNameField = new TextField("新进程");
        processNameField.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Integer> priorityBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1);
        TextField execPathField = new TextField();
        execPathField.setMaxWidth(Double.MAX_VALUE);

        // 构建文件树选择可执行文件
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(150);
        fileTreeView.setMaxWidth(Double.MAX_VALUE);
        fileTreeView.setMaxHeight(Double.MAX_VALUE);

        // 选择文件时自动填充路径和进程名
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null && newVal.getValue().endsWith(".e")) {
                execPathField.setText(buildPathFromTree(newVal));
                if (processNameField.getText().equals("新进程"))
                    processNameField.setText(newVal.getValue().replace(".e", ""));
            }
        });

        // 自动选中第一个可执行文件
        TreeItem<String> firstExec = findFirstExecutable(rootItem);
        if (firstExec != null) {
            expandPath(firstExec);
            fileTreeView.getSelectionModel().select(firstExec);
            Platform.runLater(() -> {
                int row = fileTreeView.getRow(firstExec);
                if (row >= 0) fileTreeView.scrollTo(row);
            });
        }

        // 布局构建
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
                org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess(name, priority);
                if (p != null) {
                    p.setExecutable(exec);
                    updateProcessView(); showInfo("成功", "进程已创建"); win.close();
                } else showError("失败", "无法创建进程");
            } else showError("文件错误", "无法加载可执行文件");
        });
        desktopArea.getChildren().add(win); win.toFront();
    }

    /**
     * 终止进程按钮点击事件
     */
    @FXML protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // 禁止终止IDLE进程（PID=-1）
            if (selected.getPid() == -1) { showError("非法操作", "无法结束系统闲逛进程 (IDLE)。"); return; }
            String msg = "确定要强制结束进程 [" + selected.getName() + "] (PID=" + selected.getPid() + ") 吗？\n此操作不可撤销。";
            showInternalConfirm("确认终止进程", msg, () -> {
                kernel.getProcessManager().terminateProcess(selected.getPid());
                updateProcessView(); updateMemoryView();
            });
        } else showWarning("未选择进程", "请先选择要终止的进程。");
    }

    /**
     * 创建文件按钮点击事件
     */
    @FXML protected void onCreateFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        // 处理选中项为文件时，路径取父目录
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node != null) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }
        final String finalPath = path;
        // 弹出输入框获取文件名
        showInternalInput("创建文件", "在路径 '" + finalPath + "' 下创建新文件:", "new.txt", (name) -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView(); showInfo("文件创建成功", "文件 '" + name + "' 创建成功。");
                } catch (Exception e) { showError("文件创建失败", e.getMessage()); }
            }
        });
    }

    /**
     * 停止系统按钮点击事件
     */
    @FXML protected void onStopSystemClick() {
        if (Kernel.getInstance().getScheduler() != null) Kernel.getInstance().getScheduler().stop();
        startSystemBtn.setDisable(false);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(true);
        updateControlButtonsState(false);
        showInfo("系统已暂停", "CPU 调度已停止。");
    }

    /**
     * 创建目录按钮点击事件
     */
    @FXML protected void onCreateDirectoryClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        // 处理选中项为文件时，路径取父目录
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            if (kernel.getFileSystemManager().getFileByPath(path) != null) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }
        final String finalPath = path;
        // 弹出输入框获取目录名
        showInternalInput("创建目录", "在路径 '" + finalPath + "' 下创建新目录:", "NewFolder", (name) -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createDirectory(finalPath, name);
                    updateFileSystemView(); showInfo("目录创建成功", "目录 '" + name + "' 创建成功。");
                } catch (Exception e) { showError("目录创建失败", e.getMessage()); }
            }
        });
    }



    /**
     * [事件处理] 点击 "删除" 按钮 (修复版：支持多选删除)
     */
    @FXML
    protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;

        // 1. 获取所有选中的节点
        var selectedItems = fileSystemTreeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            showWarning("未选择", "请先选择要删除的文件或目录。");
            return;
        }

        // 2. 预处理：过滤掉根目录，并收集所有要删除的路径
        // 使用 ArrayList 拷贝一份路径，避免在删除过程中因树结构变化导致列表并发修改异常
        List<String> pathsToDelete = new ArrayList<>();
        boolean containsRoot = false;

        for (TreeItem<String> item : selectedItems) {
            if (item == null || item.getParent() == null) {
                containsRoot = true; // 标记包含了根目录
                continue;
            }
            pathsToDelete.add(buildPathFromTree(item));
        }

        if (pathsToDelete.isEmpty()) {
            if (containsRoot) showError("无法删除", "根目录不可删除。");
            return;
        }

        // 3. 构建提示信息
        String msg;
        if (pathsToDelete.size() == 1) {
            String path = pathsToDelete.get(0);
            Object node = kernel.getFileSystemManager().getObjectByPath(path);
            String itemType = (node instanceof Directory) ? "目录" : "文件";
            // 从路径中提取文件名
            String itemName = path.substring(path.lastIndexOf('/') + 1);
            msg = "您确定要删除 " + itemType + " '" + itemName + "' 吗？\n路径: " + path + "\n\n⚠ 此操作不可撤销！";
        } else {
            msg = "您确定要删除选中的 " + pathsToDelete.size() + " 个项目吗？\n\n⚠ 此操作不可撤销！";
        }

        // 4. 确认删除
        showInternalConfirm("确认删除", msg, () -> {
            int successCount = 0;
            // 遍历路径执行删除
            // 建议：如果是一个文件夹和它里面的文件同时被选中，先删文件夹会导致文件路径失效
            // 虽然 deletePath 有容错，但按路径长度排序（长的先删）或直接忽略错误通常更稳健
            // 这里采用简单的遍历尝试删除
            for (String path : pathsToDelete) {
                try {
                    // 如果对象已不存在（可能因为父目录刚被删除了），deletePath 会返回 false，无需额外处理
                    if (kernel.getFileSystemManager().deletePath(path)) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // 忽略单个删除失败，继续下一个
                }
            }

            if (successCount > 0) {
                updateFileSystemView();
                showInfo("删除成功", "已成功删除 " + successCount + " 个项目。");
            } else {
                showError("删除失败", "未能删除选中目标，可能已被移除或受保护。");
            }
        });
    }




    /**
     * 内存整理按钮点击事件
     */
    @FXML protected void onDefragmentClick() {
        kernel.getMemoryManager().defragment();
        updateMemoryView(); showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    /**
     * 复制文件按钮点击事件
     */
    @FXML protected void onCopyFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            String path = buildPathFromTree(selectedItem);
            clipboardFile = kernel.getFileSystemManager().getObjectByPath(path);
            if (clipboardFile != null) {
                String name = (clipboardFile instanceof Directory d) ? d.getName() : ((File)clipboardFile).getName();
                showInfo("复制成功", "'" + name + "' 已复制到剪贴板。");
            } else showError("复制失败", "无法获取选中对象，路径可能无效。");
        } else showWarning("未选择", "请先选择要复制的文件或目录。");

        // 复用键盘快捷键的逻辑，保证行为一致
        handleCopyShortcut();
    }

    /**
     * 粘贴文件按钮点击事件
     */
    @FXML protected void onPasteFileClick() {
        if (clipboardFile != null) {
            TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
            String targetPath = "/";
            // 确定粘贴目标路径（选中文件则取父目录，选中目录则直接使用）
            if (selectedItem != null) {
                String currentPath = buildPathFromTree(selectedItem);
                Object targetNode = kernel.getFileSystemManager().getObjectByPath(currentPath);
                if (targetNode instanceof Directory) targetPath = currentPath;
                else {
                    if (currentPath.contains("/")) targetPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                    if (targetPath.isEmpty()) targetPath = "/";
                }
            }
            try {
                kernel.getFileSystemManager().paste(clipboardFile, targetPath);
                updateFileSystemView(); showInfo("粘贴成功", "已成功粘贴到 '" + targetPath + "'。");
            } catch (Exception e) { showError("粘贴失败", e.getMessage()); }
        } else showWarning("剪贴板为空", "剪贴板中没有可粘贴的文件或目录。");

        // 复用键盘快捷键的逻辑，保证行为一致
        handlePasteShortcut();
    }

    /**
     * 搜索文件按钮点击事件
     */
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

        // 输入时自动联想匹配文件
        searchBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) { searchBox.hide(); return; }
            if (newVal.equals(searchBox.getSelectionModel().getSelectedItem())) return;
            Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
            List<String> matches = new ArrayList<>();
            rootDir.searchByPrefix(newVal.trim(), matches, "");
            Platform.runLater(() -> {
                // 1. 如果数据有变化，则更新列表（避免无意义的刷新）
                if (!searchBox.getItems().equals(matches)) {
                    searchBox.getItems().setAll(matches);
                }

                // 2. 【关键修复】无论数据是否变化，只要有结果且没显示，就强制显示
                if (!matches.isEmpty()) {
                    if (!searchBox.isShowing()) {
                        searchBox.show();
                    }
                } else {
                    // 没有结果则隐藏
                    if (searchBox.isShowing()) {
                        searchBox.hide();
                    }
                }
            });
        });
        // 定位文件逻辑
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
        desktopArea.getChildren().add(win); win.toFront();
        Platform.runLater(searchBox::requestFocus);
    }




    /**
     * 创建文件编辑节点（文本编辑+保存按钮）
     * [修改] 新增 Ctrl+S 快捷键支持
     */
    private VBox createEditorNode(File file) {
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);

        // 加载文件内容
        if (file.getContent() != null) {
            String content = new String(file.getContent(), 0, file.getActualLength(), java.nio.charset.StandardCharsets.UTF_8);
            textArea.setText(content);
        }

        // 定义保存动作（复用逻辑）
        Runnable doSave = () -> {
            try {
                String text = textArea.getText();
                byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                file.setContent(data);
                showInfo("保存成功", "文件 '" + file.getName() + "' 已保存至模拟磁盘。");
            } catch (Exception ex) {
                showError("保存失败", "写入文件时出错: " + ex.getMessage());
            }
        };

        // 保存按钮
        Button saveBtn = new Button("保存");
        saveBtn.setOnAction(e -> doSave.run());

        // [新增] 监听 TextArea 的键盘事件，实现 Ctrl+S 保存
        textArea.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) {
                doSave.run();
                event.consume(); // 阻止事件冒泡
            }
        });

        ToolBar toolBar = new ToolBar(saveBtn);
        VBox editorRoot = new VBox(toolBar, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        return editorRoot;
    }





    /**
     * 打开选中的文件（创建编辑窗口）
     */
    private void openSelectedFile() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        String path = buildPathFromTree(selectedItem);

        // 获取文件对象并创建编辑窗口
        File file = kernel.getFileSystemManager().getFileByPath(path);
        if (file != null) {
            VBox editorRoot = createEditorNode(file);
            InternalWindow editorWin = new InternalWindow("编辑: " + file.getName(), editorRoot, 500, 400);
            double offset = openWindows.size() * 30;
            editorWin.setLayoutX(100 + offset);
            editorWin.setLayoutY(50 + offset);
            desktopArea.getChildren().add(editorWin);
            editorWin.toFront();
            addTaskBarItem(editorWin);
            openWindows.put(editorRoot, editorWin);
        }
    }

    /**
     * 更新所有视图数据
     */
    private void updateAllViews() {
        updateProcessView(); updateMemoryView(); updateDeviceView();
        updateFileSystemView(); updateOperationLogView(); updatePerformanceMetrics();
    }

    /**
     * 更新进程视图数据
     */
    private void updateProcessView() {
        // 进程列表
        processTableView.getItems().setAll(kernel.getProcessManager().getProcesses().stream().map(org.example.scau_os_simulation.process.Process::getPcb).toList());
        // 就绪队列
        readyQueueListView.getItems().setAll(kernel.getProcessManager().getReadyQueue().stream().map(p -> "PID: " + p.getPcb().getPid() + " (优先级: " + p.getPcb().getPriority() + ")").toList());
        // 阻塞队列
        blockedQueueListView.getItems().setAll(kernel.getProcessManager().getBlockedQueue().stream().map(p -> "PID: " + p.getPcb().getPid()).toList());
        // 运行中进程信息
        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        if (running != null) {
            PCB pcb = running.getPcb();
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr());
            axLabel.setText("AX: " + pcb.getAx());
            tsLabel.setText("时间片: " + kernel.getTimeSlice());
        } else runningPidLabel.setText("运行中PID: 无");
    }

    /**
     * 更新内存视图数据
     */
    private void updateMemoryView() {
        MemoryManager memoryManager = kernel.getMemoryManager();
        int totalMemory = memoryManager.getMemory().getSize();
        int usedMemory = memoryManager.getTotalUsedMemory();
        double usage = (double) usedMemory / totalMemory;
        // 内存使用率进度条
        memoryUsageBar.setProgress(usage);
        memoryInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", usedMemory, totalMemory));
        // 内存块列表
        memoryBlockTableView.getItems().setAll(memoryManager.getAllocatedBlocks());
        // 碎片率
        double fragmentationRate = memoryManager.getFragmentationRate();
        fragmentationLabel.setText(String.format("碎片率: %.2f%%", fragmentationRate * 100));
    }

    /**
     * 更新设备视图数据
     */
    private void updateDeviceView() {
        // 设备列表
        deviceTableView.getItems().setAll(kernel.getDeviceManager().getAllDevices());
        // 设备等待队列
        List<WaitRow> waitRows = new ArrayList<>();
        for (DeviceType t : DeviceType.values()) {
            for (DeviceRequest request : kernel.getDeviceManager().getWaitingQueue(t)) {
                waitRows.add(new WaitRow(t.toString(), request.getPid(), request.getExecutionTime()));
            }
        }
        waitQueueTableView.getItems().setAll(waitRows);
    }

    /**
     * 更新文件系统视图数据（树形结构+磁盘使用率）
     */
    private void updateFileSystemView() {
        // 保存展开状态和选中路径
        Set<String> expandedPaths = new HashSet<>();
        if (fileSystemTreeView.getRoot() != null) saveExpansionState(fileSystemTreeView.getRoot(), expandedPaths);
        String selectedPath = null;
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) { selectedPath = buildPathFromTree(selectedItem); expandedPaths.add(selectedPath); }

        // 重建文件树
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder"));
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        // 恢复展开状态和选中项
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
        // 更新磁盘使用率
        if (kernel.getFileSystemManager().getFileSystem() != null) {
            int total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
            int used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
            double usage = total > 0 ? (double) used / total : 0;
            if (diskUsageBar != null) diskUsageBar.setProgress(usage);
            if (diskInfoLabel != null) diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", used, total));
        }
    }

    /**
     * 保存文件树展开状态
     */
    private void saveExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        if (item.isExpanded()) expandedPaths.add(buildPathFromTree(item));
        for (TreeItem<String> child : item.getChildren()) saveExpansionState(child, expandedPaths);
    }

    /**
     * 恢复文件树展开状态
     */
    private void restoreExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        String currentPath = buildPathFromTree(item);
        if (expandedPaths.contains(currentPath)) item.setExpanded(true);
        for (TreeItem<String> child : item.getChildren()) restoreExpansionState(child, expandedPaths);
    }

    /**
     * 递归构建文件系统树形结构
     */
    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem) {
        for (Object child : parent.getChildren()) {
            if (child instanceof Directory dir) {
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                dirItem.setGraphic(createIcon("folder"));
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File f) {
                TreeItem<String> fileItem = new TreeItem<>(f.getName());
                // 根据文件后缀设置图标
                if (f.getName().endsWith(".e")) fileItem.setGraphic(createIcon("exec"));
                else if (f.getName().endsWith(".txt")) fileItem.setGraphic(createIcon("text"));
                else fileItem.setGraphic(createIcon("file"));
                parentItem.getChildren().add(fileItem);
            }
        }
    }

    /**
     * 创建树形节点图标（文件夹/可执行文件/文本文件/普通文件）
     */
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

    /**
     * 更新操作日志视图
     */
    private void updateOperationLogView() {
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        outputListView.getItems().setAll(kernel.getOutputLogs());
    }

    /**
     * 初始化性能图表
     */
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

    /**
     * 更新性能图表数据
     */
    private void updatePerformanceChart() {
        if (performanceChart != null && kernel != null) {
            performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
        }
    }

    /**
     * 更新性能监控指标（平均/峰值CPU、内存使用率）
     */
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

    /**
     * 初始化表格列与数据绑定
     */
    private void initBindings() {
        // 进程表格列绑定
        pidColumn.setCellValueFactory(cellData -> cellData.getValue().pidProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        stateColumn.setCellValueFactory(cellData -> cellData.getValue().stateProperty());
        priorityColumn.setCellValueFactory(cellData -> cellData.getValue().priorityProperty());
        memoryAddressColumn.setCellValueFactory(cellData -> cellData.getValue().memoryAddressProperty());
        memorySizeColumn.setCellValueFactory(cellData -> cellData.getValue().memorySizeProperty());
        // 内存块表格列绑定
        startAddressColumn.setCellValueFactory(cellData -> cellData.getValue().startAddressProperty());
        blockSizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        processColumn.setCellValueFactory(cellData -> {
            int pid = findProcessIdForMemoryBlock(cellData.getValue());
            return new javafx.beans.property.SimpleStringProperty(pid >= 0 ? String.valueOf(pid) : "N/A");
        });
        // 设备表格列绑定
        deviceTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType().toString()));
        deviceInUseColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isBusy() ? "是" : "否"));
        devicePidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCurrentUserPid()));
        deviceRemainColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getRemainingTime()));
        // 设备等待队列表格列绑定
        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device()));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid()));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time()));
    }

    /**
     * 根据内存块查找所属进程PID
     */
    private int findProcessIdForMemoryBlock(MemoryBlock block) {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize()) {
                return p.getPcb().getPid();
            }
        }
        return -1;
    }

    /**
     * 根据树形节点构建文件路径
     */
    private String buildPathFromTree(TreeItem<String> item) {
        StringBuilder path = new StringBuilder();
        while (item != null && item.getParent() != null) {
            path.insert(0, "/" + item.getValue());
            item = item.getParent();
        }
        return !path.isEmpty() ? path.toString() : "/";
    }

    /**
     * 根据路径查找树形节点
     */
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

    /**
     * 构建文件/目录的完整路径（通用查找方法）
     */
    private String buildFullPath(Object fileObj) {
        return findObjectPath(fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
    }

    /**
     * 递归查找文件/目录的路径
     */
    private String findObjectPath(Object target, Directory current, String currentPath) {
        // 匹配当前目录
        if (current == target) return currentPath.isEmpty() ? "/" : currentPath;

        // 遍历子节点
        for (Object child : current.getChildren()) {
            String childName = (child instanceof Directory d) ? d.getName() : ((File)child).getName();
            String childPath = currentPath + (currentPath.equals("/") ? "" : "/") + childName;

            // 匹配子节点
            if (child == target) return childPath;

            // 递归子目录
            if (child instanceof Directory subDir) {
                String result = findObjectPath(target, subDir, childPath);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * 在文件树中选中指定文件/目录
     */
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

    // 弹窗快捷方法（信息/警告/错误）
    private void showInfo(String title, String message) { showInternalAlert("info", title, message); }
    private void showWarning(String title, String message) { showInternalAlert("warning", title, message); }
    private void showError(String title, String message) { showInternalAlert("error", title, message); }

    /**
     * 设备等待队列行数据（Record类型）
     */
    private record WaitRow(String device, int pid, int time) {}

    /**
     * 打开终端按钮点击事件
     */
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

    /**
     * 显示内部弹窗（信息/警告/错误）
     */
    private void showInternalAlert(String type, String title, String content) {
        VBox root = new VBox(10); root.setPadding(new Insets(15)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content); msgLabel.setWrapText(true); msgLabel.setMaxWidth(250);
        Button okBtn = new Button("确定");
        root.getChildren().addAll(msgLabel, okBtn);
        InternalWindow win = new InternalWindow(title, root, 300, 150);
        double x = (desktopArea.getWidth() - 300) / 2; double y = (desktopArea.getHeight() - 150) / 2;
        win.setLayoutX(x); win.setLayoutY(y);
        okBtn.setOnAction(e -> win.close());
        desktopArea.getChildren().add(win);
    }

    /**
     * 显示确认弹窗
     */
    private void showInternalConfirm(String title, String content, Runnable onConfirm) {
        VBox root = new VBox(20); root.setPadding(new Insets(20)); root.setAlignment(Pos.CENTER);
        Label msgLabel = new Label(content); msgLabel.setWrapText(true); msgLabel.setMaxWidth(300); msgLabel.setStyle("-fx-font-size: 14px;");
        HBox btnBox = new HBox(15); btnBox.setAlignment(Pos.CENTER);
        Button yesBtn = new Button("确定"); yesBtn.getStyleClass().add("button");
        yesBtn.setStyle("-fx-background-color: #da1e28; -fx-text-fill: white;"); // 补全截断的样式
        Button noBtn = new Button("取消");
        btnBox.getChildren().addAll(yesBtn, noBtn);
        root.getChildren().addAll(msgLabel, btnBox);
        InternalWindow win = new InternalWindow(title, root, 350, 180);
        win.setLayoutX((desktopArea.getWidth() - 350) / 2); win.setLayoutY((desktopArea.getHeight() - 180) / 2);
        yesBtn.setOnAction(e -> { win.close(); if (onConfirm != null) onConfirm.run(); });
        noBtn.setOnAction(e -> win.close());
        desktopArea.getChildren().add(win); win.toFront();
    }

    /**
     * 显示输入弹窗
     */
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
        desktopArea.getChildren().add(win); win.toFront();
    }
}
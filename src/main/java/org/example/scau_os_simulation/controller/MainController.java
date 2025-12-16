package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.performance.PerformanceChartFX;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.TextEditorWindow;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;


public class MainController implements Initializable
{
    // --- 桌面环境组件 ---
    @FXML
    private StackPane rootStackPane;
    @FXML
    private FlowPane desktopArea; // 确认 FXML 中也是 FlowPane
    @FXML
    private HBox taskBarApps;
    @FXML
    private Label systemClockLabel;
    @FXML
    private Button startMenuBtn;

    // --- 隐藏的视图容器 ---
    @FXML
    private VBox processViewRoot;
    @FXML
    private VBox memoryViewRoot;
    @FXML
    private VBox fileSystemViewRoot;
    @FXML
    private AnchorPane deviceViewRoot;
    @FXML
    private VBox performanceViewRoot;
    @FXML
    private StackPane performanceChartContainer;

    // --- 控件变量 ---
    @FXML
    private Button startSystemBtn, stopSystemBtn, createProcessBtn, terminateProcessBtn;
    @FXML
    private Button undoBtn, redoBtn, defragmentBtn;
    @FXML
    private Button createFileBtn, createDirectoryBtn, deleteFileBtn, copyFileBtn, pasteFileBtn, searchFileBtn;
    @FXML
    private Button openTerminalBtn;

    // --- 数据显示组件 ---
    @FXML
    private TableView<PCB> processTableView;
    @FXML
    private TableColumn<PCB, Number> pidColumn, priorityColumn, memoryAddressColumn, memorySizeColumn;
    @FXML
    private TableColumn<PCB, String> nameColumn, stateColumn;
    @FXML
    private Label runningPidLabel, irLabel, axLabel, tsLabel;
    @FXML
    private ListView<String> outputListView, readyQueueListView, blockedQueueListView, operationLogListView;
    @FXML
    private ProgressBar memoryUsageBar, diskUsageBar, cpuUtilizationBar, systemLoadBar;
    @FXML
    private Label memoryInfoLabel, fragmentationLabel, diskInfoLabel;
    @FXML
    private Label cpuUtilizationLabel, systemLoadLabel, avgCpuLabel, avgMemoryLabel, peakCpuLabel, peakMemoryLabel;
    @FXML
    private TableView<MemoryBlock> memoryBlockTableView;
    @FXML
    private TableColumn<MemoryBlock, Number> startAddressColumn, blockSizeColumn;
    @FXML
    private TableColumn<MemoryBlock, String> processColumn;
    @FXML
    private TreeView<String> fileSystemTreeView;
    @FXML
    private TableView<Device> deviceTableView;
    @FXML
    private TableColumn<Device, String> deviceTypeColumn, deviceInUseColumn;
    @FXML
    private TableColumn<Device, Number> devicePidColumn, deviceRemainColumn;
    @FXML
    private TableView<WaitRow> waitQueueTableView;
    @FXML
    private TableColumn<WaitRow, String> waitDeviceColumn;
    @FXML
    private TableColumn<WaitRow, Number> waitPidColumn, waitTimeColumn;

    // --- 后端核心引用 ---
    private Kernel kernel;
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartFX performanceChart;
    private Object clipboardFile;

    // --- 窗口管理 ---
    private Map<Node, InternalWindow> openWindows = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 初始化核心
        kernel = Kernel.getInstance();
        initBindings();
        initializePerformanceChart();

        // 2. 初始化桌面环境
        // 【修改】移除了 initWallpaperWithCSS()，因为 FXML 中 stackPane 已经设置了 styleClass="desktop-background"
        // 且 CSS 文件中已经定义了背景图

        // 1. 裁剪逻辑 (防止窗口拖出导致父容器滚动)
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(desktopArea.widthProperty());
        clip.heightProperty().bind(desktopArea.heightProperty());
        desktopArea.setClip(clip);

        // 2. 强制 desktopArea 不受内部撑大影响
        desktopArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        initDesktop();
        initStartMenu();
        startClock();

        // 3. 初始刷新
        updateAllViews();
        updateFileSystemView();
        setupFileSystemEvents();
        updateControlButtonsState(false);

        // 【新增】全局快捷键 Ctrl+F 唤起搜索
        rootStackPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                onSearchFileClick(); // 调用搜索方法
                e.consume(); // 吞掉事件，防止传播
            }
        });

        // 4. 定时任务
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() ->
        {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
            updateOperationLogView();
            updatePerformanceChart();
            updatePerformanceMetrics();
        }), 0, 500, TimeUnit.MILLISECONDS);
    }


    // --- [新增辅助方法] 查找树中第一个以 .e 结尾的节点 ---
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

    // --- [新增辅助方法] 递归展开节点的所有父节点 ---
    private void expandPath(TreeItem<String> item) {
        TreeItem<String> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
    }



    private void initDesktop()
    {
        // 参数2 必须与你在 resources/icons 文件夹里放的文件名完全一致
        addDesktopIcon("进程管理", "process.png", processViewRoot, 800, 600);
        addDesktopIcon("内存管理", "memory.png", memoryViewRoot, 700, 500);
        addDesktopIcon("资源管理器", "computer.png", fileSystemViewRoot, 800, 600);
        addDesktopIcon("设备管理", "device.png", deviceViewRoot, 600, 400);
        addDesktopIcon("性能监视器", "monitor.png", performanceViewRoot, 800, 500);
        // 终端
        addDesktopIcon("终端", "terminal.png", null, 600, 400);
    }

    private void startClock()
    {
        Thread clockThread = new Thread(() ->
        {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            while (true)
            {
                try
                {
                    String timeText = sdf.format(new Date());
                    Platform.runLater(() ->
                    {
                        if (systemClockLabel != null) systemClockLabel.setText(timeText);
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e)
                {
                    break;
                }
            }
        });
        clockThread.setDaemon(true);
        clockThread.start();
    }

    @FXML
    protected void onDesktopClick()
    {
        // 点击桌面空白处
    }

    /**
     * 在桌面上创建一个图标 (支持图片，带容错处理)
     */
    private void addDesktopIcon(String name, String iconFileName, Node contentNode, double winWidth, double winHeight)
    {
        VBox iconBox = new VBox(5);
        iconBox.setAlignment(Pos.TOP_CENTER);
        iconBox.getStyleClass().add("desktop-icon");

        Node graphicNode;
        try
        {
            // 注意路径是否正确，对应 resources 下的目录结构
            String iconPath = "/org/example/scau_os_simulation/icons/" + iconFileName;
            if (getClass().getResource(iconPath) != null)
            {
                Image image = new Image(getClass().getResourceAsStream(iconPath));
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.getStyleClass().add("icon-image-view");
                graphicNode = imageView;
            } else
            {
                throw new RuntimeException("Icon not found");
            }
        } catch (Exception e)
        {
            Label fallbackLabel = new Label();
            fallbackLabel.getStyleClass().add("icon-label-fallback"); // CSS 控制大小
            switch (name)
            {
                case "进程管理":
                    fallbackLabel.setText("⚙️");
                    break;
                case "内存管理":
                    fallbackLabel.setText("🧠");
                    break;
                case "资源管理器":
                    fallbackLabel.setText("📁");
                    break;
                case "设备管理":
                    fallbackLabel.setText("🖨️");
                    break;
                case "性能监视器":
                    fallbackLabel.setText("📊");
                    break;
                case "终端":
                    fallbackLabel.setText("💻");
                    break;
                default:
                    fallbackLabel.setText("📄");
            }
            graphicNode = fallbackLabel;
        }

        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("icon-label"); // CSS 控制阴影和颜色

        iconBox.getChildren().addAll(graphicNode, nameLbl);

        // 双击事件
        iconBox.setOnMouseClicked(e ->
        {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY)
            {
                if ("终端".equals(name)) onOpenTerminalClick();
                else openWindow(name, contentNode, winWidth, winHeight);
            }
        });

        // 右键菜单
        ContextMenu menu = new ContextMenu();
        MenuItem openItem = new MenuItem("打开");
        openItem.setOnAction(ev ->
        {
            if ("终端".equals(name)) onOpenTerminalClick();
            else openWindow(name, contentNode, winWidth, winHeight);
        });
        menu.getItems().add(openItem);
        iconBox.setOnContextMenuRequested(ev -> menu.show(iconBox, ev.getScreenX(), ev.getScreenY()));

        // 【修改】由于 desktopArea 是 FlowPane，直接 add 即可，它会自动流式排版
        // 删除了原本用来计算 row/col 和 AnchorPane.setTopAnchor 的代码
        desktopArea.getChildren().add(iconBox);
    }

    private void openWindow(String title, Node content, double w, double h)
    {
        if (content == null) return;
        if (openWindows.containsKey(content))
        {
            InternalWindow existing = openWindows.get(content);
            existing.toFront();
            if (!desktopArea.getChildren().contains(existing))
            {
                desktopArea.getChildren().add(existing);
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
        desktopArea.getChildren().add(window);
        addTaskBarItem(window);
    }

    private void addTaskBarItem(InternalWindow window)
    {
        Button taskBtn = new Button(window.title);
        taskBtn.getStyleClass().add("task-app-btn");
        taskBtn.setOnAction(e ->
        {
            if (!window.isVisible())
            {
                window.setVisible(true);
                window.toFront();
            } else
            {
                window.toFront();
            }
        });
        window.onClosed = () -> taskBarApps.getChildren().remove(taskBtn);
        taskBarApps.getChildren().add(taskBtn);
    }



    // --- 内部类：自定义窗口 (旗舰版 Pro：支持最小化、全屏/还原、边缘缩放) ---
    class InternalWindow extends VBox {
        // 窗口拖拽/缩放相关的坐标状态
        private double xOffset = 0;
        private double yOffset = 0;
        private double initX, initY, initW, initH;
        private boolean isDraggingWindow = false;

        // 全屏/还原相关的状态
        private boolean isMaximized = false;
        private double restoreX, restoreY, restoreW, restoreH; // 用于存储还原时的位置和尺寸

        // 常量定义
        private static final double RESIZE_MARGIN = 10.0;
        private static final double MIN_WIDTH = 200;
        private static final double MIN_HEIGHT = 150;

        String title;
        Runnable onClosed;

        // UI 组件引用 (为了后续修改图标)
        private final Button maxBtn;

        // 当前的缩放模式
        private ResizeMode currentResizeMode = ResizeMode.NONE;

        private enum ResizeMode {
            NONE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
        }

        public InternalWindow(String title, Node content, double w, double h) {
            this.setManaged(false); // 关键：不受 FlowPane 布局管控，实现绝对定位
            this.title = title;

            // 初始化尺寸
            this.resize(w, h);
            this.setPrefSize(w, h);

            this.getStyleClass().add("window-frame");

            // --- 1. 标题栏构建 ---
            HBox titleBar = new HBox();
            titleBar.getStyleClass().add("window-title-bar");
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setMinHeight(32);
            titleBar.setPrefHeight(32);

            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("window-title");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // [按钮组样式]
            String btnStyle = "-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 0; -fx-alignment: center; -fx-background-color: transparent;";
            String hoverStyle = "-fx-background-color: #e0e0e0;";

            // A. 最小化按钮
            Button minBtn = new Button("—");
            minBtn.getStyleClass().add("window-close-btn");
            minBtn.setStyle(btnStyle + " -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;");
            minBtn.setPrefSize(30, 20);
            minBtn.setOnAction(e -> this.setVisible(false));
            minBtn.setOnMouseEntered(e -> minBtn.setStyle(btnStyle + hoverStyle + " -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;"));
            minBtn.setOnMouseExited(e -> minBtn.setStyle(btnStyle + " -fx-alignment: bottom-center; -fx-padding: 0 0 3 0;"));

            // B. [新增] 全屏/还原按钮
            maxBtn = new Button("□"); // 初始为全屏图标
            maxBtn.getStyleClass().add("window-close-btn");
            maxBtn.setStyle(btnStyle + "-fx-font-size: 12px;");
            maxBtn.setPrefSize(30, 20);
            maxBtn.setOnAction(e -> toggleMaximize());
            maxBtn.setOnMouseEntered(e -> maxBtn.setStyle(btnStyle + hoverStyle + "-fx-font-size: 12px;"));
            maxBtn.setOnMouseExited(e -> maxBtn.setStyle(btnStyle + "-fx-font-size: 12px;"));

            // C. 关闭按钮
            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("window-close-btn");
            closeBtn.setStyle(btnStyle + "-fx-font-size: 12px;");
            closeBtn.setPrefSize(30, 20);
            closeBtn.setOnAction(e -> close());
            closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(btnStyle + "-fx-font-size: 12px; -fx-background-color: #e81123; -fx-text-fill: white;"));
            closeBtn.setOnMouseExited(e -> closeBtn.setStyle(btnStyle + "-fx-font-size: 12px; -fx-text-fill: black;"));

            titleBar.getChildren().addAll(titleLbl, spacer, minBtn, maxBtn, closeBtn);

            // --- 2. 内容区域 ---
            VBox contentContainer = new VBox(content);
            contentContainer.setPadding(new Insets(5));
            VBox.setVgrow(contentContainer, Priority.ALWAYS);

            // 确保内容自适应
            if (content instanceof Region) ((Region) content).setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            if (content instanceof Control) ((Control) content).setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            VBox.setVgrow(content, Priority.ALWAYS);

            this.getChildren().addAll(titleBar, contentContainer);

            // --- 3. 事件处理 (EventFilter 拦截机制) ---

            // A. 鼠标移动：更新光标
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_MOVED, e -> {
                // 如果已全屏，不显示缩放光标，始终为默认
                if (isMaximized) {
                    this.setCursor(Cursor.DEFAULT);
                    return;
                }
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                setCursorBasedOnMode(mode);
            });

            // B. 鼠标移出：恢复默认
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> {
                if (!e.isPrimaryButtonDown()) this.setCursor(Cursor.DEFAULT);
            });

            // C. 鼠标按下：判定操作模式
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
                this.toFront();

                // 1. 双击标题栏 -> 切换全屏
                boolean isHeader = e.getY() < 32;
                // 排除按钮区域（假设右侧 90px 是按钮区）
                boolean isButtonArea = e.getX() > (this.getWidth() - 90) && isHeader;

                if (isHeader && !isButtonArea && e.getClickCount() == 2) {
                    toggleMaximize();
                    e.consume(); // 拦截，防止触发其他点击
                    return;
                }

                // 如果是全屏模式，禁止移动和缩放，直接返回
                if (isMaximized) return;

                // 2. 检测边缘缩放
                ResizeMode mode = getResizeMode(e.getX(), e.getY());
                if (mode != ResizeMode.NONE) {
                    currentResizeMode = mode;
                    initX = this.getLayoutX();
                    initY = this.getLayoutY();
                    initW = this.getWidth();
                    initH = this.getHeight();
                    xOffset = e.getSceneX();
                    yOffset = e.getSceneY();
                    e.consume(); // 拦截事件，防止子组件（如 TextArea）抢夺焦点
                    return;
                }

                // 3. 检测标题栏移动
                if (isHeader && !isButtonArea) {
                    currentResizeMode = ResizeMode.NONE;
                    isDraggingWindow = true;
                    initX = this.getLayoutX();
                    initY = this.getLayoutY();
                    xOffset = e.getSceneX();
                    yOffset = e.getSceneY();
                    e.consume(); // 拦截拖拽
                }
            });

            // D. 鼠标拖拽：执行操作
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_DRAGGED, e -> {
                // 全屏禁止拖拽
                if (isMaximized) return;

                if (currentResizeMode != ResizeMode.NONE) {
                    handleResize(e);
                    e.consume();
                } else if (isDraggingWindow) {
                    double deltaX = e.getSceneX() - xOffset;
                    double deltaY = e.getSceneY() - yOffset;
                    this.setLayoutX(initX + deltaX);
                    this.setLayoutY(initY + deltaY);
                    e.consume();
                }
            });

            // E. 鼠标释放：重置
            this.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> {
                isDraggingWindow = false;
                currentResizeMode = ResizeMode.NONE;
            });

            // 初始刷新布局
            Platform.runLater(() -> {
                this.requestLayout();
                this.applyCss();
            });
        }

        /**
         * 切换全屏/还原状态
         */
        private void toggleMaximize() {
            if (getParent() == null) return;
            // 获取父容器（Desktop Area）的尺寸
            // 注意：getParent() 返回的是 Node，需转为 Region 获取准确宽高
            Region parent = (Region) getParent();
            double parentW = parent.getWidth();
            double parentH = parent.getHeight();

            if (isMaximized) {
                // --- 执行还原 ---
                this.setLayoutX(restoreX);
                this.setLayoutY(restoreY);
                this.setPrefSize(restoreW, restoreH);
                this.resize(restoreW, restoreH); // 强制生效

                maxBtn.setText("□"); // 变回全屏图标
                isMaximized = false;

                // 还原圆角效果（全屏时通常直角，还原时圆角，可选）
                this.setStyle("-fx-background-radius: 5; -fx-border-radius: 5;");
            } else {
                // --- 执行全屏 ---
                // 1. 记录当前状态以便还原
                restoreX = this.getLayoutX();
                restoreY = this.getLayoutY();
                restoreW = this.getWidth();
                restoreH = this.getHeight();

                // 2. 设置全屏位置和尺寸
                // 考虑父容器可能有 Padding，这里简单设为 0,0 填满
                this.setLayoutX(0);
                this.setLayoutY(0);
                this.setPrefSize(parentW, parentH);
                this.resize(parentW, parentH); // 强制生效

                maxBtn.setText("❐"); // 变为还原图标 (Unicode 复制重叠框)
                isMaximized = true;

                // 全屏时移除圆角，看起来更沉浸
                this.setStyle("-fx-background-radius: 0; -fx-border-radius: 0;");
            }
            // 强制重新布局
            this.requestLayout();
        }

        // --- 辅助逻辑 (缩放计算) ---
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

            this.resize(newW, newH);
            this.setPrefSize(newW, newH);
            this.setLayoutX(newX);
            this.setLayoutY(newY);
            this.layout();
        }

        private ResizeMode getResizeMode(double mouseX, double mouseY) {
            boolean left = mouseX < RESIZE_MARGIN;
            boolean right = mouseX > this.getWidth() - RESIZE_MARGIN;
            boolean top = mouseY < RESIZE_MARGIN;
            boolean bottom = mouseY > this.getHeight() - RESIZE_MARGIN;

            if (left && top) return ResizeMode.TOP_LEFT;
            if (right && top) return ResizeMode.TOP_RIGHT;
            if (left && bottom) return ResizeMode.BOTTOM_LEFT;
            if (right && bottom) return ResizeMode.BOTTOM_RIGHT;
            if (top) return ResizeMode.TOP;
            if (bottom) return ResizeMode.BOTTOM;
            if (left) return ResizeMode.LEFT;
            if (right) return ResizeMode.RIGHT;
            return ResizeMode.NONE;
        }

        private boolean isLeft(ResizeMode mode) { return mode == ResizeMode.LEFT || mode == ResizeMode.TOP_LEFT || mode == ResizeMode.BOTTOM_LEFT; }
        private boolean isRight(ResizeMode mode) { return mode == ResizeMode.RIGHT || mode == ResizeMode.TOP_RIGHT || mode == ResizeMode.BOTTOM_RIGHT; }
        private boolean isTop(ResizeMode mode) { return mode == ResizeMode.TOP || mode == ResizeMode.TOP_LEFT || mode == ResizeMode.TOP_RIGHT; }
        private boolean isBottom(ResizeMode mode) { return mode == ResizeMode.BOTTOM || mode == ResizeMode.BOTTOM_LEFT || mode == ResizeMode.BOTTOM_RIGHT; }

        private void setCursorBasedOnMode(ResizeMode mode) {
            switch (mode) {
                case TOP: case BOTTOM: this.setCursor(Cursor.V_RESIZE); break;
                case LEFT: case RIGHT: this.setCursor(Cursor.H_RESIZE); break;
                case TOP_LEFT: case BOTTOM_RIGHT: this.setCursor(Cursor.NW_RESIZE); break;
                case TOP_RIGHT: case BOTTOM_LEFT: this.setCursor(Cursor.NE_RESIZE); break;
                default: this.setCursor(Cursor.DEFAULT);
            }
        }

        public void close() {
            this.setVisible(false);
            if (onClosed != null) onClosed.run();
            if (getParent() instanceof Pane) ((Pane) getParent()).getChildren().remove(this);
            openWindows.values().remove(this);
        }
    }



    private void initStartMenu()
    {
        ContextMenu startMenu = new ContextMenu();
        startMenu.getStyleClass().add("start-menu"); // 【修改】使用 CSS 类

        MenuItem itemHelp = new MenuItem("❓  关于 / 帮助");
        MenuItem itemTerminal = new MenuItem("💻  终端");
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem itemShutdown = new MenuItem("🔴  关闭系统");

        itemHelp.setOnAction(e -> showAboutWindow());
        itemTerminal.setOnAction(e -> onOpenTerminalClick());
        itemShutdown.setOnAction(e ->
        {
            if (kernel != null && kernel.getScheduler() != null) kernel.getScheduler().stop();
            Platform.exit();
            System.exit(0);
        });

        startMenu.getItems().addAll(itemHelp, itemTerminal, separator, itemShutdown);

        startMenuBtn.setOnAction(e ->
        {
            if (startMenu.isShowing()) startMenu.hide();
            else startMenu.show(startMenuBtn, Side.TOP, 0, 0);
        });
    }

    private void showAboutWindow()
    {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        // 【修改】移除所有 setStyle，改用 CSS 类 (在 Desktop.css 中定义)
        Label title = new Label("SCAU OS Simulation");
        title.getStyleClass().add("about-title");

        Label version = new Label("版本: v1.0.0 Alpha");
        version.getStyleClass().add("about-version");

        Label desc = new Label("这是一个基于 JavaFX 开发的操作系统模拟器。\n包含进程管理、内存分配、文件系统及设备管理等演示。\n开发人：陈奕彬、邓俊源、宋文理");
        desc.setWrapText(true);
        desc.setMaxWidth(350);
        desc.setAlignment(Pos.CENTER);

        Label author = new Label("© 2024 SCAU OS Team");
        author.getStyleClass().add("about-author");

        Button closeBtn = new Button("确定");
        content.getChildren().addAll(title, version, desc, new Separator(), author, closeBtn);

        InternalWindow aboutWin = new InternalWindow("关于系统", content, 450, 400);

        // 计算居中
        double x = (desktopArea.getWidth() - 450) / 2;
        double y = (desktopArea.getHeight() - 400) / 2;
        aboutWin.setLayoutX(x > 0 ? x : 100);
        aboutWin.setLayoutY(y > 0 ? y : 100);

        closeBtn.setOnAction(e -> aboutWin.close());

        desktopArea.getChildren().add(aboutWin);
        aboutWin.toFront();
        addTaskBarItem(aboutWin);
    }

    // --- 业务逻辑方法 (保持不变，省略具体实现以节省篇幅) ---
    // 请确保以下方法在你的代码中保留原样
    private void setupFileSystemEvents()
    {
        fileSystemTreeView.setOnMouseClicked(event ->
        {
            if (Kernel.getInstance().getScheduler() == null || !Kernel.getInstance().getScheduler().isRunning())
            {
                if (event.getClickCount() == 2) showWarning("系统未启动", "请先点击 [▶ 启动系统] 按钮。");
                return;
            }
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) openSelectedFile();
        });
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem editItem = new javafx.scene.control.MenuItem("编辑 / 查看");
        javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("删除");
        editItem.setOnAction(e -> openSelectedFile());
        deleteItem.setOnAction(e -> onDeleteClick());
        contextMenu.getItems().addAll(editItem, deleteItem);
        fileSystemTreeView.setContextMenu(contextMenu);
        contextMenu.setOnShowing(e ->
        {
            boolean isRunning = Kernel.getInstance().getScheduler() != null && Kernel.getInstance().getScheduler().isRunning();
            for (javafx.scene.control.MenuItem item : contextMenu.getItems()) item.setDisable(!isRunning);
        });
    }

    private void updateControlButtonsState(boolean isRunning)
    {
        boolean disable = !isRunning;
        if (createProcessBtn != null) createProcessBtn.setDisable(disable);
        if (terminateProcessBtn != null) terminateProcessBtn.setDisable(disable);
        if (defragmentBtn != null) defragmentBtn.setDisable(disable);
        if (undoBtn != null) undoBtn.setDisable(disable);
        if (redoBtn != null) redoBtn.setDisable(disable);
        if (createFileBtn != null) createFileBtn.setDisable(disable);
        if (createDirectoryBtn != null) createDirectoryBtn.setDisable(disable);
        if (deleteFileBtn != null) deleteFileBtn.setDisable(disable);
        if (openTerminalBtn != null) openTerminalBtn.setDisable(disable);
    }

    // --- FXML 事件处理 ---
    @FXML
    protected void onStartSystemClick()
    {
        Kernel.getInstance().start();
        startSystemBtn.setDisable(true);
        if (stopSystemBtn != null) stopSystemBtn.setDisable(false);
        updateControlButtonsState(true);
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }


    @FXML
    protected void onCreateProcessClick()
    {
        // 1. 准备表单控件
        TextField processNameField = new TextField("新进程");
        // 让文本框也能横向拉伸
        processNameField.setMaxWidth(Double.MAX_VALUE);

        ComboBox<Integer> priorityBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1);

        TextField execPathField = new TextField();
        execPathField.setPromptText("请选择可执行文件...");
        execPathField.setMaxWidth(Double.MAX_VALUE); // 横向拉伸

        // 文件树
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(150);

        // 【关键修复 1】允许 TreeView 填满所有可用空间
        fileTreeView.setMaxWidth(Double.MAX_VALUE);
        fileTreeView.setMaxHeight(Double.MAX_VALUE);

        // 绑定选择事件
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) ->
        {
            if (newVal != null && newVal.getValue().endsWith(".e"))
            {
                execPathField.setText(buildPathFromTree(newVal));
                if (processNameField.getText().equals("新进程"))
                    processNameField.setText(newVal.getValue().replace(".e", ""));
            }
        });

        // 自动寻找并选中第一个 .e 文件
        TreeItem<String> firstExec = findFirstExecutable(rootItem);
        if (firstExec != null) {
            expandPath(firstExec);
            fileTreeView.getSelectionModel().select(firstExec);
            Platform.runLater(() -> {
                int row = fileTreeView.getRow(firstExec);
                if (row >= 0) fileTreeView.scrollTo(row);
            });
        }

        // 2. 布局 (GridPane)
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        // 添加组件
        grid.add(new Label("进程名称:"), 0, 0);
        grid.add(processNameField, 1, 0);

        grid.add(new Label("优先级:"), 0, 1);
        grid.add(priorityBox, 1, 1);

        grid.add(new Label("文件路径:"), 0, 2);
        grid.add(execPathField, 1, 2);

        Label selectLabel = new Label("选择文件:");
        selectLabel.setAlignment(Pos.TOP_LEFT); // 让标签靠上对齐
        grid.add(selectLabel, 0, 3);
        grid.add(fileTreeView, 1, 3);

        // 【关键修复 2】设置 GridPane 的列约束，让第2列(索引1)占据剩余宽度
        ColumnConstraints col1 = new ColumnConstraints(); // 第1列自适应
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS); // 第2列总是抢占水平空间
        grid.getColumnConstraints().addAll(col1, col2);

        // 【关键修复 3】设置 TreeView 在网格中总是抢占垂直和水平空间
        GridPane.setHgrow(fileTreeView, Priority.ALWAYS);
        GridPane.setVgrow(fileTreeView, Priority.ALWAYS);
        GridPane.setHgrow(processNameField, Priority.ALWAYS);
        GridPane.setHgrow(execPathField, Priority.ALWAYS);

        // 3. 按钮区域
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.setPadding(new Insets(0, 20, 10, 20));
        Button okBtn = new Button("创建");
        Button cancelBtn = new Button("取消");
        btnBox.getChildren().addAll(okBtn, cancelBtn);

        // 4. 组合内容
        VBox root = new VBox(grid, btnBox);

        // 【关键修复 4】设置 grid 在 VBox 中抢占垂直空间
        VBox.setVgrow(grid, Priority.ALWAYS);

        // 创建内部窗口
        InternalWindow win = new InternalWindow("创建新进程", root, 500, 400); //稍微调大一点默认尺寸

        // 居中显示
        double x = (desktopArea.getWidth() - 500) / 2;
        double y = (desktopArea.getHeight() - 400) / 2;
        win.setLayoutX(x > 0 ? x : 100);
        win.setLayoutY(y > 0 ? y : 100);

        // 5. 事件绑定
        cancelBtn.setOnAction(e -> win.close());

        okBtn.setOnAction(e ->
        {
            String name = processNameField.getText().trim();
            if (name.isEmpty()) name = "新进程";
            String path = execPathField.getText().trim();
            int priority = priorityBox.getValue();

            org.example.scau_os_simulation.process.Executable exec =
                    kernel.getFileSystemManager().loadExecutable(path);

            if (exec != null)
            {
                org.example.scau_os_simulation.process.Process p =
                        kernel.getProcessManager().createProcess(name, priority);

                if (p != null)
                {
                    p.setExecutable(exec);
                    kernel.getUndoManager().executeCommand(
                            new org.example.scau_os_simulation.undo.UndoManager.CreateProcessCommand(
                                    kernel.getProcessManager(), p.getPcb().getPid(), name, priority));

                    updateProcessView();
                    showInfo("成功", "进程已创建");
                    win.close();
                } else
                {
                    showError("失败", "无法创建进程");
                }
            } else
            {
                showError("文件错误", "无法加载可执行文件");
            }
        });

        // 添加到桌面
        desktopArea.getChildren().add(win);
        win.toFront();
    }


    /**
     * [事件处理] 点击 "终止进程" 按钮时触发
     */
    @FXML
    protected void onTerminateProcessClick()
    {
        // 获取表格中当前选中的 PCB 对象
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();

        if (selected != null)
        {
            // 弹出确认对话框
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("确认终止进程");
            confirmDialog.setHeaderText("确定要终止进程吗？");
            confirmDialog.setContentText("进程 PID: " + selected.getPid() + "\n进程名称: " + selected.getName() + "\n\n此操作不可撤销。");

            // 等待用户确认
            confirmDialog.showAndWait().ifPresent(response ->
            {
                if (response == javafx.scene.control.ButtonType.OK)
                {
                    // 调用内核：终止进程
                    kernel.getProcessManager().terminateProcess(selected.getPid());
                    updateProcessView(); // 刷新视图
                    showInfo("进程终止成功", "进程 '" + selected.getName() + "' (PID: " + selected.getPid() + ") 已被终止。");
                }
            });
        } else
        {
            // 如果没选进程，提示警告
            showWarning("未选择进程", "请先选择要终止的进程。");
        }
    }

    /**
     * [事件处理] 退出程序
     */
    @FXML
    protected void onExitAction()
    {
        Platform.exit(); // 优雅关闭 JavaFX 应用
    }

    /**
     * [事件处理] 关于菜单
     */
    @FXML
    protected void onAboutAction()
    {
        // 弹出"关于"信息框
        showInfo("关于", "SCAU 操作系统模拟器 v1.0\n基于 JavaFX + Picocli 开发");
    }


    /**
     * [事件处理] 点击 "创建文件" 按钮
     */
    @FXML
    protected void onCreateFileClick()
    {
        // 获取当前树中选中的节点，确定在哪个目录下创建
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/"; // 默认为根目录
        if (selectedItem != null)
        {
            path = buildPathFromTree(selectedItem);
            // 如果选中的是文件，则取其父目录路径
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node instanceof File)
            {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }

        final String finalPath = path;

        // 弹出输入框询问文件名
        showInternalInput("创建文件", "在路径 '" + finalPath + "' 下创建新文件:", "new.txt", (name) ->
        {
            // 这里是回调：当用户点击内部窗口的“确定”后执行
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView();
                    showInfo("文件创建成功", "文件 '" + name + "' 创建成功。");
                } catch (Exception e)
                {
                    showError("文件创建失败", e.getMessage());
                }
            }
        });
    }




    /**
     * [新增] 点击 "暂停系统" 按钮时触发
     */
    @FXML
    protected void onStopSystemClick()
    {
        // 1. 调用内核停止调度
        if (Kernel.getInstance().getScheduler() != null)
        {
            Kernel.getInstance().getScheduler().stop();
        }

        // 2. 更新按钮状态：启用启动按钮，禁用暂停按钮
        startSystemBtn.setDisable(false);
        if (stopSystemBtn != null)
        {
            stopSystemBtn.setDisable(true);
        }

        // 3. 禁用大部分功能按钮（模拟系统停机状态）
        updateControlButtonsState(false);

        showInfo("系统已暂停", "CPU 调度已停止。");
    }


    /**
     * [事件处理] 点击 "创建目录" 按钮
     */
    @FXML
    protected void onCreateDirectoryClick()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null)
        {
            path = buildPathFromTree(selectedItem);
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node instanceof File)
            {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }
        final String finalPath = path;

        // 【修改点 1】 默认名字改为 "NewFolder"
        showInternalInput("创建目录", "在路径 '" + finalPath + "' 下创建新目录:", "NewFolder", (name) ->
        {
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    // 【修改点 2】 关键修复：这里原来写的是 createFile，必须改为 createDirectory
                    // 注意：createDirectory 不需要 size 参数
                    kernel.getFileSystemManager().createDirectory(finalPath, name);

                    updateFileSystemView();
                    showInfo("目录创建成功", "目录 '" + name + "' 创建成功。");
                } catch (Exception e)
                {
                    showError("目录创建失败", e.getMessage());
                }
            }
        });
    }






    /**
     * [事件处理] 点击 "删除" 按钮 (修复版：使用内部弹窗)
     */
    @FXML
    protected void onDeleteClick()
    {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();

        // 校验：不能删除根目录
        if (selected == null || selected.getParent() == null)
        {
            showError("无法删除", "请选择一个文件或目录（根目录不可删除）。");
            return;
        }

        String path = buildPathFromTree(selected);
        String itemName = selected.getValue();

        // 获取对象以判断类型（用于提示文案）
        Object node = kernel.getFileSystemManager().getObjectByPath(path);
        String itemType = (node instanceof Directory) ? "目录" : "文件";

        String msg = "您确定要删除 " + itemType + " '" + itemName + "' 吗？\n" +
                "路径: " + path + "\n\n" +
                "⚠ 此操作不可撤销！";

        // 【关键修复】使用 showInternalConfirm 替代 Alert
        showInternalConfirm("确认删除", msg, () -> {
            // 这是用户点击“确定”后的回调
            boolean success = false;
            try
            {
                // 尝试删除（支持递归删除目录）
                success = kernel.getFileSystemManager().deletePath(path);
            } catch (Exception e)
            {
                success = false;
            }

            if (success)
            {
                updateFileSystemView();
                showInfo("删除成功", itemType + " '" + itemName + "' 已被移除。");
            } else
            {
                showError("删除失败", "无法删除目标。可能是系统保护文件或路径无效。");
            }
        });
    }






    /**
     * [事件处理] 内存整理 (碎片整理)
     */
    @FXML
    protected void onDefragmentClick()
    {
        // 调用内存管理器的整理算法
        kernel.getMemoryManager().defragment();
        updateMemoryView(); // 刷新内存视图
        showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    /**
     * [事件处理] 撤销操作
     */
    @FXML
    protected void onUndoClick()
    {
        kernel.getUndoManager().undo();
        updateAllViews(); // 撤销后需要刷新所有视图，因为不确定撤销了什么操作
    }

    /**
     * [事件处理] 重做操作
     */
    @FXML
    protected void onRedoClick()
    {
        kernel.getUndoManager().redo();
        updateAllViews();
    }




    /**
     * [事件处理] 复制文件或目录 (修复版)
     */
    @FXML
    protected void onCopyFileClick()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
        {
            String path = buildPathFromTree(selectedItem);
            // 【关键修复】使用 getObjectByPath 而不是 getFileByPath
            // 这样无论是文件还是目录都能被正确存入剪贴板
            clipboardFile = kernel.getFileSystemManager().getObjectByPath(path);

            if (clipboardFile != null) {
                String name = (clipboardFile instanceof Directory) ? ((Directory)clipboardFile).getName() : ((File)clipboardFile).getName();
                showInfo("复制成功", "'" + name + "' 已复制到剪贴板。");
            } else {
                showError("复制失败", "无法获取选中对象，路径可能无效。");
            }
        } else
        {
            showWarning("未选择", "请先选择要复制的文件或目录。");
        }
    }

    /**
     * [事件处理] 粘贴文件 (修复版)
     */
    @FXML
    protected void onPasteFileClick()
    {
        if (clipboardFile != null)
        {
            TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
            String targetPath = "/";

            // 确定粘贴的目标目录
            if (selectedItem != null)
            {
                String currentPath = buildPathFromTree(selectedItem);
                // 【关键修复】获取选中的真实对象（目录或文件）
                Object targetNode = kernel.getFileSystemManager().getObjectByPath(currentPath);

                // 智能判断粘贴位置：
                if (targetNode instanceof Directory) {
                    // 如果选中的是文件夹，就粘贴到这个文件夹【里面】
                    targetPath = currentPath;
                } else {
                    // 如果选中的是文件（或者未选中有效对象），就粘贴到它的【父目录】
                    // 也就是“同级”粘贴
                    if (currentPath.contains("/")) {
                        targetPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                    }
                    if (targetPath.isEmpty()) targetPath = "/";
                }
            }

            try
            {
                // 调用内核粘贴逻辑
                kernel.getFileSystemManager().paste(clipboardFile, targetPath);
                updateFileSystemView();
                showInfo("粘贴成功", "已成功粘贴到 '" + targetPath + "'。");
            } catch (Exception e)
            {
                showError("粘贴失败", e.getMessage());
            }
        } else
        {
            showWarning("剪贴板为空", "剪贴板中没有可粘贴的文件或目录。");
        }
    }




    /**
     * [事件处理] 搜索文件 (Bug修复版：解决清空后重输不显示下拉框的问题)
     */
    @FXML
    protected void onSearchFileClick()
    {
        // 1. 构建界面
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label headerLbl = new Label("输入文件名 (支持前缀匹配，不区分大小写):");

        ComboBox<String> searchBox = new ComboBox<>();
        searchBox.setEditable(true);
        searchBox.setPromptText("例如: new...");
        searchBox.setMaxWidth(Double.MAX_VALUE);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button goBtn = new Button("定位");
        Button closeBtn = new Button("关闭");
        btnBox.getChildren().addAll(goBtn, closeBtn);

        root.getChildren().addAll(headerLbl, searchBox, btnBox);

        InternalWindow win = new InternalWindow("智能搜索", root, 350, 180);
        win.setLayoutX(desktopArea.getWidth() / 2 - 175);
        win.setLayoutY(desktopArea.getHeight() / 2 - 90);

        // 2. 【核心逻辑】监听输入
        searchBox.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            // [优化] 文本为空时，立即清空列表并关闭下拉框
            if (newVal == null || newVal.trim().isEmpty()) {
                searchBox.hide();
                searchBox.getItems().clear(); // 【关键修复】必须清空旧列表，重置状态
                return;
            }

            // 避免在选择下拉项时触发重搜
            if (newVal.equals(searchBox.getSelectionModel().getSelectedItem())) {
                return;
            }

            // 执行搜索
            Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
            List<String> matches = new ArrayList<>();
            rootDir.searchByPrefix(newVal.trim(), matches, "");

            // 更新 UI
            Platform.runLater(() -> {
                // 1. 如果数据变了，更新列表
                if (!searchBox.getItems().equals(matches)) {
                    searchBox.getItems().setAll(matches);
                }

                // 2. 只要有结果，且未显示，就弹出来 (逻辑解耦，更稳健)
                if (!matches.isEmpty()) {
                    if (!searchBox.isShowing()) {
                        searchBox.show();
                    }
                } else {
                    if (searchBox.isShowing()) {
                        searchBox.hide();
                    }
                }
            });
        });

        // 3. 定位逻辑
        Runnable doLocate = () -> {
            String path = searchBox.getEditor().getText();
            if (path != null && !path.trim().isEmpty()) {
                Object target = kernel.getFileSystemManager().getObjectByPath(path);
                if (target != null) {
                    selectFileInTree(target);
                } else {
                    showWarning("未找到", "路径无效或文件不存在。");
                }
            }
        };

        // 绑定事件
        goBtn.setOnAction(e -> doLocate.run());
        searchBox.setOnAction(e -> doLocate.run());
        closeBtn.setOnAction(e -> win.close());

        // 4. 显示窗口
        desktopArea.getChildren().add(win);
        win.toFront();
        Platform.runLater(searchBox::requestFocus);
    }

    /**
     * 辅助方法：打开当前选中文件的编辑器窗口
     */
    private void openSelectedFile()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;

        String path = buildPathFromTree(selectedItem);
        Object node = kernel.getFileSystemManager().getFileByPath(path);

        if (node instanceof File)
        {
            File file = (File) node;

            // 1. 创建编辑器界面 (TextArea + Save Button)
            TextArea textArea = new TextArea();
            textArea.setWrapText(true); // 自动换行
            
            // --- [核心修复 1] 读取文件内容 ---
            if (file.getContent() != null)
            {
                // 使用 getActualLength() 只读取有效字符，避免读取到大量的空白 \0
                String content = new String(
                    file.getContent(), 
                    0, 
                    file.getActualLength(), // 确保 File.java 中有这个方法
                    java.nio.charset.StandardCharsets.UTF_8
                );
                textArea.setText(content);
            }

            Button saveBtn = new Button("保存");
            // --- [核心修复 2] 保存文件内容 ---
            saveBtn.setOnAction(e ->
            {
                try {
                    String text = textArea.getText();
                    // 将字符串转回字节数组
                    byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    // 写入文件系统
                    file.setContent(data);
                    showInfo("保存成功", "文件已保存至模拟磁盘。");
                } catch (Exception ex) {
                    showError("保存失败", "写入文件时出错: " + ex.getMessage());
                }
            });

            // 布局工具栏
            ToolBar toolBar = new ToolBar(saveBtn);
            VBox editorRoot = new VBox(toolBar, textArea);
            VBox.setVgrow(textArea, Priority.ALWAYS);

            // 2. 放入内部窗口
            InternalWindow editorWin = new InternalWindow("编辑: " + file.getName(), editorRoot, 500, 400);

            // 简单的层叠位置计算
            double offset = openWindows.size() * 30;
            editorWin.setLayoutX(100 + offset);
            editorWin.setLayoutY(50 + offset);

            desktopArea.getChildren().add(editorWin);
            editorWin.toFront();
            addTaskBarItem(editorWin); // 添加到任务栏
            openWindows.put(editorRoot, editorWin); // 记录管理
        }
    }

    /**
     * 更新所有视图的辅助方法
     */
    private void updateAllViews()
    {
        updateProcessView();
        updateMemoryView();
        updateDeviceView();
        updateFileSystemView();
        updateOperationLogView();
        updatePerformanceMetrics();
    }

    /**
     * 更新进程管理视图
     * 刷新进程列表表格、就绪队列、阻塞队列以及当前 CPU 状态信息。
     */
    private void updateProcessView()
    {
        // 1. 更新主进程表格
        // 使用 stream 将 Process 对象转为 PCB 对象列表，然后设置给 TableView
        processTableView.getItems().setAll(
                kernel.getProcessManager().getProcesses().stream()
                        .map(org.example.scau_os_simulation.process.Process::getPcb)
                        .collect(java.util.stream.Collectors.toList())
        );

        // 2. 更新就绪队列列表 (显示 PID 和 优先级)
        readyQueueListView.getItems().setAll(
                kernel.getProcessManager().getReadyQueue().stream()
                        .map(p -> "PID: " + p.getPcb().getPid() + " (优先级: " + p.getPcb().getPriority() + ")")
                        .collect(java.util.stream.Collectors.toList())
        );

        // 3. 更新阻塞队列列表
        blockedQueueListView.getItems().setAll(
                kernel.getProcessManager().getBlockedQueue().stream()
                        .map(p -> "PID: " + p.getPcb().getPid())
                        .collect(java.util.stream.Collectors.toList())
        );

        // 4. 更新顶部 CPU 状态栏
        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        if (running != null)
        {
            PCB pcb = running.getPcb();
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr()); // 当前正在执行的指令
            axLabel.setText("AX: " + pcb.getAx()); // 当前累加器的值
            tsLabel.setText("时间片: " + kernel.getTimeSlice()); // 剩余时间片
        } else
        {
            // 如果没有进程运行
            runningPidLabel.setText("运行中PID: 无");
        }
    }

    /**
     * 更新内存管理视图
     * 刷新内存进度条、内存块表格和碎片率。
     */
    private void updateMemoryView()
    {
        MemoryManager memoryManager = kernel.getMemoryManager();
        int totalMemory = memoryManager.getMemory().getSize();
        int usedMemory = memoryManager.getTotalUsedMemory();
        // 计算使用率 (0.0 - 1.0)
        double usage = (double) usedMemory / totalMemory;

        memoryUsageBar.setProgress(usage);
        memoryInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", usedMemory, totalMemory));

        // 更新内存块表格 (显示起始地址、大小、所属进程)
        memoryBlockTableView.getItems().setAll(memoryManager.getAllocatedBlocks());

        // 计算并显示碎片率
        double fragmentationRate = memoryManager.getFragmentationRate();
        fragmentationLabel.setText(String.format("碎片率: %.2f%%", fragmentationRate * 100));
    }

    /**
     * 更新设备管理视图
     * 刷新设备列表和设备等待队列。
     */
    private void updateDeviceView()
    {
        // 刷新所有设备的状态表
        deviceTableView.getItems().setAll(kernel.getDeviceManager().getAllDevices());

        // 构建等待队列数据 (将不同设备的等待队列合并显示)
        List<WaitRow> waitRows = new ArrayList<>();
        for (DeviceType t : DeviceType.values())
        {
            for (DeviceRequest request : kernel.getDeviceManager().getWaitingQueue(t))
            {
                // 创建一个临时对象 WaitRow 用于在表格中显示
                waitRows.add(new WaitRow(t.toString(), request.getPid(), request.getExecutionTime()));
            }
        }
        waitQueueTableView.getItems().setAll(waitRows);
    }

    /**
     * 更新文件系统视图 (修复版：保持选中、展开状态及滚动条位置)
     */
    private void updateFileSystemView()
    {
        // 1. 【保存状态】
        Set<String> expandedPaths = new HashSet<>();
        // 保存当前所有已展开节点的路径
        if (fileSystemTreeView.getRoot() != null) {
            saveExpansionState(fileSystemTreeView.getRoot(), expandedPaths);
        }

        String selectedPath = null;
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            selectedPath = buildPathFromTree(selectedItem);

            // 【解决问题 2】：新建文件后，父目录自动展开
            // 如果当前选中了一个目录（例如要在该目录下新建文件），我们强制将其加入“展开列表”。
            // 这样刷新后，该目录会自动展开，用户就能立刻看到刚新建的文件了。
            expandedPaths.add(selectedPath);
        }

        // 2. 【重建树结构】
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder"));

        // 递归填充树
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        // 3. 【恢复状态】
        // 恢复所有节点的展开状态
        restoreExpansionState(rootItem, expandedPaths);
        rootItem.setExpanded(true); // 根目录始终保持展开

        // 4. 【解决问题 1】：恢复选中并滚动到原位置
        if (selectedPath != null) {
            // 根据路径找到新树中对应的节点
            TreeItem<String> targetItem = findItemByPath(rootItem, selectedPath);
            if (targetItem != null) {
                // 重新选中该节点
                fileSystemTreeView.getSelectionModel().select(targetItem);

                // 滚动到该节点所在行
                int row = fileSystemTreeView.getRow(targetItem);
                if (row >= 0) {
                    fileSystemTreeView.scrollTo(row);
                }
            }
        }

        // 5. 更新磁盘信息 (保持不变)
        if (kernel.getFileSystemManager().getFileSystem() != null)
        {
            int total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
            int used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
            double usage = total > 0 ? (double) used / total : 0;

            if (diskUsageBar != null) diskUsageBar.setProgress(usage);
            if (diskInfoLabel != null) diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", used, total));
        }
    }

    // --- 【新增辅助方法 1】递归保存展开状态 ---
    private void saveExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        if (item.isExpanded()) {
            expandedPaths.add(buildPathFromTree(item));
        }
        for (TreeItem<String> child : item.getChildren()) {
            saveExpansionState(child, expandedPaths);
        }
    }

    // --- 【新增辅助方法 2】递归恢复展开状态 ---
    private void restoreExpansionState(TreeItem<String> item, Set<String> expandedPaths) {
        String currentPath = buildPathFromTree(item);
        if (expandedPaths.contains(currentPath)) {
            item.setExpanded(true);
        }
        for (TreeItem<String> child : item.getChildren()) {
            restoreExpansionState(child, expandedPaths);
        }
    }

    /**
     * 递归填充文件树 (带图标逻辑)
     */
    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem)
    {
        for (Object child : parent.getChildren())
        {
            if (child instanceof Directory)
            {
                // 子目录 -> 文件夹图标
                Directory dir = (Directory) child;
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                dirItem.setGraphic(createIcon("folder"));
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File)
            {
                // 文件 -> 根据后缀判断图标
                File f = (File) child;
                TreeItem<String> fileItem = new TreeItem<>(f.getName());

                if (f.getName().endsWith(".e"))
                {
                    fileItem.setGraphic(createIcon("exec"));
                } else if (f.getName().endsWith(".txt"))
                {
                    fileItem.setGraphic(createIcon("text"));
                } else
                {
                    fileItem.setGraphic(createIcon("file"));
                }
                parentItem.getChildren().add(fileItem);
            }
        }
    }

    /**
     * 创建带样式的 Emoji 图标
     */
    private javafx.scene.control.Label createIcon(String type)
    {
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label();
        // 设置字体以确保Emoji显示正常，虽然CSS已设置，这里双重保险
        iconLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Segoe UI Symbol';");

        switch (type)
        {
            case "folder":
                iconLabel.setText("📁");
                iconLabel.getStyleClass().add("folder-icon");
                break;
            case "exec":
                iconLabel.setText("🚀");
                iconLabel.getStyleClass().add("exec-icon");
                break;
            case "text":
                iconLabel.setText("📝");
                iconLabel.getStyleClass().add("file-icon");
                break;
            default:
                iconLabel.setText("📄");
                iconLabel.getStyleClass().add("file-icon");
                break;
        }
        return iconLabel;
    }


    /**
     * 更新日志视图
     */
    private void updateOperationLogView()
    {
        // 更新系统操作日志
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        // 更新程序输出日志
        outputListView.getItems().setAll(kernel.getOutputLogs());
    }

    /**
     * 初始化性能图表 (纯 JavaFX 版本)
     */
    private void initializePerformanceChart()
    {
        try
        {
            // 1. 实例化新的 JavaFX 图表工具
            performanceChart = new org.example.scau_os_simulation.performance.PerformanceChartFX();

            // 2. 获取图表节点 (LineChart)
            javafx.scene.chart.LineChart<Number, Number> chart = performanceChart.getChart();

            // 3. 添加到界面容器中
            if (performanceChartContainer != null)
            {
                performanceChartContainer.getChildren().clear();
                performanceChartContainer.getChildren().add(chart);

                // --- 【关键】设置自适应布局 ---
                // 因为 Chart 是原生 JavaFX 节点，我们可以直接绑定宽高
                // 这样无论窗口怎么变，图表都会自动填满 StackPane
                chart.prefWidthProperty().bind(performanceChartContainer.widthProperty());
                chart.prefHeightProperty().bind(performanceChartContainer.heightProperty());

                // 移除自带的背景，让它融入你的卡片样式 (可选)
                chart.setStyle("-fx-background-color: transparent;");
            }
        } catch (Exception e)
        {
            System.err.println("性能图表初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 实时更新性能图表的数据点
     */
    private void updatePerformanceChart()
    {
        if (performanceChart != null && kernel != null)
        {
            performanceChart.update(
                    kernel.getSystemClock(),
                    kernel.getCpuUtilization(),
                    kernel.getMemoryUtilization()
            );
        }
    }

    /**
     * 更新性能统计文字指标
     */
    private void updatePerformanceMetrics()
    {
        PerformanceMonitor pm = kernel.getPerformanceMonitor();

        // 获取历史平均值和峰值
        double avgCpu = pm.getAverageCpuUtilization();
        double avgMem = pm.getAverageMemoryUtilization();
        double peakCpu = pm.getPeakCpuUtilization();
        double peakMem = pm.getPeakMemoryUtilization();

        // 更新标签文本
        avgCpuLabel.setText(String.format("平均CPU: %.2f%%", avgCpu * 100));
        avgMemoryLabel.setText(String.format("平均内存: %.2f%%", avgMem * 100));
        peakCpuLabel.setText(String.format("峰值CPU: %.2f%%", peakCpu * 100));
        peakMemoryLabel.setText(String.format("峰值内存: %.2f%%", peakMem * 100));

        // 获取并更新实时值
        double currentCpu = kernel.getCpuUtilization();
        double currentLoad = kernel.getSystemLoad();

        cpuUtilizationBar.setProgress(currentCpu);
        systemLoadBar.setProgress(currentLoad);

        cpuUtilizationLabel.setText(String.format("CPU: %.2f%%", currentCpu * 100));
        systemLoadLabel.setText(String.format("负载: %.2f", currentLoad));
    }

    /**
     * 初始化数据绑定
     * 将 TableView 的列与对象的属性进行关联。
     */
    private void initBindings()
    {
        // 绑定 PCB 属性到进程表
        pidColumn.setCellValueFactory(cellData -> cellData.getValue().pidProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        stateColumn.setCellValueFactory(cellData -> cellData.getValue().stateProperty());
        priorityColumn.setCellValueFactory(cellData -> cellData.getValue().priorityProperty());
        memoryAddressColumn.setCellValueFactory(cellData -> cellData.getValue().memoryAddressProperty());
        memorySizeColumn.setCellValueFactory(cellData -> cellData.getValue().memorySizeProperty());

        // 绑定内存块属性
        startAddressColumn.setCellValueFactory(cellData -> cellData.getValue().startAddressProperty());
        blockSizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());

        // 自定义单元格值：根据内存块反查占用该内存块的进程 PID
        processColumn.setCellValueFactory(cellData ->
        {
            int pid = findProcessIdForMemoryBlock(cellData.getValue());
            return new javafx.beans.property.SimpleStringProperty(pid >= 0 ? String.valueOf(pid) : "N/A");
        });

        // 绑定设备属性
        deviceTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType().toString()));
        deviceInUseColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isBusy() ? "是" : "否"));
        devicePidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCurrentUserPid()));
        deviceRemainColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getRemainingTime()));

        // 绑定设备等待队列属性 (使用 WaitRow 辅助类)
        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time));
    }

    /**
     * 辅助方法：反查占用某内存块的进程 PID
     */
    private int findProcessIdForMemoryBlock(MemoryBlock block)
    {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses())
        {
            // 通过比对地址和大小来确认归属
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize())
            {
                return p.getPcb().getPid();
            }
        }
        return -1; // 未找到，可能已经被释放
    }

    /**
     * 辅助方法：根据 TreeItem 构建文件绝对路径字符串 (例如 "/system/exec/p1.e")
     */
    private String buildPathFromTree(TreeItem<String> item)
    {
        StringBuilder path = new StringBuilder();
        // 向上遍历直到根节点
        while (item != null && item.getParent() != null)
        {
            path.insert(0, "/" + item.getValue());
            item = item.getParent();
        }
        return path.length() > 0 ? path.toString() : "/";
    }

    /**
     * 辅助方法：根据路径字符串在 TreeView 中找到对应的 TreeItem
     */
    private TreeItem<String> findItemByPath(TreeItem<String> root, String path)
    {
        if (path.equals("/")) return root;

        String[] parts = path.split("/");
        TreeItem<String> current = root;

        // 逐层查找子节点
        for (int i = 1; i < parts.length; i++)
        {
            boolean found = false;
            for (TreeItem<String> child : current.getChildren())
            {
                if (child.getValue().equals(parts[i]))
                {
                    current = child;
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        return current;
    }

    /**
     * 辅助方法：构建文件对象的全路径
     */
    private String buildFullPath(Object fileObj)
    {
        if (fileObj instanceof File)
        {
            return findFilePath((File) fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
        } else if (fileObj instanceof Directory)
        {
            return findDirectoryPath((Directory) fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
        }
        return "";
    }

    // 递归查找文件路径
    private String findFilePath(File target, Directory current, String currentPath)
    {
        for (Object child : current.getChildren())
        {
            if (child instanceof File && child == target)
            {
                return currentPath + "/" + ((File) child).getName();
            } else if (child instanceof Directory)
            {
                String result = findFilePath(target, (Directory) child, currentPath + "/" + ((Directory) child).getName());
                if (result != null) return result;
            }
        }
        return null;
    }

    // 递归查找目录路径
    private String findDirectoryPath(Directory target, Directory current, String currentPath)
    {
        if (current == target)
        {
            return currentPath.isEmpty() ? "/" : currentPath;
        }

        for (Object child : current.getChildren())
        {
            if (child instanceof Directory)
            {
                Directory subDir = (Directory) child;
                String newPath = currentPath + "/" + subDir.getName();
                String result = findDirectoryPath(target, subDir, newPath);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * 辅助方法：在文件树中选中指定的文件/目录，并滚动到可视区域
     */
    private void selectFileInTree(Object fileObj)
    {
        String path = buildFullPath(fileObj);
        if (!path.isEmpty())
        {
            TreeItem<String> root = fileSystemTreeView.getRoot();
            TreeItem<String> target = findItemByPath(root, path);
            if (target != null)
            {
                fileSystemTreeView.getSelectionModel().select(target);
                fileSystemTreeView.scrollTo(fileSystemTreeView.getSelectionModel().getSelectedIndex());
            }
        }
    }

    // --- 简单的消息弹窗辅助方法 ---

    private void showInfo(String title, String message)
    {
        showInternalAlert("info", title, message);
    }

    private void showWarning(String title, String message)
    {
        showInternalAlert("warning", title, message);
    }

    private void showError(String title, String message)
    {
        showInternalAlert("error", title, message);
    }

    /**
     * 内部类：用于封装设备等待队列的一行数据，便于 TableView 显示
     */
    private static class WaitRow
    {
        final String device;
        final int pid;
        final int time;

        WaitRow(String device, int pid, int time)
        {
            this.device = device;
            this.pid = pid;
            this.time = time;
        }
    }

    /**
     * [事件处理] 点击 "打开终端" 按钮
     */
    @FXML
    protected void onOpenTerminalClick()
    {
        try
        {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/example/scau_os_simulation/terminal_view.fxml"));
            // 加载为 Node (Parent) 而不是 Scene
            Parent terminalContent = loader.load();

            // 获取控制器以便处理关闭逻辑
            TerminalController controller = loader.getController();

            // 创建内部窗口
            InternalWindow termWin = new InternalWindow("终端", terminalContent, 600, 400);

            // 绑定关闭事件
            termWin.onClosed = () -> controller.onClose(); // 假设控制器有关闭清理方法

            openWindow("终端", terminalContent, 600, 400); // 或者复用 openWindow 逻辑

        } catch (Exception e)
        {
            e.printStackTrace();
            showError("错误", "无法打开终端: " + e.getMessage());
        }
    }

    /**
     * 模拟 Alert 弹窗
     *
     * @param type    类型 (info, warning, error) - 决定图标或标题颜色
     * @param title   标题
     * @param content 内容
     */
    private void showInternalAlert(String type, String title, String content)
    {
        // 创建内容布局
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setAlignment(Pos.CENTER);

        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(250); // 限制宽度自动换行

        Button okBtn = new Button("确定");

        root.getChildren().addAll(msgLabel, okBtn);

        // 创建内部窗口
        // 宽度 300，高度自适应
        InternalWindow win = new InternalWindow(title, root, 300, 150);

        // 设置居中
        double x = (desktopArea.getWidth() - 300) / 2;
        double y = (desktopArea.getHeight() - 150) / 2;
        win.setLayoutX(x);
        win.setLayoutY(y);

        // 按钮点击关闭窗口
        okBtn.setOnAction(e -> win.close());

        // 添加到桌面
        desktopArea.getChildren().add(win);
        // (可选) 这里可以添加一个全屏的透明遮罩层来模拟“模态”禁止点击背景
    }


    /**
     * 【新增】内部确认对话框（替代原生 Alert，防止全屏下窗口消失/闪烁）
     */
    private void showInternalConfirm(String title, String content, Runnable onConfirm) {
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);

        Label msgLabel = new Label(content);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(300);
        msgLabel.setStyle("-fx-font-size: 14px;");

        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER);

        Button yesBtn = new Button("确定");
        yesBtn.getStyleClass().add("button"); // 或者用 button-danger
        yesBtn.setStyle("-fx-background-color: #da1e28; -fx-text-fill: white;"); // 红色警示

        Button noBtn = new Button("取消");

        btnBox.getChildren().addAll(yesBtn, noBtn);
        root.getChildren().addAll(msgLabel, btnBox);

        InternalWindow win = new InternalWindow(title, root, 350, 180);

        // 居中显示
        win.setLayoutX((desktopArea.getWidth() - 350) / 2);
        win.setLayoutY((desktopArea.getHeight() - 180) / 2);

        yesBtn.setOnAction(e -> {
            win.close();
            if (onConfirm != null) onConfirm.run();
        });

        noBtn.setOnAction(e -> win.close());

        desktopArea.getChildren().add(win);
        win.toFront();
    }




    /**
     * 模拟 TextInputDialog 输入弹窗
     *
     * @param title        标题
     * @param header       提示头
     * @param defaultValue 默认值
     * @param callback     用户点击确定后的回调函数 (替代 showAndWait 的返回值)
     */
    private void showInternalInput(String title, String header, String defaultValue, java.util.function.Consumer<String> callback)
    {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        Label headerLbl = new Label(header);
        TextField textField = new TextField(defaultValue);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button okBtn = new Button("确定");
        Button cancelBtn = new Button("取消");
        btnBox.getChildren().addAll(okBtn, cancelBtn);

        root.getChildren().addAll(headerLbl, textField, btnBox);

        InternalWindow win = new InternalWindow(title, root, 320, 160);

        // 简单的居中计算
        win.setLayoutX(200);
        win.setLayoutY(200);

        cancelBtn.setOnAction(e -> win.close());

        okBtn.setOnAction(e ->
        {
            String result = textField.getText();
            win.close();
            // 执行回调
            if (callback != null)
            {
                callback.accept(result);
            }
        });

        desktopArea.getChildren().add(win);
        win.toFront();
    }
}
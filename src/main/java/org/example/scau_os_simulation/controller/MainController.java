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

import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 操作系统模拟器主控制器
 */
public class MainController implements Initializable {
    // --- 桌面环境核心组件 ---
    @FXML private StackPane rootStackPane;
    @FXML private FlowPane desktopArea;
    @FXML private HBox taskBarApps;
    @FXML private Label systemClockLabel;
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

    // --- 后端核心对象引用 ---
    private Kernel kernel;
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartFX performanceChart;
    // 【修复 3】移除了未使用的 clipboardFile (单对象)，保留 clipboardFiles (列表)
    private final Map<Node, InternalWindow> openWindows = new HashMap<>();
    private final List<Object> clipboardFiles = new ArrayList<>();

    // 窗口层 (解决闪烁)
    private Pane windowLayer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        kernel = Kernel.getInstance();
        initBindings();
        initializePerformanceChart();

        fileSystemTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // 初始化窗口层
        initWindowLayer();

        // 桌面区域自适应
        desktopArea.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        initDesktop();
        initStartMenu();
        startClock();

        updateAllViews();
        updateFileSystemView();
        setupFileSystemEvents();
        updateControlButtonsState(false);

        // 全局快捷键：Ctrl+F 搜索
        rootStackPane.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() && e.getCode() == KeyCode.F) {
                onSearchFileClick();
                e.consume();
            }
        });

        // 定时刷新 UI
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
            updateOperationLogView();
            updatePerformanceChart();
            updatePerformanceMetrics();
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

    // --- 内部窗口类 ---
    class InternalWindow extends VBox {
        private double xOffset = 0, yOffset = 0;
        private double initX, initY, initW, initH;
        private boolean isDraggingWindow = false;
        private boolean isMaximized = false;
        private double restoreX, restoreY, restoreW, restoreH;
        private static final double RESIZE_MARGIN = 10.0;
        private static final double MIN_WIDTH = 200;
        private static final double MIN_HEIGHT = 150;

        String title;
        Runnable onClosed;
        private final Button maxBtn;
        private ResizeMode currentResizeMode = ResizeMode.NONE;
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
        memoryBlockTableView.getItems().setAll(memoryManager.getAllocatedBlocks());
        double fragmentationRate = memoryManager.getFragmentationRate();
        fragmentationLabel.setText(String.format("碎片率: %.2f%%", fragmentationRate * 100));
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
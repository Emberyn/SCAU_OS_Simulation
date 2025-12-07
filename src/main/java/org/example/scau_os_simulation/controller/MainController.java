package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
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

    // --- 内部类：自定义窗口 ---
    class InternalWindow extends VBox
    {
        private double xOffset = 0;
        private double yOffset = 0;
        String title;
        Runnable onClosed;

        public InternalWindow(String title, Node content, double w, double h)
        {
            this.setManaged(false); // 防止撑大桌面
            this.title = title;
            this.setPrefSize(w, h);
            this.getStyleClass().add("window-frame");

            HBox titleBar = new HBox();
            titleBar.getStyleClass().add("window-title-bar");
            titleBar.setAlignment(Pos.CENTER_LEFT);

            Label titleLbl = new Label(title);
            titleLbl.getStyleClass().add("window-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button closeBtn = new Button("✕");
            closeBtn.getStyleClass().add("window-close-btn");
            closeBtn.setOnAction(e -> close());

            titleBar.getChildren().addAll(titleLbl, spacer, closeBtn);

            // 拖拽
            titleBar.setOnMousePressed(event ->
            {
                this.toFront();
                xOffset = event.getSceneX() - this.getLayoutX();
                yOffset = event.getSceneY() - this.getLayoutY();
            });
            titleBar.setOnMouseDragged(event ->
            {
                double newX = event.getSceneX() - xOffset;
                double newY = event.getSceneY() - yOffset;
                if (newY < 0) newY = 0;
                if (newY > desktopArea.getHeight() - 30) newY = desktopArea.getHeight() - 30;
                if (newX + this.getWidth() < 30) newX = 30 - this.getWidth();
                if (newX > desktopArea.getWidth() - 30) newX = desktopArea.getWidth() - 30;
                this.setLayoutX(newX);
                this.setLayoutY(newY);
            });

            this.setOnMousePressed(e -> this.toFront());

            // 内容处理
            content.setVisible(true);
            content.setManaged(true); // 确保内容本身是托管的

            VBox contentContainer = new VBox(content);
            VBox.setVgrow(content, Priority.ALWAYS);
            contentContainer.setPadding(new Insets(5));
            VBox.setVgrow(contentContainer, Priority.ALWAYS);

            this.getChildren().addAll(titleBar, contentContainer);

            // 强制调整窗口大小
            this.resize(w, h);

            // 在下一帧执行，强制引擎重新计算布局
            Platform.runLater(() ->
            {
                // 强制子节点刷新布局
                this.requestLayout();
                this.applyCss();

                // 技巧：微调尺寸强制重绘 (Jiggle fix)
                this.resize(w + 0.1, h + 0.1);
                this.resize(w, h);

                // 如果内容是 Parent 类型，也强制它刷新
                if (content instanceof Parent)
                {
                    ((Parent) content).requestLayout();
                    ((Parent) content).layout();
                }
            });
        }

        public void close()
        {
            this.setVisible(false);
            if (onClosed != null) onClosed.run();
            desktopArea.getChildren().remove(this);
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
        ComboBox<Integer> priorityBox = new ComboBox<>(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1);
        TextField execPathField = new TextField();
        execPathField.setPromptText("/system/exec/p1.e");

        // 文件树
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(150);
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) ->
        {
            if (newVal != null && newVal.getValue().endsWith(".e"))
            {
                execPathField.setText(buildPathFromTree(newVal));
                if (processNameField.getText().equals("新进程"))
                    processNameField.setText(newVal.getValue().replace(".e", ""));
            }
        });

        // 2. 布局 (GridPane)
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("进程名称:"), 0, 0);
        grid.add(processNameField, 1, 0);
        grid.add(new Label("优先级:"), 0, 1);
        grid.add(priorityBox, 1, 1);
        grid.add(new Label("文件路径:"), 0, 2);
        grid.add(execPathField, 1, 2);
        grid.add(new Label("选择文件:"), 0, 3);
        grid.add(fileTreeView, 1, 3);

        // 3. 按钮区域
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        btnBox.setPadding(new Insets(0, 20, 10, 20));
        Button okBtn = new Button("创建");
        Button cancelBtn = new Button("取消");
        btnBox.getChildren().addAll(okBtn, cancelBtn);

        // 4. 组合内容
        VBox root = new VBox(grid, btnBox);

        // 创建内部窗口 (InternalWindow)
        InternalWindow win = new InternalWindow("创建新进程", root, 400, 380);
        win.setLayoutX(150);
        win.setLayoutY(100);

        // 5. 事件绑定
        cancelBtn.setOnAction(e -> win.close());

        okBtn.setOnAction(e ->
        {
            String name = processNameField.getText().trim();
            if (name.isEmpty()) name = "新进程";
            String path = execPathField.getText().trim();
            int priority = priorityBox.getValue();

            // 业务逻辑
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
                    win.close(); // 成功后关闭窗口
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
     * [事件处理] 点击 "创建目录" 按钮 (逻辑同创建文件类似)
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

        showInternalInput("创建目录", "在路径 '" + finalPath + "' 下创建新目录:", "new.txt", (name) ->
        {
            // 这里是回调：当用户点击内部窗口的“确定”后执行
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
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
     * [事件处理] 点击 "删除" 按钮
     */
    @FXML
    protected void onDeleteClick()
    {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        // 如果未选择或选择的是根节点(无法删除根)
        if (selected == null || selected.getParent() == null)
        {
            showError("无法删除", "不能删除根目录。");
            return;
        }

        String path = buildPathFromTree(selected);
        String itemName = selected.getValue();

        // 确认对话框
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("确认删除");
        confirmDialog.setHeaderText("确定要删除 '" + itemName + "' 吗？");

        // 判断是文件还是目录，显示不同的提示
        Object node = kernel.getFileSystemManager().getFileByPath(path);
        String itemType = (node instanceof Directory) ? "目录" : "文件";
        confirmDialog.setContentText("您将要删除 " + itemType + ": \n" + path + "\n\n此操作不可撤销。");

        confirmDialog.showAndWait().ifPresent(response ->
        {
            if (response == javafx.scene.control.ButtonType.OK)
            {
                boolean success = false;
                try
                {
                    // 尝试删除文件或目录
                    success = kernel.getFileSystemManager().deleteFile(path) || kernel.getFileSystemManager().deleteDirectory(path);
                } catch (Exception e)
                {
                    success = false;
                }

                if (success)
                {
                    updateFileSystemView();
                    showInfo("删除成功", itemType + " '" + itemName + "' 已成功删除。");
                } else
                {
                    showError("删除失败", "无法删除 " + itemType + "。可能是目录非空或路径无效。");
                }
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
     * [事件处理] 复制文件
     */
    @FXML
    protected void onCopyFileClick()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
        {
            String path = buildPathFromTree(selectedItem);
            // 将文件对象引用暂存到 clipboardFile 变量中
            clipboardFile = kernel.getFileSystemManager().getFileByPath(path);
            showInfo("复制成功", "'" + selectedItem.getValue() + "' 已复制到剪贴板。");
        } else
        {
            showWarning("未选择文件", "请先选择要复制的文件或目录。");
        }
    }

    /**
     * [事件处理] 粘贴文件
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
                targetPath = buildPathFromTree(selectedItem);
                Object targetNode = kernel.getFileSystemManager().getFileByPath(targetPath);
                // 如果当前选中的是文件，则粘贴到其父目录
                if (!(targetNode instanceof Directory))
                {
                    targetPath = targetPath.substring(0, targetPath.lastIndexOf('/'));
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
     * [事件处理] 搜索文件
     */
    @FXML
    protected void onSearchFileClick()
    {
        // 【修改】不再使用 TextInputDialog，改用 showInternalInput
        showInternalInput("搜索文件", "在整个文件系统中搜索文件" + "请输入文件名", "new.txt", (name) ->
        {
            // 这里是回调：当用户点击内部窗口的“确定”后执行
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    Object result = kernel.getFileSystemManager().getRootDirectory().searchRecursive(name.trim());
                    selectFileInTree(result);
                    showInfo("找到文件", "已在文件树中高亮显示 '" + name + "' 。");
                } catch (Exception e)
                {
                    showError("未找到文件", e.getMessage());
                }
            }
        });
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
            // 模拟读取文件内容
            // textArea.setText(file.getContent());

            Button saveBtn = new Button("保存");
            saveBtn.setOnAction(e ->
            {
                // file.setContent(textArea.getText());
                showInfo("保存", "文件已保存");
            });

            VBox editorRoot = new VBox(5, new ToolBar(saveBtn), textArea);
            VBox.setVgrow(textArea, Priority.ALWAYS);

            // 2. 放入内部窗口
            // 每次打开都创建一个新的 InternalWindow 实例
            InternalWindow editorWin = new InternalWindow("编辑: " + file.getName(), editorRoot, 500, 400);

            // 简单的层叠位置计算
            editorWin.setLayoutX(50 + openWindows.size() * 20);
            editorWin.setLayoutY(50 + openWindows.size() * 20);

            desktopArea.getChildren().add(editorWin);
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
     * 更新文件系统视图
     */
    private void updateFileSystemView()
    {
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();

        // 创建树的根节点
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder")); // 添加文件夹图标
        rootItem.setExpanded(true);

        // 递归填充树结构
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        // 更新磁盘信息
        if (kernel.getFileSystemManager().getFileSystem() != null)
        {
            int total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
            int used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
            double usage = total > 0 ? (double) used / total : 0;

            if (diskUsageBar != null) diskUsageBar.setProgress(usage);
            if (diskInfoLabel != null) diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", used, total));
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
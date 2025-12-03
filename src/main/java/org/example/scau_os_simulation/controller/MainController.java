package org.example.scau_os_simulation.controller;

// 导入 JavaFX 的核心工具类，用于处理多线程 UI 更新
import javafx.application.Platform;
// 导入 FXML 注解，用于将界面文件(.fxml)中的组件绑定到代码变量
import javafx.fxml.FXML;
// 导入初始化接口，实现该接口的类会在界面加载时自动调用 initialize 方法
import javafx.fxml.Initializable;
// 导入各种 UI 控件（按钮、进度条、标签、表格、树视图等）
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.control.ListView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.Node;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;

// 导入后端核心逻辑类（Kernel, MemoryManager 等）
import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.performance.PerformanceChartUtil;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.TextEditorWindow;
import org.example.scau_os_simulation.performance.PerformanceMonitor;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;

// 导入并发工具，用于创建定时任务（如定时刷新界面）
import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * 主界面控制器类
 * 负责处理用户界面的交互逻辑，将后端 Kernel 的数据展示到前端 JavaFX 界面上。
 * 实现 Initializable 接口，以便在界面加载完成后进行初始化。
 */
public class MainController implements Initializable
{

    // --- 界面布局容器 (@FXML 注解表示这些变量对应 fxml 文件中的组件) ---
    @FXML
    private TabPane tabPane; // 选项卡面板，用于切换不同功能的页面
    @FXML
    private Tab processTab; // "进程管理" 选项卡
    @FXML
    private Tab memoryTab; // "内存管理" 选项卡
    @FXML
    private Tab fileSystemTab; // "文件系统" 选项卡
    @FXML
    private Pane processContainer; // 进程页面的布局容器
    @FXML
    private Pane memoryContainer; // 内存页面的布局容器
    @FXML
    private Pane fileSystemContainer; // 文件系统页面的布局容器
    @FXML
    private Pane performanceChartContainer; // 性能图表容器

    // --- 控制按钮 ---

    @FXML
    private Button startSystemBtn; // "启动系统" 按钮
    @FXML
    private Button stopSystemBtn;  // "暂停/停止系统" 按钮
    @FXML
    private Button createProcessBtn; // "创建进程" 按钮
    @FXML
    private Button terminateProcessBtn; // "终止进程" 按钮

    // --- 进程管理表格组件 ---
    // TableView<PCB>: 表格，显示 PCB (进程控制块) 对象的数据
    @FXML
    private TableView<PCB> processTableView;
    // 表格列定义：TableColumn<数据类型, 显示值的类型>
    @FXML
    private TableColumn<PCB, Number> pidColumn; // 显示 PID (数字)
    @FXML
    private TableColumn<PCB, String> nameColumn; // 显示进程名称 (字符串)
    @FXML
    private TableColumn<PCB, String> stateColumn; // 显示进程状态
    @FXML
    private TableColumn<PCB, Number> priorityColumn; // 显示优先级
    @FXML
    private TableColumn<PCB, Number> memoryAddressColumn; // 显示内存地址
    @FXML
    private TableColumn<PCB, Number> memorySizeColumn; // 显示内存大小

    // --- 系统状态显示标签 ---
    @FXML
    private Label systemClockLabel; // 显示系统时钟滴答数
    @FXML
    private Label runningPidLabel; // 显示当前正在 CPU 运行的 PID
    @FXML
    private Label irLabel; // 显示 IR (当前指令寄存器) 的内容
    @FXML
    private Label axLabel; // 显示 AX (累加器) 的值
    @FXML
    private Label tsLabel; // 显示剩余时间片
    @FXML
    private Label systemStatusLabel; // 通用状态提示标签


    // --- 列表视图 (用于显示队列和日志) ---
    @FXML
    private ListView<String> outputListView; // 显示进程执行的输出结果
    @FXML
    private ListView<String> readyQueueListView; // 显示就绪队列中的进程
    @FXML
    private ListView<String> blockedQueueListView; // 显示阻塞队列中的进程


    // --- 内存管理可视化组件 ---
    @FXML
    private ProgressBar memoryUsageBar; // 内存使用率进度条
    @FXML
    private Label memoryInfoLabel; // 内存文字信息 (例如: 100KB / 1024KB)
    @FXML
    private TableView<MemoryBlock> memoryBlockTableView; // 内存块分配表
    @FXML
    private TableColumn<MemoryBlock, Number> startAddressColumn; // 内存块起始地址列
    @FXML
    private TableColumn<MemoryBlock, Number> blockSizeColumn; // 内存块大小列
    @FXML
    private TableColumn<MemoryBlock, String> processColumn; // 占用该内存块的进程列
    @FXML
    private Label fragmentationLabel; // 内存碎片率标签

    // --- 文件系统组件 ---
    @FXML
    private TreeView<String> fileSystemTreeView; // 文件目录树视图

    private Object clipboardFile; // 剪贴板，用于存储复制的文件/目录对象 (Java对象引用)

    @FXML
    private TextField commandField; // 底部命令行输入框
    @FXML
    private Button runCommandBtn; // 执行命令按钮

    // --- 文件/内存操作按钮 ---
    @FXML
    private Button defragmentBtn; // 内存整理按钮
    @FXML
    private Button undoBtn; // 撤销按钮
    @FXML
    private Button redoBtn; // 重做按钮
    @FXML
    private Button createFileBtn; // 创建文件按钮
    @FXML
    private Button createDirectoryBtn; // 创建目录按钮
    @FXML
    private Button deleteFileBtn; // 删除按钮
    @FXML
    private Button copyFileBtn; // 复制按钮
    @FXML
    private Button pasteFileBtn; // 粘贴按钮
    @FXML
    private Button searchFileBtn; // 搜索按钮

    // --- 磁盘显示 ---
    @FXML
    private ProgressBar diskUsageBar; // 磁盘使用率进度条
    @FXML
    private Label diskInfoLabel; // 磁盘信息标签

    // --- 设备管理表格 ---
    @FXML
    private TableView<Device> deviceTableView; // 设备状态表
    @FXML
    private TableColumn<Device, String> deviceTypeColumn; // 设备类型列 (A/B/C)
    @FXML
    private TableColumn<Device, String> deviceInUseColumn; // 是否占用列
    @FXML
    private TableColumn<Device, Number> devicePidColumn; // 占用者 PID 列
    @FXML
    private TableColumn<Device, Number> deviceRemainColumn; // 剩余占用时间列

    // --- 设备等待队列表格 ---
    @FXML
    private TableView<WaitRow> waitQueueTableView; // 设备等待队列表
    @FXML
    private TableColumn<WaitRow, String> waitDeviceColumn; // 等待的设备类型
    @FXML
    private TableColumn<WaitRow, Number> waitPidColumn; // 等待的 PID
    @FXML
    private TableColumn<WaitRow, Number> waitTimeColumn; // 申请的时长

    // --- 日志与性能监控 ---
    @FXML
    private ListView<String> operationLogListView; // 系统操作日志列表

    @FXML
    private ProgressBar cpuUtilizationBar; // 实时 CPU 利用率条
    @FXML
    private ProgressBar systemLoadBar; // 实时系统负载条
    @FXML
    private Label cpuUtilizationLabel; // CPU 利用率文字
    @FXML
    private Label systemLoadLabel; // 系统负载文字
    @FXML
    private Label avgCpuLabel; // 平均 CPU 文字
    @FXML
    private Label avgMemoryLabel; // 平均内存文字
    @FXML
    private Label peakCpuLabel; // 峰值 CPU 文字
    @FXML
    private Label peakMemoryLabel; // 峰值内存文字

    // --- 后端核心引用 ---
    private Kernel kernel; // 持有操作系统的核心实例

    // --- 定时刷新器 ---
    // 创建一个单线程的定时任务执行器，用于每隔一段时间刷新 UI
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    // 性能图表工具类 (JFreeChart 封装)
    private PerformanceChartUtil performanceChart;

    /**
     * 初始化方法
     * 当 .fxml 文件加载完成，并且所有 @FXML 变量注入完毕后，JavaFX 会自动调用此方法。
     * 我们在这里进行数据的初始化绑定和定时任务的启动。
     */
    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 获取操作系统的单例实例 (Kernel)
        kernel = Kernel.getInstance();

        // 2. 初始化表格列的数据绑定 (告诉表格每列显示对象的哪个属性)
        initBindings();

        // 3. 初始化性能图表 (折线图)
        initializePerformanceChart();

        // 4. 执行一次全量视图更新，确保界面显示初始状态
        updateAllViews();
        updateFileSystemView(); // 初始化文件树

        // 5. 设置文件树的事件监听器
        // 绑定鼠标点击事件：处理双击打开文件逻辑
        fileSystemTreeView.setOnMouseClicked(event ->
        {
            // 检查系统是否正在运行，如果未启动则弹出警告
            if (Kernel.getInstance().getScheduler() == null || !Kernel.getInstance().getScheduler().isRunning()) {
                // 如果是双击操作，提示用户先启动系统
                if (event.getClickCount() == 2) {
                    showWarning("系统未启动", "请先点击顶部的 [▶ 启动系统] 按钮。");
                }
                return;
            }

            // 如果是鼠标左键双击，则尝试打开选中的文件
            if (event.getClickCount() == 2 && event.getButton() == javafx.scene.input.MouseButton.PRIMARY)
            {
                openSelectedFile();
            }
        });

        // 6. 创建并绑定右键上下文菜单 (Context Menu)
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem editItem = new javafx.scene.control.MenuItem("编辑 / 查看"); // 编辑菜单项
        javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("删除"); // 删除菜单项

        // 设置菜单项的点击动作
        editItem.setOnAction(e -> openSelectedFile()); // 点击编辑 -> 打开文件
        deleteItem.setOnAction(e -> onDeleteClick()); // 点击删除 -> 执行删除逻辑

        // 将菜单项添加到菜单中
        contextMenu.getItems().addAll(editItem, deleteItem);
        // 将菜单绑定到文件树上
        fileSystemTreeView.setContextMenu(contextMenu);

        // 设置菜单显示前的逻辑：如果系统没跑，禁用菜单项
        contextMenu.setOnShowing(e -> {
            boolean isRunning = Kernel.getInstance().getScheduler() != null &&
                    Kernel.getInstance().getScheduler().isRunning();

            // 遍历所有菜单项并根据运行状态禁用/启用
            for (javafx.scene.control.MenuItem item : contextMenu.getItems()) {
                item.setDisable(!isRunning);
            }
        });

        // 7. 初始状态下禁用大部分操作按钮 (因为系统还没启动)
        updateControlButtonsState(false);

        // 8. 启动定时刷新任务
        // 每隔 500 毫秒 (0.5秒) 执行一次界面刷新逻辑
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() ->
        {
            // Platform.runLater 用于确保 UI 更新代码在 JavaFX 主线程中执行
            // 否则会报 "Not on FX application thread" 错误
            updateProcessView();      // 更新进程信息
            updateMemoryView();       // 更新内存信息
            updateDeviceView();       // 更新设备信息
            updateOperationLogView(); // 更新日志
            updatePerformanceChart(); // 更新图表数据
            updatePerformanceMetrics(); // 更新统计指标
            // updateFileSystemView(); // 注意：文件系统视图不在这里自动刷新，避免刷新时树节点自动折叠影响用户操作
        }), 0, 500, TimeUnit.MILLISECONDS);

    }

    /**
     * 根据系统运行状态，批量启用或禁用操作按钮
     *
     * @param isRunning true=系统运行中(启用按钮)，false=系统停止(禁用按钮)
     */
    private void updateControlButtonsState(boolean isRunning)
    {
        // 如果系统正在运行(isRunning=true)，disable应为false(不禁用)
        // 如果系统停止(isRunning=false)，disable应为true(禁用)
        boolean disable = !isRunning;

        // 1. 进程管理相关按钮
        if (createProcessBtn != null) createProcessBtn.setDisable(disable);
        if (terminateProcessBtn != null) terminateProcessBtn.setDisable(disable);

        // 2. 内存/撤销/重做按钮
        if (defragmentBtn != null) defragmentBtn.setDisable(disable);
        if (undoBtn != null) undoBtn.setDisable(disable);
        if (redoBtn != null) redoBtn.setDisable(disable);

        // 3. 命令行相关组件
        if (runCommandBtn != null) runCommandBtn.setDisable(disable);
        if (commandField != null) commandField.setDisable(disable);

        // 4. 文件操作按钮
        if (createFileBtn != null) createFileBtn.setDisable(disable);
        if (createDirectoryBtn != null) createDirectoryBtn.setDisable(disable);
        if (deleteFileBtn != null) deleteFileBtn.setDisable(disable);

        // 注意：startSystemBtn 和 stopSystemBtn 不需要在这里控制，
        // 它们在自己的点击事件里单独逻辑控制
    }

    /**
     * [事件处理] 点击 "启动系统" 按钮时触发
     */
    @FXML
    protected void onStartSystemClick()
    {
        // 1. 调用内核的启动方法，开始 CPU 调度循环
        Kernel.getInstance().start();

        // 2. 更新按钮状态：禁用启动按钮，启用暂停按钮(如果有)
        startSystemBtn.setDisable(true);
        if (stopSystemBtn != null)
        {
            stopSystemBtn.setDisable(false);
        }
        // 启用所有功能按钮
        updateControlButtonsState(true);

        // 3. 弹窗提示用户系统已启动
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }

    /**
     * [事件处理] 点击 "创建进程" 按钮时触发
     * 弹出一个复杂的对话框，允许用户输入进程名、选择优先级和可执行文件。
     */
    @FXML
    protected void onCreateProcessClick()
    {
        // 1. 创建自定义对话框
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("创建新进程");
        dialog.setHeaderText("配置新进程参数\n从下方目录树选择 .e 文件或直接输入路径");

        // 2. 定义对话框按钮 (创建/取消)
        ButtonType createButtonType = new ButtonType("创建", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // 3. 创建网格布局来放置输入控件
        GridPane grid = new GridPane();
        grid.setHgap(10); // 水平间距
        grid.setVgap(10); // 垂直间距
        grid.setPadding(new Insets(20, 20, 10, 10));

        // --- 表单控件定义 ---

        // A. 进程名称输入框
        TextField processNameField = new TextField();
        processNameField.setPromptText("进程名称");
        processNameField.setText("新进程"); // 默认值

        // B. 优先级下拉选择框
        ComboBox<Integer> priorityBox = new ComboBox<>();
        priorityBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5)); // 选项 1-5
        priorityBox.setValue(1); // 默认优先级 1
        priorityBox.setMaxWidth(Double.MAX_VALUE);

        // C. 可执行文件路径输入框
        TextField execPathField = new TextField();
        execPathField.setPromptText("例如: /system/exec/p1.e");
        execPathField.setPrefWidth(300);

        // D. 嵌入一个小型文件树视图，方便用户点选文件
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true); // 默认展开根节点
        populateFileSystemTree(rootDir, rootItem); // 递归填充树节点

        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(200); // 限制树的高度

        // --- 事件监听逻辑 ---

        // 监听文件树的选择变化
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
        {
            if (newValue != null)
            {
                String selectedName = newValue.getValue();
                // 只有当选中的是以 .e 结尾的文件(模拟的可执行文件)时
                if (selectedName.endsWith(".e"))
                {
                    // 自动计算全路径并填入路径框
                    String fullPath = buildPathFromTree(newValue);
                    execPathField.setText(fullPath);

                    // 如果进程名还是默认的，自动改成文件名(去掉后缀)
                    if (processNameField.getText().equals("新进程"))
                    {
                        processNameField.setText(selectedName.replace(".e", ""));
                    }
                }
            }
        });

        // --- 将控件添加到网格布局中 ---
        grid.add(new Label("进程名称:"), 0, 0); // 第0列第0行
        grid.add(processNameField, 1, 0);   // 第1列第0行

        grid.add(new Label("优先级:"), 0, 1);
        grid.add(priorityBox, 1, 1);

        grid.add(new Label("文件路径:"), 0, 2);
        grid.add(execPathField, 1, 2);

        grid.add(new Label("文件浏览:"), 0, 3);
        grid.add(fileTreeView, 1, 3);

        // 将布局设置到对话框内容区域
        dialog.getDialogPane().setContent(grid);

        // --- 按钮启用/禁用逻辑 ---
        // 获取对话框中的"创建"按钮对象
        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true); // 默认禁用，防止创建空路径进程

        // 监听路径输入框，只有当路径以 .e 结尾且不为空时，才启用创建按钮
        execPathField.textProperty().addListener((observable, oldValue, newValue) ->
        {
            boolean valid = newValue != null && !newValue.trim().isEmpty() && newValue.trim().endsWith(".e");
            createButton.setDisable(!valid);
        });

        // 4. 显示对话框并等待用户操作 (showAndWait 是阻塞的)
        dialog.showAndWait().ifPresent(response ->
        {
            if (response == createButtonType)
            {
                // 如果用户点了"创建"，获取所有输入值
                String inputName = processNameField.getText().trim();
                final String name = inputName.isEmpty() ? "新进程" : inputName;
                final Integer priority = priorityBox.getValue();
                String execPath = execPathField.getText().trim();

                // 调用内核逻辑：加载可执行文件
                org.example.scau_os_simulation.process.Executable exec =
                        kernel.getFileSystemManager().loadExecutable(execPath);

                if (exec != null)
                {
                    // 调用内核逻辑：创建进程
                    org.example.scau_os_simulation.process.Process p =
                            kernel.getProcessManager().createProcess(name, priority);

                    if (p != null)
                    {
                        // 将加载的代码绑定到进程
                        p.setExecutable(exec);

                        // 记录到撤销管理器，以便支持"撤销创建"
                        kernel.getUndoManager().executeCommand(
                                new org.example.scau_os_simulation.undo.UndoManager.CreateProcessCommand(
                                        kernel.getProcessManager(), p.getPcb().getPid(), name, priority
                                )
                        );

                        // 更新 UI 并在主线程弹出成功提示
                        Platform.runLater(() ->
                        {
                            updateProcessView();
                            showInfo("进程创建成功", "进程 '" + name + "' 已创建 (优先级: " + priority + ")");
                        });
                    } else
                    {
                        showError("创建失败", "无法创建进程 (可能PID耗尽或内存不足)");
                    }
                } else
                {
                    showError("文件错误", "无法加载可执行文件: " + execPath + "\n请确认路径正确且文件存在。");
                }
            }
        });
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("操作系统模拟器");
        alert.setContentText("这是一个基于JavaFX的操作系统模拟框架，用于演示操作系统的基本概念。");
        alert.showAndWait();
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

        // 弹出输入框询问文件名
        TextInputDialog dialog = new TextInputDialog("new.txt");
        dialog.setTitle("创建文件");
        dialog.setHeaderText("在路径 '" + path + "'下创建新文件");
        dialog.setContentText("请输入文件名:");

        final String finalPath = path;
        dialog.showAndWait().ifPresent(name ->
        {
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    // 调用内核：创建文件，默认大小 1KB
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView(); // 手动刷新文件树
                    showInfo("文件创建成功", "文件 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e)
                {
                    showError("文件创建失败", "无法创建文件 '" + name + "': " + e.getMessage());
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

        TextInputDialog dialog = new TextInputDialog("new_directory");
        dialog.setTitle("创建目录");
        dialog.setHeaderText("在路径 '" + path + "' 下创建新目录");
        dialog.setContentText("请输入目录名:");

        final String finalPath = path;
        dialog.showAndWait().ifPresent(name ->
        {
            if (name != null && !name.trim().isEmpty())
            {
                try
                {
                    kernel.getFileSystemManager().createDirectory(finalPath, name);
                    updateFileSystemView();
                    showInfo("目录创建成功", "目录 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e)
                {
                    showError("目录创建失败", "无法创建目录 '" + name + "': " + e.getMessage());
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
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("搜索文件");
        dialog.setHeaderText("在整个文件系统中搜索文件");
        dialog.setContentText("请输入文件名:");

        dialog.showAndWait().ifPresent(fileName ->
        {
            if (fileName != null && !fileName.trim().isEmpty())
            {
                // 递归搜索
                Object result = kernel.getFileSystemManager().getRootDirectory().searchRecursive(fileName.trim());
                if (result != null)
                {
                    // 如果找到，在文件树中选中该节点
                    selectFileInTree(result);
                    showInfo("找到文件", "已在文件树中高亮显示 '" + fileName + "'。");
                } else
                {
                    showWarning("未找到文件", "未找到名为 '" + fileName + "' 的文件。");
                }
            }
        });
    }

    /**
     * [事件处理] 执行命令行指令
     */
    @FXML
    protected void onRunCommandClick()
    {
        String command = commandField.getText();
        if (command != null && !command.trim().isEmpty())
        {
            // 将命令交给内核的命令执行器处理
            kernel.getCommandExecutor().execute(command);
            commandField.clear(); // 清空输入框
            updateAllViews(); // 命令可能改变了任何状态，全量刷新
        }
    }

    /**
     * 辅助方法：打开当前选中文件的编辑器窗口
     */
    private void openSelectedFile()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();

        // 1. 校验是否选中
        if (selectedItem == null)
        {
            return;
        }

        // 2. 根据树节点构建完整路径字符串
        String path = buildPathFromTree(selectedItem);

        // 3. 从内核文件系统获取对象
        Object node = kernel.getFileSystemManager().getFileByPath(path);

        // 4. 判断类型：如果是文件则打开，如果是目录则忽略或展开
        if (node instanceof File)
        {
            File file = (File) node;

            // 5. 创建并显示编辑器窗口
            // 注意：使用 Platform.runLater 确保在 JavaFX 线程中运行
            Platform.runLater(() ->
            {
                try
                {
                    TextEditorWindow editor = new TextEditorWindow(file, path);
                    editor.show(); // 使用 show() 允许同时打开多个窗口(非模态)
                } catch (Exception e)
                {
                    showError("打开失败", "无法打开文件编辑器: " + e.getMessage());
                }
            });
        } else if (node instanceof Directory)
        {
            // 目录双击通常是展开/折叠，TreeView 自带此功能，此处不做处理
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
            systemClockLabel.setText("系统时钟: " + kernel.getSystemClock());
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
    private void updateFileSystemView() {
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();

        // 创建树的根节点
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setGraphic(createIcon("folder")); // 添加文件夹图标
        rootItem.setExpanded(true);

        // 递归填充树结构
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        // 更新磁盘信息
        if (kernel.getFileSystemManager().getFileSystem() != null) {
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
    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem) {
        for (Object child : parent.getChildren()) {
            if (child instanceof Directory) {
                // 子目录 -> 文件夹图标
                Directory dir = (Directory) child;
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                dirItem.setGraphic(createIcon("folder"));
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File) {
                // 文件 -> 根据后缀判断图标
                File f = (File) child;
                TreeItem<String> fileItem = new TreeItem<>(f.getName());

                if (f.getName().endsWith(".e")) {
                    fileItem.setGraphic(createIcon("exec"));
                } else if (f.getName().endsWith(".txt")) {
                    fileItem.setGraphic(createIcon("text"));
                } else {
                    fileItem.setGraphic(createIcon("file"));
                }
                parentItem.getChildren().add(fileItem);
            }
        }
    }

    /**
     * 创建带样式的 Emoji 图标
     */
    private javafx.scene.control.Label createIcon(String type) {
        javafx.scene.control.Label iconLabel = new javafx.scene.control.Label();
        // 设置字体以确保Emoji显示正常，虽然CSS已设置，这里双重保险
        iconLabel.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Segoe UI Symbol';");

        switch (type) {
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
     * 初始化性能图表 (JavaFX + SwingNode 混合使用 JFreeChart)
     */
    private void initializePerformanceChart()
    {
        try
        {
            performanceChart = new PerformanceChartUtil();
            if (performanceChartContainer != null)
            {
                // 将 Swing 的图表面板添加到 JavaFX 容器中
                performanceChartContainer.getChildren().add(performanceChart.getChartPanel());
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
        performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
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
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message)
    {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
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
}
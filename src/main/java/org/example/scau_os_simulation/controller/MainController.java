package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import javafx.scene.layout.VBox;
import javafx.scene.control.TextField;

import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.geometry.Insets;
import javafx.scene.control.Label;


import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.Directory;

import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.device.Device;
import org.example.scau_os_simulation.device.DeviceRequest;
import org.example.scau_os_simulation.device.DeviceType;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 主控制器类 - 操作系统模拟器的UI大脑
 * 
 * 这个类就像是操作系统的"前台接待员"，负责：
 * 1. 把复杂的数据转换成用户能看懂的界面
 * 2. 接收用户的操作指令（点击按钮、输入命令等）
 * 3. 把用户的操作转换成对内核的调用
 * 4. 定时更新界面显示，就像时钟一样保持最新状态
 * 
 * 想象这个类就像一个智能管家：
 * - 它知道家里所有房间的情况（进程、内存、文件系统、设备）
 * - 它会定时检查每个房间的状态
 * - 当主人（用户）下达指令时，它会协调各个部门（内核组件）来完成任务
 * - 它会用图表、表格等直观的方式向主人汇报情况
 * 
 * 主要功能区域：
 * 1. 进程管理区：显示所有进程的状态，可以创建和终止进程
 * 2. 内存管理区：显示内存使用情况，像任务管理器一样
 * 3. 文件系统区：显示文件和目录，像资源管理器一样
 * 4. 设备管理区：显示设备使用情况和等待队列
 * 5. 命令行区：支持输入命令来操作系统
 */
public class MainController implements Initializable {
    /**
     * 内核实例 - 这是与操作系统内核通信的桥梁
     * 
     * 就像智能管家需要知道家里的各个部门一样，控制器需要访问内核来获取系统信息
     * 通过内核，控制器可以：
     * - 获取进程信息
     * - 查看内存使用情况
     * - 管理文件系统
     * - 控制设备状态
     */
    private Kernel kernel;
    
    @FXML
    private TabPane tabPane;
    
    @FXML
    private Tab processTab;
    
    @FXML
    private Tab memoryTab;
    
    @FXML
    private Tab fileSystemTab;
    
    @FXML
    private VBox processContainer;
    
    @FXML
    private VBox memoryContainer;
    
    @FXML
    private VBox fileSystemContainer;
    
    @FXML
    private Button createProcessBtn;
    
    @FXML
    private Button terminateProcessBtn;

    @FXML
    private TableView<PCB> processTableView;
    @FXML
    private TableColumn<PCB, Number> pidColumn;
    @FXML
    private TableColumn<PCB, String> nameColumn;
    @FXML
    private TableColumn<PCB, String> stateColumn;
    @FXML
    private TableColumn<PCB, Number> priorityColumn;
    @FXML
    private TableColumn<PCB, Number> memoryAddressColumn;
    @FXML
    private TableColumn<PCB, Number> memorySizeColumn;

    @FXML
    private Label systemClockLabel;
    @FXML
    private Label runningPidLabel;
    @FXML
    private Label irLabel;
    @FXML
    private Label axLabel;
    @FXML
    private Label tsLabel;
    @FXML
    private ListView<String> outputListView;
    @FXML
    private javafx.scene.control.ListView<String> readyQueueListView;
    @FXML
    private javafx.scene.control.ListView<String> blockedQueueListView;

    @FXML
    private ProgressBar memoryUsageBar;
    @FXML
    private Label memoryInfoLabel;
    @FXML
    private TableView<MemoryBlock> memoryBlockTableView;
    @FXML
    private TableColumn<MemoryBlock, Number> startAddressColumn;
    @FXML
    private TableColumn<MemoryBlock, Number> blockSizeColumn;
    @FXML
    private TableColumn<MemoryBlock, String> processColumn;

    @FXML
    private TreeView<String> fileSystemTreeView;
    @FXML
    private javafx.scene.control.TextField commandField;
    @FXML
    private Button runCommandBtn;

    @FXML
    private ProgressBar diskUsageBar;
    @FXML
    private Label diskInfoLabel;

    @FXML
    private TableView<Device> deviceTableView;
    @FXML
    private TableColumn<Device, String> deviceTypeColumn;
    @FXML
    private TableColumn<Device, String> deviceInUseColumn;
    @FXML
    private TableColumn<Device, Number> devicePidColumn;
    @FXML
    private TableColumn<Device, Number> deviceRemainColumn;

    @FXML
    private TableView<WaitRow> waitQueueTableView;
    @FXML
    private TableColumn<WaitRow, String> waitDeviceColumn;
    @FXML
    private TableColumn<WaitRow, Number> waitPidColumn;
    @FXML
    private TableColumn<WaitRow, Number> waitTimeColumn;

    /**
     * UI更新定时器 - 负责定时刷新界面显示
     * 
     * 就像一个定时闹钟，每300毫秒响一次，提醒管家检查家里情况
     * 使用单线程调度器确保界面更新操作不会冲突
     * 
     * 为什么要定时更新？
     * - 操作系统的状态在不断变化（进程运行、内存分配、文件操作等）
     * - 用户需要看到最新的状态
     * - 手动刷新太麻烦，自动更新更方便
     * 
     * 300毫秒的选择：
     * - 足够快：用户几乎感觉不到延迟
     * - 不太快：不会给系统造成太大负担
     */
    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    
    /**
     * 初始化控制器
     *
     * 在FXML加载完成后自动调用：
     * 1. 获取内核实例以便与系统核心交互
     * 2. 初始化各表格/列表的数据绑定
     * 3. 首次刷新所有视图，展示初始状态
     * 4. 启动UI定时刷新任务，保持界面数据实时更新
     *
     * @param location FXML文件位置
     * @param resources 本地化资源
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化控制器
        kernel = Kernel.getInstance();
        initBindings();
        updateAllViews();
        updateFileSystemView();
        // 周期刷新仅更新“进程/内存/设备”，避免频繁重建文件树导致闪烁与误选
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
        }), 0, 500, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 创建进程按钮点击事件
     *
     * 动作为：
     * 1. 通过进程管理器创建一个新进程
     * 2. 从文件系统加载一个示例可执行文件并赋给进程
     * 3. 刷新进程视图
     */
    @FXML
    protected void onCreateProcessClick() {
        // 创建进程对话框 - 让用户输入进程详细信息
        Dialog<org.example.scau_os_simulation.process.Process> dialog = new Dialog<>();
        dialog.setTitle("创建新进程");
        dialog.setHeaderText("请输入新进程的详细信息");
        
        // 设置按钮类型
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        
        // 创建表单网格
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));
        
        // 添加输入字段
        TextField processNameField = new TextField();
        processNameField.setPromptText("进程名称");
        processNameField.setText("新进程");
        
        // 优先级选择
        ChoiceDialog<Integer> priorityDialog = new ChoiceDialog<>(1, 1, 2, 3, 4, 5);
        priorityDialog.setTitle("选择优先级");
        priorityDialog.setHeaderText("选择进程优先级");
        priorityDialog.setContentText("优先级:");
        
        // 可执行文件选择
        ChoiceDialog<String> execDialog = new ChoiceDialog<>("/system/exec/p1.e", 
            "/system/exec/p1.e", "/system/exec/p2.e", "/system/exec/p3.e");
        execDialog.setTitle("选择可执行文件");
        execDialog.setHeaderText("选择进程要运行的程序");
        execDialog.setContentText("可执行文件:");
        
        grid.add(new Label("进程名称:"), 0, 0);
        grid.add(processNameField, 1, 0);
        
        dialog.getDialogPane().setContent(grid);
        
        // 处理结果
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == javafx.scene.control.ButtonType.OK) {
                final String name = processNameField.getText().trim().isEmpty() ? "新进程" : processNameField.getText().trim();
                
                // 获取优先级
                priorityDialog.showAndWait().ifPresent(priority -> {
                    // 获取可执行文件
                    execDialog.showAndWait().ifPresent(execPath -> {
                        // 创建进程
                        org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess(name, priority);
                        org.example.scau_os_simulation.process.Executable exec = kernel.getFileSystemManager().loadExecutable(execPath);
                        if (p != null && exec != null) {
                            p.setExecutable(exec);
                            Platform.runLater(() -> {
                                updateProcessView();
                                showInfo("进程创建成功", "进程 '" + name + "' 已成功创建并设置优先级为 " + priority);
                            });
                        } else {
                            Platform.runLater(() -> showError("进程创建失败", "无法创建进程或加载可执行文件"));
                        }
                    });
                });
            }
            return null;
        });
        
        dialog.showAndWait();
    }
    
    /**
     * 终止进程按钮点击事件
     *
     * 当用户在进程表中选中一个进程后：
     * 1. 获取选中条目的PCB
     * 2. 调用进程管理器终止该进程
     * 3. 刷新进程视图
     */
    @FXML
    protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            // 创建确认对话框
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("确认终止进程");
            confirmDialog.setHeaderText("确定要终止进程吗？");
            confirmDialog.setContentText("进程 PID: " + selected.getPid() + "\n进程名称: " + selected.getName() + "\n\n此操作不可撤销。");
            
            confirmDialog.showAndWait().ifPresent(response -> {
                if (response == javafx.scene.control.ButtonType.OK) {
                    kernel.getProcessManager().terminateProcess(selected.getPid());
                    updateProcessView();
                    showInfo("进程终止成功", "进程 '" + selected.getName() + "' (PID: " + selected.getPid() + ") 已被终止。");
                }
            });
        } else {
            showWarning("未选择进程", "请先选择要终止的进程。");
        }
    }
    
    // 添加缺失的方法
    /**
     * 菜单：退出
     *
     * 退出应用程序（触发JavaFX生命周期的stop钩子）
     */
    @FXML
    protected void onExitAction() {
        Platform.exit();
    }
    
    /**
     * 菜单：关于
     *
     * 显示关于窗口，介绍本模拟器用途
     */
    @FXML
    protected void onAboutAction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("操作系统模拟器");
        alert.setContentText("这是一个基于JavaFX的操作系统模拟框架，用于演示操作系统的基本概念。");
        alert.showAndWait();
    }
    
    /**
     * 创建文件按钮点击事件
     *
     * 在 `/user` 目录下创建一个示例文件并刷新文件系统视图
     */
    @FXML
    protected void onCreateFileClick() {
                TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            // 如果选中了一个文件，则使用其父目录
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node instanceof File) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }

        TextInputDialog dialog = new TextInputDialog("new.txt");
        dialog.setTitle("创建文件");
        dialog.setHeaderText("在路径 '" + path + "'下创建新文件");
        dialog.setContentText("请输入文件名:");

        final String finalPath = path;
        dialog.showAndWait().ifPresent(name -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView();
                    showInfo("文件创建成功", "文件 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e) {
                    showError("文件创建失败", "无法创建文件 '" + name + "': " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 创建目录按钮点击事件
     *
     * 在 `/user` 目录下创建一个 `docs` 子目录并刷新视图
     */
    @FXML
    protected void onCreateDirectoryClick() {
                TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
            // 如果选中了一个文件，则使用其父目录
            Object node = kernel.getFileSystemManager().getFileByPath(path);
            if (node instanceof File) {
                path = path.substring(0, path.lastIndexOf('/'));
                if (path.isEmpty()) path = "/";
            }
        }

        TextInputDialog dialog = new TextInputDialog("new_directory");
        dialog.setTitle("创建目录");
        dialog.setHeaderText("在路径 '" + path + "' 下创建新目录");
        dialog.setContentText("请输入目录名:");

        final String finalPath = path;
        dialog.showAndWait().ifPresent(name -> {
            if (name != null && !name.trim().isEmpty()) {
                try {
                    kernel.getFileSystemManager().createDirectory(finalPath, name);
                    updateFileSystemView();
                    showInfo("目录创建成功", "目录 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e) {
                    showError("目录创建失败", "无法创建目录 '" + name + "': " + e.getMessage());
                }
            }
        });
    }
    
    /**
     * 删除按钮点击事件
     *
     * 根据树中选定节点计算路径，尝试删除对应的文件或空目录。
     * 若失败则弹窗提示可能原因（目录非空或路径无效）。
     */
    /**
     * 从TreeItem构建文件/目录的完整路径
     *
     * @param item 树中的选中项
     * @return 完整路径字符串
     */
    @FXML
    protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getParent() == null) { // 根节点不允许删除
            showError("无法删除", "不能删除根目录。");
            return;
        }
        
        String path = buildPathFromTree(selected);
        String itemName = selected.getValue();
        
        // 创建确认删除对话框
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("确认删除");
        confirmDialog.setHeaderText("确定要删除 '" + itemName + "' 吗？");
        
        // 判断是文件还是目录
        Object node = kernel.getFileSystemManager().getFileByPath(path);
        String itemType = (node instanceof org.example.scau_os_simulation.filesystem.Directory) ? "目录" : "文件";
        confirmDialog.setContentText("您将要删除 " + itemType + ": \n" + path + "\n\n此操作不可撤销。");
        
        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                boolean success = false;
                try {
                    success = kernel.getFileSystemManager().deleteFile(path) || kernel.getFileSystemManager().deleteDirectory(path);
                } catch (Exception e) {
                    success = false;
                }

                if (success) {
                    updateFileSystemView();
                    showInfo("删除成功", itemType + " '" + itemName + "' 已成功删除。");
                } else {
                    showError("删除失败", "无法删除 " + itemType + "。可能是目录非空或路径无效。");
                }
            }
        });
    }

    /**
     * 执行命令按钮点击事件
     *
     * 支持的简单命令：
     * - `$create /path/name` 在指定路径创建文件
     * - `$mkdir /path/name`  创建目录
     * - `$delete /path/file` 删除文件
     * - `$rmdir /path/dir`   删除空目录
     * - `$type /path/file`   查看可执行文件内容
     * - `$copy src dest`     复制可执行内容到目标
     */
    @FXML
    protected void onRunCommandClick() {
        String cmd = commandField.getText();
        if (cmd == null) return;
        cmd = cmd.trim();
        try {
            if (cmd.startsWith("$create")) {
                String p = cmd.substring(7).trim();
                String name = p;
                String parent = "/";
                int idx = p.lastIndexOf('/');
                if (idx >= 0) { parent = p.substring(0, idx); name = p.substring(idx + 1); }
                kernel.getFileSystemManager().createFile(parent, name, 1);
            } else if (cmd.startsWith("$mkdir")) {
                String p = cmd.substring(6).trim();
                String name = p;
                String parent = "/";
                int idx = p.lastIndexOf('/');
                if (idx >= 0) { parent = p.substring(0, idx); name = p.substring(idx + 1); }
                kernel.getFileSystemManager().createDirectory(parent, name);
            } else if (cmd.startsWith("$delete")) {
                String p = cmd.substring(7).trim();
                kernel.getFileSystemManager().deleteFile(p);
            } else if (cmd.startsWith("$rmdir")) {
                String p = cmd.substring(6).trim();
                boolean ok = kernel.getFileSystemManager().deleteDirectory(p);
                if (!ok) {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText(null);
                    a.setContentText("目录非空或不存在");
                    a.show();
                }
            } else if (cmd.startsWith("$type")) {
                String p = cmd.substring(5).trim();
                org.example.scau_os_simulation.process.Executable e = kernel.getFileSystemManager().loadExecutable(p);
                if (e != null) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < e.length(); i++) sb.append(e.fetch(i)).append("\n");
                    Alert a = new Alert(Alert.AlertType.INFORMATION);
                    a.setHeaderText(p);
                    a.setContentText(sb.toString());
                    a.show();
                } else {
                    org.example.scau_os_simulation.filesystem.File f = kernel.getFileSystemManager().getFileByPath(p);
                    if (f != null) {
                        String content = new String(f.getContent(), java.nio.charset.StandardCharsets.UTF_8);
                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setHeaderText(p);
                        a.setContentText(content);
                        a.show();
                    }
                }
            } else if (cmd.startsWith("$copy")) {
                String args = cmd.substring(5).trim();
                String[] arr = args.split(" ");
                if (arr.length >= 2) {
                    String src = arr[0];
                    String dest = arr[1];
                    // 先尝试复制可执行文件
                    org.example.scau_os_simulation.process.Executable e = kernel.getFileSystemManager().loadExecutable(src);
                    if (e != null) {
                        java.util.List<String> lines = new java.util.ArrayList<>();
                        for (int i = 0; i < e.length(); i++) lines.add(e.fetch(i));
                        String name = dest;
                        String parent = "/";
                        int idx = dest.lastIndexOf('/');
                        if (idx >= 0) { parent = dest.substring(0, idx); name = dest.substring(idx + 1); }
                        kernel.getFileSystemManager().createExecutable(parent, name, lines);
                    } else {
                        // 复制普通文本文件
                        org.example.scau_os_simulation.filesystem.File f = kernel.getFileSystemManager().getFileByPath(src);
                        if (f != null) {
                            String name = dest;
                            String parent = "/";
                            int idx = dest.lastIndexOf('/');
                            if (idx >= 0) { parent = dest.substring(0, idx); name = dest.substring(idx + 1); }
                            org.example.scau_os_simulation.filesystem.File nf = kernel.getFileSystemManager().createFile(parent, name, Math.max(1, f.getSize()));
                            if (nf != null) nf.setContent(f.getContent());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        updateFileSystemView();
    }
    
    /**
     * 更新进程视图
     *
     * 负责刷新“进程管理”选项卡中的所有信息。
     * 想象成管家在检查所有家庭成员（进程）的状态。
     *
     * 它会做以下事情：
     * 1. 更新进程列表：显示所有进程的基本信息（PID、名字、状态、优先级、内存地址、内存大小）。
     * 2. 显示当前运行的进程：显示当前正在运行的进程的PID和指令寄存器（IR）、累加器（AX）、时间片（TS）等信息。
     * 3. 更新就绪队列：显示哪些进程已经准备好运行，正在等待CPU时间。
     * 4. 更新阻塞队列：显示哪些进程因为等待某些事件（如I/O操作）而被阻塞。
     * 5. 更新系统时钟：显示当前系统时钟的时间。
     */
    private void updateProcessView() {
        if (processTableView == null) return;
        javafx.collections.ObservableList<PCB> items = javafx.collections.FXCollections.observableArrayList();
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            items.add(p.getPcb());
        }
        processTableView.setItems(items);
        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        runningPidLabel.setText(running == null ? "-" : String.valueOf(running.getPcb().getPid()));
        irLabel.setText(running == null ? "-" : running.getPcb().getIr());
        axLabel.setText(running == null ? "0" : String.valueOf(running.getPcb().getAx()));
        tsLabel.setText(running == null ? "0" : String.valueOf(running.getPcb().getTimeSlice()));
        systemClockLabel.setText(String.valueOf(kernel.getScheduler().getSystemClock()));
        javafx.collections.ObservableList<String> readyItems = javafx.collections.FXCollections.observableArrayList();
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getReadyQueue()) readyItems.add(String.valueOf(p.getPcb().getPid()));
        readyQueueListView.setItems(readyItems);
        javafx.collections.ObservableList<String> blockedItems = javafx.collections.FXCollections.observableArrayList();
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getBlockedQueue()) blockedItems.add(String.valueOf(p.getPcb().getPid()));
        blockedQueueListView.setItems(blockedItems);
        if (outputListView != null) {
            outputListView.setItems(javafx.collections.FXCollections.observableArrayList(kernel.getOutputLogs()));
        }
    }
    
    /**
     * 更新内存视图
     *
     * 负责刷新“内存管理”选项卡中的所有信息。
     * 就像一个仓库管理员，时刻关注着仓库（内存）的使用情况。
     *
     * 它会做两件事：
     * 1. 更新内存使用率：通过进度条和文本显示总内存中有多少已经被占用。
     * 2. 更新内存块列表：显示每个被分配的内存块的起始地址、大小以及正在使用它的进程。
     */
    private void updateMemoryView() {
        MemoryManager mm = kernel.getMemoryManager();
        double used = 0;
        for (MemoryBlock b : mm.getAllocatedBlocks()) used += b.getSize();
        double total = mm.getMemory().getSize();
        if (memoryUsageBar != null) memoryUsageBar.setProgress(total == 0 ? 0 : used / total);
        if (memoryInfoLabel != null) memoryInfoLabel.setText("已使用: " + (int)used + "KB / 总计: " + (int)total + "KB");
        if (memoryBlockTableView != null) memoryBlockTableView.setItems(javafx.collections.FXCollections.observableArrayList(mm.getAllocatedBlocks()));
    }
    
    /**
     * 更新文件系统视图
     *
     * 负责刷新“文件系统”选项卡中的所有信息。
     * 就像一个图书管理员，整理并展示所有的书籍（文件和目录）。
     *
     * 它会做两件事：
     * 1. 构建并显示文件系统树：从根目录开始，递归地创建文件和目录的树状视图，就像资源管理器一样。
     * 2. 更新磁盘使用率：通过进度条和文本显示磁盘空间的使用情况。
     */
    private void updateFileSystemView() {
        java.util.Set<String> expandedPaths = new java.util.HashSet<>();
        String selectedPath = null;
        if (fileSystemTreeView != null) {
            TreeItem<String> oldRoot = fileSystemTreeView.getRoot();
            if (oldRoot != null) {
                collectExpandedPaths(oldRoot, expandedPaths);
            }
            TreeItem<String> sel = fileSystemTreeView.getSelectionModel().getSelectedItem();
            if (sel != null) selectedPath = buildPathFromTree(sel);
        }
        Directory root = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = buildTree(root);
        rootItem.setExpanded(true);
        if (fileSystemTreeView != null) {
            fileSystemTreeView.setRoot(rootItem);
            for (String p : expandedPaths) {
                TreeItem<String> ti = findItemByPath(rootItem, p);
                if (ti != null) ti.setExpanded(true);
            }
            if (selectedPath != null) {
                TreeItem<String> target = findItemByPath(rootItem, selectedPath);
                if (target != null) fileSystemTreeView.getSelectionModel().select(target);
            }
        }
        double used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
        double total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
        diskUsageBar.setProgress(total == 0 ? 0 : used / total);
        diskInfoLabel.setText("已使用: " + (int)used + "KB / 总计: " + (int)total + "KB");
    }

    /**
     * 构建文件系统树
     *
     * 从传入目录开始递归遍历，将子目录与文件转换为树节点用于TreeView展示。
     *
     * @param dir 起始目录
     * @return 对应的树节点
     */
    private TreeItem<String> buildTree(Directory dir) {
        TreeItem<String> item = new TreeItem<>(dir.getName());
        for (Object child : dir.getChildren()) {
            if (child instanceof Directory) item.getChildren().add(buildTree((Directory) child));
            else if (child instanceof File) item.getChildren().add(new TreeItem<>(((File) child).getName()));
        }
        return item;
    }

    /**
     * 初始化数据绑定
     *
     * 这个方法是连接数据和视图的桥梁，它告诉每个表格列应该显示哪个数据。
     * 想象成给每个展示柜的每个格子贴上标签，告诉它应该展示什么物品。
     *
     * 它是如何工作的？
     * - `setCellValueFactory` 就是贴标签的动作。
     * - `c -\u003e new Simple...Property(...)` 是标签的内容，它指定了要展示的数据源。
     *   - `c.getValue()` 获取到一行的数据对象（比如一个PCB或一个MemoryBlock）。
     *   - `c.getValue().getXXX()` 从数据对象中取出具体的属性值（比如PID、状态等）。
     *
     * 这种方式（数据绑定）的好处是，一旦绑定关系建立，当数据源（例如进程列表）更新时，
     * JavaFX会自动刷新表格视图，我们不需要手动去一行一行地更新UI，非常高效。
     */
    private void initBindings() {
        if (pidColumn != null) pidColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPid()));
        if (nameColumn != null) nameColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        if (stateColumn != null) stateColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getState().name()));
        if (priorityColumn != null) priorityColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPriority()));
        if (memoryAddressColumn != null) memoryAddressColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getMemoryAddress()));
        if (memorySizeColumn != null) memorySizeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getMemorySize()));

        if (startAddressColumn != null) startAddressColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getStartAddress()));
        if (blockSizeColumn != null) blockSizeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getSize()));
        if (processColumn != null) processColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(findProcessByBlock(c.getValue())));

        if (deviceTypeColumn != null) deviceTypeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getType().name()));
        if (deviceInUseColumn != null) deviceInUseColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().isInUse() ? "是" : "否"));
        if (devicePidColumn != null) devicePidColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getUsedByPid()));
        if (deviceRemainColumn != null) deviceRemainColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getRemainingTime()));
        if (waitDeviceColumn != null) waitDeviceColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().device));
        if (waitPidColumn != null) waitPidColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().pid));
        if (waitTimeColumn != null) waitTimeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().time));
    }

    /**
     * 根据内存块查找对应的进程名
     *
     * 这个方法就像一个侦探，你给它一个内存地址和大小（一块内存），
     * 它会去搜寻所有的进程，看看哪个进程的“房产证”（PCB中的内存信息）与这块内存匹配。
     *
     * @param block 要查找的内存块
     * @return 拥有该内存块的进程名；如果没找到，则返回空字符串
     */
    private String findProcessByBlock(MemoryBlock block) {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize()) {
                return p.getPcb().getName();
            }
        }
        return "";
    }

    /**
     * 收集所有已展开节点的路径
     *
     * 作用：在刷新文件系统树之前记录用户展开的层级，刷新后再恢复，避免树“缩回”。
     * @param node 当前遍历的节点
     * @param out  输出集合，存放形如 `/a/b` 的路径字符串
     */
    private void collectExpandedPaths(TreeItem<String> node, java.util.Set<String> out) {
        if (node == null) return;
        if (node.isExpanded()) out.add(buildPathFromTree(node));
        for (TreeItem<String> c : node.getChildren()) collectExpandedPaths(c, out);
    }

    /**
     * 按路径在树中查找节点
     *
     * @param root 根节点
     * @param path 绝对路径（如 `/system/exec`）
     * @return 匹配的树节点；未找到返回 null
     */
    private TreeItem<String> findItemByPath(TreeItem<String> root, String path) {
        if (root == null) return null;
        if (path == null || path.isEmpty() || "/".equals(path)) return root;
        String[] parts = path.split("/");
        TreeItem<String> cur = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            TreeItem<String> next = null;
            for (TreeItem<String> child : cur.getChildren()) {
                if (part.equals(child.getValue())) { next = child; break; }
            }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }

    /**
     * 更新所有视图
     *
     * 这是一个“总动员”方法，它会一次性调用所有独立的视图更新方法，
     * 确保进程、内存、文件系统和设备这四个主要界面的信息都得到刷新。
     *
     * 就像管家每天早上会把所有房间都巡视一遍，确保一切都井井有条。
     */
    private void updateAllViews() {
        updateProcessView();
        updateMemoryView();
        updateDeviceView();
    }

    /**
     * 更新设备视图
     *
     * 负责刷新“设备管理”选项卡中的所有信息。
     * 想象成管家在检查所有连接到家里的设备（打印机、扫描仪等）的状态。
     *
     * 它会做两件事：
     * 1. 更新设备列表：显示每个设备是否正在被占用，被哪个进程占用，以及剩余工作时间。
     * 2. 更新设备等待队列：显示哪些进程正在排队等待使用某个设备。
     */
    private void updateDeviceView() {
        if (deviceTableView == null) return;
        java.util.List<Device> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<org.example.scau_os_simulation.device.DeviceType, java.util.List<Device>> e : kernel.getDeviceManager().getDevices().entrySet()) {
            list.addAll(e.getValue());
        }
        deviceTableView.setItems(javafx.collections.FXCollections.observableArrayList(list));
        if (waitQueueTableView != null) {
            java.util.List<WaitRow> rows = new java.util.ArrayList<>();
            for (java.util.Map.Entry<org.example.scau_os_simulation.device.DeviceType, java.util.Deque<org.example.scau_os_simulation.device.DeviceRequest>> e : kernel.getDeviceManager().getWaitQueues().entrySet()) {
                String dev = e.getKey().name();
                for (org.example.scau_os_simulation.device.DeviceRequest r : e.getValue()) {
                    rows.add(new WaitRow(dev, r.pid(), r.timeUnits()));
                }
            }
            waitQueueTableView.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        }
    }

    /**
     * 从树节点反推构建完整路径
     *
     * 由选中的TreeItem一路向上追溯到根节点，将每层名称按顺序拼接为绝对路径。
     *
     * @param item 选中的树节点
     * @return 形如 `/a/b/c` 的路径字符串
     */
    /**
     * 从树节点构建绝对路径字符串
     *
     * 规则：忽略可视化根节点名 `root`，将其下的层级用 `/` 连接。
     * @param item 选中的树节点
     * @return 形如 `/a/b/c` 的路径；若是根则返回 `/`
     */
    private String buildPathFromTree(TreeItem<String> item) {
        java.util.Deque<String> parts = new java.util.ArrayDeque<>();
        TreeItem<String> cur = item;
        while (cur != null) {
            String val = cur.getValue();
            if (val != null && !"root".equals(val)) parts.addFirst(val);
            cur = cur.getParent();
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append('/').append(p);
        }
        if (sb.length() == 0) return "/";
        return sb.toString();
    }

    /**
     * 设备等待队列表格行模型
     *
     * 字段：设备类型字符串、进程PID、预计剩余时间片。
     */
    private static class WaitRow {
        final String device;
        final int pid;
        final int time;
        WaitRow(String device, int pid, int time) {
            this.device = device;
            this.pid = pid;
            this.time = time;
        }
    }
    
    /**
     * 显示信息对话框
     * 
     * @param title 对话框标题
     * @param message 信息内容
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 显示错误对话框
     * 
     * @param title 对话框标题
     * @param message 错误信息
     */
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * 显示警告对话框
     * 
     * @param title 对话框标题
     * @param message 警告信息
     */
    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

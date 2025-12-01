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
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.Node;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;

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

import java.util.concurrent.Executors;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable
{


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
    private VBox performanceChartContainer;

    @FXML
    private Button startSystemBtn; // 对应 FXML 中的 fx:id
    @FXML
    private Button stopSystemBtn;  // 对应 FXML 中的 fx:id
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
    private Label systemStatusLabel; // 建议在界面加一个 Label 显示状态

    @FXML
    private ListView<String> outputListView;
    @FXML
    private ListView<String> readyQueueListView;
    @FXML
    private ListView<String> blockedQueueListView;

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
    private Label fragmentationLabel;

    @FXML
    private TreeView<String> fileSystemTreeView;

    private Object clipboardFile;
    @FXML
    private TextField commandField;
    @FXML
    private Button runCommandBtn;
    @FXML
    private Button defragmentBtn;
    @FXML
    private Button undoBtn;
    @FXML
    private Button redoBtn;
    @FXML
    private Button createFileBtn;
    @FXML
    private Button createDirectoryBtn;
    @FXML
    private Button deleteFileBtn;
    @FXML
    private Button copyFileBtn;
    @FXML
    private Button pasteFileBtn;
    @FXML
    private Button searchFileBtn;

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

    @FXML
    private ListView<String> operationLogListView;

    @FXML
    private ProgressBar cpuUtilizationBar;
    @FXML
    private ProgressBar systemLoadBar;
    @FXML
    private Label cpuUtilizationLabel;
    @FXML
    private Label systemLoadLabel;
    @FXML
    private Label avgCpuLabel;
    @FXML
    private Label avgMemoryLabel;
    @FXML
    private Label peakCpuLabel;
    @FXML
    private Label peakMemoryLabel;

    private Kernel kernel;

    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartUtil performanceChart;

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        kernel = Kernel.getInstance();
        initBindings();
        initializePerformanceChart();

        updateAllViews();
        updateFileSystemView(); // 初始化时更新一次即可

        // 1. 绑定双击事件：双击文件打开编辑器
        fileSystemTreeView.setOnMouseClicked(event ->
        {
            if (Kernel.getInstance().getScheduler() == null || !Kernel.getInstance().getScheduler().isRunning()) {
                if (event.getClickCount() == 2) {
                    showWarning("系统未启动", "请先点击顶部的 [▶ 启动系统] 按钮。");
                }
                return;
            }

            if (event.getClickCount() == 2 && event.getButton() == javafx.scene.input.MouseButton.PRIMARY)
            {
                openSelectedFile();
            }
        });

        // 2. 绑定右键菜单：添加“编辑”选项
        javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
        javafx.scene.control.MenuItem editItem = new javafx.scene.control.MenuItem("编辑 / 查看");
        javafx.scene.control.MenuItem deleteItem = new javafx.scene.control.MenuItem("删除");

        editItem.setOnAction(e -> openSelectedFile());
        deleteItem.setOnAction(e -> onDeleteClick()); // 复用已有的删除方法

        contextMenu.getItems().addAll(editItem, deleteItem);
        fileSystemTreeView.setContextMenu(contextMenu);
        contextMenu.setOnShowing(e -> {
            boolean isRunning = Kernel.getInstance().getScheduler() != null &&
                    Kernel.getInstance().getScheduler().isRunning();

            // 遍历所有菜单项并根据运行状态禁用/启用
            for (javafx.scene.control.MenuItem item : contextMenu.getItems()) {
                item.setDisable(!isRunning);
            }
        });

        updateControlButtonsState(false);

        // 周期刷新仅更新"进程/内存/设备"，【修改点】移除 updateFileSystemView()
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() ->
        {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
            updateOperationLogView();
            updatePerformanceChart();
            updatePerformanceMetrics();
            // updateFileSystemView(); <--- 已移除，避免重建文件树导致无法展开
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

        // 1. 进程管理按钮
        if (createProcessBtn != null) createProcessBtn.setDisable(disable);
        if (terminateProcessBtn != null) terminateProcessBtn.setDisable(disable);

        // 2. 内存/撤销按钮
        if (defragmentBtn != null) defragmentBtn.setDisable(disable);
        if (undoBtn != null) undoBtn.setDisable(disable);
        if (redoBtn != null) redoBtn.setDisable(disable);

        // 3. 命令行按钮
        if (runCommandBtn != null) runCommandBtn.setDisable(disable);
        if (commandField != null) commandField.setDisable(disable);

        // 4. 文件操作按钮 (如果你希望文件操作也必须在启动后进行)
        if (createFileBtn != null) createFileBtn.setDisable(disable);
        if (createDirectoryBtn != null) createDirectoryBtn.setDisable(disable);
        if (deleteFileBtn != null) deleteFileBtn.setDisable(disable);

        // 注意：startSystemBtn 和 stopSystemBtn 不需要在这里控制，
        // 它们在自己的点击事件里单独控制
    }

    @FXML
    protected void onStartSystemClick()
    {
        // 1. 启动内核
        Kernel.getInstance().start();

        // 2. 更新按钮状态
        startSystemBtn.setDisable(true); // 启动后禁用启动按钮
        if (stopSystemBtn != null)
        {
            stopSystemBtn.setDisable(false); // 启用暂停按钮
        }
        updateControlButtonsState(true);

        // 3. 提示用户
        showInfo("系统已启动", "CPU 开始运行，调度器已激活。");
    }

    @FXML
    protected void onCreateProcessClick()
    {
        // 1. 创建对话框
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("创建新进程");
        dialog.setHeaderText("配置新进程参数\n从下方目录树选择 .e 文件或直接输入路径");

        // 2. 设置按钮类型 (OK 和 Cancel)
        ButtonType createButtonType = new ButtonType("创建", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButtonType, ButtonType.CANCEL);

        // 3. 创建布局网格
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        // --- 表单控件 ---

        // A. 进程名称
        TextField processNameField = new TextField();
        processNameField.setPromptText("进程名称");
        processNameField.setText("新进程");

        // B. 优先级选择 (使用 ComboBox 替代 ChoiceDialog)
        ComboBox<Integer> priorityBox = new ComboBox<>();
        priorityBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5));
        priorityBox.setValue(1); // 默认优先级
        priorityBox.setMaxWidth(Double.MAX_VALUE);

        // C. 可执行文件路径输入框
        TextField execPathField = new TextField();
        execPathField.setPromptText("例如: /system/exec/p1.e");
        execPathField.setPrefWidth(300);

        // D. 文件系统树形视图 (复用现有的 populateFileSystemTree 方法)
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem); // 复用类中已有的方法填充树

        TreeView<String> fileTreeView = new TreeView<>(rootItem);
        fileTreeView.setPrefHeight(200); // 限制高度，避免对话框太长

        // --- 事件监听逻辑 ---

        // 监听树的选择：当用户点击树节点时，自动填充路径框
        fileTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) ->
        {
            if (newValue != null)
            {
                String selectedName = newValue.getValue();
                // 只有当选中的是以 .e 结尾的文件时，才自动填入
                if (selectedName.endsWith(".e"))
                {
                    // 复用类中已有的 buildPathFromTree 方法获取绝对路径
                    String fullPath = buildPathFromTree(newValue);
                    execPathField.setText(fullPath);

                    // 如果进程名还是默认值，且用户选了文件，顺便把进程名改成文件名(去掉.e)
                    if (processNameField.getText().equals("新进程"))
                    {
                        processNameField.setText(selectedName.replace(".e", ""));
                    }
                }
            }
        });

        // --- 布局组装 ---
        grid.add(new Label("进程名称:"), 0, 0);
        grid.add(processNameField, 1, 0);

        grid.add(new Label("优先级:"), 0, 1);
        grid.add(priorityBox, 1, 1);

        grid.add(new Label("文件路径:"), 0, 2);
        grid.add(execPathField, 1, 2);

        grid.add(new Label("文件浏览:"), 0, 3); // 标签
        grid.add(fileTreeView, 1, 3);        // 树放在第二列

        dialog.getDialogPane().setContent(grid);

        // --- 按钮状态验证 ---
        // 获取对话框中的"创建"按钮
        Node createButton = dialog.getDialogPane().lookupButton(createButtonType);
        createButton.setDisable(true); // 默认禁用，直到输入有效的 .e 路径

        // 监听路径输入框，只有以 .e 结尾且不为空时才启用创建按钮
        execPathField.textProperty().addListener((observable, oldValue, newValue) ->
        {
            boolean valid = newValue != null && !newValue.trim().isEmpty() && newValue.trim().endsWith(".e");
            createButton.setDisable(!valid);
        });

        // 4. 显示对话框并处理结果
        dialog.showAndWait().ifPresent(response ->
        {
            if (response == createButtonType)
            {
                // 获取用户输入
                String inputName = processNameField.getText().trim();
                // 使用 final 变量（或不修改的变量）来存储最终结果
                final String name = inputName.isEmpty() ? "新进程" : inputName;
                final Integer priority = priorityBox.getValue();
                String execPath = execPathField.getText().trim();

                // 执行加载逻辑
                // 1. 加载可执行文件
                org.example.scau_os_simulation.process.Executable exec =
                        kernel.getFileSystemManager().loadExecutable(execPath);

                if (exec != null)
                {
                    // 2. 创建进程
                    org.example.scau_os_simulation.process.Process p =
                            kernel.getProcessManager().createProcess(name, priority);

                    if (p != null)
                    {
                        p.setExecutable(exec);

                        // 记录撤销操作
                        kernel.getUndoManager().executeCommand(
                                new org.example.scau_os_simulation.undo.UndoManager.CreateProcessCommand(
                                        kernel.getProcessManager(), p.getPcb().getPid(), name, priority
                                )
                        );

                        // 更新 UI
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

    @FXML
    protected void onTerminateProcessClick()
    {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null)
        {
            Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
            confirmDialog.setTitle("确认终止进程");
            confirmDialog.setHeaderText("确定要终止进程吗？");
            confirmDialog.setContentText("进程 PID: " + selected.getPid() + "\n进程名称: " + selected.getName() + "\n\n此操作不可撤销。");

            confirmDialog.showAndWait().ifPresent(response ->
            {
                if (response == javafx.scene.control.ButtonType.OK)
                {
                    kernel.getProcessManager().terminateProcess(selected.getPid());
                    updateProcessView();
                    showInfo("进程终止成功", "进程 '" + selected.getName() + "' (PID: " + selected.getPid() + ") 已被终止。");
                }
            });
        } else
        {
            showWarning("未选择进程", "请先选择要终止的进程。");
        }
    }

    @FXML
    protected void onExitAction()
    {
        Platform.exit();
    }

    @FXML
    protected void onAboutAction()
    {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("操作系统模拟器");
        alert.setContentText("这是一个基于JavaFX的操作系统模拟框架，用于演示操作系统的基本概念。");
        alert.showAndWait();
    }

    @FXML
    protected void onCreateFileClick()
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
                    kernel.getFileSystemManager().createFile(finalPath, name, 1);
                    updateFileSystemView(); // 手动刷新
                    showInfo("文件创建成功", "文件 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e)
                {
                    showError("文件创建失败", "无法创建文件 '" + name + "': " + e.getMessage());
                }
            }
        });
    }

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
                    updateFileSystemView(); // 手动刷新
                    showInfo("目录创建成功", "目录 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e)
                {
                    showError("目录创建失败", "无法创建目录 '" + name + "': " + e.getMessage());
                }
            }
        });
    }

    @FXML
    protected void onDeleteClick()
    {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getParent() == null)
        {
            showError("无法删除", "不能删除根目录。");
            return;
        }

        String path = buildPathFromTree(selected);
        String itemName = selected.getValue();

        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle("确认删除");
        confirmDialog.setHeaderText("确定要删除 '" + itemName + "' 吗？");

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
                    success = kernel.getFileSystemManager().deleteFile(path) || kernel.getFileSystemManager().deleteDirectory(path);
                } catch (Exception e)
                {
                    success = false;
                }

                if (success)
                {
                    updateFileSystemView(); // 手动刷新
                    showInfo("删除成功", itemType + " '" + itemName + "' 已成功删除。");
                } else
                {
                    showError("删除失败", "无法删除 " + itemType + "。可能是目录非空或路径无效。");
                }
            }
        });
    }

    @FXML
    protected void onDefragmentClick()
    {
        kernel.getMemoryManager().defragment();
        updateMemoryView();
        showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    @FXML
    protected void onUndoClick()
    {
        kernel.getUndoManager().undo();
        updateAllViews();
    }

    @FXML
    protected void onRedoClick()
    {
        kernel.getUndoManager().redo();
        updateAllViews();
    }

    @FXML
    protected void onCopyFileClick()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null)
        {
            String path = buildPathFromTree(selectedItem);
            clipboardFile = kernel.getFileSystemManager().getFileByPath(path);
            showInfo("复制成功", "'" + selectedItem.getValue() + "' 已复制到剪贴板。");
        } else
        {
            showWarning("未选择文件", "请先选择要复制的文件或目录。");
        }
    }

    @FXML
    protected void onPasteFileClick()
    {
        if (clipboardFile != null)
        {
            TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
            String targetPath = "/";
            if (selectedItem != null)
            {
                targetPath = buildPathFromTree(selectedItem);
                Object targetNode = kernel.getFileSystemManager().getFileByPath(targetPath);
                if (!(targetNode instanceof Directory))
                {
                    targetPath = targetPath.substring(0, targetPath.lastIndexOf('/'));
                    if (targetPath.isEmpty()) targetPath = "/";
                }
            }

            try
            {
                kernel.getFileSystemManager().paste(clipboardFile, targetPath);
                updateFileSystemView(); // 手动刷新
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
                Object result = kernel.getFileSystemManager().getRootDirectory().searchRecursive(fileName.trim());
                if (result != null)
                {
                    selectFileInTree(result);
                    showInfo("找到文件", "已在文件树中高亮显示 '" + fileName + "'。");
                } else
                {
                    showWarning("未找到文件", "未找到名为 '" + fileName + "' 的文件。");
                }
            }
        });
    }

    @FXML
    protected void onRunCommandClick()
    {
        String command = commandField.getText();
        if (command != null && !command.trim().isEmpty())
        {
            kernel.getCommandExecutor().execute(command);
            commandField.clear();
            updateAllViews();
        }
    }

    /**
     * 打开当前选中文件的编辑器
     */
    private void openSelectedFile()
    {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();

        // 1. 校验是否选中
        if (selectedItem == null)
        {
            return;
        }

        // 2. 获取完整路径
        String path = buildPathFromTree(selectedItem);

        // 3. 从内核文件系统获取对象
        Object node = kernel.getFileSystemManager().getFileByPath(path);

        // 4. 判断类型：如果是文件则打开，如果是目录则忽略或展开
        if (node instanceof File)
        {
            File file = (File) node;

            // 5. 创建并显示编辑器窗口
            // 注意：使用 Platform.runLater 确保在 JavaFX 线程中运行（虽然通常已经是）
            Platform.runLater(() ->
            {
                try
                {
                    TextEditorWindow editor = new TextEditorWindow(file, path);
                    editor.show(); // 使用 show() 允许同时打开多个窗口，不要用 showAndWait()
                } catch (Exception e)
                {
                    showError("打开失败", "无法打开文件编辑器: " + e.getMessage());
                }
            });
        } else if (node instanceof Directory)
        {
            // 如果是目录，双击通常是展开/折叠，TreeView 自带此功能，此处可不做处理
            // 或者可以在这里提示“无法编辑目录”
        }
    }

    private void updateAllViews()
    {
        updateProcessView();
        updateMemoryView();
        updateDeviceView();
        updateFileSystemView();
        updateOperationLogView();
        updatePerformanceMetrics();
    }

    private void updateProcessView()
    {
        processTableView.getItems().setAll(
                kernel.getProcessManager().getProcesses().stream()
                        .map(org.example.scau_os_simulation.process.Process::getPcb)
                        .collect(java.util.stream.Collectors.toList())
        );

        readyQueueListView.getItems().setAll(
                kernel.getProcessManager().getReadyQueue().stream()
                        .map(p -> "PID: " + p.getPcb().getPid() + " (优先级: " + p.getPcb().getPriority() + ")")
                        .collect(java.util.stream.Collectors.toList())
        );
        blockedQueueListView.getItems().setAll(
                kernel.getProcessManager().getBlockedQueue().stream()
                        .map(p -> "PID: " + p.getPcb().getPid())
                        .collect(java.util.stream.Collectors.toList())
        );

        org.example.scau_os_simulation.process.Process running = kernel.getProcessManager().getRunning();
        if (running != null)
        {
            PCB pcb = running.getPcb();
            systemClockLabel.setText("系统时钟: " + kernel.getSystemClock());
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr());
            axLabel.setText("AX: " + pcb.getAx());
            tsLabel.setText("时间片: " + kernel.getTimeSlice());
        } else
        {
            runningPidLabel.setText("运行中PID: 无");
        }
    }

    private void updateMemoryView()
    {
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

    private void updateDeviceView()
    {
        deviceTableView.getItems().setAll(kernel.getDeviceManager().getAllDevices());

        List<WaitRow> waitRows = new ArrayList<>();
        for (DeviceType t : DeviceType.values())
        {
            for (DeviceRequest request : kernel.getDeviceManager().getWaitingQueue(t))
            {
                waitRows.add(new WaitRow(t.toString(), request.getPid(), request.getExecutionTime()));
            }
        }
        waitQueueTableView.getItems().setAll(waitRows);
    }

    private void updateFileSystemView()
    {
        Directory rootDir = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = new TreeItem<>(rootDir.getName());
        rootItem.setExpanded(true);
        populateFileSystemTree(rootDir, rootItem);
        fileSystemTreeView.setRoot(rootItem);

        int totalDiskSpace = kernel.getFileSystemManager().getFileSystem().getTotalSize();
        int usedDiskSpace = kernel.getFileSystemManager().getFileSystem().getUsedSize();
        double diskUsage = (double) usedDiskSpace / totalDiskSpace;
        diskUsageBar.setProgress(diskUsage);
        diskInfoLabel.setText(String.format("已用: %d KB / 总量: %d KB", usedDiskSpace, totalDiskSpace));
    }

    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem)
    {
        for (Object child : parent.getChildren())
        {
            if (child instanceof Directory)
            {
                Directory dir = (Directory) child;
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File)
            {
                parentItem.getChildren().add(new TreeItem<>(((File) child).getName()));
            }
        }
    }

    private void updateOperationLogView()
    {
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        outputListView.getItems().setAll(kernel.getOutputLogs()); // 同时更新执行结果日志
    }

    private void initializePerformanceChart()
    {
        try
        {
            performanceChart = new PerformanceChartUtil();
            if (performanceChartContainer != null)
            {
                performanceChartContainer.getChildren().add(performanceChart.getChartPanel());
            }
        } catch (Exception e)
        {
            System.err.println("性能图表初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePerformanceChart()
    {
        performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
    }

    private void updatePerformanceMetrics()
    {
        // 获取 PerformanceMonitor 的数据
        // 注意：如果你之前重构了 PerformanceMonitor，确保这里调用正确
        PerformanceMonitor pm = kernel.getPerformanceMonitor();

        // 获取平均值等统计数据
        double avgCpu = pm.getAverageCpuUtilization();
        double avgMem = pm.getAverageMemoryUtilization();
        double peakCpu = pm.getPeakCpuUtilization();
        double peakMem = pm.getPeakMemoryUtilization();

        // 更新文本标签 (乘 100 显示百分号)
        avgCpuLabel.setText(String.format("平均CPU: %.2f%%", avgCpu * 100));
        avgMemoryLabel.setText(String.format("平均内存: %.2f%%", avgMem * 100));
        peakCpuLabel.setText(String.format("峰值CPU: %.2f%%", peakCpu * 100));
        peakMemoryLabel.setText(String.format("峰值内存: %.2f%%", peakMem * 100));

        // 更新实时状态条
        double currentCpu = kernel.getCpuUtilization();
        double currentLoad = kernel.getSystemLoad();

        cpuUtilizationBar.setProgress(currentCpu); // 进度条需要 0.0-1.0
        systemLoadBar.setProgress(currentLoad);

        cpuUtilizationLabel.setText(String.format("CPU: %.2f%%", currentCpu * 100));
        systemLoadLabel.setText(String.format("负载: %.2f", currentLoad)); // 负载通常直接显示数值
    }

    private void initBindings()
    {
        pidColumn.setCellValueFactory(cellData -> cellData.getValue().pidProperty());
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().nameProperty());
        stateColumn.setCellValueFactory(cellData -> cellData.getValue().stateProperty());
        priorityColumn.setCellValueFactory(cellData -> cellData.getValue().priorityProperty());
        memoryAddressColumn.setCellValueFactory(cellData -> cellData.getValue().memoryAddressProperty());
        memorySizeColumn.setCellValueFactory(cellData -> cellData.getValue().memorySizeProperty());

        startAddressColumn.setCellValueFactory(cellData -> cellData.getValue().startAddressProperty());
        blockSizeColumn.setCellValueFactory(cellData -> cellData.getValue().sizeProperty());
        processColumn.setCellValueFactory(cellData ->
        {
            int pid = findProcessIdForMemoryBlock(cellData.getValue());
            return new javafx.beans.property.SimpleStringProperty(pid >= 0 ? String.valueOf(pid) : "N/A");
        });

        deviceTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType().toString()));
        deviceInUseColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isBusy() ? "是" : "否"));
        devicePidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getCurrentUserPid()));
        deviceRemainColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getRemainingTime()));

        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time));
    }

    private int findProcessIdForMemoryBlock(MemoryBlock block)
    {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses())
        {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize())
            {
                return p.getPcb().getPid();
            }
        }
        return -1;
    }

    private String buildPathFromTree(TreeItem<String> item)
    {
        StringBuilder path = new StringBuilder();
        while (item != null && item.getParent() != null)
        {
            path.insert(0, "/" + item.getValue());
            item = item.getParent();
        }
        return path.length() > 0 ? path.toString() : "/";
    }

    private TreeItem<String> findItemByPath(TreeItem<String> root, String path)
    {
        if (path.equals("/")) return root;

        String[] parts = path.split("/");
        TreeItem<String> current = root;

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
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

import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.performance.PerformanceChartUtil;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.Directory;
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

public class MainController implements Initializable {

    private Kernel kernel;

    @FXML private TabPane tabPane;
    @FXML private Tab processTab;
    @FXML private Tab memoryTab;
    @FXML private Tab fileSystemTab;
    @FXML private VBox processContainer;
    @FXML private VBox memoryContainer;
    @FXML private VBox fileSystemContainer;
    @FXML private VBox performanceChartContainer;

    @FXML private Button createProcessBtn;
    @FXML private Button terminateProcessBtn;

    @FXML private TableView<PCB> processTableView;
    @FXML private TableColumn<PCB, Number> pidColumn;
    @FXML private TableColumn<PCB, String> nameColumn;
    @FXML private TableColumn<PCB, String> stateColumn;
    @FXML private TableColumn<PCB, Number> priorityColumn;
    @FXML private TableColumn<PCB, Number> memoryAddressColumn;
    @FXML private TableColumn<PCB, Number> memorySizeColumn;

    @FXML private Label systemClockLabel;
    @FXML private Label runningPidLabel;
    @FXML private Label irLabel;
    @FXML private Label axLabel;
    @FXML private Label tsLabel;
    @FXML private ListView<String> outputListView;
    @FXML private ListView<String> readyQueueListView;
    @FXML private ListView<String> blockedQueueListView;

    @FXML private ProgressBar memoryUsageBar;
    @FXML private Label memoryInfoLabel;
    @FXML private TableView<MemoryBlock> memoryBlockTableView;
    @FXML private TableColumn<MemoryBlock, Number> startAddressColumn;
    @FXML private TableColumn<MemoryBlock, Number> blockSizeColumn;
    @FXML private TableColumn<MemoryBlock, String> processColumn;
    @FXML private Label fragmentationLabel;

    @FXML private TreeView<String> fileSystemTreeView;

    private Object clipboardFile;
    @FXML private TextField commandField;
    @FXML private Button runCommandBtn;
    @FXML private Button defragmentBtn;
    @FXML private Button undoBtn;
    @FXML private Button redoBtn;
    @FXML private Button copyFileBtn;
    @FXML private Button pasteFileBtn;
    @FXML private Button searchFileBtn;

    @FXML private ProgressBar diskUsageBar;
    @FXML private Label diskInfoLabel;

    @FXML private TableView<Device> deviceTableView;
    @FXML private TableColumn<Device, String> deviceTypeColumn;
    @FXML private TableColumn<Device, String> deviceInUseColumn;
    @FXML private TableColumn<Device, Number> devicePidColumn;
    @FXML private TableColumn<Device, Number> deviceRemainColumn;

    @FXML private TableView<WaitRow> waitQueueTableView;
    @FXML private TableColumn<WaitRow, String> waitDeviceColumn;
    @FXML private TableColumn<WaitRow, Number> waitPidColumn;
    @FXML private TableColumn<WaitRow, Number> waitTimeColumn;

    @FXML private ListView<String> operationLogListView;

    @FXML private ProgressBar cpuUtilizationBar;
    @FXML private ProgressBar systemLoadBar;
    @FXML private Label cpuUtilizationLabel;
    @FXML private Label systemLoadLabel;
    @FXML private Label avgCpuLabel;
    @FXML private Label avgMemoryLabel;
    @FXML private Label peakCpuLabel;
    @FXML private Label peakMemoryLabel;

    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    private PerformanceChartUtil performanceChart;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        kernel = Kernel.getInstance();
        initBindings();
        initializePerformanceChart();

        updateAllViews();
        updateFileSystemView(); // 初始化时更新一次即可

        // 周期刷新仅更新"进程/内存/设备"，【修改点】移除 updateFileSystemView()
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            updateProcessView();
            updateMemoryView();
            updateDeviceView();
            updateOperationLogView();
            updatePerformanceChart();
            // updateFileSystemView(); <--- 已移除，避免重建文件树导致无法展开
        }), 0, 500, TimeUnit.MILLISECONDS);
    }

    @FXML
    protected void onCreateProcessClick() {
        Dialog<org.example.scau_os_simulation.process.Process> dialog = new Dialog<>();
        dialog.setTitle("创建新进程");
        dialog.setHeaderText("请输入新进程的详细信息");

        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField processNameField = new TextField();
        processNameField.setPromptText("进程名称");
        processNameField.setText("新进程");

        ChoiceDialog<Integer> priorityDialog = new ChoiceDialog<>(1, 1, 2, 3, 4, 5);
        priorityDialog.setTitle("选择优先级");
        priorityDialog.setHeaderText("选择进程优先级");
        priorityDialog.setContentText("优先级:");

        ChoiceDialog<String> execDialog = new ChoiceDialog<>("/system/exec/p1.e",
                "/system/exec/p1.e", "/system/exec/p2.e", "/system/exec/p3.e",
                "/system/exec/producer1.e", "/system/exec/consumer1.e"); // 添加生产者消费者
        execDialog.setTitle("选择可执行文件");
        execDialog.setHeaderText("选择进程要运行的程序");
        execDialog.setContentText("可执行文件:");

        grid.add(new Label("进程名称:"), 0, 0);
        grid.add(processNameField, 1, 0);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == javafx.scene.control.ButtonType.OK) {
                final String name = processNameField.getText().trim().isEmpty() ? "新进程" : processNameField.getText().trim();

                priorityDialog.showAndWait().ifPresent(priority -> {
                    execDialog.showAndWait().ifPresent(execPath -> {
                        org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess(name, priority);
                        org.example.scau_os_simulation.process.Executable exec = kernel.getFileSystemManager().loadExecutable(execPath);
                        if (p != null && exec != null) {
                            p.setExecutable(exec);
                            kernel.getUndoManager().executeCommand(
                                    new org.example.scau_os_simulation.undo.UndoManager.CreateProcessCommand(
                                            kernel.getProcessManager(), p.getPcb().getPid(), name, priority
                                    )
                            );
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

    @FXML
    protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
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

    @FXML protected void onExitAction() { Platform.exit(); }

    @FXML
    protected void onAboutAction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于");
        alert.setHeaderText("操作系统模拟器");
        alert.setContentText("这是一个基于JavaFX的操作系统模拟框架，用于演示操作系统的基本概念。");
        alert.showAndWait();
    }

    @FXML
    protected void onCreateFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
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
                    updateFileSystemView(); // 手动刷新
                    showInfo("文件创建成功", "文件 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e) {
                    showError("文件创建失败", "无法创建文件 '" + name + "': " + e.getMessage());
                }
            }
        });
    }

    @FXML
    protected void onCreateDirectoryClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        String path = "/";
        if (selectedItem != null) {
            path = buildPathFromTree(selectedItem);
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
                    updateFileSystemView(); // 手动刷新
                    showInfo("目录创建成功", "目录 '" + name + "' 已在路径 '" + finalPath + "' 下成功创建。");
                } catch (Exception e) {
                    showError("目录创建失败", "无法创建目录 '" + name + "': " + e.getMessage());
                }
            }
        });
    }

    @FXML
    protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getParent() == null) {
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

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                boolean success = false;
                try {
                    success = kernel.getFileSystemManager().deleteFile(path) || kernel.getFileSystemManager().deleteDirectory(path);
                } catch (Exception e) {
                    success = false;
                }

                if (success) {
                    updateFileSystemView(); // 手动刷新
                    showInfo("删除成功", itemType + " '" + itemName + "' 已成功删除。");
                } else {
                    showError("删除失败", "无法删除 " + itemType + "。可能是目录非空或路径无效。");
                }
            }
        });
    }

    @FXML
    protected void onDefragmentClick() {
        kernel.getMemoryManager().defragment();
        updateMemoryView();
        showInfo("内存整理完成", "内存碎片整理已完成。");
    }

    @FXML
    protected void onUndoClick() {
        kernel.getUndoManager().undo();
        updateAllViews();
    }

    @FXML
    protected void onRedoClick() {
        kernel.getUndoManager().redo();
        updateAllViews();
    }

    @FXML
    protected void onCopyFileClick() {
        TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null) {
            String path = buildPathFromTree(selectedItem);
            clipboardFile = kernel.getFileSystemManager().getFileByPath(path);
            showInfo("复制成功", "'" + selectedItem.getValue() + "' 已复制到剪贴板。");
        } else {
            showWarning("未选择文件", "请先选择要复制的文件或目录。");
        }
    }

    @FXML
    protected void onPasteFileClick() {
        if (clipboardFile != null) {
            TreeItem<String> selectedItem = fileSystemTreeView.getSelectionModel().getSelectedItem();
            String targetPath = "/";
            if (selectedItem != null) {
                targetPath = buildPathFromTree(selectedItem);
                Object targetNode = kernel.getFileSystemManager().getFileByPath(targetPath);
                if (!(targetNode instanceof Directory)) {
                    targetPath = targetPath.substring(0, targetPath.lastIndexOf('/'));
                    if (targetPath.isEmpty()) targetPath = "/";
                }
            }

            try {
                kernel.getFileSystemManager().paste(clipboardFile, targetPath);
                updateFileSystemView(); // 手动刷新
                showInfo("粘贴成功", "已成功粘贴到 '" + targetPath + "'。");
            } catch (Exception e) {
                showError("粘贴失败", e.getMessage());
            }
        } else {
            showWarning("剪贴板为空", "剪贴板中没有可粘贴的文件或目录。");
        }
    }

    @FXML
    protected void onSearchFileClick() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("搜索文件");
        dialog.setHeaderText("在整个文件系统中搜索文件");
        dialog.setContentText("请输入文件名:");

        dialog.showAndWait().ifPresent(fileName -> {
            if (fileName != null && !fileName.trim().isEmpty()) {
                Object result = kernel.getFileSystemManager().getRootDirectory().searchRecursive(fileName.trim());
                if (result != null) {
                    selectFileInTree(result);
                    showInfo("找到文件", "已在文件树中高亮显示 '" + fileName + "'。");
                } else {
                    showWarning("未找到文件", "未找到名为 '" + fileName + "' 的文件。");
                }
            }
        });
    }

    @FXML
    protected void onRunCommandClick() {
        String command = commandField.getText();
        if (command != null && !command.trim().isEmpty()) {
            kernel.getCommandExecutor().execute(command);
            commandField.clear();
            updateAllViews();
        }
    }

    private void updateAllViews() {
        updateProcessView();
        updateMemoryView();
        updateDeviceView();
        updateFileSystemView();
        updateOperationLogView();
        updatePerformanceMetrics();
    }

    private void updateProcessView() {
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
        if (running != null) {
            PCB pcb = running.getPcb();
            systemClockLabel.setText("系统时钟: " + kernel.getSystemClock());
            runningPidLabel.setText("运行中PID: " + pcb.getPid());
            irLabel.setText("IR: " + pcb.getIr());
            axLabel.setText("AX: " + pcb.getAx());
            tsLabel.setText("时间片: " + kernel.getTimeSlice());
        } else {
            runningPidLabel.setText("运行中PID: 无");
        }
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

    private void populateFileSystemTree(Directory parent, TreeItem<String> parentItem) {
        for (Object child : parent.getChildren()) {
            if (child instanceof Directory) {
                Directory dir = (Directory) child;
                TreeItem<String> dirItem = new TreeItem<>(dir.getName());
                parentItem.getChildren().add(dirItem);
                populateFileSystemTree(dir, dirItem);
            } else if (child instanceof File) {
                parentItem.getChildren().add(new TreeItem<>(((File) child).getName()));
            }
        }
    }

    private void updateOperationLogView() {
        operationLogListView.getItems().setAll(kernel.getOperationLogger().getLogs());
        outputListView.getItems().setAll(kernel.getOutputLogs()); // 同时更新执行结果日志
    }

    private void initializePerformanceChart() {
        try {
            performanceChart = new PerformanceChartUtil();
            if (performanceChartContainer != null) {
                performanceChartContainer.getChildren().add(performanceChart.getChartPanel());
            }
        } catch (Exception e) {
            System.err.println("性能图表初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePerformanceChart() {
        performanceChart.update(kernel.getSystemClock(), kernel.getCpuUtilization(), kernel.getMemoryUtilization());
    }

    private void updatePerformanceMetrics() {
        double cpuUtilization = kernel.getCpuUtilization();
        double systemLoad = kernel.getSystemLoad();

        cpuUtilizationBar.setProgress(cpuUtilization);
        systemLoadBar.setProgress(systemLoad);

        cpuUtilizationLabel.setText(String.format("CPU使用率: %.2f%%", cpuUtilization * 100));
        systemLoadLabel.setText(String.format("系统负载: %.2f", systemLoad));

        avgCpuLabel.setText(String.format("平均CPU: %.2f%%", kernel.getPerformanceMonitor().getAverageCpuUtilization() * 100));
        avgMemoryLabel.setText(String.format("平均内存: %.2f%%", kernel.getPerformanceMonitor().getAverageMemoryUtilization() * 100));
        peakCpuLabel.setText(String.format("峰值CPU: %.2f%%", kernel.getPerformanceMonitor().getPeakCpuUtilization() * 100));
        peakMemoryLabel.setText(String.format("峰值内存: %.2f%%", kernel.getPerformanceMonitor().getPeakMemoryUtilization() * 100));
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

        waitDeviceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().device));
        waitPidColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().pid));
        waitTimeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().time));
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
        return path.length() > 0 ? path.toString() : "/";
    }

    private TreeItem<String> findItemByPath(TreeItem<String> root, String path) {
        if (path.equals("/")) return root;

        String[] parts = path.split("/");
        TreeItem<String> current = root;

        for (int i = 1; i < parts.length; i++) {
            boolean found = false;
            for (TreeItem<String> child : current.getChildren()) {
                if (child.getValue().equals(parts[i])) {
                    current = child;
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        return current;
    }

    private String buildFullPath(Object fileObj) {
        if (fileObj instanceof File) {
            return findFilePath((File) fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
        } else if (fileObj instanceof Directory) {
            return findDirectoryPath((Directory) fileObj, kernel.getFileSystemManager().getRootDirectory(), "");
        }
        return "";
    }

    private String findFilePath(File target, Directory current, String currentPath) {
        for (Object child : current.getChildren()) {
            if (child instanceof File && child == target) {
                return currentPath + "/" + ((File) child).getName();
            } else if (child instanceof Directory) {
                String result = findFilePath(target, (Directory) child, currentPath + "/" + ((Directory) child).getName());
                if (result != null) return result;
            }
        }
        return null;
    }

    private String findDirectoryPath(Directory target, Directory current, String currentPath) {
        if (current == target) {
            return currentPath.isEmpty() ? "/" : currentPath;
        }

        for (Object child : current.getChildren()) {
            if (child instanceof Directory) {
                Directory subDir = (Directory) child;
                String newPath = currentPath + "/" + subDir.getName();
                String result = findDirectoryPath(target, subDir, newPath);
                if (result != null) return result;
            }
        }
        return null;
    }

    private void selectFileInTree(Object fileObj) {
        String path = buildFullPath(fileObj);
        if (!path.isEmpty()) {
            TreeItem<String> root = fileSystemTreeView.getRoot();
            TreeItem<String> target = findItemByPath(root, path);
            if (target != null) {
                fileSystemTreeView.getSelectionModel().select(target);
                fileSystemTreeView.scrollTo(fileSystemTreeView.getSelectionModel().getSelectedIndex());
            }
        }
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

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
}
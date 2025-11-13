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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.kernel.MemoryManager;
import org.example.scau_os_simulation.memory.MemoryBlock;
import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.process.PCB;
import org.example.scau_os_simulation.device.Device;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
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

    private final ScheduledExecutorService uiExec = Executors.newSingleThreadScheduledExecutor();
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化控制器
        kernel = Kernel.getInstance();
        initBindings();
        updateAllViews();
        uiExec.scheduleAtFixedRate(() -> Platform.runLater(this::updateAllViews), 0, 300, TimeUnit.MILLISECONDS);
    }
    
    @FXML
    protected void onCreateProcessClick() {
        org.example.scau_os_simulation.process.Process p = kernel.getProcessManager().createProcess("新进程", 1);
        org.example.scau_os_simulation.process.Executable exec = kernel.getFileSystemManager().loadExecutable("/system/exec/p1.e");
        if (p != null) p.setExecutable(exec);
        updateProcessView();
    }
    
    @FXML
    protected void onTerminateProcessClick() {
        PCB selected = processTableView == null ? null : processTableView.getSelectionModel().getSelectedItem();
        if (selected != null) kernel.getProcessManager().terminateProcess(selected.getPid());
        updateProcessView();
    }
    
    // 添加缺失的方法
    @FXML
    protected void onExitAction() {
        Platform.exit();
    }
    
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
        kernel.getFileSystemManager().createFile("/user", "new.txt", 1);
        updateFileSystemView();
    }
    
    @FXML
    protected void onCreateDirectoryClick() {
        kernel.getFileSystemManager().createDirectory("/user", "docs");
        updateFileSystemView();
    }
    
    @FXML
    protected void onDeleteClick() {
        if (fileSystemTreeView == null) return;
        TreeItem<String> selected = fileSystemTreeView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        String path = buildPathFromTree(selected);
        boolean deleted = false;
        try {
            deleted = kernel.getFileSystemManager().deleteFile(path);
            if (!deleted) deleted = kernel.getFileSystemManager().deleteDirectory(path);
        } catch (Exception ignored) {}
        if (!deleted) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText(null);
            a.setContentText("删除失败，可能是目录非空或路径无效");
            a.show();
        }
        updateFileSystemView();
    }

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
                }
            } else if (cmd.startsWith("$copy")) {
                String args = cmd.substring(5).trim();
                String[] arr = args.split(" ");
                if (arr.length >= 2) {
                    org.example.scau_os_simulation.process.Executable e = kernel.getFileSystemManager().loadExecutable(arr[0]);
                    if (e != null) {
                        java.util.List<String> lines = new java.util.ArrayList<>();
                        for (int i = 0; i < e.length(); i++) lines.add(e.fetch(i));
                        String dest = arr[1];
                        String name = dest;
                        String parent = "/";
                        int idx = dest.lastIndexOf('/');
                        if (idx >= 0) { parent = dest.substring(0, idx); name = dest.substring(idx + 1); }
                        kernel.getFileSystemManager().createExecutable(parent, name, lines);
                    }
                }
            }
        } catch (Exception ignored) {}
        updateFileSystemView();
    }
    
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
    }
    
    private void updateMemoryView() {
        MemoryManager mm = kernel.getMemoryManager();
        double used = 0;
        for (MemoryBlock b : mm.getAllocatedBlocks()) used += b.getSize();
        double total = mm.getMemory().getSize();
        if (memoryUsageBar != null) memoryUsageBar.setProgress(total == 0 ? 0 : used / total);
        if (memoryInfoLabel != null) memoryInfoLabel.setText("已使用: " + (int)used + "KB / 总计: " + (int)total + "KB");
        if (memoryBlockTableView != null) memoryBlockTableView.setItems(javafx.collections.FXCollections.observableArrayList(mm.getAllocatedBlocks()));
    }
    
    private void updateFileSystemView() {
        Directory root = kernel.getFileSystemManager().getRootDirectory();
        TreeItem<String> rootItem = buildTree(root);
        if (fileSystemTreeView != null) fileSystemTreeView.setRoot(rootItem);
        double used = kernel.getFileSystemManager().getFileSystem().getUsedSize();
        double total = kernel.getFileSystemManager().getFileSystem().getTotalSize();
        diskUsageBar.setProgress(total == 0 ? 0 : used / total);
        diskInfoLabel.setText("已使用: " + (int)used + "KB / 总计: " + (int)total + "KB");
    }

    private TreeItem<String> buildTree(Directory dir) {
        TreeItem<String> item = new TreeItem<>(dir.getName());
        for (Object child : dir.getChildren()) {
            if (child instanceof Directory) item.getChildren().add(buildTree((Directory) child));
            else if (child instanceof File) item.getChildren().add(new TreeItem<>(((File) child).getName()));
        }
        return item;
    }

    private void initBindings() {
        if (pidColumn == null) return;
        pidColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPid()));
        nameColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        stateColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getState().name()));
        priorityColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getPriority()));
        memoryAddressColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getMemoryAddress()));
        memorySizeColumn.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getMemorySize()));

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

    private String findProcessByBlock(MemoryBlock block) {
        for (org.example.scau_os_simulation.process.Process p : kernel.getProcessManager().getProcesses()) {
            if (p.getPcb().getMemoryAddress() == block.getStartAddress() && p.getPcb().getMemorySize() == block.getSize()) {
                return p.getPcb().getName();
            }
        }
        return "";
    }

    private void updateAllViews() {
        updateProcessView();
        updateMemoryView();
        updateFileSystemView();
        updateDeviceView();
    }

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
                    rows.add(new WaitRow(dev, r.getPid(), r.getTimeUnits()));
                }
            }
            waitQueueTableView.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        }
    }

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

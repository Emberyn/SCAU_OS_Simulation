package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import org.example.scau_os_simulation.kernel.Kernel;

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
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 初始化控制器
        kernel = Kernel.getInstance();
    }
    
    @FXML
    protected void onCreateProcessClick() {
        kernel.getProcessManager().createProcess("新进程", 1);
        updateProcessView();
    }
    
    @FXML
    protected void onTerminateProcessClick() {
        // 终止选中的进程
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
        // 创建文件的逻辑
        System.out.println("创建文件");
        updateFileSystemView();
    }
    
    @FXML
    protected void onCreateDirectoryClick() {
        // 创建目录的逻辑
        System.out.println("创建目录");
        updateFileSystemView();
    }
    
    @FXML
    protected void onDeleteClick() {
        // 删除文件或目录的逻辑
        System.out.println("删除文件或目录");
        updateFileSystemView();
    }
    
    private void updateProcessView() {
        // 更新进程视图
    }
    
    private void updateMemoryView() {
        // 更新内存视图
    }
    
    private void updateFileSystemView() {
        // 更新文件系统视图
    }
}
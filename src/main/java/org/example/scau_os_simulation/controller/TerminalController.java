package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.example.scau_os_simulation.cli.ShellContext;
import org.example.scau_os_simulation.kernel.Kernel;

import java.net.URL;
import java.util.ResourceBundle;

public class TerminalController implements Initializable
{

    @FXML
    private ScrollPane scrollPane;
    @FXML
    private VBox terminalContainer;
    @FXML
    private TextFlow historyFlow;
    @FXML
    private Label promptLabel;
    @FXML
    private TextField inputField;

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 初始化欢迎语 (黑色)
        appendTextToHistory("SCAU OS Kernel v1.0 [Simulation Mode]\n", Color.BLACK);
        appendTextToHistory("Type 'help' for a list of commands.\n\n", Color.BLACK);

        // 2. 更新提示符
        updatePrompt();

        // 3. 注册 Kernel 监听器
        // 当内核有输出时（例如 ls 的结果），会调用 appendOutput
        Kernel.getInstance().setTerminalListener(this::appendOutput);

        // 4. 自动聚焦输入框
        Platform.runLater(() -> inputField.requestFocus());
    }

    /**
     * 处理回车事件
     */
    @FXML
    public void onEnterPressed()
    {
        String command = inputField.getText();

        // 1. 将 "当前提示符" + "用户输入的命令" 固定到历史记录中
        // 提示符用绿色
        appendTextToHistory(promptLabel.getText(), Color.web("#008000")); // Green
        // 命令用黑色
        appendTextToHistory(command + "\n", Color.BLACK);

        // 2. 清空输入框，准备接收下一条
        inputField.clear();

        // 3. 执行命令
        if (command != null && !command.trim().isEmpty())
        {
            // Kernel 执行命令，输出结果会通过 listener 回调 appendOutput 方法
            Kernel.getInstance().getCommandExecutor().execute(command);
        }

        // 4. 更新提示符 (路径可能改变了，如 cd)
        updatePrompt();

        // 5. 滚动到底部
        scrollToBottom();
    }

    /**
     * 来自 Kernel 的回调：显示命令执行结果
     */
    public void appendOutput(String text)
    {
        Platform.runLater(() ->
        {
            // 输出结果统一为黑色
            appendTextToHistory(text + "\n", Color.BLACK);
            scrollToBottom();
        });
    }

    /**
     * 辅助方法：向历史记录添加带颜色的文本
     */
    private void appendTextToHistory(String content, Color color)
    {
        Text textNode = new Text(content);
        textNode.setFill(color);
        textNode.setStyle("-fx-font-family: 'Consolas', 'Monospaced'; -fx-font-size: 14px;");
        historyFlow.getChildren().add(textNode);
    }

    /**
     * 更新底部的提示符
     */
    private void updatePrompt()
    {
        String currentPath = ShellContext.getInstance().getCurrentPath();
        promptLabel.setText("[root@scau-os " + currentPath + "]# ");
    }

    /**
     * 点击窗口任意位置，聚焦输入框
     */
    @FXML
    public void onTerminalClicked()
    {
        inputField.requestFocus();
    }

    /**
     * 滚动条自动滚到底部
     */
    private void scrollToBottom()
    {
        // 简单的自动滚动逻辑
        // 在 UI 更新后执行，确保滚动到最新位置
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    /**
     * 窗口关闭时的清理
     */
    public void onClose()
    {
        Kernel.getInstance().setTerminalListener(null);
    }
}
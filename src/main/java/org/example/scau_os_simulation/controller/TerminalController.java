package org.example.scau_os_simulation.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.scau_os_simulation.cli.ShellContext;
import org.example.scau_os_simulation.kernel.Kernel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class TerminalController implements Initializable
{
    @FXML private VBox terminalContainer;
    @FXML private TextArea historyArea;
    @FXML private javafx.scene.control.Label promptLabel;
    @FXML private TextField inputField;

    // 历史记录存储
    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 初始化欢迎语
        appendTextToHistory("SCAU OS Kernel v1.0 [Simulation Mode]\n", null);
        appendTextToHistory("Type 'help' for a list of commands.\n\n", null);

        // 2. 更新提示符
        updatePrompt();

        // 3. 注册 Kernel 监听器
        Kernel.getInstance().setTerminalListener(this::appendOutput);

        // 4. 自动聚焦输入框
        Platform.runLater(() -> inputField.requestFocus());

        // 5. 监听键盘上下键事件 (历史记录切换)
        inputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.UP) {
                navigateHistory(-1); // 向上翻
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                navigateHistory(1);  // 向下翻
                event.consume();
            }
        });

        // 6. 监听 TextArea 点击，把焦点还给输入框
        // 这样用户复制完文字后，随便点一下就能继续打字
        if (historyArea != null) {
            historyArea.setOnMouseClicked(e -> inputField.requestFocus());
        }
    }

    /**
     * 历史记录导航逻辑
     */
    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;

        int newIndex = historyIndex + direction;

        // 边界检查
        if (newIndex < 0) newIndex = 0;
        if (newIndex > commandHistory.size()) newIndex = commandHistory.size();

        if (newIndex != historyIndex) {
            historyIndex = newIndex;
            if (historyIndex == commandHistory.size()) {
                inputField.clear();
            } else {
                String cmd = commandHistory.get(historyIndex);
                inputField.setText(cmd);
                inputField.positionCaret(cmd.length());
            }
        }
    }

    /**
     * 处理回车事件
     */
    @FXML
    public void onEnterPressed()
    {
        String command = inputField.getText();

        // 显示用户输入的命令
        if (historyArea != null) {
            historyArea.appendText(promptLabel.getText() + command + "\n");
        }

        inputField.clear();

        if (command != null && !command.trim().isEmpty())
        {
            // 保存到历史记录 (去重：不连续存储相同的命令)
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
            }
            // 重置索引到最新
            historyIndex = commandHistory.size();

            // 执行命令
            Kernel.getInstance().getCommandExecutor().execute(command);
        }

        updatePrompt();
        scrollToBottom();
    }

    /**
     * 来自 Kernel 的回调：显示命令执行结果
     */
    public void appendOutput(String text)
    {
        Platform.runLater(() ->
        {
            appendTextToHistory(text + "\n", null);
            scrollToBottom();
        });
    }

    /**
     * 辅助方法：向历史记录添加文本
     */
    private void appendTextToHistory(String content, Color color)
    {
        if (historyArea != null) {
            historyArea.appendText(content);
        }
    }

    /**
     * 更新底部的提示符
     */
    private void updatePrompt()
    {
        if (promptLabel != null) {
            String currentPath = ShellContext.getInstance().getCurrentPath();
            promptLabel.setText("[root@scau-os " + currentPath + "]# ");
        }
    }

    /**
     * 【修复报错的关键方法】
     * 点击窗口任意空白处，聚焦输入框
     */
    @FXML
    public void onTerminalClicked()
    {
        if (inputField != null) {
            inputField.requestFocus();
        }
    }

    /**
     * 滚动到底部
     */
    private void scrollToBottom()
    {
        if (historyArea != null) {
            Platform.runLater(() -> historyArea.setScrollTop(Double.MAX_VALUE));
        }
    }

    /**
     * 窗口关闭时的清理
     */
    public void onClose()
    {
        Kernel.getInstance().setTerminalListener(null);
    }
}
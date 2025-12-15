package org.example.scau_os_simulation.controller;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.example.scau_os_simulation.cli.ShellContext;
import org.example.scau_os_simulation.kernel.Kernel;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * 终端控制器（TerminalController）
 * 终极修复版：使用 TextFormatter 实现完美的“可选中、不跳动、只读”控制台
 */
public class TerminalController implements Initializable
{
    @FXML private VBox terminalContainer;
    @FXML private TextArea historyArea;
    @FXML private javafx.scene.control.Label promptLabel;
    @FXML private TextField inputField;

    private final List<String> commandHistory = new ArrayList<>();
    private int historyIndex = 0;

    // 滚动粘底定时器
    private AnimationTimer bottomStickTimer;
    private volatile long stickUntilMs = 0;

    // 【核心标志位】是否正在进行系统输出
    // 只有当此标志为 true 时，historyArea 才允许内容变更
    private boolean isSystemOutput = false;

    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 初始化欢迎语
        appendTextToHistory("SCAU OS Kernel v1.0 [Simulation Mode]\n", null);
        appendTextToHistory("Type 'help' for a list of commands.\n\n", null);

        updatePrompt();
        Kernel.getInstance().setTerminalListener(this::appendOutput);
        Platform.runLater(() -> inputField.requestFocus());

        // 2. 输入框键盘事件（历史记录切换）
        inputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.UP) {
                navigateHistory(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                navigateHistory(1);
                event.consume();
            }
        });

        // 3. 点击容器空白处聚焦输入框
        // 智能判断：只有点击了非 TextArea 区域才聚焦，避免打断用户的选中操作
        if (terminalContainer != null) {
            terminalContainer.setOnMouseClicked(e -> {
                // 判断点击目标是否是 historyArea 或其内部节点（如滚动条）
                // 使用 lookup 检查是否点击在 TextArea 的可视范围内
                boolean isClickOnTextArea = historyArea.isHover();
                if (!isClickOnTextArea) {
                    inputField.requestFocus();
                }
            });
        }

        // 4. 【核心修复】配置历史输出区域
        if (historyArea != null) {
            // A. 开启编辑模式：解决"点击跳回顶部"问题
            // 只要是可编辑的，光标就会跟随点击位置，视图自然稳定
            historyArea.setEditable(true);

            // B. 使用 TextFormatter 拦截所有用户修改
            // 这是实现"只读"的最稳健方法。它拦截所有变更（粘贴、打字、删除），但在 isSystemOutput=true 时放行。
            historyArea.setTextFormatter(new TextFormatter<>(change -> {
                // 如果是系统正在输出（代码调用 appendText），允许修改
                if (isSystemOutput) {
                    return change;
                }

                // 如果是用户操作（键盘输入、粘贴、剪切），检查是否试图修改内容
                if (change.isContentChange()) {
                    // 如果试图改变内容，直接拒绝（返回 null）
                    // 但允许光标移动和选区改变（因为那是 non-content change）
                    return null;
                }

                return change; // 允许光标移动、选区选择
            }));

            // C. 键盘透传：用户在查看历史时打字，自动转到输入框
            historyArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
                String ch = e.getCharacter();
                // 忽略控制字符，只处理有效输入
                if (!e.isShortcutDown() && !e.isAltDown() && ch.length() > 0 && ch.charAt(0) >= 32) {
                    inputField.requestFocus();
                    inputField.appendText(ch);
                    inputField.positionCaret(inputField.getLength());
                    e.consume();
                }
            });

            // D. 处理特殊键：回车、退格键回到输入框
            historyArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.BACK_SPACE) {
                    inputField.requestFocus();
                    e.consume();
                }
            });

            // E. 配置右键菜单：只保留复制
            ContextMenu contextMenu = new ContextMenu();
            MenuItem copyItem = new MenuItem("复制");
            copyItem.setOnAction(ev -> historyArea.copy());
            MenuItem selectAllItem = new MenuItem("全选");
            selectAllItem.setOnAction(ev -> historyArea.selectAll());
            contextMenu.getItems().addAll(copyItem, selectAllItem);
            historyArea.setContextMenu(contextMenu);

            // F. 强制布局刷新：解决"由于布局未更新导致无法选中"的问题
            Platform.runLater(() -> {
                historyArea.requestLayout();
                historyArea.layout();
            });
        }

        // 初始化粘底定时器
        bottomStickTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (System.currentTimeMillis() < stickUntilMs) {
                    forceScrollToBottom();
                } else {
                    stop();
                }
            }
        };
    }

    private void navigateHistory(int direction) {
        if (commandHistory.isEmpty()) return;
        int newIndex = historyIndex + direction;
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

    @FXML
    public void onEnterPressed()
    {
        String command = inputField.getText();

        // 使用安全的方式追加文本
        safeAppendText(promptLabel.getText() + command + "\n");

        forceScrollToBottom();
        startStickBottom(200);

        inputField.clear();

        if (command != null && !command.trim().isEmpty())
        {
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
            }
            historyIndex = commandHistory.size();
            Kernel.getInstance().getCommandExecutor().execute(command);
        }
        updatePrompt();
    }

    public void appendOutput(String text)
    {
        Platform.runLater(() ->
        {
            // 使用安全的方式追加文本
            safeAppendText(text + "\n");

            Platform.runLater(this::forceScrollToBottom);
            startStickBottom(400);
        });
    }

    private void appendTextToHistory(String content, Color color)
    {
        safeAppendText(content);
        forceScrollToBottom();
    }

    /**
     * 【核心方法】安全追加文本
     * 1. 开启系统输出标志位
     * 2. 执行追加
     * 3. 关闭标志位
     * 这样 TextFormatter 就会放行这次修改，而拦截用户的修改
     */
    private void safeAppendText(String text) {
        if (historyArea != null) {
            isSystemOutput = true; // 解锁
            try {
                historyArea.appendText(text);
            } finally {
                isSystemOutput = false; // 上锁
            }
        }
    }

    /**
     * 强力滚动到底部
     */
    private void forceScrollToBottom()
    {
        if (historyArea != null) {
            // 只有当用户没有正在选中文本时，才强制滚动，避免打断用户复制
            if (historyArea.getSelection().getLength() == 0) {
                historyArea.setScrollTop(Double.MAX_VALUE);
            }
        }
    }

    private void startStickBottom(int durationMs) {
        stickUntilMs = System.currentTimeMillis() + Math.max(50, durationMs);
        if (bottomStickTimer != null) bottomStickTimer.start();
    }

    @FXML
    public void onTerminalClicked() {
        // 留空，逻辑已移至 initialize
    }

    private void updatePrompt() {
        if (promptLabel != null) {
            String currentPath = ShellContext.getInstance().getCurrentPath();
            promptLabel.setText("[root@scau-os " + currentPath + "]# ");
        }
    }

    public void onClose() {
        Kernel.getInstance().setTerminalListener(null);
    }
}
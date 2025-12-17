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
 * 核心功能：实现类Linux风格的终端交互，包含命令输入、历史记录、系统输出展示、只读可选中的历史区域
 * 终极修复版特性：
 * 1. 使用TextFormatter实现"可选中、不跳动、只读"的控制台历史区域
 * 2. 智能滚动粘底，不打断用户查看历史
 * 3. 键盘事件透传，优化用户交互体验
 * 4. 完善的命令历史记录导航（上下键切换）
 */
public class TerminalController implements Initializable
{
    // FXML控件注入 - 终端容器（整体布局）
    @FXML private VBox terminalContainer;
    // FXML控件注入 - 历史输出区域（展示命令执行结果和系统信息）
    @FXML private TextArea historyArea;
    // FXML控件注入 - 命令提示符标签（展示当前路径和用户信息）
    @FXML private javafx.scene.control.Label promptLabel;
    // FXML控件注入 - 命令输入框（用户输入命令的区域）
    @FXML private TextField inputField;

    // 命令历史记录列表 - 存储用户输入过的所有有效命令
    private final List<String> commandHistory = new ArrayList<>();
    // 历史记录导航索引 - 用于上下键切换历史命令时定位
    private int historyIndex = 0;

    // 滚动粘底定时器 - 确保系统输出时自动滚动到底部，但允许用户手动查看历史
    private AnimationTimer bottomStickTimer;
    // 粘底截止时间戳 - 控制粘底定时器的运行时长（毫秒）
    private volatile long stickUntilMs = 0;

    // 【核心标志位】系统输出状态标识
    // 只有当此标志为true时，historyArea才允许内容变更（拦截用户手动修改）
    private boolean isSystemOutput = false;

    /**
     * 初始化方法 - 控制器加载完成后自动执行
     * 负责初始化界面、绑定事件、配置控件属性、设置内核监听器
     * @param location FXML文件的URL路径
     * @param resources 资源束（国际化使用）
     */
    @Override
    public void initialize(URL location, ResourceBundle resources)
    {
        // 1. 初始化欢迎语 - 启动时展示系统版本信息
        appendTextToHistory("SCAU OS Kernel v1.0 [Simulation Mode]\n", null);
        appendTextToHistory("Type 'help' for a list of commands.\n\n", null);

        // 更新命令提示符（显示初始路径）
        updatePrompt();
        // 注册终端输出监听器 - 内核输出内容时回调appendOutput方法
        Kernel.getInstance().setTerminalListener(this::appendOutput);
        // 初始化焦点 - 界面加载完成后聚焦到输入框
        Platform.runLater(() -> inputField.requestFocus());

        // 2. 输入框键盘事件处理 - 上下键切换历史命令
        inputField.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.UP) {
                // 上键 - 查看上一条历史命令
                navigateHistory(-1);
                event.consume(); // 消费事件，避免默认行为
            } else if (event.getCode() == KeyCode.DOWN) {
                // 下键 - 查看下一条历史命令
                navigateHistory(1);
                event.consume();
            }
        });

        // 3. 容器点击事件 - 智能聚焦输入框
        // 仅当点击非historyArea区域时聚焦，避免打断用户选中历史内容
        if (terminalContainer != null) {
            terminalContainer.setOnMouseClicked(e -> {
                // 判断是否点击在historyArea区域（包括滚动条等内部节点）
                boolean isClickOnTextArea = historyArea.isHover();
                if (!isClickOnTextArea) {
                    inputField.requestFocus();
                }
            });
        }

        // 4. 核心配置 - 历史输出区域（historyArea）的增强配置
        if (historyArea != null) {
            // A. 开启编辑模式 - 解决"点击跳回顶部"问题
            // 可编辑状态下光标会跟随点击位置，保证视图稳定性
            historyArea.setEditable(true);

            // B. TextFormatter核心拦截 - 实现"只读"特性
            // 拦截所有内容修改操作，仅在系统输出时放行
            historyArea.setTextFormatter(new TextFormatter<>(change -> {
                // 系统输出时允许内容变更（代码调用appendText）
                if (isSystemOutput) {
                    return change;
                }

                // 用户操作时：拦截内容修改，但允许光标/选区变更
                if (change.isContentChange()) {
                    return null; // 拒绝内容修改
                }

                return change; // 允许光标移动、选区选择等非内容操作
            }));

            // C. 键盘透传 - 历史区域打字自动转到输入框
            historyArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
                String ch = e.getCharacter();
                // 过滤控制字符，仅处理可打印字符（ASCII >= 32）
                if (!e.isShortcutDown() && !e.isAltDown() && ch.length() > 0 && ch.charAt(0) >= 32) {
                    inputField.requestFocus(); // 聚焦输入框
                    inputField.appendText(ch); // 追加输入字符
                    inputField.positionCaret(inputField.getLength()); // 光标移到末尾
                    e.consume(); // 消费事件
                }
            });

            // D. 特殊键处理 - 回车/退格键自动回到输入框
            historyArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.BACK_SPACE) {
                    inputField.requestFocus();
                    e.consume();
                }
            });

            // E. 自定义右键菜单 - 仅保留复制/全选功能（移除剪切/粘贴）
            ContextMenu contextMenu = new ContextMenu();
            // 复制菜单项
            MenuItem copyItem = new MenuItem("复制");
            copyItem.setOnAction(ev -> historyArea.copy());
            // 全选菜单项
            MenuItem selectAllItem = new MenuItem("全选");
            selectAllItem.setOnAction(ev -> historyArea.selectAll());
            contextMenu.getItems().addAll(copyItem, selectAllItem);
            historyArea.setContextMenu(contextMenu);

            // F. 强制布局刷新 - 解决"布局未更新导致无法选中"的问题
            Platform.runLater(() -> {
                historyArea.requestLayout();
                historyArea.layout();
            });
        }

        // 初始化粘底定时器 - 用于系统输出时自动滚动到底部
        bottomStickTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // 未到截止时间则继续强制滚动到底部
                if (System.currentTimeMillis() < stickUntilMs) {
                    forceScrollToBottom();
                } else {
                    // 超时后停止定时器，允许用户手动滚动
                    stop();
                }
            }
        };
    }

    /**
     * 历史命令导航 - 上下键切换命令
     * @param direction 导航方向：-1（上一条），1（下一条）
     */
    private void navigateHistory(int direction) {
        // 无历史命令时直接返回
        if (commandHistory.isEmpty()) return;
        // 计算新的索引位置
        int newIndex = historyIndex + direction;
        // 边界检查：最小为0，最大为历史记录数量（超出时清空输入框）
        if (newIndex < 0) newIndex = 0;
        if (newIndex > commandHistory.size()) newIndex = commandHistory.size();

        // 索引变化时更新输入框内容
        if (newIndex != historyIndex) {
            historyIndex = newIndex;
            if (historyIndex == commandHistory.size()) {
                // 索引等于记录数时清空输入框（最新状态）
                inputField.clear();
            } else {
                // 显示对应索引的历史命令，并将光标移到末尾
                String cmd = commandHistory.get(historyIndex);
                inputField.setText(cmd);
                inputField.positionCaret(cmd.length());
            }
        }
    }

    /**
     * 回车事件处理 - 执行用户输入的命令
     * FXML绑定：输入框的回车按键事件
     */
    @FXML
    public void onEnterPressed()
    {
        // 获取输入框中的命令文本
        String command = inputField.getText();

        // 将命令和提示符追加到历史区域
        safeAppendText(promptLabel.getText() + command + "\n");

        // 强制滚动到底部，并启动粘底定时器（200ms）
        forceScrollToBottom();
        startStickBottom(200);

        // 清空输入框
        inputField.clear();

        // 处理有效命令（非空且非空白）
        if (command != null && !command.trim().isEmpty())
        {
            // 去重存储：避免重复添加相同的连续命令
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
            }
            // 更新历史索引到最新位置
            historyIndex = commandHistory.size();
            // 提交命令到内核执行器执行
            Kernel.getInstance().getCommandExecutor().execute(command);
        }
        // 更新命令提示符（可能路径已变更）
        updatePrompt();
    }

    /**
     * 内核输出回调方法 - 接收内核输出并展示到终端
     * @param text 内核输出的文本内容
     */
    public void appendOutput(String text)
    {
        // 确保在JavaFX应用线程执行UI操作
        Platform.runLater(() ->
        {
            // 安全追加输出文本（自动换行）
            safeAppendText(text + "\n");

            // 强制滚动到底部，并延长粘底时间（400ms）
            Platform.runLater(this::forceScrollToBottom);
            startStickBottom(400);
        });
    }

    /**
     * 历史区域文本追加 - 带颜色支持（预留扩展）
     * @param content 要追加的文本内容
     * @param color 文本颜色（null使用默认颜色）
     */
    private void appendTextToHistory(String content, Color color)
    {
        // 安全追加文本到历史区域
        safeAppendText(content);
        // 强制滚动到底部
        forceScrollToBottom();
    }

    /**
     * 【核心方法】安全追加文本到历史区域
     * 通过控制isSystemOutput标志位，确保只有系统输出能修改历史区域内容
     * 防止用户手动修改历史记录，同时保证代码调用的正常追加
     * @param text 要追加的文本内容
     */
    private void safeAppendText(String text) {
        // 空检查：避免空指针异常
        if (historyArea != null) {
            isSystemOutput = true; // 解锁：允许内容修改
            try {
                // 执行文本追加
                historyArea.appendText(text);
            } finally {
                isSystemOutput = false; // 上锁：拦截用户修改（必须在finally中确保执行）
            }
        }
    }

    /**
     * 强制滚动到底部 - 忽略用户选区状态（仅在无选区时生效）
     * 保证系统输出时内容可见，同时不打断用户复制操作
     */
    private void forceScrollToBottom()
    {
        if (historyArea != null) {
            // 仅当用户没有选中文本时强制滚动，避免打断复制操作
            if (historyArea.getSelection().getLength() == 0) {
                // 设置滚动条到最底部（Double.MAX_VALUE确保滚动到底）
                historyArea.setScrollTop(Double.MAX_VALUE);
            }
        }
    }

    /**
     * 启动粘底定时器 - 确保指定时长内保持滚动到底部
     * @param durationMs 粘底时长（毫秒）
     */
    private void startStickBottom(int durationMs) {
        // 更新粘底截止时间
        stickUntilMs = System.currentTimeMillis() + Math.max(50, durationMs);
        // 启动定时器（自动处理重复调用）
        if (bottomStickTimer != null) bottomStickTimer.start();
    }

    /**
     * 终端点击事件处理 - 预留FXML绑定方法
     * 逻辑已移至initialize中的terminalContainer点击事件
     */
    @FXML
    public void onTerminalClicked() {
        // 留空，逻辑已移至 initialize
    }

    /**
     * 更新命令提示符 - 展示当前工作路径
     * 格式：[root@scau-os 路径]#
     */
    private void updatePrompt() {
        if (promptLabel != null) {
            // 获取Shell上下文的当前路径
            String currentPath = ShellContext.getInstance().getCurrentPath();
            // 更新提示符文本
            promptLabel.setText("[root@scau-os " + currentPath + "]# ");
        }
    }

    /**
     * 终端关闭时的清理方法 - 释放资源
     * 解除内核的终端监听器绑定，避免内存泄漏
     */
    public void onClose() {
        Kernel.getInstance().setTerminalListener(null);
    }
}
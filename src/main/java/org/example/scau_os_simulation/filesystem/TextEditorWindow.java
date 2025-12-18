package org.example.scau_os_simulation.filesystem;


import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;

import java.nio.charset.StandardCharsets;

/**
 * 简易文本编辑器窗口 (记事本)
 */
public class TextEditorWindow extends Stage
{

    private final File targetFile;
    private final TextArea textArea;
    private final String filePath;

    public TextEditorWindow(File file, String path)
    {
        this.targetFile = file;
        this.filePath = path;

        this.setTitle("记事本 - " + file.getName());

        // 1. 初始化文本区域
        this.textArea = new TextArea();

        this.textArea.setFont(Font.font(16));
        // 从模拟文件系统中读取内容（假设存储的是UTF-8文本）
        if (file.getContent() != null)
        {
            // 只读取实际长度的字节转换为字符串
            // 注意：这里调用了我们在 File 中新增的 getActualLength() 方法
            String content = new String(file.getContent(), 0, file.getActualLength(), StandardCharsets.UTF_8);
            this.textArea.setText(content);
        }

        // 2. 创建菜单栏
        MenuBar menuBar = new MenuBar();

        // --- [文件] 菜单 ---
        Menu fileMenu = new Menu("文件(F)");
        MenuItem saveItem = new MenuItem("保存(S)");
        MenuItem exitItem = new MenuItem("退出(X)");

        // 设置快捷键显示的文本
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));

        // 绑定事件
        saveItem.setOnAction(e -> saveFile());
        exitItem.setOnAction(e -> this.close());

        fileMenu.getItems().addAll(saveItem, new SeparatorMenuItem(), exitItem);

        // --- [编辑] 菜单 ---
        Menu editMenu = new Menu("编辑(E)");
        MenuItem copyItem = new MenuItem("复制(C)");
        MenuItem pasteItem = new MenuItem("粘贴(V)");
        MenuItem cutItem = new MenuItem("剪切(X)");

        // TextArea 自带了这些功能的实现，我们要做的就是触发它们
        copyItem.setOnAction(e -> textArea.copy());
        pasteItem.setOnAction(e -> textArea.paste());
        cutItem.setOnAction(e -> textArea.cut());

        // 绑定快捷键显示
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN));
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN));
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN));

        editMenu.getItems().addAll(cutItem, copyItem, pasteItem);

        menuBar.getMenus().addAll(fileMenu, editMenu);

        // 3. 布局
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(textArea);

        Scene scene = new Scene(root, 600, 400);

        // 4. 全局快捷键处理 (处理 Ctrl+S 保存)
        // 注意：TextArea 自带了 Ctrl+C/V/X 的处理，所以这里重点处理保存
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                this::saveFile
        );

        this.setScene(scene);
    }

    /**
     * 保存文件内容回操作系统模拟器的文件系统
     */
    private void saveFile()
    {
        try
        {
            String text = textArea.getText();
            byte[] data = text.getBytes(StandardCharsets.UTF_8);

            // 写入模拟文件对象
            // 注意：这里没有检查磁盘空间配额，严谨的话应该调用 fileSystem.allocateSpace 判断
            targetFile.setContent(data);

            // 记录日志
            Kernel.getInstance().getOperationLogger().info(
                    org.example.scau_os_simulation.logging.OperationLogger.OperationType.FILE_WRITE,
                    "保存文件: " + filePath,
                    null
            );

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("保存成功");
            alert.setHeaderText(null);
            alert.setContentText("文件已保存至模拟磁盘。");
            alert.show(); // 使用 show() 而不是 showAndWait() 避免阻塞编辑

        } catch (Exception e)
        {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("保存失败");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }
}

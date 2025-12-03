package org.example.scau_os_simulation.undo;

import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * 操作撤销管理器 - 管理可撤销操作
 * <p>
 * 支持撤销以下操作：
 * - 进程创建
 * - 文件创建/删除
 * - 目录创建/删除
 * <p>
 * 使用命令模式实现撤销功能
 */
public class UndoManager
{
    private final Stack<UndoableCommand> undoStack;
    private final Stack<UndoableCommand> redoStack;
    private final int maxStackSize;

    /**
     * 可撤销命令接口
     */
    public interface UndoableCommand
    {
        void execute();

        void undo();

        String getDescription();

        Map<String, Object> getDetails();
    }

    /**
     * 创建进程命令
     */
    public static class CreateProcessCommand implements UndoableCommand
    {
        private final org.example.scau_os_simulation.kernel.ProcessManager processManager;
        private final int pid;
        private final String name;
        private final int priority;

        public CreateProcessCommand(org.example.scau_os_simulation.kernel.ProcessManager processManager,
                                    int pid, String name, int priority)
        {
            this.processManager = processManager;
            this.pid = pid;
            this.name = name;
            this.priority = priority;
        }

        @Override
        public void execute()
        {
            // 进程已经在创建时执行了
        }

        @Override
        public void undo()
        {
            processManager.terminateProcess(pid);
        }

        @Override
        public String getDescription()
        {
            return "创建进程: " + name + " (PID: " + pid + ")";
        }

        @Override
        public Map<String, Object> getDetails()
        {
            Map<String, Object> details = new HashMap<>();
            details.put("pid", pid);
            details.put("name", name);
            details.put("priority", priority);
            return details;
        }
    }


    /**
     * 创建文件命令
     */
    public static class CreateFileCommand implements UndoableCommand
    {
        private final org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager;
        private final String path;
        private final String name;

        public CreateFileCommand(org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager,
                                 String path, String name)
        {
            this.fileSystemManager = fileSystemManager;
            this.path = path;
            this.name = name;
        }

        @Override
        public void execute()
        {
            // 文件已经在创建时执行了
        }

        @Override
        public void undo()
        {
            fileSystemManager.deleteFile(path + "/" + name);
        }

        @Override
        public String getDescription()
        {
            return "创建文件: " + path + "/" + name;
        }

        @Override
        public Map<String, Object> getDetails()
        {
            Map<String, Object> details = new HashMap<>();
            details.put("path", path);
            details.put("name", name);
            return details;
        }
    }



    /**
     * 删除文件命令（修复版 - 支持内容恢复）
     */
    public static class DeleteFileCommand implements UndoableCommand
    {
        private final org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager;
        private final String fullPath;
        private final int size;
        private final byte[] backupContent; // 【新增】用于备份内容

        // 【修改】构造函数增加 content 参数
        public DeleteFileCommand(org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager,
                                 String fullPath, int size, byte[] content)
        {
            this.fileSystemManager = fileSystemManager;
            this.fullPath = fullPath;
            this.size = size;
            // 深度拷贝，防止引用被外部修改
            this.backupContent = content != null ? content.clone() : new byte[0];
        }

        @Override
        public void execute()
        {
            // 文件已经在外部被删除了，这里只负责记录状态
        }

        @Override
        public void undo()
        {
            String dirPath = fullPath.substring(0, fullPath.lastIndexOf('/'));
            // 根目录下特殊处理
            if (dirPath.isEmpty()) dirPath = "/";

            String fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);

            // 1. 恢复文件对象
            org.example.scau_os_simulation.filesystem.File f =
                    fileSystemManager.createFile(dirPath, fileName, size);

            // 2. 【新增】恢复文件内容
            if (f != null && backupContent != null) {
                try {
                    f.setContent(backupContent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public String getDescription()
        {
            return "删除文件: " + fullPath;
        }

        @Override
        public Map<String, Object> getDetails()
        {
            Map<String, Object> details = new HashMap<>();
            details.put("path", fullPath);
            details.put("size", size);
            return details;
        }
    }

    /**
     * 创建目录命令
     */
    public static class CreateDirectoryCommand implements UndoableCommand
    {
        private final org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager;
        private final String path;
        private final String name;

        public CreateDirectoryCommand(org.example.scau_os_simulation.kernel.FileSystemManager fileSystemManager,
                                      String path, String name)
        {
            this.fileSystemManager = fileSystemManager;
            this.path = path;
            this.name = name;
        }

        @Override
        public void execute()
        {
            // 目录已经在创建时执行了
        }

        @Override
        public void undo()
        {
            fileSystemManager.deleteDirectory(path + "/" + name);
        }

        @Override
        public String getDescription()
        {
            return "创建目录: " + path + "/" + name;
        }

        @Override
        public Map<String, Object> getDetails()
        {
            Map<String, Object> details = new HashMap<>();
            details.put("path", path);
            details.put("name", name);
            return details;
        }
    }

    /**
     * 构造函数
     *
     * @param maxStackSize 最大堆栈大小
     */
    public UndoManager(int maxStackSize)
    {
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.maxStackSize = maxStackSize;
    }

    /**
     * 执行命令并添加到撤销栈
     *
     * @param command 要执行的命令
     */
    public void executeCommand(UndoableCommand command)
    {
        command.execute();
        undoStack.push(command);

        // 限制栈大小
        if (undoStack.size() > maxStackSize)
        {
            undoStack.remove(0);
        }

        // 清空重做栈
        redoStack.clear();
    }

    /**
     * 撤销上一个操作
     *
     * @return true表示撤销成功，false表示没有可撤销的操作
     */
    public boolean undo()
    {
        if (undoStack.isEmpty())
        {
            return false;
        }

        UndoableCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
        return true;
    }

    /**
     * 重做上一个撤销的操作
     *
     * @return true表示重做成功，false表示没有可重做的操作
     */
    public boolean redo()
    {
        if (redoStack.isEmpty())
        {
            return false;
        }

        UndoableCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
        return true;
    }

    /**
     * 获取可撤销的操作列表
     *
     * @return 操作描述列表
     */
    public List<String> getUndoableOperations()
    {
        List<String> operations = new ArrayList<>();
        for (UndoableCommand command : undoStack)
        {
            operations.add(command.getDescription());
        }
        Collections.reverse(operations);
        return operations;
    }

    /**
     * 获取可重做的操作列表
     *
     * @return 操作描述列表
     */
    public List<String> getRedoableOperations()
    {
        List<String> operations = new ArrayList<>();
        for (UndoableCommand command : redoStack)
        {
            operations.add(command.getDescription());
        }
        return operations;
    }

    /**
     * 清空撤销历史
     */
    public void clear()
    {
        undoStack.clear();
        redoStack.clear();
    }

    /**
     * 是否可以撤销
     *
     * @return true表示可以撤销
     */
    public boolean canUndo()
    {
        return !undoStack.isEmpty();
    }

    /**
     * 是否可以重做
     *
     * @return true表示可以重做
     */
    public boolean canRedo()
    {
        return !redoStack.isEmpty();
    }

    /**
     * 获取撤销栈大小
     *
     * @return 撤销栈中的操作数量
     */
    public int getUndoStackSize()
    {
        return undoStack.size();
    }

    /**
     * 获取重做栈大小
     *
     * @return 重做栈中的操作数量
     */
    public int getRedoStackSize()
    {
        return redoStack.size();
    }
}
package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件系统管理器
 * 1. 修复了复制粘贴不扣除磁盘空间的 Bug
 * 2. 新增了 UI 监听器机制，解决 CLI 操作后资源管理器不刷新的问题
 */
public class FileSystemManager
{
    private final FileSystem fileSystem;
    private final Directory rootDirectory;

    // 【新增】监听器列表，用于通知 UI 刷新
    private final List<Runnable> listeners = new ArrayList<>();

    public FileSystemManager(FileSystem fileSystem)
    {
        this.fileSystem = fileSystem;
        this.rootDirectory = new Directory("root");

        // 初始化基础目录
        if (findDirectoryByPath("/system") == null) rootDirectory.addChild(new Directory("system"));
        if (findDirectoryByPath("/user") == null) rootDirectory.addChild(new Directory("user"));

        Directory systemDir = findDirectoryByPath("/system");

        // 创建内核文件
        if (systemDir.findChild("kernel.sys") == null) {
            File kernelFile = new File("kernel.sys", 128);
            systemDir.addChild(kernelFile);
            fileSystem.allocateSpace(128);
        }

        if (systemDir.findChild("exec") == null) {
            systemDir.addChild(new Directory("exec"));
        }
    }

    // --- 【新增】监听器相关方法 ---

    /**
     * 注册文件系统变更监听器
     */
    public void addListener(Runnable listener) {
        this.listeners.add(listener);
    }

    /**
     * 通知所有监听器（通常是 UI）文件系统已发生变化
     */
    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // --- 核心业务方法 ---

    /**
     * 粘贴 (复制) 文件或目录
     * 修复点：强制执行深拷贝，并扣除相应的磁盘空间
     */
    public synchronized Object paste(Object source, String targetPath)
    {
        Directory targetDir = findDirectoryByPath(targetPath);
        if (targetDir == null) {
            System.err.println("粘贴失败：目标路径不存在 " + targetPath);
            return null;
        }

        Object result = null;
        // 1. 如果是文件
        if (source instanceof File) {
            result = pasteFile((File) source, targetDir);
        }
        // 2. 如果是目录
        else if (source instanceof Directory) {
            result = pasteDirectoryRecursive((Directory) source, targetDir);
        }

        // 【新增】如果粘贴成功，通知 UI 刷新
        if (result != null) {
            notifyListeners();
        }
        return result;
    }

    private File pasteFile(File srcFile, Directory targetDir) {
        int size = srcFile.getSize();

        if (!fileSystem.allocateSpace(size)) {
            System.err.println("错误：磁盘空间不足，无法复制文件: " + srcFile.getName());
            return null;
        }

        String uniqueName = getUniqueName(targetDir, srcFile.getName());
        File newFile = new File(uniqueName, size);

        if (srcFile.getContent() != null) {
            byte[] srcData = srcFile.getContent();
            byte[] newData = new byte[srcData.length];
            System.arraycopy(srcData, 0, newData, 0, srcData.length);
            newFile.setContent(newData);
        }

        targetDir.addChild(newFile);
        return newFile;
    }

    private Directory pasteDirectoryRecursive(Directory srcDir, Directory targetDir) {
        String uniqueName = getUniqueName(targetDir, srcDir.getName());
        Directory newDir = new Directory(uniqueName);
        targetDir.addChild(newDir);

        for (Object child : srcDir.getChildren()) {
            if (child instanceof File) {
                pasteFile((File) child, newDir);
            } else if (child instanceof Directory) {
                pasteDirectoryRecursive((Directory) child, newDir);
            }
        }
        return newDir;
    }

    private String getUniqueName(Directory parent, String originalName) {
        if (parent.findChild(originalName) == null) return originalName;

        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        String newName;
        do {
            newName = baseName + "(" + counter + ")" + extension;
            counter++;
        } while (parent.findChild(newName) != null);
        return newName;
    }

    public synchronized File createFile(String path, String name, int size) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;

        if (!fileSystem.allocateSpace(size)) {
            System.out.println("磁盘空间不足");
            return null;
        }

        String uniqueName = getUniqueName(parent, name);
        File newFile = new File(uniqueName, size);
        parent.addChild(newFile);

        // 【新增】通知 UI 刷新
        notifyListeners();
        return newFile;
    }

    public synchronized Directory createDirectory(String path, String name) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;
        String uniqueName = getUniqueName(parent, name);
        Directory newDir = new Directory(uniqueName);
        parent.addChild(newDir);

        // 【新增】通知 UI 刷新
        notifyListeners();
        return newDir;
    }

    public synchronized boolean deletePath(String path) {
        if (path.equals("/") || path.isEmpty()) return false;
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (parentPath.isEmpty()) parentPath = "/";

        Directory parent = findDirectoryByPath(parentPath);
        if (parent == null) return false;
        Object target = parent.findChild(name);
        if (target == null) return false;

        boolean success = false;
        if (target instanceof File) {
            File f = (File) target;
            parent.removeChild(f);
            fileSystem.freeSpace(f.getSize());
            success = true;
        } else if (target instanceof Directory) {
            success = deleteDirectoryRecursive(parent, (Directory) target);
        }

        // 【新增】如果删除成功，通知 UI 刷新
        if (success) {
            notifyListeners();
        }
        return success;
    }

    private boolean deleteDirectoryRecursive(Directory parent, Directory target) {
        List<Object> children = new ArrayList<>(target.getChildren());
        for (Object child : children) {
            if (child instanceof File) {
                target.removeChild(child);
                fileSystem.freeSpace(((File) child).getSize());
            } else if (child instanceof Directory) {
                deleteDirectoryRecursive(target, (Directory) child);
            }
        }
        return parent.removeChild(target);
    }

    public synchronized boolean deleteFile(String path) { return deletePath(path); }
    public synchronized boolean deleteDirectory(String path) { return deletePath(path); }

    public Directory getDirectory(String path) {
        return findDirectoryByPath(path);
    }

    private Directory findDirectoryByPath(String path) {
        if (path.equals("/") || path.isEmpty()) return rootDirectory;
        String[] parts = path.split("/");
        Directory current = rootDirectory;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            boolean found = false;
            for (Object child : current.getChildren()) {
                if (child instanceof Directory d && d.getName().equals(part)) {
                    current = d; found = true; break;
                }
            }
            if (!found) return null;
        }
        return current;
    }

    public synchronized File createExecutable(String path, String name, List<String> instructions) {
        int size = Math.max(1, (instructions.size() + 63) / 64);
        // createFile 内部已经会调用 notifyListeners()
        File f = createFile(path, name, size);
        if (f == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String s : instructions) sb.append(s).append("\n");
        f.setContent(sb.toString().getBytes(StandardCharsets.UTF_8));

        // 可以在这里再通知一次，或者依赖 createFile 的通知
        return f;
    }

    public synchronized File createExecutable(String path, String name, org.example.scau_os_simulation.process.Executable exec) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < exec.length(); i++) lines.add(exec.fetch(i));
        return createExecutable(path, name, lines);
    }

    public synchronized org.example.scau_os_simulation.process.Executable loadExecutable(String path) {
        File f = getFileByPath(path);
        if (f == null) return null;
        if (f.getContent() == null) return new org.example.scau_os_simulation.process.Executable(new ArrayList<>());

        String content = new String(f.getContent(), StandardCharsets.UTF_8);
        List<String> lines = Arrays.asList(content.split("\n"));
        return new org.example.scau_os_simulation.process.Executable(lines);
    }

    public synchronized File getFileByPath(String path) {
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (parentPath.isEmpty()) parentPath = "/";

        Directory dir = findDirectoryByPath(parentPath);
        if (dir == null) return null;

        Object child = dir.findChild(fileName);
        return (child instanceof File) ? (File) child : null;
    }

    public synchronized Object getObjectByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.equals("/")) return rootDirectory;
        Directory dir = findDirectoryByPath(path);
        if (dir != null) return dir;
        return getFileByPath(path);
    }

    public synchronized Directory getRootDirectory() { return rootDirectory; }
    public synchronized FileSystem getFileSystem() { return fileSystem; }
}
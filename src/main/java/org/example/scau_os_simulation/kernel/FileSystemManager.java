package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件系统管理器 - 修复了复制粘贴不扣除磁盘空间的 Bug
 */
public class FileSystemManager
{
    private final FileSystem fileSystem;
    private final Directory rootDirectory;

    public FileSystemManager(FileSystem fileSystem)
    {
        this.fileSystem = fileSystem;
        this.rootDirectory = new Directory("root");

        // 初始化基础目录
        // 使用 findDirectoryByPath 防止重复创建 (exec(1) 问题)
        if (findDirectoryByPath("/system") == null) rootDirectory.addChild(new Directory("system"));
        if (findDirectoryByPath("/user") == null) rootDirectory.addChild(new Directory("user"));

        Directory systemDir = findDirectoryByPath("/system");

        // 创建内核文件
        if (systemDir.findChild("kernel.sys") == null) {
            File kernelFile = new File("kernel.sys", 128);
            systemDir.addChild(kernelFile);
            // 注意：初始化时的系统文件也要扣除空间，为了严谨最好加上
            fileSystem.allocateSpace(128);
        }

        if (systemDir.findChild("exec") == null) {
            systemDir.addChild(new Directory("exec"));
        }
    }

    // --- 核心修复：完全重写的 paste 方法 ---

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

        // 1. 如果是文件
        if (source instanceof File) {
            return pasteFile((File) source, targetDir);
        }
        // 2. 如果是目录
        else if (source instanceof Directory) {
            return pasteDirectoryRecursive((Directory) source, targetDir);
        }

        return null;
    }

    /**
     * 内部辅助：粘贴单个文件 (包含空间申请和深拷贝)
     */
    private File pasteFile(File srcFile, Directory targetDir) {
        int size = srcFile.getSize();

        // 【关键修复 step 1】先申请磁盘空间
        if (!fileSystem.allocateSpace(size)) {
            System.err.println("错误：磁盘空间不足，无法复制文件: " + srcFile.getName());
            return null;
        }

        // 【关键修复 step 2】处理重名 (如 producer.e -> producer(1).e)
        String uniqueName = getUniqueName(targetDir, srcFile.getName());

        // 【关键修复 step 3】创建新对象
        File newFile = new File(uniqueName, size);

        // 【关键修复 step 4】深拷贝数据 (Deep Copy)
        if (srcFile.getContent() != null) {
            byte[] srcData = srcFile.getContent();
            byte[] newData = new byte[srcData.length];
            // 内存级别的复制，确保新旧文件互不影响
            System.arraycopy(srcData, 0, newData, 0, srcData.length);
            newFile.setContent(newData);
        }

        // 挂载到目标目录
        targetDir.addChild(newFile);
        return newFile;
    }

    /**
     * 内部辅助：递归粘贴目录
     */
    private Directory pasteDirectoryRecursive(Directory srcDir, Directory targetDir) {
        // 创建新目录名
        String uniqueName = getUniqueName(targetDir, srcDir.getName());
        Directory newDir = new Directory(uniqueName);
        targetDir.addChild(newDir);

        // 遍历源目录的所有子节点
        for (Object child : srcDir.getChildren()) {
            if (child instanceof File) {
                // 复用上面的文件粘贴逻辑，但目标是刚创建的 newDir
                // 注意：这里我们不检查返回值，如果某个子文件因空间不足失败，只打印错误，继续复制下一个
                pasteFile((File) child, newDir);
            } else if (child instanceof Directory) {
                // 递归处理子文件夹
                pasteDirectoryRecursive((Directory) child, newDir);
            }
        }
        return newDir;
    }

    // --- 以下是原有的辅助方法和增删逻辑 (保持不变) ---

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
        return newFile;
    }

    public synchronized Directory createDirectory(String path, String name) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;
        String uniqueName = getUniqueName(parent, name);
        Directory newDir = new Directory(uniqueName);
        parent.addChild(newDir);
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

        if (target instanceof File) {
            File f = (File) target;
            parent.removeChild(f);
            fileSystem.freeSpace(f.getSize()); // 记得释放空间
            return true;
        } else if (target instanceof Directory) {
            return deleteDirectoryRecursive(parent, (Directory) target);
        }
        return false;
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

    // 辅助方法：为了兼容性，保留这些删除方法
    public synchronized boolean deleteFile(String path) { return deletePath(path); }
    public synchronized boolean deleteDirectory(String path) { return deletePath(path); }

    // 公开这个查找方法，方便 Kernel 调用检查目录是否存在
    public Directory getDirectory(String path) {
        return findDirectoryByPath(path);
    }

    // 私有查找逻辑
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
        // 计算大小：指令数/64 (向上取整)
        int size = Math.max(1, (instructions.size() + 63) / 64);
        File f = createFile(path, name, size);
        if (f == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String s : instructions) sb.append(s).append("\n");
        f.setContent(sb.toString().getBytes(StandardCharsets.UTF_8));
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
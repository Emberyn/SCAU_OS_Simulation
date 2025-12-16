package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

/**
 * 文件系统管理器 - 负责目录/文件的增删查以及空间管理
 * 提供简化的层级文件系统
 * * 【线程安全修复】增加了 synchronized 关键字，防止并发读写导致的文件树结构损坏或 UI 遍历报错。
 */
public class FileSystemManager
{
    private final FileSystem fileSystem;
    private final Directory rootDirectory;

    public FileSystemManager(FileSystem fileSystem)
    {
        this.fileSystem = fileSystem;
        this.rootDirectory = new Directory("root");

        // 创建基本目录结构
        Directory systemDir = new Directory("system");
        Directory userDir = new Directory("user");

        rootDirectory.addChild(systemDir);
        rootDirectory.addChild(userDir);

        // 创建一些系统文件
        File kernelFile = new File("kernel.sys", 128);
        systemDir.addChild(kernelFile);

        Directory execDir = new Directory("exec");
        systemDir.addChild(execDir);
    }

    /**
     * 处理命名冲突，生成唯一名称
     * (内部私有方法，通常被同步方法调用，本身不需要 public synchronized，但在同步块内执行是安全的)
     */
    private String getUniqueName(Directory parent, String originalName) {
        if (parent.findChild(originalName) == null) {
            return originalName;
        }

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

    /**
     * 在指定路径创建文件
     */
    public synchronized File createFile(String path, String name, int size)
    {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;

        if (!fileSystem.allocateSpace(size))
        {
            System.out.println("磁盘空间不足");
            return null;
        }

        String uniqueName = getUniqueName(parent, name);
        File newFile = new File(uniqueName, size);
        parent.addChild(newFile);

        return newFile;
    }

    /**
     * 在指定路径创建子目录
     */
    public synchronized Directory createDirectory(String path, String name)
    {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) return null;

        String uniqueName = getUniqueName(parent, name);
        Directory newDir = new Directory(uniqueName);
        parent.addChild(newDir);
        return newDir;
    }

    /**
     * 通用的删除方法（支持文件和递归删除目录）
     */
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
            fileSystem.freeSpace(f.getSize());
            return true;
        } else if (target instanceof Directory) {
            return deleteDirectoryRecursive(parent, (Directory) target);
        }

        return false;
    }

    private boolean deleteDirectoryRecursive(Directory parent, Directory target) {
        // 复制一份子节点列表以防止并发修改异常（虽然现在加了锁，防御性复制依然是好习惯）
        java.util.List<Object> children = new java.util.ArrayList<>(target.getChildren());

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

    /**
     * 删除指定路径的文件
     */
    public synchronized boolean deleteFile(String path)
    {
        // ... (原逻辑不变，只需加上 synchronized)
        // 为了代码复用，其实可以直接调用 deletePath，但为了保持您原有的细粒度控制，这里保留原逻辑
        String[] parts = path.split("/");
        if (parts.length == 0) return false;

        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++)
        {
            String part = parts[i];
            if (part.isEmpty()) continue;
            Object child = dir.findChild(part);
            if (!(child instanceof Directory d)) return false;
            dir = d;
        }

        String name = parts[parts.length - 1];
        Object child = dir.findChild(name);
        if (!(child instanceof File file)) return false;

        dir.removeChild(file);
        fileSystem.freeSpace(file.getSize());
        return true;
    }

    /**
     * 删除空目录
     */
    public synchronized boolean deleteDirectory(String path)
    {
        if (path.equals("/") || path.isEmpty()) return false;
        String[] parts = path.split("/");
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++)
        {
            String part = parts[i];
            if (part.isEmpty()) continue;
            Object child = dir.findChild(part);
            if (!(child instanceof Directory d)) return false;
            dir = d;
        }
        String name = parts[parts.length - 1];
        Object child = dir.findChild(name);
        if (!(child instanceof Directory target)) return false;
        if (!target.getChildren().isEmpty()) return false;
        return dir.removeChild(target);
    }

    /**
     * 按路径查找目录 (内部辅助方法，已被同步方法调用，本身可以不加 synchronized，但加了也无害)
     */
    private Directory findDirectoryByPath(String path)
    {
        if (path.equals("/") || path.isEmpty()) return rootDirectory;

        String[] parts = path.split("/");
        Directory current = rootDirectory;

        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            boolean found = false;
            for (Object child : current.getChildren())
            {
                if (child instanceof Directory d && d.getName().equals(part))
                {
                    current = d;
                    found = true;
                    break;
                }
            }
            if (!found) return null;
        }
        return current;
    }

    public synchronized File createExecutable(String path, String name, java.util.List<String> instructions)
    {
        File f = createFile(path, name, Math.max(1, (instructions.size() + 63) / 64));
        if (f == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : instructions) sb.append(s).append("\n");
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        f.setContent(bytes);
        return f;
    }

    public synchronized File createExecutable(String path, String name, org.example.scau_os_simulation.process.Executable exec)
    {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < exec.length(); i++) lines.add(exec.fetch(i));
        return createExecutable(path, name, lines);
    }

    public synchronized org.example.scau_os_simulation.process.Executable loadExecutable(String path)
    {
        // 查找逻辑
        File f = getFileByPath(path);
        if (f == null) return null;

        String content = new String(f.getContent(), java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<String> lines = java.util.Arrays.asList(content.split("\n"));

        return new org.example.scau_os_simulation.process.Executable(lines);
    }

    public synchronized File getFileByPath(String path)
    {
        String[] parts = path.split("/");
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++)
        {
            String part = parts[i];
            if (part.isEmpty()) continue;
            Object child = dir.findChild(part);
            if (child instanceof Directory d) dir = d;
            else return null;
        }
        String fileName = parts[parts.length - 1];
        Object child = dir.findChild(fileName);
        if (child instanceof File f) return f;
        else return null;
    }

    public synchronized Object getObjectByPath(String path) {
        if (path == null || path.isEmpty()) return null;
        if (path.equals("/")) return rootDirectory;

        Directory dir = findDirectoryByPath(path);
        if (dir != null) return dir;

        return getFileByPath(path);
    }

    public synchronized Directory getRootDirectory()
    {
        return rootDirectory;
    }

    public synchronized FileSystem getFileSystem()
    {
        return fileSystem;
    }

    public synchronized Object paste(Object source, String targetPath)
    {
        Directory target = findDirectoryByPath(targetPath);
        if (target == null) throw new IllegalArgumentException("目标路径不存在");
        return target.copyChild(source, null);
    }
}
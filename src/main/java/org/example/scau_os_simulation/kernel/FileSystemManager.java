package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

/**
 * 文件系统管理器 - 负责目录/文件的增删查以及空间管理
 * 提供简化的层级文件系统：
 * - 根目录下预置 `system` 与 `user` 两个目录，用于区分系统资源与用户数据；
 * - 支持创建/删除文件与目录，空间分配与释放（按 KB 计量，不涉及碎片与块表）；
 * - 支持加载/生成“可执行文件”文本内容，便于教学演示指令脚本的读取与执行。
 */
public class FileSystemManager
{
    private final FileSystem fileSystem;
    private final Directory rootDirectory;

    /**
     * 构造并初始化基本目录结构
     */
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
     * 【新增】处理命名冲突，生成唯一名称
     * 逻辑：如果 originalName 存在，则尝试 originalName(1), originalName(2)...
     * @param parent 父目录
     * @param originalName 原始名称
     * @return 唯一的名称
     */
    private String getUniqueName(Directory parent, String originalName) {
        // 如果当前名字没有冲突，直接返回
        if (parent.findChild(originalName) == null) {
            return originalName;
        }

        String baseName = originalName;
        String extension = "";

        // 分离文件名和扩展名 (例如: new.txt -> base="new", ext=".txt")
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        String newName;
        // 循环尝试，直到找到一个不存在的名字
        do {
            newName = baseName + "(" + counter + ")" + extension;
            counter++;
        } while (parent.findChild(newName) != null);

        return newName;
    }

    /**
     * 在指定路径创建文件 (已修复命名冲突)
     *
     * @param path 目录路径，如 "/user"
     * @param name 文件名
     * @param size 文件大小（KB）
     * @return 新建的文件；空间不足或路径不存在时返回 null
     */
    public File createFile(String path, String name, int size)
    {
        // 1) 根据路径找到父目录
        Directory parent = findDirectoryByPath(path);
        if (parent == null)
        {
            return null;
        }

        // 2) 向文件系统申请空间
        if (!fileSystem.allocateSpace(size))
        {
            System.out.println("磁盘空间不足");
            return null;
        }

        // 3) 【修复点】获取唯一名称，防止重复
        String uniqueName = getUniqueName(parent, name);

        // 4) 创建文件对象并挂到父目录下
        File newFile = new File(uniqueName, size);
        parent.addChild(newFile);

        return newFile;
    }

    /**
     * 在指定路径创建子目录 (已修复命名冲突)
     *
     * @param path 父目录路径
     * @param name 子目录名
     * @return 新建的目录；父目录不存在时返回 null
     */
    public Directory createDirectory(String path, String name)
    {
        Directory parent = findDirectoryByPath(path);
        if (parent == null)
        {
            return null;
        }

        // 【修复点】获取唯一名称，防止重复
        String uniqueName = getUniqueName(parent, name);

        Directory newDir = new Directory(uniqueName);
        parent.addChild(newDir);
        return newDir;
    }

    /**
     * 通用的删除方法（支持文件和递归删除目录）
     */
    public boolean deletePath(String path) {
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
    public boolean deleteFile(String path)
    {
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
    public boolean deleteDirectory(String path)
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
     * 按路径查找目录
     */
    private Directory findDirectoryByPath(String path)
    {
        if (path.equals("/") || path.isEmpty())
        {
            return rootDirectory;
        }

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

            if (!found)
            {
                return null;
            }
        }

        return current;
    }

    /**
     * 生成可执行文件
     */
    public File createExecutable(String path, String name, java.util.List<String> instructions)
    {
        // 这里的 createFile 也会自动应用重命名规则
        File f = createFile(path, name, Math.max(1, (instructions.size() + 63) / 64));
        if (f == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : instructions)
        {
            sb.append(s).append("\n");
        }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        f.setContent(bytes);
        return f;
    }

    public File createExecutable(String path, String name, org.example.scau_os_simulation.process.Executable exec)
    {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < exec.length(); i++)
        {
            lines.add(exec.fetch(i));
        }
        return createExecutable(path, name, lines);
    }

    /**
     * 加载可执行文件
     */
    public org.example.scau_os_simulation.process.Executable loadExecutable(String path)
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
        if (!(child instanceof File f)) return null;

        String content = new String(f.getContent(), java.nio.charset.StandardCharsets.UTF_8);
        java.util.List<String> lines = java.util.Arrays.asList(content.split("\n"));

        return new org.example.scau_os_simulation.process.Executable(lines);
    }

    /**
     * 按绝对路径获取普通文件
     */
    public File getFileByPath(String path)
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

    public Directory getRootDirectory()
    {
        return rootDirectory;
    }

    public FileSystem getFileSystem()
    {
        return fileSystem;
    }

    /**
     * 粘贴文件或目录
     */
    public Object paste(Object source, String targetPath)
    {
        Directory target = findDirectoryByPath(targetPath);
        if (target == null) throw new IllegalArgumentException("目标路径不存在");
        return target.copyChild(source, null);
    }
}
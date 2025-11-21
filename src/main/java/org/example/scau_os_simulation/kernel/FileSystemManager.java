package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

/**
 * 文件系统管理器 - 负责目录/文件的增删查以及空间管理
 *
 * 提供简化的层级文件系统：
 * - 根目录下预置 `system` 与 `user` 两个目录，用于区分系统资源与用户数据；
 * - 支持创建/删除文件与目录，空间分配与释放（按 KB 计量，不涉及碎片与块表）；
 * - 支持加载/生成“可执行文件”文本内容，便于教学演示指令脚本的读取与执行。
 */
public class FileSystemManager {
    private final FileSystem fileSystem;
    private final Directory rootDirectory;
    
    /**
     * 构造并初始化基本目录结构
     *
     * 初始化内容：
     * - `root` 作为可视化根；下设 `system` 与 `user` 两个直系子目录；
     * - 在 `system` 下创建 `kernel.sys`（占位文件）与 `exec` 目录（用于存放可执行脚本）。
     */
    public FileSystemManager(FileSystem fileSystem) {
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
     * 在指定路径创建文件
     * @param path 目录路径，如 "/user"
     * @param name 文件名
     * @param size 文件大小（KB）
     * @return 新建的文件；空间不足或路径不存在时返回 null
     */
    public File createFile(String path, String name, int size) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) {
            return null;
        }
        
        // 检查文件系统空间
        if (!fileSystem.allocateSpace(size)) {
            System.out.println("磁盘空间不足");
            return null;
        }
        
        File newFile = new File(name, size);
        parent.addChild(newFile);
        return newFile;
    }
    
    /**
     * 在指定路径创建子目录
     * @param path 父目录路径
     * @param name 子目录名
     * @return 新建的目录；父目录不存在时返回 null
     */
    public Directory createDirectory(String path, String name) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) {
            return null;
        }
        
        Directory newDir = new Directory(name);
        parent.addChild(newDir);
        return newDir;
    }
    
    /**
     * 删除指定路径的文件
     *
     * 通过逐级解析路径定位父目录与文件；删除后释放占用空间。
     * @param path 文件绝对路径
     * @return 删除成功与否
     */
    public boolean deleteFile(String path) {
        String[] parts = path.split("/");
        if (parts.length == 0) return false;
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++) {
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
     *
     * 根目录不可删除；仅当目标目录为空时删除成功。
     * @param path 目录绝对路径
     * @return 删除成功与否
     */
    public boolean deleteDirectory(String path) {
        if (path.equals("/") || path.isEmpty()) return false;
        String[] parts = path.split("/");
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++) {
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
     *
     * 简单的逐级名称匹配；不存在任何符号链接或复杂解析。
     * @param path 绝对路径（如 "/system/exec"）
     * @return 对应目录；不存在返回 null
     */
    private Directory findDirectoryByPath(String path) {
        if (path.equals("/") || path.isEmpty()) {
            return rootDirectory;
        }
        
        // 简单路径解析
        String[] parts = path.split("/");
        Directory current = rootDirectory;
        
        for (String part : parts) {
            if (part.isEmpty()) continue;
            
            boolean found = false;
            for (Object child : current.getChildren()) {
                if (child instanceof Directory d && d.getName().equals(part)) {
                    current = d;
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                return null;
            }
        }
        
        return current;
    }

    /**
     * 生成可执行文件
     *
     * 将指令行集合写入文件内容；文件大小按64KB块向上取整。
     * 说明：
     * - 指令文本以 UTF-8 存储，每行一条；
     * - 目标文件容量只做近似估算（按行数粗略取整），便于演示存储消耗。
     */
    public File createExecutable(String path, String name, java.util.List<String> instructions) {
        File f = createFile(path, name, Math.max(1, (instructions.size() + 63) / 64));
        if (f == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : instructions) { sb.append(s).append("\n"); }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        f.setContent(bytes);
        return f;
    }

    /**
     * 加载可执行文件
     *
     * 读取文本内容并解析为指令列表，封装为 Executable 对象。
     *
     * 路径解析规则：
     * - 逐级在目录中查找名称匹配的子目录；最后一段名称匹配文件；
     * - 若任一层不存在或类型不匹配，返回 null。
     */
    public org.example.scau_os_simulation.process.Executable loadExecutable(String path) {
        String[] parts = path.split("/");
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            Object child = dir.findChild(part);
            if (child instanceof Directory d) dir = d; else return null;
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
     * @param path 绝对路径（如 "/user/docs/readme.txt"）
     * @return 文件对象；不存在或类型不匹配时返回 null
     */
    public File getFileByPath(String path) {
        String[] parts = path.split("/");
        Directory dir = rootDirectory;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            Object child = dir.findChild(part);
            if (child instanceof Directory d) dir = d; else return null;
        }
        String fileName = parts[parts.length - 1];
        Object child = dir.findChild(fileName);
        if (child instanceof File f) return f; else return null;
    }
    
    /**
     * 获取根目录
     */
    public Directory getRootDirectory() {
        return rootDirectory;
    }
    
    /**
     * 获取文件系统存储模型
     */
    public FileSystem getFileSystem() {
        return fileSystem;
    }
}

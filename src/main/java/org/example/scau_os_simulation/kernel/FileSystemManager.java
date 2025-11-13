package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

public class FileSystemManager {
    private final FileSystem fileSystem;
    private final Directory rootDirectory;
    
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
    
    public Directory createDirectory(String path, String name) {
        Directory parent = findDirectoryByPath(path);
        if (parent == null) {
            return null;
        }
        
        Directory newDir = new Directory(name);
        parent.addChild(newDir);
        return newDir;
    }
    
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

    public File createExecutable(String path, String name, java.util.List<String> instructions) {
        File f = createFile(path, name, Math.max(1, (instructions.size() + 63) / 64));
        if (f == null) return null;
        StringBuilder sb = new StringBuilder();
        for (String s : instructions) { sb.append(s).append("\n"); }
        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        f.setContent(bytes);
        return f;
    }

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
    
    public Directory getRootDirectory() {
        return rootDirectory;
    }
    
    public FileSystem getFileSystem() {
        return fileSystem;
    }
}

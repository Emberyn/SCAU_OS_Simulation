package org.example.scau_os_simulation.kernel;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.filesystem.FileSystem;

public class FileSystemManager {
    private FileSystem fileSystem;
    private Directory rootDirectory;
    
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
        // 实现文件删除逻辑
        return false;
    }
    
    public boolean deleteDirectory(String path) {
        // 实现目录删除逻辑
        return false;
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
                if (child instanceof Directory && ((Directory) child).getName().equals(part)) {
                    current = (Directory) child;
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
    
    public Directory getRootDirectory() {
        return rootDirectory;
    }
    
    public FileSystem getFileSystem() {
        return fileSystem;
    }
}
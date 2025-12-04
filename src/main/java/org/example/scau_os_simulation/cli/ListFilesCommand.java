package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "列出目录内容")
public class ListFilesCommand implements Runnable {

    @Parameters(index = "0", description = "路径", defaultValue = "/")
    private String path;

    @Option(names = {"-l"}, description = "详细信息")
    private boolean detail;

    @Override
    public void run() {
        Object node = Kernel.getInstance().getFileSystemManager().getFileByPath(path);
        
        // 如果 getFileByPath 返回 null，可能是因为它只返回 File，需要检查是不是 Directory
        // FileSystemManager.getFileByPath 似乎只返回 File (根据之前的 Read)
        // 让我们检查 FileSystemManager 的 api。
        // 它有 getFileByPath 返回 File。那 Directory 呢？
        // 它有一个 findDirectoryByPath 或者是内部方法。
        // 让我们看看 Kernel 代码。
        // FileSystemManager.getFileByPath implementation:
        // Object child = dir.findChild(fileName); if (child instanceof File f) return f; else return null;
        // 看来它只返回文件。
        // 我需要一个新的方法或者用更底层的方法来获取 Directory。
        // 但是 FileSystemManager.rootDirectory 是 public (getter)。
        // 我可以自己遍历。
        
        // 实际上，我应该在 FileSystemManager 中增加一个通用 getNodeByPath 或者在这里实现简单的查找。
        // 为了不修改 Kernel 太多，我在这里实现简单的查找逻辑。
        
        Directory current = Kernel.getInstance().getFileSystemManager().getRootDirectory();
        if (path.equals("/")) {
            listDir(current);
            return;
        }
        
        // 简单解析路径
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            Object child = current.findChild(part);
            if (child instanceof Directory) {
                current = (Directory) child;
            } else if (child instanceof File) {
                 // 如果是文件，显示单个文件信息
                 printNode(child);
                 return;
            } else {
                Kernel.getInstance().logOutput("错误: 路径不存在 " + path);
                return;
            }
        }
        
        listDir(current);
    }

    private void listDir(Directory dir) {
        Kernel.getInstance().logOutput("目录 " + dir.getName() + " 的内容:");
        for (Object child : dir.getChildren()) {
            printNode(child);
        }
    }
    
    private void printNode(Object node) {
        if (node instanceof Directory) {
            Kernel.getInstance().logOutput("[DIR]  " + ((Directory) node).getName());
        } else if (node instanceof File) {
            File f = (File) node;
            String info = "[FILE] " + f.getName();
            if (detail) {
                info += " (Size: " + f.getSize() + "KB)";
            }
            Kernel.getInstance().logOutput(info);
        }
    }
}

package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "tree", description = "以树状图列出目录内容")
public class TreeCommand implements Runnable {

    @Parameters(index = "0", description = "路径", defaultValue = ".")
    private String path;

    // 支持 /f (Windows风格) 和 -f (Linux风格)
    @Option(names = {"-f", "/f"}, description = "显示每个目录中的文件名")
    private boolean showFiles;

    @Override
    public void run() {
        String absolutePath = ShellContext.getInstance().resolvePath(path);

        // 手动查找目标目录节点
        Directory rootDir = Kernel.getInstance().getFileSystemManager().getRootDirectory();
        Object targetNode = findNode(rootDir, absolutePath);

        if (targetNode instanceof Directory) {
            Directory targetDir = (Directory) targetNode;
            Kernel.getInstance().printToTerminal(targetDir.getName());
            printTree(targetDir, "", true);
        } else if (targetNode instanceof File) {
            Kernel.getInstance().printToTerminal(path + " [error opening dir]");
        } else {
            Kernel.getInstance().printToTerminal("Folder path not found.");
        }
    }

    /**
     * 递归打印树结构
     * @param dir 当前目录
     * @param prefix 前缀字符
     * @param isTail 是否是当前层级的最后一个元素
     */
    private void printTree(Directory dir, String prefix, boolean isTail) {
        List<Object> children = dir.getChildren();

        // 如果不显示文件，先过滤一下 children 列表，只保留 Directory
        if (!showFiles) {
            children = children.stream().filter(c -> c instanceof Directory).toList();
        }

        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            boolean isLast = (i == children.size() - 1);

            String childName = (child instanceof Directory) ? ((Directory) child).getName() : ((File) child).getName();

            // 打印当前节点
            Kernel.getInstance().printToTerminal(prefix + (isLast ? "└── " : "├── ") + childName);

            // 如果是目录，递归打印
            if (child instanceof Directory) {
                // 计算下一级的前缀：如果当前是最后一个，下一级前缀就是空格；否则是竖线
                String nextPrefix = prefix + (isLast ? "    " : "│   ");
                printTree((Directory) child, nextPrefix, isLast);
            }
        }
    }

    // 辅助方法：查找节点 (复用类似 ListFilesCommand 的逻辑)
    private Object findNode(Directory root, String absPath) {
        if (absPath.equals("/")) return root;
        String[] parts = absPath.split("/");
        Object current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current instanceof Directory) {
                boolean found = false;
                for (Object child : ((Directory) current).getChildren()) {
                    String name = (child instanceof Directory) ? ((Directory) child).getName() : ((File) child).getName();
                    if (name.equals(part)) {
                        current = child;
                        found = true;
                        break;
                    }
                }
                if (!found) return null;
            } else {
                return null;
            }
        }
        return current;
    }
}
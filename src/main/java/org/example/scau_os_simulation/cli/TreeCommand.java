package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.stream.Collectors;

@Command(name = "tree", description = "以树状图列出目录内容")
public class TreeCommand implements Runnable {

    @Parameters(index = "0", description = "路径", defaultValue = ".")
    private String path;

    @Option(names = {"-f", "/f"}, description = "显示每个目录中的文件名")
    private boolean showFiles;

    @Override
    public void run() {
        String absolutePath = ShellContext.getInstance().resolvePath(path);

        Directory rootDir = Kernel.getInstance().getFileSystemManager().getRootDirectory();
        Object targetNode = findNode(rootDir, absolutePath);

        if (targetNode instanceof Directory) {
            Directory targetDir = (Directory) targetNode;

            // 【优化】使用 StringBuilder 缓冲所有输出
            // 这样避免了成百上千次调用 printToTerminal 导致的 UI 线程拥堵
            StringBuilder sb = new StringBuilder();
            sb.append(targetDir.getName()).append("\n");

            printTree(targetDir, "", true, sb);

            // 【重要修复】分批输出大量数据，避免UI线程阻塞和滚动条问题
            String fullOutput = sb.toString().trim();
            String[] lines = fullOutput.split("\n");
            
            if (lines.length > 100) {
                // 如果输出行数超过100行，分批输出，每批50行
                int batchSize = 50;
                StringBuilder batchBuilder = new StringBuilder();
                
                for (int i = 0; i < lines.length; i++) {
                    batchBuilder.append(lines[i]).append("\n");
                    
                    // 每50行输出一批，或最后一行
                    if ((i + 1) % batchSize == 0 || i == lines.length - 1) {
                        Kernel.getInstance().printToTerminal(batchBuilder.toString().trim());
                        batchBuilder = new StringBuilder();
                        
                        // 小延迟，让UI有时间更新滚动条
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // 忽略中断
                        }
                    }
                }
            } else {
                // 一次性推送到终端，性能极快，且滚动条能立即正确计算高度
                Kernel.getInstance().printToTerminal(fullOutput);
            }

        } else if (targetNode instanceof File) {
            Kernel.getInstance().printToTerminal(path + " [error opening dir]");
        } else {
            Kernel.getInstance().printToTerminal("Folder path not found.");
        }
    }

    /**
     * 递归构建字符串
     */
    private void printTree(Directory dir, String prefix, boolean isTail, StringBuilder sb) {
        List<Object> children = dir.getChildren();

        if (!showFiles) {
            children = children.stream().filter(c -> c instanceof Directory).collect(Collectors.toList());
        }

        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            boolean isLast = (i == children.size() - 1);

            String childName = (child instanceof Directory) ? ((Directory) child).getName() : ((File) child).getName();

            // 追加到缓冲区
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(childName).append("\n");

            if (child instanceof Directory) {
                String nextPrefix = prefix + (isLast ? "    " : "│   ");
                printTree((Directory) child, nextPrefix, isLast, sb);
            }
        }
    }

    // 复用之前的查找逻辑
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
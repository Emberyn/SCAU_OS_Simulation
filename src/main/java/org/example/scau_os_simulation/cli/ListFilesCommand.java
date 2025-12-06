package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "ls", description = "列出目录内容")
public class ListFilesCommand implements Runnable
{

    // 默认值改为 "." 表示当前目录
    @Parameters(index = "0", description = "路径", defaultValue = ".")
    private String path;

    @Option(names = {"-l"}, description = "详细信息")
    private boolean detail;

    @Override
    public void run()
    {
        // 1. 解析路径 (将 "." 或 "subdir" 转换为绝对路径)
        String absolutePath = ShellContext.getInstance().resolvePath(path);

        // 2. 手动查找节点 (因为 Kernel 的 getFileByPath 可能只返回 File)
        Directory current = Kernel.getInstance().getFileSystemManager().getRootDirectory();

        if (absolutePath.equals("/"))
        {
            listDir(current);
            return;
        }

        String[] parts = absolutePath.split("/");
        Object targetNode = current;
        boolean found = true;

        // 遍历查找
        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            if (targetNode instanceof Directory)
            {
                boolean childFound = false;
                for (Object child : ((Directory) targetNode).getChildren())
                {
                    String childName = (child instanceof Directory) ? ((Directory) child).getName() : ((File) child).getName();
                    if (childName.equals(part))
                    {
                        targetNode = child;
                        childFound = true;
                        break;
                    }
                }
                if (!childFound)
                {
                    found = false;
                    break;
                }
            } else
            {
                found = false;
                break;
            }
        }

        if (!found)
        {
            Kernel.getInstance().printToTerminal("ls: cannot access '" + path + "': No such file or directory");
            return;
        }

        if (targetNode instanceof Directory)
        {
            listDir((Directory) targetNode);
        } else if (targetNode instanceof File)
        {
            printNode(targetNode);
        }
    }

    private void listDir(Directory dir)
    {
        if (dir.getChildren().isEmpty())
        {
            return; // 空目录不打印内容
        }
        for (Object child : dir.getChildren())
        {
            printNode(child);
        }
    }

    private void printNode(Object node)
    {
        if (node instanceof Directory)
        {
            // 蓝色显示目录 (模拟颜色代码在 LogView 中可能无效，这里仅用文本标记)
            Kernel.getInstance().printToTerminal("[DIR]  " + ((Directory) node).getName());
        } else if (node instanceof File)
        {
            File f = (File) node;
            String info = f.getName();
            if (detail)
            {
                info += "\t" + f.getSize() + "KB";
            }
            Kernel.getInstance().printToTerminal(info);
        }
    }
}
package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code ListFilesCommand} 类实现了 {@code ls} 命令，用于列出指定目录的内容。
 * 它支持显示详细信息（通过 {@code -l} 选项）。
 * 使用 Picocli 库来解析命令行参数和选项。
 */
@Command(name = "ls", description = "列出目录内容")
public class ListFilesCommand implements Runnable
{

    /**
     * {@code path} 字段用于接收命令行中指定的目录路径。
     * {@code @Parameters(index = "0", description = "路径", defaultValue = ".")} 注解表示这是第一个位置参数，
     * 其描述为 "路径"，并且如果用户未提供路径，则默认值为 "."（表示当前目录）。
     */
    @Parameters(index = "0", description = "路径", defaultValue = ".")
    private String path;

    /**
     * {@code detail} 字段是一个布尔类型，用于判断用户是否使用了 {@code -l} 选项。
     * 如果使用了 {@code -l}，则为 {@code true}，表示需要显示文件的详细信息（例如文件大小）。
     */
    @Option(names = {"-l"}, description = "详细信息")
    private boolean detail;

    @Override
    public void run()
    {
        // 1. 解析路径
        // 使用 ShellContext 将用户输入的路径（可能是相对路径或 "."）解析为标准的绝对路径。
        String absolutePath = ShellContext.getInstance().resolvePath(path);

        // 2. 获取文件系统根目录，作为查找的起点。
        Directory current = Kernel.getInstance().getFileSystemManager().getRootDirectory();

        // 特殊处理：如果路径是根目录 "/"，则直接列出根目录内容。
        if (absolutePath.equals("/"))
        {
            listDir(current);
            return;
        }

        // 将绝对路径按 "/" 分割成各个目录或文件名称片段。
        String[] parts = absolutePath.split("/");
        // targetNode 用于在文件系统树中逐步定位目标。
        Object targetNode = current;
        // found 标志用于记录是否成功找到目标路径。
        boolean found = true;

        // 3. 遍历查找目标节点
        for (String part : parts)
        {
            // 跳过空字符串片段（例如，路径开头或连续斜杠导致）。
            if (part.isEmpty()) continue;

            // 如果当前节点是目录，则在其子项中查找下一个片段。
            if (targetNode instanceof Directory)
            {
                boolean childFound = false; // 标志当前片段是否在子项中找到。
                // 遍历当前目录的所有子项（文件或子目录）。
                for (Object child : ((Directory) targetNode).getChildren())
                {
                    // 获取子项的名称。
                    String childName = (child instanceof Directory) ? ((Directory) child).getName() : ((File) child).getName();
                    // 如果子项名称与当前路径片段匹配。
                    if (childName.equals(part))
                    {
                        targetNode = child; // 更新 targetNode 为找到的子项。
                        childFound = true;  // 标记为已找到。
                        break;              // 跳出内层循环，处理下一个路径片段。
                    }
                }
                // 如果当前路径片段未在子项中找到，则整个路径无效。
                if (!childFound)
                {
                    found = false;
                    break;
                }
            } else
            {
                // 如果 targetNode 不是目录（即是文件），但路径中还有后续片段，说明路径无效。
                found = false;
                break;
            }
        }

        // 4. 根据查找结果进行处理
        if (!found)
        {
            // 如果目标路径未找到，则打印错误消息。
            Kernel.getInstance().printToTerminal("ls: cannot access '" + path + "': No such file or directory");
            return;
        }

        // 如果目标节点是目录，则列出其内容。
        if (targetNode instanceof Directory)
        {
            listDir((Directory) targetNode);
        } else if (targetNode instanceof File)
        {
            // 如果目标节点是文件，则直接打印文件信息。
            printNode(targetNode);
        }
    }

    /**
     * 辅助方法：列出指定目录 {@code dir} 的内容。
     * 遍历目录下的所有子项（文件和子目录），并调用 {@code printNode} 方法打印它们的信息。
     *
     * @param dir 要列出内容的目录对象。
     */
    private void listDir(Directory dir)
    {
        // 如果目录为空，则不打印任何内容。
        if (dir.getChildren().isEmpty())
        {
            return; 
        }
        // 遍历目录中的所有子项。
        for (Object child : dir.getChildren())
        {
            printNode(child); // 打印每个子项的信息。
        }
    }

    /**
     * 辅助方法：打印文件系统节点（文件或目录）的信息到终端。
     * 根据节点的类型和 {@code detail} 选项决定打印的格式。
     *
     * @param node 要打印信息的节点对象（可以是 {@code Directory} 或 {@code File}）。
     */
    private void printNode(Object node)
    {
        if (node instanceof Directory)
        {
            // 如果是目录，打印 "[DIR]" 标记和目录名称。
            // 模拟颜色代码在 LogView 中可能无效，这里仅用文本标记。
            Kernel.getInstance().printToTerminal("[DIR]  " + ((Directory) node).getName());
        } else if (node instanceof File)
        {
            // 如果是文件，打印文件名称。
            File f = (File) node;
            String info = f.getName();
            if (detail)
            {
                // 如果开启了详细模式（-l 选项），则追加文件大小信息。
                info += "\t" + f.getSize() + "KB";
            }
            Kernel.getInstance().printToTerminal(info); // 打印文件信息。
        }
    }
}
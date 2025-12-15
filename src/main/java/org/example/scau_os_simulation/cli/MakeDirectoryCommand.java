package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code MakeDirectoryCommand} 类实现了 {@code mkdir} 命令，用于在文件系统中创建新的目录。
 * 它使用 Picocli 库来解析命令行参数。
 */
@Command(name = "mkdir", description = "创建目录")
public class MakeDirectoryCommand implements Runnable
{

    /**
     * {@code inputPath} 字段用于接收命令行中指定的新目录的名称或路径。
     * {@code @Parameters(index = "0", description = "目录名或路径")} 注解表示这是第一个位置参数，其描述为 "目录名或路径"。
     */
    @Parameters(index = "0", description = "目录名或路径")
    private String inputPath;

    @Override
    public void run()
    {
        // 1. 解析目标绝对路径
        // 使用 ShellContext 将用户输入的相对路径或目录名解析为完整的绝对路径。
        String absolutePath = ShellContext.getInstance().resolvePath(inputPath);

        // 2. 分离父路径和新目录名
        // 找到路径中最后一个斜杠的位置，以区分父目录路径和要创建的新目录的名称。
        int lastSlash = absolutePath.lastIndexOf('/');
        // 提取父目录路径。如果路径是根目录 "/"，则父路径也是 "/"；否则截取到最后一个斜杠之前。
        String parentPath = (lastSlash == 0) ? "/" : absolutePath.substring(0, lastSlash);
        // 提取新目录的名称，即最后一个斜杠之后的部分。
        String dirName = absolutePath.substring(lastSlash + 1);

        // 3. 调用内核创建目录
        // 通过 Kernel 实例获取文件系统管理器，并尝试创建新目录。
        // createDirectory 方法会返回新创建的目录对象，如果创建失败则返回 null。
        var dir = Kernel.getInstance().getFileSystemManager().createDirectory(parentPath, dirName);

        // 4. 根据创建结果输出信息到终端
        if (dir != null)
        {
            // 如果目录成功创建，则打印成功消息。
            Kernel.getInstance().printToTerminal("Created directory: " + absolutePath);
        } else
        {
            // 如果目录创建失败，则打印错误消息。
            // 失败原因可能是父目录不存在，或者同名目录已存在。
            Kernel.getInstance().printToTerminal("mkdir: cannot create directory '" + inputPath + "': Parent not found or exists");
        }
    }
}
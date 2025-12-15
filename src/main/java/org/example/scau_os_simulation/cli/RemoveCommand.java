package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code RemoveCommand} 类实现了 {@code rm} 命令，用于删除文件或目录。
 * 它使用 Picocli 库来解析命令行参数。
 */
@Command(name = "rm", description = "删除文件或目录")
public class RemoveCommand implements Runnable
{

    /**
     * {@code path} 字段用于接收命令行中指定的文件或目录路径。
     * {@code @Parameters(index = "0", description = "路径")} 注解表示这是第一个位置参数，其描述为 "路径"。
     */
    @Parameters(index = "0", description = "路径")
    private String path;

    @Override
    public void run()
    {
        // 尝试删除文件
        // 首先调用文件系统管理器尝试删除指定路径的文件。
        boolean success = Kernel.getInstance().getFileSystemManager().deleteFile(path);
        if (!success)
        {
            // 如果删除文件失败（例如，路径不是文件或者文件不存在），则尝试删除目录。
            success = Kernel.getInstance().getFileSystemManager().deleteDirectory(path);
        }

        // 根据删除操作的结果，向终端输出相应的信息。
        if (success)
        {
            // 如果文件或目录成功删除，则打印成功消息。
            Kernel.getInstance().printToTerminal("删除成功: " + path);
        } else
        {
            // 如果文件和目录都删除失败，则打印失败消息。
            // 失败原因可能是文件/目录不存在，或者尝试删除非空目录。
            Kernel.getInstance().printToTerminal("删除失败: 文件/目录不存在，或目录非空");
        }
    }
}

package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "删除文件或目录")
public class RemoveCommand implements Runnable
{

    @Parameters(index = "0", description = "路径")
    private String path;

    @Override
    public void run()
    {
        // 尝试删除文件
        boolean success = Kernel.getInstance().getFileSystemManager().deleteFile(path);
        if (!success)
        {
            // 尝试删除目录
            success = Kernel.getInstance().getFileSystemManager().deleteDirectory(path);
        }

        if (success)
        {
            Kernel.getInstance().printToTerminal("删除成功: " + path);
        } else
        {
            Kernel.getInstance().printToTerminal("删除失败: 文件/目录不存在，或目录非空");
        }
    }
}

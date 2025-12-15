package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "删除文件或目录 (自动递归)")
public class RemoveCommand implements Runnable
{
    @Parameters(index = "0", description = "路径")
    private String path;

    @Override
    public void run()
    {
        // 1. 【修复】解析绝对路径 (解决问题1：找不到文件)
        String absolutePath = ShellContext.getInstance().resolvePath(path);

        // 2. 【修复】调用支持递归删除的通用方法 (解决问题2：目录非空)
        // 注意：这里调用的是我们刚刚在 FileSystemManager 新增的 deletePath 方法
        boolean success = Kernel.getInstance().getFileSystemManager().deletePath(absolutePath);

        if (success)
        {
            Kernel.getInstance().printToTerminal("删除成功: " + absolutePath);
        } else
        {
            Kernel.getInstance().printToTerminal("删除失败: " + path + " 不存在或系统保护");
        }
    }
}
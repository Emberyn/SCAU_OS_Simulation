package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "cd", description = "切换当前工作目录")
public class ChangeDirectoryCommand implements Runnable
{

    @Parameters(index = "0", description = "目标路径", defaultValue = "/")
    private String path;

    @Override
    public void run()
    {
        ShellContext ctx = ShellContext.getInstance();
        String targetPath = ctx.resolvePath(path);

        // 验证目录是否存在
        if (ctx.isValidDirectory(targetPath))
        {
            ctx.setCurrentPath(targetPath);
            // 可以在这里输出类似 Linux 的 prompt 更新，或者仅在日志显示
            Kernel.getInstance().printToTerminal("Switched to: " + targetPath);
        } else
        {
            Kernel.getInstance().printToTerminal("bash: cd: " + path + ": No such directory");
        }
    }
}
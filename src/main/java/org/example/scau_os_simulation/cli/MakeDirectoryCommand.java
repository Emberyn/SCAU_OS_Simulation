package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "mkdir", description = "创建目录")
public class MakeDirectoryCommand implements Runnable
{

    @Parameters(index = "0", description = "目录名或路径")
    private String inputPath;

    @Override
    public void run()
    {
        // 1. 解析目标绝对路径
        String absolutePath = ShellContext.getInstance().resolvePath(inputPath);

        // 2. 分离父路径和新目录名
        int lastSlash = absolutePath.lastIndexOf('/');
        String parentPath = (lastSlash == 0) ? "/" : absolutePath.substring(0, lastSlash);
        String dirName = absolutePath.substring(lastSlash + 1);

        // 3. 调用内核创建
        var dir = Kernel.getInstance().getFileSystemManager().createDirectory(parentPath, dirName);

        if (dir != null)
        {
            Kernel.getInstance().printToTerminal("Created directory: " + absolutePath);
        } else
        {
            Kernel.getInstance().printToTerminal("mkdir: cannot create directory '" + inputPath + "': Parent not found or exists");
        }
    }
}
package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "touch", description = "创建空文件")
public class TouchCommand implements Runnable
{
    @Parameters(index = "0", description = "文件名或路径")
    private String inputPath;

    @Override
    public void run()
    {
        // 1. 解析路径
        String absolutePath = ShellContext.getInstance().resolvePath(inputPath);

        // 2. 分离父目录和文件名
        // 例如 /user/docs/new.txt -> parent="/user/docs", name="new.txt"
        int lastSlash = absolutePath.lastIndexOf('/');
        String parentPath = (lastSlash == 0) ? "/" : absolutePath.substring(0, lastSlash);
        String fileName = absolutePath.substring(lastSlash + 1);

        // 3. 检查文件是否已存在
        Object existing = Kernel.getInstance().getFileSystemManager().getFileByPath(absolutePath);
        if (existing != null) {
            // touch 在真实系统中是更新时间戳，这里我们简单处理：如果存在就不报错也不覆盖
            return;
        }

        // 4. 调用内核创建文件 (默认 1KB)
        var file = Kernel.getInstance().getFileSystemManager().createFile(parentPath, fileName, 1);

        if (file != null)
        {
            Kernel.getInstance().printToTerminal("Created file: " + absolutePath);
        } else
        {
            Kernel.getInstance().printToTerminal("touch: cannot create file '" + inputPath + "': Parent directory not found or disk full");
        }
    }
}
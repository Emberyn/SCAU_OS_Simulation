package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import org.example.scau_os_simulation.process.Executable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", description = "从可执行文件创建新进程")
public class CreateProcessCommand implements Runnable
{

    @Parameters(index = "0", description = "进程名称", defaultValue = "NewProcess")
    private String name;

    @Option(names = {"-p", "--priority"}, description = "优先级 (1-5)", defaultValue = "1")
    private int priority;

    @Option(names = {"-f", "--file"}, description = "可执行文件路径 (支持相对路径)", required = true)
    private String filePath;

    @Override
    public void run()
    {
        Kernel kernel = Kernel.getInstance();

        // 1. 校验优先级
        if (priority < 1 || priority > 5)
        {
            kernel.printToTerminal("错误: 优先级必须在 1 到 5 之间");
            return;
        }

        // 2. 解析文件路径 (核心步骤)
        // 利用 ShellContext 将用户输入的路径（可能是相对路径）转换为系统的绝对路径
        String resolvedPath = ShellContext.getInstance().resolvePath(filePath);

        // 3. 简单的文件后缀校验 (类似于 MainController)
        if (!resolvedPath.endsWith(".e"))
        {
            kernel.printToTerminal("错误: 文件必须以 .e 结尾");
            return;
        }

        // 4. 尝试加载可执行文件
        Executable exec = kernel.getFileSystemManager().loadExecutable(resolvedPath);
        if (exec == null)
        {
            kernel.printToTerminal("错误: 无法加载文件 '" + resolvedPath + "' (文件不存在或格式错误)");
            return;
        }

        // 5. 创建进程
        // 如果用户没填名字(默认NewProcess)，且为了方便，我们可以自动把文件名作为进程名(可选优化)
        if ("NewProcess".equals(name))
        {
            // 从路径中提取文件名: /a/b/demo.e -> demo
            String fileName = resolvedPath.substring(resolvedPath.lastIndexOf('/') + 1);
            if (fileName.endsWith(".e"))
            {
                name = fileName.substring(0, fileName.length() - 2);
            }
        }

        var p = kernel.getProcessManager().createProcess(name, priority);

        if (p != null)
        {
            // 6. 将加载的代码绑定到进程
            p.setExecutable(exec);

            kernel.printToTerminal("进程创建成功: " + name +
                    " (PID: " + p.getPcb().getPid() +
                    ", 优先级: " + priority +
                    ", 源文件: " + resolvedPath + ")");
        } else
        {
            kernel.printToTerminal("进程创建失败: 可能 PID 耗尽或内存不足");
        }
    }
}
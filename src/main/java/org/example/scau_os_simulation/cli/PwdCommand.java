package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;

/**
 * {@code PwdCommand} 类实现了 {@code pwd} 命令，用于显示当前工作目录。
 * 它使用 Picocli 库来定义命令。
 */
@Command(name = "pwd", description = "显示当前工作目录")
public class PwdCommand implements Runnable
{
    @Override
    public void run()
    {
        // 获取 ShellContext 的单例实例，并从中获取当前工作目录的路径。
        // 然后通过 Kernel 的 printToTerminal 方法将当前路径输出到模拟器的终端界面。
        Kernel.getInstance().printToTerminal(ShellContext.getInstance().getCurrentPath());
    }
}
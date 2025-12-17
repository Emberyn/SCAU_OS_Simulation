package org.example.scau_os_simulation.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * {@code OSShellCommand} 是 SCAU 操作系统模拟器命令行接口 (CLI) 的主入口点。
 * 它使用 Picocli 框架来定义和管理所有可用的子命令。
 * 当用户在模拟器终端中输入命令时，这个类负责解析并分派到相应的子命令处理器。
 */
@Command(name = "", // 根命令，没有特定的名称，直接作为 CLI 的入口。
        description = "SCAU OS 模拟器命令行接口", // CLI 的整体描述，会在帮助信息中显示。
        mixinStandardHelpOptions = true, // 自动添加 --help (-h) 和 --version (-V) 选项，方便用户查看帮助和版本信息。
        subcommands = {
                HelpCommand.class,           // Picocli 内置的帮助命令，用于显示其他命令的用法。
                CreateProcessCommand.class,  // `create` 命令，用于创建新进程。
                KillProcessCommand.class,    // `kill` 命令，用于终止指定进程。
                ListFilesCommand.class,      // `ls` 命令，用于列出当前目录的文件和子目录。
                RemoveCommand.class,         // `rm` 命令，用于删除文件或目录。
                MakeDirectoryCommand.class,  // `mkdir` 命令，用于创建新目录。
                ChangeDirectoryCommand.class,// `cd` 命令，用于改变当前工作目录。
                PwdCommand.class,             // `pwd` 命令，用于显示当前工作目录。
                TreeCommand.class,  // 注册 tree 命令
                TouchCommand.class, // 注册 touch 命令
                TypeCommand.class,
                CopyCommand.class
        })
public class OSShellCommand implements Runnable
{
    @Override
    public void run()
    {
        // 当用户只输入了空命令（例如，直接回车）或者输入了无法被任何子命令识别的命令时，
        // Picocli 会调用这个根命令的 run 方法。
    }
}
package org.example.scau_os_simulation.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

/**
 * 这是 Shell 的顶级入口命令。
 * 通过 subcommands 属性注册所有可用的子命令。
 */
@Command(name = "",
        description = "SCAU OS 模拟器命令行接口",
        mixinStandardHelpOptions = true, // 自动添加 -h 和 -V
        subcommands = {
                HelpCommand.class,           // 内置帮助命令
                CreateProcessCommand.class,  // create
                KillProcessCommand.class,    // kill
                ListFilesCommand.class,      // ls
                RemoveCommand.class,         // rm
                MakeDirectoryCommand.class,  // mkdir (新)
                ChangeDirectoryCommand.class,// cd    (新)
                PwdCommand.class             // pwd   (新)
        })
public class OSShellCommand implements Runnable
{
    @Override
    public void run()
    {
        // 如果用户只输入了空命令或无法识别的命令，会进入这里
        // 通常不需要做任何事，或者打印帮助
    }
}
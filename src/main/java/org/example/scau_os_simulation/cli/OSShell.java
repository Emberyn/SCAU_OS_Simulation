package org.example.scau_os_simulation.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "", mixinStandardHelpOptions = true, version = "OS Sim 1.0",
        description = "Interactive OS Shell",
        subcommands = {
                CreateProcessCommand.class,
                KillProcessCommand.class,
                ListFilesCommand.class,
                MakeDirectoryCommand.class,
                RemoveCommand.class,
                CommandLine.HelpCommand.class
        })
public class OSShell implements Runnable {
    @Override
    public void run() {
        // 当没有输入子命令时执行
        org.example.scau_os_simulation.kernel.Kernel.getInstance().logOutput("请输入具体的命令 (例如: help, create, ls)");
    }
}

package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;

@Command(name = "pwd", description = "显示当前工作目录")
public class PwdCommand implements Runnable
{
    @Override
    public void run()
    {
        Kernel.getInstance().printToTerminal(ShellContext.getInstance().getCurrentPath());
    }
}
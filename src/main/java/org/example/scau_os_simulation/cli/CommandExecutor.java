package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Writer;

public class CommandExecutor
{
    private final CommandLine commandLine;

    public CommandExecutor()
    {
        this.commandLine = new CommandLine(new OSShellCommand());

        PrintWriter customWriter = new PrintWriter(new Writer()
        {
            @Override
            public void write(char[] cbuf, int off, int len)
            {
                String msg = new String(cbuf, off, len);
                if (msg.endsWith("\n"))
                {
                    msg = msg.substring(0, msg.length() - 1);
                }
                Kernel.getInstance().printToTerminal(msg);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });

        this.commandLine.setOut(customWriter);
        this.commandLine.setErr(customWriter);

        // 【修改点 1】改为 false，禁止未知参数，这样输入乱码时 Picocli 会抛出异常
        this.commandLine.setUnmatchedArgumentsAllowed(false);
    }

    public void execute(String input)
    {
        if (input == null || input.trim().isEmpty())
        {
            return;
        }

        try
        {
            String[] args = input.trim().split("\\s+");

            // 执行命令
            int exitCode = commandLine.execute(args);

            // 可选：在这里处理特定的 exitCode

        } catch (CommandLine.UnmatchedArgumentException e) {
            // 【修改点 2】专门捕获“未匹配参数”异常，即“命令未找到”
            Kernel.getInstance().printToTerminal("'" + input.split("\\s+")[0] + "' 不是内部或外部命令，也不是可运行的程序。");
            Kernel.getInstance().printToTerminal("请输入 'help' 查看可用命令列表。");

        } catch (CommandLine.ParameterException e) {
            // 【修改点 3】捕获其他参数错误（如缺少必填参数）
            Kernel.getInstance().printToTerminal("命令参数错误: " + e.getMessage());

        } catch (Exception e) {
            Kernel.getInstance().printToTerminal("系统错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
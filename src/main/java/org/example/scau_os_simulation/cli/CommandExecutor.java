package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Writer;

/**
 * 命令执行器
 * 负责解析用户输入的字符串，并调用对应的 Command 类
 */
public class CommandExecutor
{
    private final CommandLine commandLine;

    public CommandExecutor()
    {
        // 1. 初始化命令行，传入顶级命令对象
        this.commandLine = new CommandLine(new OSShellCommand());

        // 2. 创建一个自定义的 Writer，用于拦截 Picocli 的输出
        PrintWriter customWriter = new PrintWriter(new Writer()
        {
            @Override
            public void write(char[] cbuf, int off, int len)
            {
                // 将字符数组转为字符串
                String msg = new String(cbuf, off, len);

                // Picocli 的帮助信息通常末尾自带换行符
                // 为了防止终端出现双重换行（TerminalController 可能也会加 \n），
                // 我们可以去掉末尾的一个换行符
                if (msg.endsWith("\n"))
                {
                    msg = msg.substring(0, msg.length() - 1);
                }

                // 核心：将拦截到的信息发送给内核的终端通道
                Kernel.getInstance().printToTerminal(msg);
            }

            @Override
            public void flush()
            {
                // 不需要操作
            }

            @Override
            public void close()
            {
                // 不需要操作
            }
        });

        // 3. 将 Picocli 的标准输出(out)和错误输出(err)重定向到我们的 writer
        this.commandLine.setOut(customWriter);
        this.commandLine.setErr(customWriter);

        // 配置未匹配命令时的处理逻辑（可选）
        this.commandLine.setUnmatchedArgumentsAllowed(true);
    }

    /**
     * 执行命令字符串
     *
     * @param input 用户在文本框输入的完整字符串 (例如 "cd /user")
     */
    public void execute(String input)
    {
        if (input == null || input.trim().isEmpty())
        {
            return;
        }

        try
        {
            // 1. 简单的参数分割 (按空格分割，暂不支持引号包裹的带空格文件名)
            // 如果需要支持 "mkdir 'my folder'" 这种带空格的，需要更复杂的正则分割
            String[] args = input.trim().split("\\s+");

            // 2. Picocli 执行
            // execute 方法会根据 args[0] 找到对应的子命令类并调用其 run() 方法
            int exitCode = commandLine.execute(args);

            // 如果你需要处理命令执行失败的情况，可以检查 exitCode
            if (exitCode != 0)
            {
                // 可以在这里处理错误，但在各个 Command 内部打印错误信息通常更方便
            }

        } catch (Exception e)
        {
            Kernel.getInstance().printToTerminal("错误: 命令执行异常 - " + e.getMessage());
            e.printStackTrace();
        }
    }
}
package org.example.scau_os_simulation.cli;
/**
 * 包声明：将当前类归类到cli（Command Line Interface，命令行界面）模块下
 * 作用：组织代码结构，避免类名冲突，cli模块专门处理命令行相关的逻辑
 */

// 导入内核类：Kernel是整个操作系统模拟的核心类，提供终端输出等核心能力
import org.example.scau_os_simulation.kernel.Kernel;
// 导入Picocli框架的核心类：Picocli是一个专门用于快速开发CLI命令的Java框架，简化命令解析、参数校验等工作
import picocli.CommandLine;
// 导入Java IO相关类：用于处理字符输出流，实现命令执行结果的重定向
import java.io.PrintWriter;
import java.io.Writer;

/**
 * CLI命令执行器（CommandExecutor）
 * 核心功能：
 * 1. 基于Picocli框架解析用户输入的命令字符串（比如"create-process --name test --priority 1"）
 * 2. 执行解析后的命令，并将执行结果/错误信息重定向到系统终端（而非默认的控制台）
 * 3. 统一处理命令执行过程中的各种异常，给出友好的错误提示
 *
 * 零基础说明：
 * - CLI：命令行界面（Command Line Interface），用户通过输入文本命令和程序交互（比如Windows的cmd、Linux的终端）
 * - Picocli：第三方Java框架，不用自己写复杂的命令解析逻辑（比如拆分命令、校验参数），开箱即用
 */
public class CommandExecutor
{
    /**
     * 成员变量：Picocli框架的核心命令行处理对象
     * 作用：相当于一个"命令解析器+执行器"，负责：
     *  1. 解析用户输入的命令字符串（拆分成命令名、参数）
     *  2. 找到对应的命令实现类（OSShellCommand）并执行
     *  3. 处理命令执行过程中的参数校验、异常等
     * 修饰符说明：
     *  - private：只能在当前类中访问，避免外部误修改
     *  - final：一旦初始化就不能修改引用，保证对象唯一性
     */
    private final CommandLine commandLine;

    /**
     * 构造函数：初始化命令执行器的核心配置
     * 执行时机：创建CommandExecutor对象时自动调用（比如new CommandExecutor()）
     * 核心做了3件事：
     *  1. 初始化Picocli的命令解析器
     *  2. 自定义输出流，将命令执行结果重定向到系统终端
     *  3. 配置参数校验规则（禁止未知参数）
     */
    public CommandExecutor()
    {
        // 第一步：初始化Picocli的CommandLine对象
        // 参数OSShellCommand：是我们自定义的"命令集合类"（需要自己实现），包含所有支持的命令（比如创建进程、查看内存）
        // 作用：告诉Picocli该解析/执行哪些命令
        this.commandLine = new CommandLine(new OSShellCommand());

        // 第二步：自定义输出流（核心：把命令执行结果输出到系统终端，而非默认的控制台）
        // 零基础说明：
        // - Writer：Java中处理字符输出的基础类，是所有字符输出流的父类
        // - PrintWriter：包装Writer，提供更便捷的输出方法（比如println）
        // 为什么要自定义？默认情况下Picocli的输出会打印到控制台（System.out），但我们需要输出到模拟系统的终端UI，所以要重定向
        PrintWriter customWriter = new PrintWriter(new Writer()
        {
            /**
             * 重写Writer的write方法：核心逻辑，处理所有要输出的字符
             * @param cbuf 待输出的字符数组（比如命令执行结果的字符）
             * @param off  字符数组的起始偏移量（从第几个字符开始处理）
             * @param len  要处理的字符长度
             */
            @Override
            public void write(char[] cbuf, int off, int len)
            {
                // 1. 将字符数组转成字符串（从off开始，取len个字符）
                String msg = new String(cbuf, off, len);
                // 2. 去除字符串末尾的换行符（避免终端显示多余的空行）
                if (msg.endsWith("\n"))
                {
                    msg = msg.substring(0, msg.length() - 1);
                }
                // 3. 调用内核的终端输出方法，将消息显示到模拟系统的终端UI上
                // Kernel.getInstance()：单例模式，获取内核唯一实例（保证整个系统只有一个终端输出入口）
                Kernel.getInstance().printToTerminal(msg);
            }

            /**
             * 重写flush方法：刷新输出流（强制将缓存的字符输出）
             * 此处空实现：因为我们直接把消息传给了内核终端，没有缓存，所以无需刷新
             */
            @Override
            public void flush() {}

            /**
             * 重写close方法：关闭输出流
             * 此处空实现：因为我们的输出流是指向内核终端，不需要关闭（终端一直存在）
             */
            @Override
            public void close() {}
        });

        // 第三步：配置Picocli的输出流
        this.commandLine.setOut(customWriter); // 设置普通输出流（命令执行的正常结果）
        this.commandLine.setErr(customWriter); // 设置错误输出流（命令执行的异常信息）
        // 第四步：配置参数校验规则：禁止未知参数
        // 含义：如果用户输入了框架不认识的参数/命令，直接抛出UnmatchedArgumentException异常
        // 举例：用户输入"abc 123"，而abc不是定义的命令，就会触发异常
        // 为什么要禁止？避免用户输入乱码/无效命令时，框架静默忽略，而是给出明确的错误提示
        this.commandLine.setUnmatchedArgumentsAllowed(false);
    }

    /**
     * 核心执行方法：解析并执行用户输入的命令字符串
     * @param input 用户在终端输入的命令字符串（比如"create-process --name test"）
     * 执行流程：
     *  1. 空值校验：如果输入为空/全是空格，直接返回（不处理）
     *  2. 尝试解析并执行命令
     *  3. 捕获不同类型的异常，给出友好的错误提示
     */
    public void execute(String input)
    {
        // 第一步：空值/空白校验
        // input == null：用户没输入任何内容（比如终端输入框为空）
        // input.trim().isEmpty()：用户只输入了空格/制表符（无实际命令）
        if (input == null || input.trim().isEmpty())
        {
            return; // 直接返回，不执行任何逻辑
        }

        try
        {
            // 第二步：解析并执行命令（核心逻辑）
            // input.split("\\s+")：将命令字符串按"任意空白符"拆分（空格、制表符等）
            // 举例：输入"create-process --name test" → 拆分成数组：["create-process", "--name", "test"]
            // commandLine.execute()：Picocli的核心执行方法，做了这些事：
            //  1. 解析拆分后的数组，找到对应的命令（比如create-process）
            //  2. 校验参数（比如是否缺少--name的值）
            //  3. 执行命令的业务逻辑（比如调用内核创建进程）
            //  4. 将执行结果通过我们自定义的输出流输出到终端
            commandLine.execute(input.split("\\s+"));

        } catch (CommandLine.UnmatchedArgumentException e) {
            // 异常类型1：未知参数/命令异常（用户输入了未定义的命令）
            // 触发场景：比如输入"abc 123"，abc不是OSShellCommand中定义的命令
            // 处理逻辑：给出友好提示，引导用户输入help查看可用命令
            // input.split("\\s+")[0]：取用户输入的第一个单词（即错误的命令名）
            Kernel.getInstance().printToTerminal("'" + input.split("\\s+")[0] + "' 不是内部或外部命令，也不是可运行的程序。");
            Kernel.getInstance().printToTerminal("请输入 'help' 查看可用命令列表。");

        } catch (CommandLine.ParameterException e) {
            // 异常类型2：参数错误异常（命令存在，但参数不对）
            // 触发场景：比如输入"create-process"（缺少必填的--name参数）、"create-process --age 18"（参数名错误）
            // 处理逻辑：提示参数错误，并显示具体的错误信息（e.getMessage()）
            Kernel.getInstance().printToTerminal("命令参数错误: " + e.getMessage());

        } catch (Exception e) {
            // 异常类型3：其他所有未捕获的异常（比如命令执行时的业务异常、空指针等）
            // 触发场景：比如创建进程时，内核返回异常、内存不足等
            // 处理逻辑：
            //  1. 给用户显示友好的系统错误提示
            //  2. 打印异常堆栈（e.printStackTrace()），方便开发者调试（定位问题）
            Kernel.getInstance().printToTerminal("系统错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
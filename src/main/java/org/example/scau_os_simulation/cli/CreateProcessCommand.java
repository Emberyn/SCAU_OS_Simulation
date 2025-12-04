package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "create", description = "创建新进程")
public class CreateProcessCommand implements Runnable {

    @Parameters(index = "0", description = "进程名称", defaultValue = "NewProcess")
    private String name;

    @Option(names = {"-p", "--priority"}, description = "优先级 (1-5)", defaultValue = "1")
    private int priority;

    @Override
    public void run() {
        if (priority < 1 || priority > 5) {
            Kernel.getInstance().logOutput("错误: 优先级必须在 1 到 5 之间");
            return;
        }

        var p = Kernel.getInstance().getProcessManager().createProcess(name, priority);
        if (p != null) {
            // 默认加载一个示例程序，后续可以扩展为参数指定程序路径
            p.setExecutable(Kernel.getInstance().getFileSystemManager().loadExecutable("/system/exec/p1.e"));
            Kernel.getInstance().logOutput("进程创建成功: " + name + " (PID: " + p.getPcb().getPid() + ", 优先级: " + priority + ")");
        } else {
            Kernel.getInstance().logOutput("进程创建失败: " + name);
        }
    }
}

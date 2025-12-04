package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "kill", description = "终止进程")
public class KillProcessCommand implements Runnable {

    @Parameters(index = "0", description = "进程PID")
    private int pid;

    @Override
    public void run() {
        var pm = Kernel.getInstance().getProcessManager();
        if (pm.findProcess(pid) != null) {
            pm.terminateProcess(pid);
            Kernel.getInstance().logOutput("进程已终止: PID " + pid);
        } else {
            Kernel.getInstance().logOutput("错误: 未找到 PID 为 " + pid + " 的进程");
        }
    }
}

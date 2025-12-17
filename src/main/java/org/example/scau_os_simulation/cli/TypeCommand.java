package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "type", description = "Displays the contents of a text file.")
public class TypeCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "The file to display")
    private String path;

    @Override
    public Integer call() throws Exception {
        // 1. 解析路径
        String fullPath = ShellContext.getInstance().resolvePath(path);

        // 2. 获取文件对象
        Object node = Kernel.getInstance().getFileSystemManager().getObjectByPath(fullPath);

        // 3. 错误处理：文件不存在
        if (node == null) {
            Kernel.getInstance().printToTerminal("Error: File not found: " + path);
            return 1;
        }

        // 4. 错误处理：试图读取目录
        if (node instanceof Directory) {
            Kernel.getInstance().printToTerminal("Error: '" + path + "' is a directory.");
            return 1;
        }

        // 5. 读取并显示文件内容
        if (node instanceof File) {
            File file = (File) node;

            // 【关键修复 1】只读取实际长度的字节，防止尾部出现大量空字符
            if (file.getContent() != null) {
                String content = new String(
                        file.getContent(),
                        0,
                        file.getActualLength(), // 使用实际长度
                        StandardCharsets.UTF_8
                );
                // 【关键修复 2】输出到模拟器终端，而不是 IDE 控制台
                Kernel.getInstance().printToTerminal(content);
            } else {
                Kernel.getInstance().printToTerminal(""); // 空文件
            }
            return 0;
        }

        return 1;
    }
}
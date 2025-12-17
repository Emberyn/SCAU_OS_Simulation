package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.filesystem.File;
import org.example.scau_os_simulation.kernel.FileSystemManager;
import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "copy", description = "Copies a file or directory. Supports overwriting.")
public class CopyCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Source file path")
    private String sourcePath;

    @Parameters(index = "1", description = "Destination path")
    private String destPath;

    @Override
    public Integer call() throws Exception {
        FileSystemManager fsManager = Kernel.getInstance().getFileSystemManager();
        ShellContext context = ShellContext.getInstance();
        Kernel kernel = Kernel.getInstance(); // 用于输出信息到 UI

        // 1. 解析路径
        String absSource = context.resolvePath(sourcePath);
        String absDest = context.resolvePath(destPath);

        // 2. 获取源对象
        Object sourceNode = fsManager.getObjectByPath(absSource);
        if (sourceNode == null) {
            kernel.printToTerminal("Error: Source not found: " + sourcePath);
            return 1;
        }

        // 3. 判断目标状态
        Object destNode = fsManager.getObjectByPath(absDest);

        // --- 情况 A: 目标是一个已存在的目录 (粘贴进去) ---
        if (destNode instanceof Directory) {
            Object result = fsManager.paste(sourceNode, absDest);
            if (result != null) {
                kernel.printToTerminal("Copied '" + sourcePath + "' into directory '" + destPath + "'");
                return 0;
            } else {
                kernel.printToTerminal("Error: Paste failed (Disk full?).");
                return 1;
            }
        }

        // --- 情况 B: 目标是一个已存在的文件 (准备覆盖) ---
        else if (destNode instanceof File) {
            if (sourceNode instanceof Directory) {
                kernel.printToTerminal("Error: Cannot overwrite file '" + destPath + "' with a directory.");
                return 1;
            }

            // 模拟覆盖：先删除旧文件，再创建新文件
            // 注意：要先获取父目录路径
            String parentPath = getParentPath(absDest);
            String fileName = getFileName(absDest);

            // 删除旧文件
            if (!fsManager.deletePath(absDest)) {
                kernel.printToTerminal("Error: Failed to overwrite (Permission denied?).");
                return 1;
            }

            // 执行手动复制
            return manualCopy((File) sourceNode, parentPath, fileName, fsManager, kernel);
        }

        // --- 情况 C: 目标不存在 (重命名复制 / 新建) ---
        else {
            String parentPath = getParentPath(absDest);
            String fileName = getFileName(absDest);

            // 检查父目录是否存在
            if (fsManager.getDirectory(parentPath) == null) {
                kernel.printToTerminal("Error: Target directory does not exist: " + parentPath);
                return 1;
            }

            if (sourceNode instanceof Directory) {
                kernel.printToTerminal("Error: Renaming directories during copy is not supported yet.");
                return 1;
            }

            // 执行手动复制
            return manualCopy((File) sourceNode, parentPath, fileName, fsManager, kernel);
        }
    }

    /**
     * 手动复制文件逻辑：申请空间 -> 创建文件 -> 复制内容
     */
    private int manualCopy(File srcFile, String targetDir, String targetName, FileSystemManager fs, Kernel kernel) {
        // 1. 创建新文件 (createFile 内部会自动处理 allocation 和 listeners)
        // 注意：createFile 会自动处理重名（变成 name(1)），但在“覆盖”场景下，
        // 因为我们刚刚删除了旧文件，所以这里理论上会拿到原名。
        File newFile = fs.createFile(targetDir, targetName, srcFile.getSize());

        if (newFile == null) {
            kernel.printToTerminal("Error: Failed to create file (Disk full?).");
            return 1;
        }

        // 2. 深拷贝内容
        if (srcFile.getContent() != null) {
            byte[] newData = srcFile.getContent().clone();
            newFile.setContent(newData);
        }

        kernel.printToTerminal("Copied to: " + targetDir + (targetDir.equals("/") ? "" : "/") + newFile.getName());
        return 0;
    }

    // 辅助：获取父目录路径
    private String getParentPath(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash == 0) return "/"; // 父目录是根目录
        return fullPath.substring(0, lastSlash);
    }

    // 辅助：获取文件名
    private String getFileName(String fullPath) {
        return fullPath.substring(fullPath.lastIndexOf('/') + 1);
    }
}
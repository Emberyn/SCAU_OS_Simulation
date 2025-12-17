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
        Kernel kernel = Kernel.getInstance();

        String absSource = context.resolvePath(sourcePath);
        String absDest = context.resolvePath(destPath);

        Object sourceNode = fsManager.getObjectByPath(absSource);
        if (sourceNode == null) {
            kernel.printToTerminal("Error: Source not found: " + sourcePath);
            return 1;
        }

        Object destNode = fsManager.getObjectByPath(absDest);

        // A. 目标是目录 -> 粘贴进去
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

        // B. 目标是文件 -> 覆盖
        else if (destNode instanceof File) {
            if (sourceNode instanceof Directory) {
                kernel.printToTerminal("Error: Cannot overwrite file '" + destPath + "' with a directory.");
                return 1;
            }

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

        // C. 目标不存在 -> 重命名复制 / 新建
        else {
            String parentPath = getParentPath(absDest);
            String fileName = getFileName(absDest);

            if (fsManager.getDirectory(parentPath) == null) {
                kernel.printToTerminal("Error: Target directory does not exist: " + parentPath);
                return 1;
            }

            if (sourceNode instanceof Directory) {
                kernel.printToTerminal("Error: Renaming directories during copy is not supported yet.");
                return 1;
            }

            return manualCopy((File) sourceNode, parentPath, fileName, fsManager, kernel);
        }
    }

    /**
     * 【修复】手动复制逻辑
     * 这里必须传入 String 类型的 targetDir (路径)，而不是 Directory 对象
     */
    private int manualCopy(File srcFile, String targetDirPath, String targetName, FileSystemManager fs, Kernel kernel) {
        // 【关键修复】createFile 的第一个参数必须是 String 路径
        File newFile = fs.createFile(targetDirPath, targetName, srcFile.getSize());

        if (newFile == null) {
            kernel.printToTerminal("Error: Failed to create file (Disk full?).");
            return 1;
        }

        if (srcFile.getContent() != null) {
            byte[] newData = srcFile.getContent().clone();
            newFile.setContent(newData);
        }

        kernel.printToTerminal("Copied to: " + targetDirPath + (targetDirPath.equals("/") ? "" : "/") + newFile.getName());
        return 0;
    }

    private String getParentPath(String fullPath) {
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash == 0) return "/";
        return fullPath.substring(0, lastSlash);
    }

    private String getFileName(String fullPath) {
        return fullPath.substring(fullPath.lastIndexOf('/') + 1);
    }
}
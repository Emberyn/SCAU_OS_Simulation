package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.kernel.Kernel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "mkdir", description = "创建目录")
public class MakeDirectoryCommand implements Runnable {

    @Parameters(index = "0", description = "目录路径 (例如 /user/newdir)")
    private String path;

    @Override
    public void run() {
        // 解析父目录和新目录名
        int lastSlash = path.lastIndexOf('/');
        String parentPath;
        String dirName;
        
        if (lastSlash == -1) {
            // 相对路径暂不支持，假设根目录？或者报错
            // 简单起见，强制绝对路径，或者假设在 / 下（如果没 /）
            // 但为了稳健，这里假设必须是 /parent/newname 格式
            Kernel.getInstance().logOutput("错误: 请使用绝对路径，例如 /user/newdir");
            return;
        }
        
        if (lastSlash == 0) {
            parentPath = "/";
            dirName = path.substring(1);
        } else {
            parentPath = path.substring(0, lastSlash);
            dirName = path.substring(lastSlash + 1);
        }

        var dir = Kernel.getInstance().getFileSystemManager().createDirectory(parentPath, dirName);
        if (dir != null) {
            Kernel.getInstance().logOutput("目录创建成功: " + path);
            // 这里可能需要通知 UI 刷新，但 CLI 模式下主要关注逻辑
            // MainController 的定时刷新会更新 UI
        } else {
            Kernel.getInstance().logOutput("目录创建失败: 父目录不存在或同名目录已存在");
        }
    }
}

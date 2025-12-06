package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.kernel.Kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shell 上下文管理器
 * 负责记录当前工作目录，并处理路径解析（支持相对路径、绝对路径、.. 和 .）
 */
public class ShellContext
{
    private static ShellContext instance;
    private String currentPath = "/"; // 默认为根目录

    private ShellContext()
    {
    }

    public static ShellContext getInstance()
    {
        if (instance == null)
        {
            instance = new ShellContext();
        }
        return instance;
    }

    public String getCurrentPath()
    {
        return currentPath;
    }

    public void setCurrentPath(String path)
    {
        this.currentPath = path.equals("/") ? path : path.replaceAll("/+$", ""); // 去除末尾斜杠
    }

    /**
     * 核心方法：将输入路径解析为绝对路径
     * 输入 "docs" (当前在 /user) -> 返回 "/user/docs"
     * 输入 "../system" (当前在 /user/docs) -> 返回 "/user/system"
     * 输入 "/root" -> 返回 "/root"
     */
    public String resolvePath(String inputPath)
    {
        if (inputPath == null || inputPath.trim().isEmpty())
        {
            return currentPath;
        }

        // 1. 确定基准路径
        String basePath;
        if (inputPath.startsWith("/"))
        {
            basePath = ""; // 绝对路径，从空字符串开始拼接
        } else
        {
            basePath = currentPath; // 相对路径，基于当前目录
        }

        // 2. 拆分路径段
        String fullPathStr = basePath + "/" + inputPath;
        String[] parts = fullPathStr.split("/");
        List<String> stack = new ArrayList<>();

        // 3. 处理 . 和 ..
        for (String part : parts)
        {
            if (part.isEmpty() || part.equals("."))
            {
                continue;
            }
            if (part.equals(".."))
            {
                if (!stack.isEmpty())
                {
                    stack.remove(stack.size() - 1); // 回退一级
                }
            } else
            {
                stack.add(part);
            }
        }

        // 4. 重组路径
        if (stack.isEmpty())
        {
            return "/";
        }
        return "/" + String.join("/", stack);
    }

    /**
     * 验证路径是否是一个有效的目录
     */
    public boolean isValidDirectory(String absolutePath)
    {
        if (absolutePath.equals("/")) return true;

        // 由于 FileSystemManager 的 findDirectoryByPath 是私有的，
        // 我们利用 getRootDirectory 手动遍历验证
        Directory current = Kernel.getInstance().getFileSystemManager().getRootDirectory();
        String[] parts = absolutePath.split("/");

        for (String part : parts)
        {
            if (part.isEmpty()) continue;
            boolean found = false;
            for (Object child : current.getChildren())
            {
                if (child instanceof Directory && ((Directory) child).getName().equals(part))
                {
                    current = (Directory) child;
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
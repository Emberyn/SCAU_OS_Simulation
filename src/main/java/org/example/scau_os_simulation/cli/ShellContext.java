package org.example.scau_os_simulation.cli;

import org.example.scau_os_simulation.filesystem.Directory;
import org.example.scau_os_simulation.kernel.Kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code ShellContext} 类负责管理 Shell 的当前工作目录，并提供路径解析功能。
 * 它实现了单例模式，确保整个系统只有一个 Shell 上下文实例。
 *
 * 主要职责包括：
 * 1. 记录和管理当前用户所在的目录路径。
 * 2. 解析用户输入的相对路径或绝对路径，将其转换为标准的绝对路径。
 * 3. 验证给定路径是否指向一个有效的目录。
 *
 * 这个类是命令行界面 (CLI) 操作文件系统的重要组成部分。
 */
public class ShellContext
{
    // 单例模式的实例，确保 ShellContext 在整个应用程序中只有一个。
    private static ShellContext instance;
    // 记录当前 Shell 所在的工作目录的绝对路径。
    // 初始时，默认为文件系统的根目录 "/"。
    private String currentPath = "/";

    /**
     * 私有构造函数，防止从外部实例化。
     * 这是单例模式的典型实现，确保只能通过 {@code getInstance()} 方法获取实例。
     */
    private ShellContext()
    {
    }

    /**
     * 获取 {@code ShellContext} 的唯一实例。
     * 如果实例尚未创建，则会创建一个新实例。
     * 这是单例模式的访问点。
     *
     * @return {@code ShellContext} 的唯一实例。
     */
    public static ShellContext getInstance()
    {
        // 检查实例是否已存在，如果不存在则创建。
        if (instance == null)
        {
            instance = new ShellContext();
        }
        // 返回现有或新创建的实例。
        return instance;
    }


    public String getCurrentPath()
    {
        return currentPath;
    }


    public void setCurrentPath(String path)
    {
        // 如果路径是根目录 "/"，则直接使用；否则，移除末尾的斜杠。
        this.currentPath = path.equals("/") ? path : path.replaceAll("/+$", "");
    }

    /**
     * 核心方法：将用户输入的路径解析为标准绝对路径。
     * 支持相对路径、绝对路径、特殊路径符号（.. 表示上一级目录，. 表示当前目录）。
     * @param inputPath 用户输入的路径字符串（可以是相对路径、绝对路径或空值）
     * @return 解析后的标准绝对路径（格式统一，无末尾冗余斜杠）
     * @example
     * 1. 当前目录为 /user，输入 "docs" → 返回 "/user/docs"
     * 2. 当前目录为 /user/docs，输入 "../system" → 返回 "/user/system"
     * 3. 当前目录为 /user，输入 "/root" → 返回 "/root"
     * 4. 输入为空或null → 返回当前目录路径
     */
    public String resolvePath(String inputPath)
    {
        // 处理空路径或空白路径：直接返回当前目录
        if (inputPath == null || inputPath.trim().isEmpty())
        {
            return currentPath;
        }

        // 步骤1：确定路径解析的基准目录
        String basePath;
        // 如果输入路径以 / 开头，说明是绝对路径，基准设为空字符串（后续直接拼接）
        if (inputPath.startsWith("/"))
        {
            basePath = ""; 
        } else
        {
            // 否则是相对路径，基准设为当前工作目录
            basePath = currentPath;
        }

        // 步骤2：拼接基准路径和输入路径，然后拆分为路径片段
        String fullPathStr = basePath + "/" + inputPath;
        // 按 / 分割路径为字符串数组（会产生空字符串片段，后续处理）
        String[] parts = fullPathStr.split("/");
        // 用栈结构处理路径片段，方便处理..和.
        List<String> stack = new ArrayList<>();

        // 步骤3：遍历路径片段，处理特殊符号
        for (String part : parts)
        {
            // 跳过空片段（由连续/或路径开头/产生）和当前目录符号.
            if (part.isEmpty() || part.equals("."))
            {
                continue;
            }
            // 处理上一级目录符号..：如果栈非空则弹出最上层片段（回到上一级）
            if (part.equals(".."))
            {
                if (!stack.isEmpty())
                {
                    stack.remove(stack.size() - 1); 
                }
            } else
            {
                // 普通目录片段：压入栈中
                stack.add(part);
            }
        }

        // 步骤4：重组路径片段为标准绝对路径
        if (stack.isEmpty())
        {
            // 栈为空说明解析后是根目录
            return "/";
        }
        // 用/连接栈中所有片段，开头加/形成绝对路径
        return "/" + String.join("/", stack);
    }

    /**
     * 验证给定的绝对路径是否指向文件系统中一个真实存在的目录。
     * 该方法通过遍历文件系统树来检查路径的有效性。
     * @param absolutePath 需要验证的绝对路径字符串。
     * @return 如果路径指向一个有效目录，则返回 {@code true}；否则返回 {@code false}。
     */
    public boolean isValidDirectory(String absolutePath)
    {
        // 根目录 "/" 总是有效的。
        if (absolutePath.equals("/")) return true;

        // 获取文件系统的根目录，作为遍历的起点。
        // 注意：FileSystemManager 的 findDirectoryByPath 是私有的，所以我们手动遍历。
        Directory current = Kernel.getInstance().getFileSystemManager().getRootDirectory();
        // 将绝对路径按 "/" 分割成各个目录名称片段。
        String[] parts = absolutePath.split("/");

        // 遍历路径的每个片段，逐级查找目录。
        for (String part : parts)
        {
            // 跳过空字符串片段（例如，路径开头或连续斜杠导致）。
            if (part.isEmpty()) continue;

            boolean found = false; // 标记当前片段是否找到对应的子目录。
            // 遍历当前目录的所有子项。
            for (Object child : current.getChildren())
            {
                // 如果子项是目录且名称匹配，则进入该子目录。
                if (child instanceof Directory && ((Directory) child).getName().equals(part))
                {
                    current = (Directory) child;
                    found = true;
                    break; // 找到后跳出内层循环，处理下一个路径片段。
                }
            }
            // 如果当前路径片段没有找到对应的子目录，说明路径无效。
            if (!found) return false;
        }
        // 所有路径片段都成功匹配，说明这是一个有效目录。
        return true;
    }
}
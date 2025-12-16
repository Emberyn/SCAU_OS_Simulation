package org.example.scau_os_simulation.filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录 - 文件系统中的“文件夹”节点
 * <p>
 * 概念解释：
 * - 目录用于组织文件与子目录，形成树状层级结构；就像电脑中的“文件夹”。
 * - 每个目录拥有一个名字（`name`）以及一个子元素列表（`children`）。
 * - `children` 可以同时容纳两类对象：`Directory`（子目录）与 `File`（文件）。
 * <p>
 * 使用场景：
 * - 在 UI 的 TreeView 中，我们会把 `Directory` 递归展开，展示其子内容。
 * - 在删除目录时，需要确保其子列表为空，否则不允许删除（避免“非空目录”误删）。
 */
public class Directory
{
    private final String name;
    private final List<Object> children;

    /**
     * 构造一个目录
     *
     * @param name 目录名（不含路径分隔符）
     */
    public Directory(String name)
    {
        this.name = name;
        this.children = new ArrayList<>();
    }

    /**
     * 获取目录名
     */
    public String getName()
    {
        return name;
    }

    /**
     * 获取所有直接子元素（文件或目录）
     * <p>
     * 注意：返回的是可变列表，请谨慎对其进行修改；更推荐通过
     * `addChild/removeChild` 方法来维护一致性与类型校验。
     */
    public List<Object> getChildren()
    {
        return children;
    }

    /**
     * 添加子节点（文件或目录）
     * <p>
     * 约束：仅允许添加 `File` 或 `Directory` 类型，其他类型会抛出异常。
     * 这样做可以保证文件系统结构的合法性。
     */
    public void addChild(Object child)
    {
        if (child instanceof File || child instanceof Directory)
        {
            children.add(child);
        } else
        {
            throw new IllegalArgumentException("只能添加File或Directory类型的对象");
        }
    }


    /**
     * 【新增】递归搜索所有匹配前缀的文件/目录（不区分大小写）
     *
     * @param prefix 搜索前缀
     * @param resultList 用于收集结果的列表 (路径字符串)
     * @param currentPath 当前递归到的路径 (用于构建完整路径)
     */
    public void searchByPrefix(String prefix, java.util.List<String> resultList, String currentPath) {
        String lowerPrefix = prefix.toLowerCase();

        for (Object child : children) {
            String name = (child instanceof File) ? ((File) child).getName() : ((Directory) child).getName();
            String fullPath = currentPath + "/" + name;

            // 匹配前缀 (不区分大小写)
            if (name.toLowerCase().startsWith(lowerPrefix)) {
                resultList.add(fullPath);
            }

            // 如果是目录，继续递归
            if (child instanceof Directory) {
                ((Directory) child).searchByPrefix(prefix, resultList, fullPath);
            }
        }
    }


    /**
     * 移除子节点
     *
     * @param child 目标子元素（匹配引用相等）
     * @return 是否移除成功
     */
    public boolean removeChild(Object child)
    {
        return children.remove(child);
    }

    /**
     * 按名称查找直接子节点
     *
     * @param name 子节点名称（大小写精确匹配）
     * @return 匹配的文件或目录；未找到返回 null
     */
    public Object findChild(String name)
    {
        for (Object child : children)
        {
            if (child instanceof File && ((File) child).getName().equals(name))
            {
                return child;
            } else if (child instanceof Directory && ((Directory) child).getName().equals(name))
            {
                return child;
            }
        }
        return null;
    }




    /**
     * 复制文件或目录到当前目录
     * 修复：智能命名策略
     * 1. 如果目标路径下没有同名文件，直接保留原名 (如复制到其他文件夹)
     * 2. 只有发生冲突时，才自动生成 "xxx_副本N" 的名称
     *
     * @param source  要复制的文件或目录
     * @param newName 新名称（如果为null则自动判断）
     * @return 复制后的新对象
     */
    public Object copyChild(Object source, String newName)
    {
        // 1. 获取源文件的原始名称
        String originalName = (source instanceof File) ? ((File) source).getName() : ((Directory) source).getName();

        // 2. 确定最终名称
        String finalName = newName;
        if (finalName == null) {
            // 【关键修复】先检查当前目录下是否存在同名文件
            if (findChild(originalName) == null) {
                // 没有冲突，直接用原名
                finalName = originalName;
            } else {
                // 有冲突，生成副本名称
                finalName = generateCopyName(originalName);
            }
        }

        // 3. 执行复制逻辑
        if (source instanceof File)
        {
            File sourceFile = (File) source;
            File copiedFile = new File(finalName, sourceFile.getSize());
            copiedFile.setContent(sourceFile.getContent()); // 复制内容
            addChild(copiedFile);
            return copiedFile;
        } else if (source instanceof Directory)
        {
            Directory sourceDir = (Directory) source;
            Directory copiedDir = new Directory(finalName);
            // 递归复制子目录和文件
            for (Object child : sourceDir.getChildren())
            {
                // 子项复制时传 null，让它们在新的子目录里自己判断（通常子目录是空的，所以会保持原名）
                copiedDir.copyChild(child, null);
            }
            addChild(copiedDir);
            return copiedDir;
        }
        throw new IllegalArgumentException("只能复制File或Directory类型的对象");
    }



    /**
     * 生成复制文件的名称（在原名称后添加副本标识）
     *
     * @param originalName 原始名称
     * @return 生成的副本名称
     */
    private String generateCopyName(String originalName)
    {
        String baseName = originalName;
        String extension = "";

        // 分离文件名和扩展名
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalName.length() - 1)
        {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        // 生成唯一的副本名称
        int counter = 1;
        String newName;
        do
        {
            newName = baseName + "_副本" + counter + extension;
            counter++;
        } while (findChild(newName) != null);

        return newName;
    }


    /**
     * 递归搜索文件或目录（按名称）
     *
     * @param name 要搜索的名称
     * @return 找到的对象，未找到返回null
     */
    public Object searchRecursive(String name)
    {
        // 先在当前目录中查找
        Object result = findChild(name);
        if (result != null)
        {
            return result;
        }

        // 递归搜索子目录
        for (Object child : children)
        {
            if (child instanceof Directory)
            {
                Object subResult = ((Directory) child).searchRecursive(name);
                if (subResult != null)
                {
                    return subResult;
                }
            }
        }

        return null;
    }
}

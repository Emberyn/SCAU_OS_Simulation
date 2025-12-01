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
     *
     * @param source  要复制的文件或目录
     * @param newName 新名称（如果为null则自动生成）
     * @return 复制后的新对象
     */
    public Object copyChild(Object source, String newName)
    {
        if (source instanceof File)
        {
            File sourceFile = (File) source;
            String name = newName != null ? newName : generateCopyName(sourceFile.getName());
            File copiedFile = new File(name, sourceFile.getSize());
            copiedFile.setContent(sourceFile.getContent());
            addChild(copiedFile);
            return copiedFile;
        } else if (source instanceof Directory)
        {
            Directory sourceDir = (Directory) source;
            String name = newName != null ? newName : generateCopyName(sourceDir.getName());
            Directory copiedDir = new Directory(name);
            // 递归复制子目录和文件
            for (Object child : sourceDir.getChildren())
            {
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

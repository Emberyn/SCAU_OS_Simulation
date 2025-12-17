package org.example.scau_os_simulation.filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录 - 文件系统中的“文件夹”节点
 */
public class Directory
{
    private final String name;
    private final List<Object> children;

    /**
     * 构造一个目录
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
     */
    public List<Object> getChildren()
    {
        return children;
    }

    /**
     * 添加子节点（文件或目录）
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
     * 递归搜索所有匹配前缀的文件/目录（不区分大小写）
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
     * @param child 目标子元素（匹配引用相等）
     * @return 是否移除成功
     */
    public boolean removeChild(Object child)
    {
        return children.remove(child);
    }


    /**
     * 按名称查找直接子节点
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
}

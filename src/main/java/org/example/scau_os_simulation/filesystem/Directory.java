package org.example.scau_os_simulation.filesystem;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录 - 文件系统中的“文件夹”节点
 *
 * 概念解释：
 * - 目录用于组织文件与子目录，形成树状层级结构；就像电脑中的“文件夹”。
 * - 每个目录拥有一个名字（`name`）以及一个子元素列表（`children`）。
 * - `children` 可以同时容纳两类对象：`Directory`（子目录）与 `File`（文件）。
 *
 * 使用场景：
 * - 在 UI 的 TreeView 中，我们会把 `Directory` 递归展开，展示其子内容。
 * - 在删除目录时，需要确保其子列表为空，否则不允许删除（避免“非空目录”误删）。
 */
public class Directory {
    private final String name;
    private final List<Object> children;
    
    /**
     * 构造一个目录
     * @param name 目录名（不含路径分隔符）
     */
    public Directory(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }
    
    /**
     * 获取目录名
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取所有直接子元素（文件或目录）
     *
     * 注意：返回的是可变列表，请谨慎对其进行修改；更推荐通过
     * `addChild/removeChild` 方法来维护一致性与类型校验。
     */
    public List<Object> getChildren() {
        return children;
    }
    
    /**
     * 添加子节点（文件或目录）
     *
     * 约束：仅允许添加 `File` 或 `Directory` 类型，其他类型会抛出异常。
     * 这样做可以保证文件系统结构的合法性。
     */
    public void addChild(Object child) {
        if (child instanceof File || child instanceof Directory) {
            children.add(child);
        } else {
            throw new IllegalArgumentException("只能添加File或Directory类型的对象");
        }
    }
    
    /**
     * 移除子节点
     * @param child 目标子元素（匹配引用相等）
     * @return 是否移除成功
     */
    public boolean removeChild(Object child) {
        return children.remove(child);
    }
    
    /**
     * 按名称查找直接子节点
     * @param name 子节点名称（大小写精确匹配）
     * @return 匹配的文件或目录；未找到返回 null
     */
    public Object findChild(String name) {
        for (Object child : children) {
            if (child instanceof File && ((File) child).getName().equals(name)) {
                return child;
            } else if (child instanceof Directory && ((Directory) child).getName().equals(name)) {
                return child;
            }
        }
        return null;
    }
}

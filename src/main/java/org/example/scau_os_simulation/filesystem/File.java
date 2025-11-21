package org.example.scau_os_simulation.filesystem;

/**
 * 文件 - 固定大小的字节容器
 *
 * 概念解释：
 * - 文件以“KB”为单位定义大小（`size`），底层通过 `byte[] content` 存储具体字节数据。
 * - 这里的 `size` 表示容量的 KB 数；真实字节容量为 `size * 1024`。
 * - 为了简单起见，文件不包含权限/时间戳等元数据，仅聚焦容量与内容。
 *
 * 常见操作：
 * - 通过 `setContent` 写入数据（长度不得超过容量）。
 * - 通过 `getContent` 读取底层字节数组（只读引用）。
 */
public class File {
    private final String name;
    private final int size;
    private final byte[] content;
    
    /**
     * 构造一个文件
     * @param name 文件名（不含路径）
     * @param size 容量（KB）
     */
    public File(String name, int size) {
        this.name = name;
        this.size = size;
        this.content = new byte[size * 1024];
    }
    
    /** 获取文件名 */
    public String getName() {
        return name;
    }
    
    /** 获取容量（KB） */
    public int getSize() {
        return size;
    }
    
    /**
     * 获取底层内容缓冲区
     * 注意：返回的是数组引用，调用方请勿越界写入或泄露机密数据。
     */
    public byte[] getContent() {
        return content;
    }
    
    /**
     * 写入文件内容
     * @param content 字节数组，长度不得超过文件容量（size*1024）
     * @throws IllegalArgumentException 当内容长度超过容量时抛出
     */
    public void setContent(byte[] content) {
        if (content.length > this.content.length) {
            throw new IllegalArgumentException("内容大小超过文件大小");
        }
        System.arraycopy(content, 0, this.content, 0, content.length);
    }
}

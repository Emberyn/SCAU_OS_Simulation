package org.example.scau_os_simulation.filesystem;

import java.util.Arrays;

/**
 * 文件类 - 模拟文件系统中的文件节点
 * 【修复】移除了固定大小限制，支持动态内容扩容
 */
public class File {
    private String name;
    private String type; // 例如 "txt", "exe"
    private byte[] content;
    private int size; // 分配的大小 (模拟磁盘占用空间)
    private int actualLength; // 实际内容长度

    public File(String name, int size) {
        this.name = name;
        this.size = size;
        // 初始化时根据请求的大小分配，但后续可扩容
        this.content = new byte[size > 0 ? size : 1024];
        this.actualLength = 0;
        this.type = extractType(name);
    }

    public File(String name, byte[] content) {
        this.name = name;
        this.content = content;
        this.size = content.length;
        this.actualLength = content.length;
        this.type = extractType(name);
    }

    private String extractType(String name) {
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex > 0 && dotIndex < name.length() - 1) ? name.substring(dotIndex + 1) : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.type = extractType(name);
    }

    public byte[] getContent() {
        return content;
    }

    /**
     * 【关键修复】设置文件内容，支持动态扩容
     */
    public void setContent(byte[] newContent) {
        if (newContent == null) {
            this.actualLength = 0;
            return;
        }

        // 如果新内容超过当前容量，进行扩容 (策略：取新内容长度 或 原长度的1.5倍，防止频繁分配)
        if (newContent.length > this.content.length) {
            int newSize = Math.max(newContent.length, (int)(this.content.length * 1.5));
            // 模拟重新分配磁盘块
            this.content = Arrays.copyOf(newContent, newSize);
            this.size = newSize;
        } else {
            // 容量足够，直接覆盖
            System.arraycopy(newContent, 0, this.content, 0, newContent.length);
            // 清除尾部旧数据(可选，为了安全)
            if (newContent.length < this.actualLength) {
                Arrays.fill(this.content, newContent.length, this.actualLength, (byte)0);
            }
        }

        this.actualLength = newContent.length;
    }

    public int getSize() {
        return size;
    }

    public int getActualLength() {
        return actualLength;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return name + (type.isEmpty() ? "" : " (" + type + ")");
    }
}
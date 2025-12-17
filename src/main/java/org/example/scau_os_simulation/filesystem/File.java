package org.example.scau_os_simulation.filesystem;

import java.util.Arrays;

/**
 * 文件类 - 模拟文件系统中的文件节点
 */
public class File {
    private String name;
    private String type;
    private byte[] content;
    private int size;
    private int actualLength; // 实际内容长度

    public File(String name, int size) {
        this.name = name;
        this.size = size;
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



    /**
     * 设置文件内容，支持动态扩容
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
            // System.arraycopy：高效的数组拷贝方法（比手动循环赋值快），核心参数说明：
            // 参数1：源数组（newContent）- 要写入的新内容
            // 参数2：源数组起始位置（0）- 从新内容的第1个字节开始
            // 参数3：目标数组（this.content）- 要写入的目标数组（当前文件的存储数组）
            // 参数4：目标数组起始位置（0）- 从目标数组的第1个字节开始覆盖
            // 参数5：要拷贝的长度（newContent.length）- 把新内容的所有字节都拷贝过去
            System.arraycopy(newContent, 0, this.content, 0, newContent.length);
            System.arraycopy(newContent, 0, this.content, 0, newContent.length);
            // 清除尾部旧数据(可选，为了安全)
            if (newContent.length < this.actualLength) {
                // Arrays.fill：把数组指定区间的元素填充为指定值
                // 参数1：要填充的数组（this.content）
                // 参数2：起始索引（newContent.length）- 从新内容结束的位置开始
                // 参数3：结束索引（this.actualLength）- 到原来的实际内容结束位置为止
                // 参数4：填充值（(byte)0）- 字节0对应ASCII的空字符，模拟磁盘擦除旧数据
                Arrays.fill(this.content, newContent.length, this.actualLength, (byte)0);
            }
        }

        this.actualLength = newContent.length;
    }



}
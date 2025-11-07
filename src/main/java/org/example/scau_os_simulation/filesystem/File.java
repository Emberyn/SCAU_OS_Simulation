package org.example.scau_os_simulation.filesystem;

public class File {
    private String name;
    private int size; // 文件大小，单位KB
    private byte[] content;
    
    public File(String name, int size) {
        this.name = name;
        this.size = size;
        this.content = new byte[size * 1024]; // 转换为字节
    }
    
    public String getName() {
        return name;
    }
    
    public int getSize() {
        return size;
    }
    
    public byte[] getContent() {
        return content;
    }
    
    public void setContent(byte[] content) {
        if (content.length > this.content.length) {
            throw new IllegalArgumentException("内容大小超过文件大小");
        }
        System.arraycopy(content, 0, this.content, 0, content.length);
    }
}
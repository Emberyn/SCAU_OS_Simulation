package org.example.scau_os_simulation.filesystem;

public class File {
    private final String name;
    private final int size;
    private final byte[] content;
    
    public File(String name, int size) {
        this.name = name;
        this.size = size;
        this.content = new byte[size * 1024];
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

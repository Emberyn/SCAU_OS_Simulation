package org.example.scau_os_simulation.filesystem;

import java.util.ArrayList;
import java.util.List;

public class Directory {
    private final String name;
    private final List<Object> children;
    
    public Directory(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }
    
    public String getName() {
        return name;
    }
    
    public List<Object> getChildren() {
        return children;
    }
    
    public void addChild(Object child) {
        if (child instanceof File || child instanceof Directory) {
            children.add(child);
        } else {
            throw new IllegalArgumentException("只能添加File或Directory类型的对象");
        }
    }
    
    public boolean removeChild(Object child) {
        return children.remove(child);
    }
    
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

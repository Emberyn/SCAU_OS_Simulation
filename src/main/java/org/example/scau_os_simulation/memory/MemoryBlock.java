package org.example.scau_os_simulation.memory;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * 内存块实体类
 * 封装物理内存中连续块的核心信息：起始地址（可修改）、块大小（固定）
 * 支持JavaFX属性绑定，便于UI实时展示内存块状态
 */
public class MemoryBlock {
    // 核心修改：把 Property 作为成员变量（只初始化一次，保证绑定/监听有效）
    private final IntegerProperty startAddress = new SimpleIntegerProperty();
    // size 是 final，用 Property 包装后依然只读（符合原逻辑）
    private final IntegerProperty size = new SimpleIntegerProperty();


    public MemoryBlock(int startAddress, int size) {
        // 给 Property 设初始值（而非直接给变量赋值）
        this.startAddress.set(startAddress);
        this.size.set(size);
    }


    public int getStartAddress() {
        // 从 Property 取值（而非直接返回变量）
        return startAddress.get();
    }


    public void setStartAddress(int startAddress) {
        // 给 Property 设值（会触发监听，UI 自动更新）
        this.startAddress.set(startAddress);
    }


    public int getSize() {
        // 从 Property 取值
        return size.get();
    }

    /**
     * 获取起始地址的JavaFX整数属性（用于UI控件数据绑定）
     * @return 起始地址的IntegerProperty对象（全局唯一）
     */
    public IntegerProperty startAddressProperty() {
        // 返回成员变量（而非新建），保证绑定/监听有效
        return startAddress;
    }

    /**
     * 获取内存块大小的JavaFX整数属性（用于UI控件数据绑定）
     * @return 大小的IntegerProperty对象（全局唯一）
     */
    public IntegerProperty sizeProperty() {
        return size;
    }
}
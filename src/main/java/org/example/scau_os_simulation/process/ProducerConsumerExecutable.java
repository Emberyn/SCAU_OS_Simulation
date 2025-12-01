package org.example.scau_os_simulation.process;

import java.util.List;
import java.util.ArrayList;

/**
 * 生产者消费者问题的可执行文件
 * 
 * 演示经典的进程同步问题：
 * - 生产者进程生产物品并放入缓冲区
 * - 消费者进程从缓冲区取出物品并消费
 * - 使用信号量实现同步：
 *   * mutex：互斥信号量，保证对缓冲区的互斥访问
 *   * empty：空位信号量，表示缓冲区中空位数量
 *   * full：满位信号量，表示缓冲区中物品数量
 */
public class ProducerConsumerExecutable extends Executable {
    private final String type;  // "producer" 或 "consumer"
    private final int id;       // 生产者/消费者编号
    private final int items;    // 要生产/消费的项目数
    
    /**
     * 构造函数
     * @param type 类型："producer" 或 "consumer"
     * @param id 编号
     * @param items 项目数量
     */
    public ProducerConsumerExecutable(String type, int id, int items) {
        super();
        this.type = type;
        this.id = id;
        this.items = items;
        
        // 生成指令序列
        generateInstructions();
    }
    
    /**
     * 生成指令序列
     */
    private void generateInstructions() {
        List<String> instructions = new ArrayList<>();
        
        if ("producer".equals(type)) {
            // 生产者指令序列
            for (int i = 0; i < items; i++) {
                instructions.add("x=" + (i + id * 100));          // 设置生产值
                instructions.add("wait(empty)");                  // 等待空位
                instructions.add("wait(mutex)");                    // 获取互斥锁
                instructions.add("x++");                            // 生产物品（模拟）
                instructions.add("signal(mutex)");                // 释放互斥锁
                instructions.add("signal(full)");                 // 增加满位
            }
        } else {
            // 消费者指令序列
            for (int i = 0; i < items; i++) {
                instructions.add("wait(full)");                     // 等待满位
                instructions.add("wait(mutex)");                    // 获取互斥锁
                instructions.add("x--");                          // 消费物品（模拟）
                instructions.add("signal(mutex)");                  // 释放互斥锁
                instructions.add("signal(empty)");                // 增加空位
            }
        }
        
        instructions.add("end"); // 程序结束
        
        // 设置指令到父类
        setInstructions(instructions);
    }
    
    public String getName() {
        return type + id + "_pc";
    }
    
    public String getType() {
        return type;
    }
    
    public int getId() {
        return id;
    }
    
    public int getItems() {
        return items;
    }
}
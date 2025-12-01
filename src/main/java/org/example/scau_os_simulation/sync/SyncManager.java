package org.example.scau_os_simulation.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * 同步管理器 - 管理所有信号量
 * 
 * 这个类负责：
 * 1. 创建和管理信号量
 * 2. 提供信号量操作接口
 * 3. 跟踪哪些进程在等待哪些信号量
 */
public class SyncManager {
    private final Map<String, Semaphore> semaphores;  // 信号量映射表
    
    /**
     * 构造函数
     */
    public SyncManager() {
        this.semaphores = new HashMap<>();
    }
    
    /**
     * 创建新的信号量
     * 
     * @param name 信号量名称
     * @param initialValue 初始值
     * @return 创建的信号量
     */
    public Semaphore createSemaphore(String name, int initialValue) {
        Semaphore semaphore = new Semaphore(name, initialValue);
        semaphores.put(name, semaphore);
        return semaphore;
    }
    
    /**
     * 获取信号量
     * 
     * @param name 信号量名称
     * @return 信号量，如果不存在则返回null
     */
    public Semaphore getSemaphore(String name) {
        return semaphores.get(name);
    }
    
    /**
     * 执行P操作（wait）
     * 
     * @param name 信号量名称
     * @param pid 进程PID
     * @return true表示成功获取资源，false表示需要阻塞等待
     */
    public boolean wait(String name, int pid) {
        Semaphore semaphore = semaphores.get(name);
        if (semaphore == null) {
            return false; // 信号量不存在
        }
        return semaphore.wait(pid);
    }
    
    /**
     * 执行V操作（signal）
     * 
     * @param name 信号量名称
     * @return 被唤醒的进程PID，如果没有等待进程则返回-1
     */
    public int signal(String name) {
        Semaphore semaphore = semaphores.get(name);
        if (semaphore == null) {
            return -1; // 信号量不存在
        }
        return semaphore.signal();
    }
    
    /**
     * 获取所有信号量
     * 
     * @return 所有信号量的列表
     */
    public List<Semaphore> getAllSemaphores() {
        return new ArrayList<>(semaphores.values());
    }
    
    /**
     * 删除信号量
     * 
     * @param name 信号量名称
     * @return true表示删除成功，false表示信号量不存在
     */
    public boolean removeSemaphore(String name) {
        return semaphores.remove(name) != null;
    }
    
    /**
     * 获取信号量的统计信息
     * 
     * @return 包含所有信号量信息的映射
     */
    public Map<String, Object> getSemaphoreStatistics() {
        Map<String, Object> stats = new HashMap<>();
        for (Map.Entry<String, Semaphore> entry : semaphores.entrySet()) {
            Semaphore semaphore = entry.getValue();
            Map<String, Object> semaphoreInfo = new HashMap<>();
            semaphoreInfo.put("value", semaphore.getValue());
            semaphoreInfo.put("waitingCount", semaphore.getWaitingCount());
            semaphoreInfo.put("waitingPids", semaphore.getWaitingPids());
            stats.put(entry.getKey(), semaphoreInfo);
        }
        return stats;
    }
}
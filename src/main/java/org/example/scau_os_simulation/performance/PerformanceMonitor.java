package org.example.scau_os_simulation.performance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * 性能监控器 - 监控系统性能指标
 * 
 * 监控以下指标：
 * - CPU利用率
 * - 内存使用率
 * - 进程数量变化
 * - 系统负载
 * 
 * 提供历史数据记录和趋势分析
 */
public class PerformanceMonitor {
    private final List<PerformanceSnapshot> history;
    private final int maxHistorySize;
    private LocalDateTime startTime;
    
    /**
     * 性能快照内部类
     */
    public static class PerformanceSnapshot {
        private final LocalDateTime timestamp;
        private final double cpuUtilization;
        private final double memoryUsage;
        private final int processCount;
        private final int readyQueueSize;
        private final int blockedQueueSize;
        
        public PerformanceSnapshot(LocalDateTime timestamp, double cpuUtilization, double memoryUsage, 
                                int processCount, int readyQueueSize, int blockedQueueSize) {
            this.timestamp = timestamp;
            this.cpuUtilization = cpuUtilization;
            this.memoryUsage = memoryUsage;
            this.processCount = processCount;
            this.readyQueueSize = readyQueueSize;
            this.blockedQueueSize = blockedQueueSize;
        }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getProcessCount() { return processCount; }
        public int getReadyQueueSize() { return readyQueueSize; }
        public int getBlockedQueueSize() { return blockedQueueSize; }
        
        public double getSystemLoad() {
            return (cpuUtilization + (memoryUsage / 100.0)) / 2.0;
        }
    }
    
    /**
     * 构造函数
     * @param maxHistorySize 最大历史记录数
     */
    public PerformanceMonitor(int maxHistorySize) {
        this.history = new ArrayList<>();
        this.maxHistorySize = maxHistorySize;
        this.startTime = LocalDateTime.now();
    }
    
    /**
     * 记录性能快照
     */
    public void recordSnapshot(double cpuUtilization, double memoryUsage, int processCount, 
                              int readyQueueSize, int blockedQueueSize) {
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            LocalDateTime.now(), cpuUtilization, memoryUsage, processCount, readyQueueSize, blockedQueueSize
        );
        
        history.add(snapshot);
        
        // 限制历史记录数量
        if (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }
    
    /**
     * 获取最新快照
     */
    public PerformanceSnapshot getLatestSnapshot() {
        if (history.isEmpty()) {
            return new PerformanceSnapshot(LocalDateTime.now(), 0.0, 0.0, 0, 0, 0);
        }
        return history.get(history.size() - 1);
    }
    
    /**
     * 获取历史数据
     */
    public List<PerformanceSnapshot> getHistory() {
        return new ArrayList<>(history);
    }
    
    /**
     * 获取最近N个快照
     */
    public List<PerformanceSnapshot> getRecentSnapshots(int n) {
        int start = Math.max(0, history.size() - n);
        return new ArrayList<>(history.subList(start, history.size()));
    }
    
    /**
     * 获取平均CPU利用率
     */
    public double getAverageCpuUtilization() {
        if (history.isEmpty()) return 0.0;
        
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) {
            sum += snapshot.getCpuUtilization();
        }
        return sum / history.size();
    }
    
    /**
     * 获取平均内存使用率
     */
    public double getAverageMemoryUsage() {
        if (history.isEmpty()) return 0.0;
        
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) {
            sum += snapshot.getMemoryUsage();
        }
        return sum / history.size();
    }

    public double getAverageMemoryUtilization() {
        return getAverageMemoryUsage();
    }
    
    /**
     * 获取平均系统负载
     */
    public double getAverageSystemLoad() {
        if (history.isEmpty()) return 0.0;
        
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) {
            sum += snapshot.getSystemLoad();
        }
        return sum / history.size();
    }
    
    /**
     * 获取峰值CPU利用率
     */
    public double getPeakCpuUtilization() {
        if (history.isEmpty()) return 0.0;
        
        double peak = 0.0;
        for (PerformanceSnapshot snapshot : history) {
            peak = Math.max(peak, snapshot.getCpuUtilization());
        }
        return peak;
    }
    
    /**
     * 获取峰值内存使用率
     */
    public double getPeakMemoryUsage() {
        if (history.isEmpty()) return 0.0;
        
        double peak = 0.0;
        for (PerformanceSnapshot snapshot : history) {
            peak = Math.max(peak, snapshot.getMemoryUsage());
        }
        return peak;
    }

    public double getPeakMemoryUtilization() {
        return getPeakMemoryUsage();
    }
    
    /**
     * 获取性能统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalSnapshots", history.size());
        stats.put("averageCpuUtilization", getAverageCpuUtilization());
        stats.put("averageMemoryUsage", getAverageMemoryUsage());
        stats.put("averageSystemLoad", getAverageSystemLoad());
        stats.put("peakCpuUtilization", getPeakCpuUtilization());
        stats.put("peakMemoryUsage", getPeakMemoryUsage());
        stats.put("monitoringDuration", java.time.Duration.between(startTime, LocalDateTime.now()).getSeconds());
        
        PerformanceSnapshot latest = getLatestSnapshot();
        stats.put("currentCpuUtilization", latest.getCpuUtilization());
        stats.put("currentMemoryUsage", latest.getMemoryUsage());
        stats.put("currentSystemLoad", latest.getSystemLoad());
        stats.put("currentProcessCount", latest.getProcessCount());
        
        return stats;
    }
    
    /**
     * 清空历史数据
     */
    public void clearHistory() {
        history.clear();
        startTime = LocalDateTime.now();
    }
}
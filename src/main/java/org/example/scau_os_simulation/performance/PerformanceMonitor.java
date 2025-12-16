package org.example.scau_os_simulation.performance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections; // 引入

/**
 * 性能监控器 - 监控系统性能指标 (线程安全修复版)
 */
public class PerformanceMonitor
{
    // 【修改 1】使用同步列表，或者在操作方法上加 synchronized
    private final List<PerformanceSnapshot> history;
    private final int maxHistorySize;
    private LocalDateTime startTime;

    // ... (PerformanceSnapshot 内部类保持不变，省略以节省空间，请保留原有的内部类代码) ...
    public static class PerformanceSnapshot {
        // ... (保持原样) ...
        private final LocalDateTime timestamp;
        private final double cpuUtilization;
        private final double memoryUsage;
        private final int processCount;
        private final int readyQueueSize;
        private final int blockedQueueSize;

        public PerformanceSnapshot(LocalDateTime timestamp, double cpuUtilization, double memoryUsage,
                                   int processCount, int readyQueueSize, int blockedQueueSize)
        {
            this.timestamp = timestamp;
            this.cpuUtilization = cpuUtilization;
            this.memoryUsage = memoryUsage;
            this.processCount = processCount;
            this.readyQueueSize = readyQueueSize;
            this.blockedQueueSize = blockedQueueSize;
        }
        // ... getters 保持不变 ...
        public LocalDateTime getTimestamp() { return timestamp; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getProcessCount() { return processCount; }
        public int getReadyQueueSize() { return readyQueueSize; }
        public int getBlockedQueueSize() { return blockedQueueSize; }
        public double getSystemLoad() { return (cpuUtilization + memoryUsage) / 2.0; }
    }

    public PerformanceMonitor(int maxHistorySize)
    {
        // 【修改 2】初始化为 ArrayList，但我们将在方法级加锁
        this.history = new ArrayList<>();
        this.maxHistorySize = maxHistorySize;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 记录性能快照 (加锁)
     */
    public synchronized void recordSnapshot(double cpuUtilization, double memoryUsage, int processCount,
                                            int readyQueueSize, int blockedQueueSize)
    {
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
                LocalDateTime.now(), cpuUtilization, memoryUsage, processCount, readyQueueSize, blockedQueueSize
        );

        history.add(snapshot);

        if (history.size() > maxHistorySize)
        {
            history.remove(0);
        }
    }

    /**
     * 获取最新快照 (加锁)
     */
    public synchronized PerformanceSnapshot getLatestSnapshot()
    {
        if (history.isEmpty())
        {
            return new PerformanceSnapshot(LocalDateTime.now(), 0.0, 0.0, 0, 0, 0);
        }
        return history.get(history.size() - 1);
    }

    /**
     * 获取历史数据 (加锁)
     */
    public synchronized List<PerformanceSnapshot> getHistory()
    {
        // 返回副本，防止外部遍历时发生并发修改
        return new ArrayList<>(history);
    }

    /**
     * 获取最近N个快照 (加锁)
     */
    public synchronized List<PerformanceSnapshot> getRecentSnapshots(int n)
    {
        int start = Math.max(0, history.size() - n);
        return new ArrayList<>(history.subList(start, history.size()));
    }

    // --- 以下统计方法也需要加锁，因为它们遍历了 history ---

    public synchronized double getAverageCpuUtilization()
    {
        if (history.isEmpty()) return 0.0;
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) sum += snapshot.getCpuUtilization();
        return sum / history.size();
    }

    public synchronized double getAverageMemoryUsage()
    {
        if (history.isEmpty()) return 0.0;
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) sum += snapshot.getMemoryUsage();
        return sum / history.size();
    }

    public double getAverageMemoryUtilization() { return getAverageMemoryUsage(); }

    public synchronized double getAverageSystemLoad()
    {
        if (history.isEmpty()) return 0.0;
        double sum = 0.0;
        for (PerformanceSnapshot snapshot : history) sum += snapshot.getSystemLoad();
        return sum / history.size();
    }

    public synchronized double getPeakCpuUtilization()
    {
        if (history.isEmpty()) return 0.0;
        double peak = 0.0;
        for (PerformanceSnapshot snapshot : history) peak = Math.max(peak, snapshot.getCpuUtilization());
        return peak;
    }

    public synchronized double getPeakMemoryUsage()
    {
        if (history.isEmpty()) return 0.0;
        double peak = 0.0;
        for (PerformanceSnapshot snapshot : history) peak = Math.max(peak, snapshot.getMemoryUsage());
        return peak;
    }

    public double getPeakMemoryUtilization() { return getPeakMemoryUsage(); }

    public synchronized Map<String, Object> getStatistics()
    {
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

    public synchronized void clearHistory()
    {
        history.clear();
        startTime = LocalDateTime.now();
    }
}
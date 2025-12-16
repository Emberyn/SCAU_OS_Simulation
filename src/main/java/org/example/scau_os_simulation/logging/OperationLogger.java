package org.example.scau_os_simulation.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志记录器
 * <p>
 * 优化点：
 * 1. 使用 synchronized 保证线程安全（关键，防止 UI 读取时内核正在写入导致崩溃）。
 * 2. 使用 Java Record 简化 LogEntry。
 * 3. 优化了字符串格式化性能。
 */
public class OperationLogger {
    // 使用线程安全的列表，或者在操作方法上加 synchronized
    private final List<LogEntry> logs;
    private static final int MAX_LOG_SIZE = 1000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public enum LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }

    public enum OperationType {
        SYSTEM,
        PROCESS_CREATE, PROCESS_TERMINATE, PROCESS_SCHEDULE,
        MEMORY_ALLOCATE, MEMORY_FREE, MEMORY_DEFRAGMENT,
        FILE_CREATE, FILE_DELETE, FILE_WRITE,
        DIRECTORY_CREATE, DIRECTORY_DELETE,
        DEVICE_REQUEST, DEVICE_COMPLETE,
        SEMAPHORE_WAIT, SEMAPHORE_SIGNAL
    }

    /**
     * [优化] 使用 Java Record 替代传统 Class
     * Record 自动提供构造函数、equals、hashCode 和 getter (访问器名为 .message() 而非 .getMessage())
     */
    public record LogEntry(LocalDateTime timestamp, LogLevel level, OperationType type, String message, Map<String, Object> details) {
        // 紧凑构造函数：处理 defensive copy
        public LogEntry {
            details = details != null ? Map.copyOf(details) : Map.of();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s] %s %s: %s",
                    timestamp.format(TIME_FORMATTER),
                    level,
                    type,
                    message));

            if (!details.isEmpty()) {
                sb.append(" | 详情:");
                details.forEach((k, v) -> sb.append(" ").append(k).append("=").append(v));
            }
            return sb.toString();
        }
    }

    public OperationLogger() {
        this.logs = new ArrayList<>();
    }

    /**
     * 核心记录方法
     * [优化] 添加 synchronized 关键字，确保原子性（添加+删除队首）
     */
    public synchronized void log(LogLevel level, OperationType type, String message, Map<String, Object> details) {
        LogEntry entry = new LogEntry(LocalDateTime.now(), level, type, message, details);
        logs.add(entry);

        // 限制日志数量，防止内存溢出
        if (logs.size() > MAX_LOG_SIZE) {
            logs.remove(0);
        }
    }

    // --- 便捷方法 (保留以备后续扩展，IDE 警告"未使用"可忽略，因为这是工具类 API) ---

    public void info(OperationType type, String message) {
        log(LogLevel.INFO, type, message, null);
    }

    public void info(OperationType type, String message, Map<String, Object> details) {
        log(LogLevel.INFO, type, message, details);
    }

    public void warning(OperationType type, String message) {
        log(LogLevel.WARNING, type, message, null);
    }

    public void error(OperationType type, String message) {
        log(LogLevel.ERROR, type, message, null);
    }

    public void error(OperationType type, String message, Map<String, Object> details) {
        log(LogLevel.ERROR, type, message, details);
    }

    public void debug(OperationType type, String message) {
        log(LogLevel.DEBUG, type, message, null);
    }

    /**
     * 获取日志字符串列表 (供 UI ListView 使用)
     * [优化] synchronized 确保遍历时不会被修改
     */
    public synchronized List<String> getLogs() {
        List<String> out = new ArrayList<>(logs.size());
        for (LogEntry e : logs) {
            out.add(e.toString());
        }
        return out;
    }

    /**
     * 获取所有日志对象 (供高级分析使用)
     */
    public synchronized List<LogEntry> getAllLogs() {
        return new ArrayList<>(logs);
    }

    // --- 统计与清理方法 ---

    public synchronized void clearLogs() {
        logs.clear();
    }

    /**
     * [优化] 统计逻辑
     */
    public synchronized Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", logs.size());

        // 使用 Stream API 简化统计 (虽然在同步块中循环稍微快一点，但 Stream 更易读)
        // 这里为了性能保持循环统计
        Map<String, Integer> levelCounts = new HashMap<>();
        Map<String, Integer> typeCounts = new HashMap<>();

        for (LogEntry entry : logs) {
            levelCounts.merge(entry.level().name(), 1, Integer::sum);
            typeCounts.merge(entry.type().name(), 1, Integer::sum);
        }

        stats.put("levelCounts", levelCounts);
        stats.put("typeCounts", typeCounts);

        return stats;
    }
}
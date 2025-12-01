package org.example.scau_os_simulation.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * 操作日志记录器
 * <p>
 * 记录所有系统操作，包括：
 * - 进程创建/终止
 * - 内存分配/释放
 * - 文件系统操作
 * - 设备请求
 * - 信号量操作
 * <p>
 * 支持日志级别、时间戳、操作类型分类
 */
public class OperationLogger
{
    private final List<LogEntry> logs;

    /**
     * 日志级别枚举
     */
    public enum LogLevel
    {
        DEBUG, INFO, WARNING, ERROR
    }

    /**
     * 操作类型枚举
     */
    public enum OperationType
    {
        SYSTEM,
        PROCESS_CREATE, PROCESS_TERMINATE, PROCESS_SCHEDULE,
        MEMORY_ALLOCATE, MEMORY_FREE, MEMORY_DEFRAGMENT,
        FILE_CREATE, FILE_DELETE, FILE_WRITE,
        DIRECTORY_CREATE, DIRECTORY_DELETE,
        DEVICE_REQUEST, DEVICE_COMPLETE,
        SEMAPHORE_WAIT, SEMAPHORE_SIGNAL
    }

    /**
     * 日志条目内部类
     */
    public static class LogEntry
    {
        private final LocalDateTime timestamp;
        private final LogLevel level;
        private final OperationType type;
        private final String message;
        private final Map<String, Object> details;

        public LogEntry(LocalDateTime timestamp, LogLevel level, OperationType type, String message, Map<String, Object> details)
        {
            this.timestamp = timestamp;
            this.level = level;
            this.type = type;
            this.message = message;
            this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        }

        public LocalDateTime getTimestamp()
        {
            return timestamp;
        }

        public LogLevel getLevel()
        {
            return level;
        }

        public OperationType getType()
        {
            return type;
        }

        public String getMessage()
        {
            return message;
        }

        public Map<String, Object> getDetails()
        {
            return new HashMap<>(details);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s] %s %s: %s",
                    timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                    level.name(),
                    type.name(),
                    message));

            if (!details.isEmpty())
            {
                sb.append(" | 详情:");
                for (Map.Entry<String, Object> entry : details.entrySet())
                {
                    sb.append(" ").append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            return sb.toString();
        }
    }

    /**
     * 构造函数
     */
    public OperationLogger()
    {
        this.logs = new ArrayList<>();
    }

    /**
     * 记录日志
     *
     * @param level   日志级别
     * @param type    操作类型
     * @param message 日志消息
     * @param details 详细信息
     */
    public void log(LogLevel level, OperationType type, String message, Map<String, Object> details)
    {
        LogEntry entry = new LogEntry(LocalDateTime.now(), level, type, message, details);
        logs.add(entry);

        // 限制日志数量，防止内存溢出
        if (logs.size() > 1000)
        {
            logs.remove(0);
        }
    }

    /**
     * 记录信息日志
     */
    public void info(OperationType type, String message)
    {
        log(LogLevel.INFO, type, message, null);
    }

    /**
     * 记录信息日志（带详情）
     */
    public void info(OperationType type, String message, Map<String, Object> details)
    {
        log(LogLevel.INFO, type, message, details);
    }

    /**
     * 记录警告日志
     */
    public void warning(OperationType type, String message)
    {
        log(LogLevel.WARNING, type, message, null);
    }

    /**
     * 记录错误日志
     */
    public void error(OperationType type, String message)
    {
        log(LogLevel.ERROR, type, message, null);
    }

    /**
     * 记录错误日志（带详情）
     */
    public void error(OperationType type, String message, Map<String, Object> details)
    {
        log(LogLevel.ERROR, type, message, details);
    }

    /**
     * 记录调试日志
     */
    public void debug(OperationType type, String message)
    {
        log(LogLevel.DEBUG, type, message, null);
    }

    /**
     * 获取所有日志
     *
     * @return 日志列表
     */
    public List<LogEntry> getAllLogs()
    {
        return new ArrayList<>(logs);
    }

    public java.util.List<String> getLogs()
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (LogEntry e : logs) out.add(e.toString());
        return out;
    }

    /**
     * 获取指定级别的日志
     *
     * @param level 日志级别
     * @return 指定级别的日志列表
     */
    public List<LogEntry> getLogsByLevel(LogLevel level)
    {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : logs)
        {
            if (entry.getLevel() == level)
            {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 获取指定类型的日志
     *
     * @param type 操作类型
     * @return 指定类型的日志列表
     */
    public List<LogEntry> getLogsByType(OperationType type)
    {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : logs)
        {
            if (entry.getType() == type)
            {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 获取最近N条日志
     *
     * @param n 数量
     * @return 最近N条日志
     */
    public List<LogEntry> getRecentLogs(int n)
    {
        int start = Math.max(0, logs.size() - n);
        return new ArrayList<>(logs.subList(start, logs.size()));
    }

    /**
     * 清空日志
     */
    public void clearLogs()
    {
        logs.clear();
    }

    /**
     * 获取日志统计信息
     *
     * @return 统计信息映射
     */
    public Map<String, Object> getStatistics()
    {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", logs.size());

        // 按级别统计
        Map<String, Integer> levelCounts = new HashMap<>();
        for (LogLevel level : LogLevel.values())
        {
            levelCounts.put(level.name(), getLogsByLevel(level).size());
        }
        stats.put("levelCounts", levelCounts);

        // 按类型统计
        Map<String, Integer> typeCounts = new HashMap<>();
        for (OperationType type : OperationType.values())
        {
            typeCounts.put(type.name(), getLogsByType(type).size());
        }
        stats.put("typeCounts", typeCounts);

        return stats;
    }
}
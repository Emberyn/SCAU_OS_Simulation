package org.example.scau_os_simulation.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志记录器（OperationLogger）
 * 核心职责：
 * 1. 统一记录系统全量操作日志（进程、内存、文件、设备、同步等维度）
 * 2. 按级别（DEBUG/INFO/WARNING/ERROR）和类型分类日志，支持筛选分析
 * 3. 保证线程安全，适配UI与内核并发访问场景，防止数据不一致
 * 4. 限制日志最大容量，提供日志统计、清理、格式化展示等辅助能力
 *
 * 关键优化点：
 * 1. 线程安全：核心方法添加synchronized，保证日志操作原子性（写入/读取/统计）
 * 2. 数据结构：使用Java Record简化LogEntry，自动生成核心方法，减少冗余代码
 * 3. 防御性拷贝：对日志详情Map做不可变处理，防止外部篡改日志数据
 * 4. 性能优化：预定义时间格式化器，优化字符串拼接逻辑，统计逻辑兼顾性能与可读性
 */
public class OperationLogger {
    // 日志存储列表（按时间顺序存储，最新日志在尾部）
    private final List<LogEntry> logs;
    // 日志最大容量（防止无限制存储导致内存溢出）
    private static final int MAX_LOG_SIZE = 1000;
    // 预定义时间格式化器（避免重复创建，提升性能）：格式为 时:分:秒.毫秒
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * 日志级别枚举（LogLevel）
     * 定义日志的严重程度，用于区分不同重要性的日志信息
     */
    public enum LogLevel {
        DEBUG,   // 调试级别：开发调试专用，记录详细的系统运行细节
        INFO,    // 信息级别：常规操作记录（默认级别），如进程创建、内存分配成功
        WARNING, // 警告级别：非致命异常，如磁盘空间不足、内存碎片率过高
        ERROR    // 错误级别：致命异常，如内存分配失败、进程启动异常
    }

    /**
     * 操作类型枚举（OperationType）
     * 分类记录系统操作类型，便于日志筛选、统计和问题定位
     */
    public enum OperationType {
        SYSTEM,                          // 系统级操作（内核启动/关闭、调度器启停）
        PROCESS_CREATE, PROCESS_TERMINATE, PROCESS_SCHEDULE, // 进程操作：创建、终止、调度切换
        MEMORY_ALLOCATE, MEMORY_FREE, MEMORY_DEFRAGMENT,     // 内存操作：分配、释放、碎片整理
        FILE_CREATE, FILE_DELETE, FILE_WRITE,                // 文件操作：创建、删除、写入
        DIRECTORY_CREATE, DIRECTORY_DELETE,                  // 目录操作：创建、删除
        DEVICE_REQUEST, DEVICE_COMPLETE,                     // 设备操作：IO请求提交、IO完成回调
        SEMAPHORE_WAIT, SEMAPHORE_SIGNAL                     // 同步操作：信号量等待(P)、信号量唤醒(V)
    }

    /**
     * 日志条目记录（LogEntry）
     * [优化] 使用Java Record替代传统Class，自动生成构造器、equals、hashCode和只读访问器
     * 每个条目包含完整的日志元信息，是日志的最小不可变单元
     *
     * @param timestamp 日志产生的时间戳（精确到毫秒）
     * @param level 日志级别（DEBUG/INFO/WARNING/ERROR）
     * @param type 操作类型（进程/内存/文件等）
     * @param message 日志核心消息（简洁描述操作内容）
     * @param details 日志详情（可选键值对，存储操作参数，如内存地址、文件路径、IO延迟等）
     */
    public record LogEntry(LocalDateTime timestamp, LogLevel level, OperationType type, String message, Map<String, Object> details) {
        // 紧凑构造函数：对详情Map做防御性拷贝，确保日志数据不可变
        // 防止外部修改传入的Map导致日志内容被篡改（如修改内存分配大小）
        public LogEntry {
            // 若详情不为null则转为不可变Map，null则用空Map替代
            details = details != null ? Map.copyOf(details) : Map.of();
        }

        /**
         * 日志条目字符串格式化（供UI组件展示）
         * 格式化规则：[时间] 级别 类型: 核心消息 | 详情: 键1=值1 键2=值2
         * @return 人性化的日志字符串
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            // 拼接核心日志信息（时间+级别+类型+消息）
            sb.append(String.format("[%s] %s %s: %s",
                    timestamp.format(TIME_FORMATTER), // 格式化时间戳
                    level,
                    type,
                    message));

            // 若有详情，追加详情信息
            if (!details.isEmpty()) {
                sb.append(" | 详情:");
                details.forEach((k, v) -> sb.append(" ").append(k).append("=").append(v));
            }
            return sb.toString();
        }
    }

    /**
     * 构造函数 - 初始化日志记录器
     * 初始化日志存储列表（ArrayList保证顺序性和读写性能）
     */
    public OperationLogger() {
        this.logs = new ArrayList<>();
    }

    /**
     * 核心日志记录方法（底层）
     * [优化] 添加synchronized关键字，保证日志写入+容量限制操作的原子性
     * 防止多线程场景下（如内核写入日志、UI读取日志）出现ConcurrentModificationException
     *
     * @param level 日志级别（DEBUG/INFO等）
     * @param type 操作类型（进程/内存等）
     * @param message 日志核心消息（简洁描述操作）
     * @param details 日志详情（可选，存储操作的具体参数）
     */
    public synchronized void log(LogLevel level, OperationType type, String message, Map<String, Object> details) {
        // 创建日志条目（时间戳为当前系统时间）
        LogEntry entry = new LogEntry(LocalDateTime.now(), level, type, message, details);
        logs.add(entry); // 新增日志添加到列表尾部（最新日志在最后）

        // 日志数量超出最大值时，移除列表首部的最旧日志（FIFO策略）
        if (logs.size() > MAX_LOG_SIZE) {
            logs.remove(0);
        }
    }

    // --- 便捷日志方法（简化调用，无需手动指定日志级别） ---

    /**
     * 便捷方法 - 记录INFO级别日志（无详情）
     * @param type 操作类型
     * @param message 日志核心消息
     */
    public void info(OperationType type, String message) {
        log(LogLevel.INFO, type, message, null);
    }

    /**
     * 便捷方法 - 记录INFO级别日志（带详情）
     * @param type 操作类型
     * @param message 日志核心消息
     * @param details 日志详情（键值对参数）
     */
    public void info(OperationType type, String message, Map<String, Object> details) {
        log(LogLevel.INFO, type, message, details);
    }

    /**
     * 便捷方法 - 记录WARNING级别日志（无详情）
     * @param type 操作类型
     * @param message 日志核心消息
     */
    public void warning(OperationType type, String message) {
        log(LogLevel.WARNING, type, message, null);
    }

    /**
     * 便捷方法 - 记录ERROR级别日志（无详情）
     * @param type 操作类型
     * @param message 日志核心消息
     */
    public void error(OperationType type, String message) {
        log(LogLevel.ERROR, type, message, null);
    }

    /**
     * 便捷方法 - 记录ERROR级别日志（带详情）
     * @param type 操作类型
     * @param message 日志核心消息
     * @param details 日志详情（如异常信息、错误码、失败参数等）
     */
    public void error(OperationType type, String message, Map<String, Object> details) {
        log(LogLevel.ERROR, type, message, details);
    }

    /**
     * 便捷方法 - 记录DEBUG级别日志（无详情）
     * @param type 操作类型
     * @param message 日志核心消息
     */
    public void debug(OperationType type, String message) {
        log(LogLevel.DEBUG, type, message, null);
    }

    /**
     * 获取格式化后的日志字符串列表（供UI ListView展示）
     * [优化] synchronized保证遍历期间日志列表不被修改，避免并发异常
     * @return 按时间排序的日志字符串列表（最新日志在最后）
     */
    public synchronized List<String> getLogs() {
        // 预初始化列表容量，减少扩容开销
        List<String> out = new ArrayList<>(logs.size());
        for (LogEntry e : logs) {
            out.add(e.toString()); // 转换为UI友好的格式化字符串
        }
        return out;
    }

    /**
     * 获取所有日志条目对象列表（供高级分析/筛选使用）
     * synchronized保证线程安全，返回副本避免外部修改内部日志列表
     * @return 日志条目对象的副本列表（不可修改原列表）
     */
    public synchronized List<LogEntry> getAllLogs() {
        return new ArrayList<>(logs); // 防御性拷贝，返回新列表
    }

    // --- 日志管理辅助方法 ---

    /**
     * 清空所有日志（线程安全）
     * synchronized保证清空操作原子性，避免清空时其他线程写入日志
     */
    public synchronized void clearLogs() {
        logs.clear();
    }

    /**
     * 获取日志统计信息（供系统监控/分析使用）
     * [优化] 循环统计兼顾性能（比Stream快），同步保证数据一致性
     * @return 统计结果Map，包含：
     *         - totalLogs: 日志总数
     *         - levelCounts: 各日志级别的数量（如INFO:50, ERROR:2）
     *         - typeCounts: 各操作类型的数量（如PROCESS_CREATE:10, MEMORY_ALLOCATE:8）
     */
    public synchronized Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalLogs", logs.size()); // 记录总日志数

        // 统计各日志级别出现次数
        Map<String, Integer> levelCounts = new HashMap<>();
        // 统计各操作类型出现次数
        Map<String, Integer> typeCounts = new HashMap<>();

        // 遍历日志列表，累加统计
        for (LogEntry entry : logs) {
            // merge方法：键不存在则设为1，存在则累加1
            levelCounts.merge(entry.level().name(), 1, Integer::sum);
            typeCounts.merge(entry.type().name(), 1, Integer::sum);
        }

        stats.put("levelCounts", levelCounts);
        stats.put("typeCounts", typeCounts);

        return stats;
    }
}
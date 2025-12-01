package org.example.scau_os_simulation.process;

/**
 * 进程状态枚举
 * <p>
 * - NEW        新建（尚未投入调度）
 * - READY      就绪（等待CPU）
 * - RUNNING    运行（占用CPU）
 * - BLOCKED    阻塞（等待外部事件/设备）
 * - TERMINATED 终止（结束）
 */
public enum ProcessState
{
    NEW,
    READY,
    RUNNING,
    BLOCKED,
    TERMINATED
}

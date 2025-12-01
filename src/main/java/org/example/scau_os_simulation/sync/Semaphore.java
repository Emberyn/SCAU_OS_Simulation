package org.example.scau_os_simulation.sync;

import java.util.LinkedList;
import java.util.Queue;
import java.util.ArrayList;
import java.util.List;

/**
 * 信号量 - 进程同步机制
 * * 信号量是一个经典的进程同步工具，用于控制对共享资源的访问。
 * 它维护一个计数器，表示可用资源的数量。
 * * P操作（wait）：请求资源，如果计数器>0则减1，否则阻塞等待
 * V操作（signal）：释放资源，计数器加1，如果有等待进程则唤醒一个
 */
public class Semaphore
{
    private int value;                    // 信号量值
    private final String name;             // 信号量名称
    private final Queue<Integer> waitingQueue;  // 等待队列

    /**
     * 构造函数
     *
     * @param name         信号量名称
     * @param initialValue 初始值
     */
    public Semaphore(String name, int initialValue)
    {
        this.name = name;
        this.value = initialValue;
        this.waitingQueue = new LinkedList<>();
    }

    /**
     * P操作（wait）- 请求资源
     * 如果信号量值>0，则减1并继续
     * 否则，进程进入等待队列并阻塞
     * * @param pid 请求资源的进程PID
     *
     * @return true表示成功获取资源，false表示需要阻塞等待
     */
    public synchronized boolean wait(int pid)
    {
        if (value > 0)
        {
            value--;
            return true;  // 立即获得资源
        } else
        {
            waitingQueue.offer(pid);
            return false; // 需要阻塞等待
        }
    }

    /**
     * V操作（signal）- 释放资源
     * 信号量值加1，如果有等待进程则唤醒队列中的第一个进程
     * * @return 如果有等待的进程，返回被唤醒的进程PID；否则返回-1
     */
    public synchronized int signal()
    {
        value++; // 释放资源，总量+1

        if (!waitingQueue.isEmpty())
        {
            Integer waitingPid = waitingQueue.poll();
            if (waitingPid != null)
            {
                // 【修改点】这里删除了 value--;
                // 原因：被唤醒的进程会在CPU中重新执行 wait() 指令，那时候它会自己消耗资源（执行 value--）
                // 如果这里提前减了，进程醒来看到 value=0 会再次阻塞，导致死锁。
                return waitingPid;
            }
        }

        return -1; // 没有等待的进程
    }

    /**
     * 获取信号量值
     */
    public synchronized int getValue()
    {
        return value;
    }

    /**
     * 获取信号量名称
     */
    public String getName()
    {
        return name;
    }

    /**
     * 获取等待队列中的进程数量
     */
    public synchronized int getWaitingCount()
    {
        return waitingQueue.size();
    }

    /**
     * 获取等待队列中的所有进程PID
     */
    public synchronized List<Integer> getWaitingPids()
    {
        return new ArrayList<>(waitingQueue);
    }
}
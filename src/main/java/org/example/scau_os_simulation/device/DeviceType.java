package org.example.scau_os_simulation.device;

/**
 * 设备类型枚举
 * <p>
 * 说明：
 * - A/B/C 三类设备用于模拟不同的外设资源（例如不同型号或用途）。
 * - 在 `DeviceManager` 中，不同类型会有不同的数量与队列管理。
 */
public enum DeviceType
{
    A,
    B,
    C
}

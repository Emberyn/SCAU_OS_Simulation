package org.example.scau_os_simulation.device;

/**
 * 设备请求 - 表示一个进程对某类设备的占用需求
 *
 * 背景：
 * - 当设备忙碌时，进程不能立即获得设备；它会将一个 `DeviceRequest` 放入等待队列。
 * - 调度器推进时，设备完成任务后会尝试从队列头取出请求并分配空闲设备。
 *
 * @param pid       请求的进程ID
 * @param type      设备类型（A/B/C）
 * @param timeUnits 预计占用的时间片数
 */
public record DeviceRequest(int pid, DeviceType type, int timeUnits) {
}

package org.example.scau_os_simulation.performance;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

/**
 * 系统性能监控图表（JavaFX实现）
 * 核心功能：
 * 1. 实时展示CPU利用率、内存利用率的折线变化趋势
 * 2. 实现“滑动窗口”效果：仅保留最近60个数据点，模拟实时监控的滚动图表
 * 3. 固定Y轴范围（0-100%），X轴随系统时间动态滚动，保证图表展示直观
 */
public class PerformanceChartFX
{
    /**
     * 核心折线图组件：展示CPU/内存利用率的变化曲线
     * 泛型说明：<Number, Number> 表示X轴（时间）和Y轴（利用率）均为数字类型
     */
    private final LineChart<Number, Number> lineChart;

    /**
     * CPU利用率数据系列：对应折线图中的“CPU利用率”线条
     * XYChart.Series：封装一组X-Y坐标数据，每个数据点对应“时间-利用率”
     */
    private final XYChart.Series<Number, Number> cpuSeries;

    /**
     * 内存利用率数据系列：对应折线图中的“内存利用率”线条
     */
    private final XYChart.Series<Number, Number> memorySeries;

    /**
     * 图表最大数据点数（滑动窗口大小）
     * 设计目的：限制图表仅保留最近60个数据点，避免数据过多导致图表卡顿、显示拥挤
     * 效果：超过60个点时，自动移除最旧的点，实现“滚动显示”
     */
    private final int MAX_DATA_POINTS = 60;

    /**
     * 构造函数：初始化性能图表的所有组件（坐标轴、折线图、数据系列）
     * 执行时机：创建性能监控面板时调用，仅初始化一次
     */
    public PerformanceChartFX()
    {
        // ========================== 步骤1：初始化坐标轴 ==========================
        // X轴：代表系统时间（System Clock，调度器的时钟周期数）
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("时间"); // X轴显示名称
        xAxis.setForceZeroInRange(false); // 关键配置：X轴不强制从0开始
        // 作用：系统时间持续增加时，X轴会自动向右滚动，只显示最近的时间范围（配合滑动窗口）
        // 若设为true，X轴始终从0开始，数据点会不断向右延伸，图表无法滚动
        xAxis.setAutoRanging(true); // X轴范围自动调整（根据当前数据点的时间动态变化）

        // Y轴：代表利用率百分比（0%-100%）
        // 参数说明：NumberAxis(最小值, 最大值, 刻度间隔)
        NumberAxis yAxis = new NumberAxis(0, 100, 10); // 范围0-100，每10个单位一个刻度
        yAxis.setLabel("使用率 (%)"); // Y轴显示名称
        yAxis.setAutoRanging(false); // 锁定Y轴范围，不自动调整
        // 设计原因：利用率的取值范围固定为0%-100%，锁定后图表展示更稳定，用户易读

        // ========================== 步骤2：初始化折线图 ==========================
        // 创建折线图：传入X轴和Y轴，确定图表的坐标系
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setAnimated(false); // 禁用图表动画
        // 性能优化原因：实时数据每秒更新多次，动画会消耗CPU资源，导致图表卡顿
        lineChart.setCreateSymbols(false); // 不显示数据点的小圆圈，仅显示折线
        // 设计原因：数据点密集（60个），显示符号会导致图表杂乱，仅显示线条更清晰
        lineChart.setLegendVisible(true); // 显示图例（区分CPU和内存线条）
        // 图例位置：默认在图表上方/右侧，用户可通过图例识别不同线条的含义

        // ========================== 步骤3：初始化数据系列 ==========================
        // CPU利用率数据系列：设置名称（会显示在图例中）
        cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("CPU 利用率");

        // 内存利用率数据系列：设置名称
        memorySeries = new XYChart.Series<>();
        memorySeries.setName("内存 利用率");

        // ========================== 步骤4：将数据系列添加到图表 ==========================
        // 图表通过数据系列展示数据，添加后CPU/内存的线条才会出现在图表中
        lineChart.getData().add(cpuSeries);
        lineChart.getData().add(memorySeries);
    }


    /**
     * 获取图表组件（供UI布局使用）
     * 作用：返回初始化好的折线图对象，让外层界面（如MainController）能将图表添加到面板中
     * @return 配置完成的LineChart对象
     */
    public LineChart<Number, Number> getChart()
    {
        return lineChart;
    }


    /**
     * 实时更新图表数据（核心方法）
     * 触发时机：调度器每200ms调用一次（与系统时钟同步），传入最新的CPU/内存利用率
     * @param time        当前系统时间（调度器的systemClock，long类型）
     * @param cpuUsage    CPU利用率（0.0-1.0，0.0=0%，1.0=100%）
     * @param memoryUsage 内存利用率（0.0-1.0，0.0=0%，1.0=100%）
     */
    public void update(long time, double cpuUsage, double memoryUsage)
    {
        // 步骤1：将0.0-1.0的利用率转换为0-100的百分比（符合Y轴显示范围）
        double cpuVal = cpuUsage * 100;
        double memVal = memoryUsage * 100;

        // 步骤2：添加新数据点到对应系列
        // XYChart.Data<>(X轴值, Y轴值)：创建一个坐标点，对应“当前时间-当前利用率”
        // 示例：time=100，cpuVal=50 → 坐标(100,50)，表示第100个系统时钟时CPU利用率50%
        cpuSeries.getData().add(new XYChart.Data<>(time, cpuVal));
        memorySeries.getData().add(new XYChart.Data<>(time, memVal));

        // 步骤3：移除旧数据点，实现“滑动窗口”效果
        // 当CPU数据点数量超过最大值（60），移除第一个（最旧）的数据点
        if (cpuSeries.getData().size() > MAX_DATA_POINTS)
        {
            cpuSeries.getData().remove(0);
        }
        // 内存数据点同理，保证两个系列的数据点数量一致，图表展示同步
        if (memorySeries.getData().size() > MAX_DATA_POINTS)
        {
            memorySeries.getData().remove(0);
        }
        // 效果：图表始终只显示最近60个时间周期的利用率变化，避免数据堆积
    }
}
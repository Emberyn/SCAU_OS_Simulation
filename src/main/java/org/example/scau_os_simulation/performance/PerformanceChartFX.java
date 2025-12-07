package org.example.scau_os_simulation.performance;

import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

public class PerformanceChartFX
{

    private final LineChart<Number, Number> lineChart;
    private final XYChart.Series<Number, Number> cpuSeries;
    private final XYChart.Series<Number, Number> memorySeries;
    private final int MAX_DATA_POINTS = 60; // 图表上最多保留60个点，模拟滚动效果

    public PerformanceChartFX()
    {
        // 1. 定义坐标轴
        // X轴：时间 (System Clock)
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("时间");
        xAxis.setForceZeroInRange(false); // X轴不强制从0开始，实现滚动效果
        xAxis.setAutoRanging(true);

        // Y轴：百分比 (0-100)
        NumberAxis yAxis = new NumberAxis(0, 100, 10); // 范围0-100，刻度10
        yAxis.setLabel("使用率 (%)");
        yAxis.setAutoRanging(false); // 锁定Y轴范围

        // 2. 创建折线图
        lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setAnimated(false); // 禁用动画，对于实时数据更新性能更好
        lineChart.setCreateSymbols(false); // 不显示数据点的小圆圈，只显示线条
        lineChart.setLegendVisible(true); // 显示图例

        // 3. 初始化数据系列
        cpuSeries = new XYChart.Series<>();
        cpuSeries.setName("CPU 利用率");

        memorySeries = new XYChart.Series<>();
        memorySeries.setName("内存 利用率");

        // 4. 添加数据系列到图表
        lineChart.getData().add(cpuSeries);
        lineChart.getData().add(memorySeries);
    }

    /**
     * 获取图表节点，用于添加到界面
     */
    public LineChart<Number, Number> getChart()
    {
        return lineChart;
    }

    /**
     * 更新数据
     *
     * @param time        当前系统时间 (修改为 long 类型以匹配 Kernel)
     * @param cpuUsage    CPU利用率 (0.0 - 1.0)
     * @param memoryUsage 内存利用率 (0.0 - 1.0)
     */
    public void update(long time, double cpuUsage, double memoryUsage)
    {
        // 转换成百分比 (0-100)
        double cpuVal = cpuUsage * 100;
        double memVal = memoryUsage * 100;

        // 添加新数据点
        // JavaFX 的图表支持 Number 类型，所以传入 long 是完全没问题的
        cpuSeries.getData().add(new XYChart.Data<>(time, cpuVal));
        memorySeries.getData().add(new XYChart.Data<>(time, memVal));

        // 移除旧数据点，实现"滑动窗口"效果
        if (cpuSeries.getData().size() > MAX_DATA_POINTS)
        {
            cpuSeries.getData().remove(0);
        }
        if (memorySeries.getData().size() > MAX_DATA_POINTS)
        {
            memorySeries.getData().remove(0);
        }
    }
}
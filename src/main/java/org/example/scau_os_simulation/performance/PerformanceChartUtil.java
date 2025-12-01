package org.example.scau_os_simulation.performance;

import javafx.embed.swing.SwingNode;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * 性能图表工具类
 * <p>
 * 用于创建和更新CPU使用率、内存使用率的实时图表
 */
public class PerformanceChartUtil
{
    private TimeSeries cpuSeries;
    private TimeSeries memorySeries;
    private TimeSeriesCollection dataset;
    private JFreeChart chart;
    private ChartPanel chartPanel;
    private SwingNode swingNode;

    /**
     * 构造函数
     */
    public PerformanceChartUtil()
    {
        initializeChart();
    }

    /**
     * 初始化图表
     */
    private void initializeChart()
    {
        // 创建时间序列
        cpuSeries = new TimeSeries("CPU使用率");
        memorySeries = new TimeSeries("内存使用率");

        // 创建数据集
        dataset = new TimeSeriesCollection();
        dataset.addSeries(cpuSeries);
        dataset.addSeries(memorySeries);

        // 创建图表
        chart = ChartFactory.createTimeSeriesChart(
                "系统性能监控",
                "时间",
                "使用率 (%)",
                dataset,
                true,
                true,
                false
        );

        // 自定义图表外观
        chart.setBackgroundPaint(Color.WHITE);

        // 获取图表区域并自定义
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        // 自定义渲染器
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.RED);      // CPU使用率 - 红色
        renderer.setSeriesPaint(1, Color.BLUE);     // 内存使用率 - 蓝色
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        // 自定义时间轴
        DateAxis timeAxis = (DateAxis) plot.getDomainAxis();
        timeAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
        timeAxis.setAutoRange(true);
        timeAxis.setFixedAutoRange(60000.0); // 显示最近60秒的数据

        // 自定义数值轴
        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.setRange(0.0, 100.0); // 0-100%
        valueAxis.setAutoRange(false);

        // 创建图表面板
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));
        chartPanel.setMouseWheelEnabled(true);

        // 创建SwingNode用于在JavaFX中使用
        swingNode = new SwingNode();
        swingNode.setContent(chartPanel);
    }

    /**
     * 获取图表节点
     */
    public SwingNode getChartNode()
    {
        return swingNode;
    }

    public SwingNode getChartPanel()
    {
        return swingNode;
    }

    /**
     * 更新图表数据
     */
    public void updateData(double cpuUtilization, double memoryUsage)
    {
        Millisecond now = new Millisecond();
        cpuSeries.add(now, cpuUtilization);
        memorySeries.add(now, memoryUsage);

        // 限制数据点数量，防止内存溢出
        if (cpuSeries.getItemCount() > 300)
        { // 保留最近300个数据点
            cpuSeries.delete(0, cpuSeries.getItemCount() - 301);
        }
        if (memorySeries.getItemCount() > 300)
        {
            memorySeries.delete(0, memorySeries.getItemCount() - 301);
        }
    }

    public void update(long clock, double cpuUtilization, double memoryUtilization)
    {
        updateData(cpuUtilization * 100.0, memoryUtilization * 100.0);
    }

    /**
     * 从历史数据更新图表
     */
    public void updateFromHistory(List<PerformanceMonitor.PerformanceSnapshot> history)
    {
        // 清空现有数据
        cpuSeries.clear();
        memorySeries.clear();

        // 添加历史数据
        for (PerformanceMonitor.PerformanceSnapshot snapshot : history)
        {
            Millisecond time = new Millisecond(java.util.Date.from(
                    snapshot.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
            ));
            cpuSeries.add(time, snapshot.getCpuUtilization());
            memorySeries.add(time, snapshot.getMemoryUsage());
        }
    }

    /**
     * 清空图表数据
     */
    public void clear()
    {
        cpuSeries.clear();
        memorySeries.clear();
    }
}
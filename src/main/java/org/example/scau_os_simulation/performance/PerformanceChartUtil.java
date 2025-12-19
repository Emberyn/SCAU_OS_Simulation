package org.example.scau_os_simulation.performance;

import javafx.embed.swing.SwingNode;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
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
 * 性能图表工具类 (稳定修复版)
 */
public class PerformanceChartUtil
{
    private TimeSeries cpuSeries;
    private TimeSeries memorySeries;
    private TimeSeriesCollection dataset;
    private JFreeChart chart;
    private ChartPanel chartPanel;
    private SwingNode swingNode;

    public PerformanceChartUtil()
    {
        // 1. 先配置字体，防止中文乱码
        configFont();
        initializeChart();
    }

    /**
     * 配置 JFreeChart 字体以支持中文
     */
    private void configFont() {
        // 创建一个标准主题
        StandardChartTheme theme = (StandardChartTheme) StandardChartTheme.createJFreeTheme();

        // 指定支持中文的字体 (微软雅黑)
        Font font = new Font("Microsoft YaHei", Font.PLAIN, 12);
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 16);

        // 应用字体到各个部分
        theme.setExtraLargeFont(titleFont); // 标题
        theme.setLargeFont(font);           // 轴向标签
        theme.setRegularFont(font);         // 图例、刻度
        theme.setSmallFont(font);           // 小字体

        // 应用主题
        ChartFactory.setChartTheme(theme);
    }

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
                "系统性能监控趋势",  // 标题
                "时间",             // X轴标签
                "使用率 (%)",       // Y轴标签
                dataset,
                true,              // 显示图例
                true,
                false
        );

        // --- 外观美化 ---
        chart.setBackgroundPaint(Color.WHITE);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(new Color(230, 230, 230));
        plot.setRangeGridlinePaint(new Color(230, 230, 230));
        plot.setOutlineVisible(false);

        // 自定义线条颜色
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, new Color(0, 103, 192)); // CPU 蓝
        renderer.setSeriesPaint(1, new Color(216, 59, 1));  // 内存 橙
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));
        renderer.setSeriesStroke(1, new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        // --- 坐标轴设置 ---
        DateAxis timeAxis = (DateAxis) plot.getDomainAxis();
        timeAxis.setDateFormatOverride(new SimpleDateFormat("HH:mm:ss"));
        timeAxis.setAutoRange(true);
        timeAxis.setFixedAutoRange(60000.0); // 默认显示最近60秒
        // 强制字体设置 (双重保险)
        timeAxis.setLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        timeAxis.setTickLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 11));

        NumberAxis valueAxis = (NumberAxis) plot.getRangeAxis();
        valueAxis.setRange(0.0, 100.0);
        valueAxis.setAutoRange(false);
        valueAxis.setLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        valueAxis.setTickLabelFont(new Font("Microsoft YaHei", Font.PLAIN, 11));

        // 标题和图例字体
        if (chart.getLegend() != null) {
            chart.getLegend().setItemFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        }
        if (chart.getTitle() != null) {
            chart.getTitle().setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        }

        // --- 创建面板 ---
        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 400));

        // 开启鼠标滚轮缩放 (替代平移功能)
        // 遇到报错是因为旧版 JFreeChart 没有 setDomainPanning，但通常有 setMouseWheelEnabled
        try {
            chartPanel.setMouseWheelEnabled(true);
        } catch (NoSuchMethodError e) {
            // 如果版本实在太老，连这个都没有，那就忽略，保证程序能跑
            System.err.println("JFreeChart 版本过低，无法启用鼠标缩放");
        }

        // 创建SwingNode
        swingNode = new SwingNode();
        swingNode.setContent(chartPanel);
    }

    public SwingNode getChartNode()
    {
        return swingNode;
    }

    public SwingNode getChartPanel()
    {
        return swingNode;
    }

    public void updateData(double cpuUtilization, double memoryUsage)
    {
        Millisecond now = new Millisecond();
        cpuSeries.addOrUpdate(now, cpuUtilization);
        memorySeries.addOrUpdate(now, memoryUsage);

        // 限制数据点数量 (增加到 600 以保留更多历史，约 5 分钟)
        if (cpuSeries.getItemCount() > 600)
        {
            cpuSeries.delete(0, cpuSeries.getItemCount() - 601);
        }
        if (memorySeries.getItemCount() > 600)
        {
            memorySeries.delete(0, memorySeries.getItemCount() - 601);
        }
    }

    public void update(long clock, double cpuUtilization, double memoryUtilization)
    {
        updateData(cpuUtilization * 100.0, memoryUtilization * 100.0);
    }

    public void updateFromHistory(List<PerformanceMonitor.PerformanceSnapshot> history)
    {
        cpuSeries.clear();
        memorySeries.clear();
        for (PerformanceMonitor.PerformanceSnapshot snapshot : history)
        {
            Millisecond time = new Millisecond(java.util.Date.from(
                    snapshot.getTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant()
            ));
            cpuSeries.addOrUpdate(time, snapshot.getCpuUtilization() * 100.0);
            memorySeries.addOrUpdate(time, snapshot.getMemoryUsage() * 100.0);
        }
    }

    public void clear()
    {
        cpuSeries.clear();
        memorySeries.clear();
    }
}
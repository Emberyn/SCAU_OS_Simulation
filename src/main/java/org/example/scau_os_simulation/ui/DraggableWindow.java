package org.example.scau_os_simulation.ui;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;

/**
 * 模拟操作系统内部的窗口
 */
public class DraggableWindow extends BorderPane
{
    private double xOffset = 0;
    private double yOffset = 0;
    private final Pane parentDesktop; // 桌面引用，用于置顶窗口

    public DraggableWindow(String title, Node content, Pane desktop)
    {
        this.parentDesktop = desktop;

        // 1. 设置窗口样式
        this.getStyleClass().add("os-window");
        this.setPrefSize(600, 400); // 默认大小

        // 2. 创建标题栏
        HBox titleBar = new HBox();
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 0 0 0 10;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // 关闭按钮
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("window-close-btn");
        closeBtn.setOnAction(e -> closeWindow());

        // 最小化按钮 (可选)
        Button minBtn = new Button("_");
        minBtn.getStyleClass().add("window-min-btn");
        minBtn.setOnAction(e -> this.setVisible(false));

        titleBar.getChildren().addAll(titleLabel, minBtn, closeBtn);
        this.setTop(titleBar);

        // 3. 设置内容区域
        this.setCenter(content);

        // 4. 实现拖拽逻辑
        titleBar.setOnMousePressed(event ->
        {
            xOffset = event.getSceneX() - this.getLayoutX();
            yOffset = event.getSceneY() - this.getLayoutY();
            toFront(); // 点击标题栏时置顶
        });

        titleBar.setOnMouseDragged(event ->
        {
            this.setLayoutX(event.getSceneX() - xOffset);
            this.setLayoutY(event.getSceneY() - yOffset);
            parentDesktop.setCursor(Cursor.MOVE);
        });

        titleBar.setOnMouseReleased(e -> parentDesktop.setCursor(Cursor.DEFAULT));

        // 点击窗口内容也置顶
        this.setOnMouseClicked(e -> toFront());
    }

    private void closeWindow()
    {
        // 这里只是隐藏，并没有销毁，方便模拟“关闭”
        this.setVisible(false);
    }

    public void open()
    {
        this.setVisible(true);
        this.toFront();
        // 简单的打开动画效果
        this.setScaleX(0.8);
        this.setScaleY(0.8);
        this.setOpacity(0);

        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(javafx.util.Duration.millis(200), this);
        ft.setToValue(1.0);
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(200), this);
        st.setToX(1.0);
        st.setToY(1.0);

        javafx.animation.ParallelTransition pt = new javafx.animation.ParallelTransition(ft, st);
        pt.play();
    }
}
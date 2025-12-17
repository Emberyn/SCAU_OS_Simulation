package org.example.scau_os_simulation;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.scau_os_simulation.kernel.Kernel;

import java.io.IOException;

public class OSApplication extends Application
{

    private Kernel kernel;

    @Override
    public void start(Stage stage) throws IOException
    {
        // 全局激活 AtlantaFX (PrimerLight) 主题
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        kernel = new Kernel();
        kernel.initialize();

        // 加载界面
        FXMLLoader fxmlLoader = new FXMLLoader(OSApplication.class.getResource("main-view.fxml"));

        // 创建场景
        Scene scene = new Scene(fxmlLoader.load(), 1000, 720);

        // 加载自定义微调样式 (style.css)
        java.net.URL css = OSApplication.class.getResource("style.css");
        if (css != null)
        {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("操作系统模拟器 - AtlantaFX Edition");
        stage.setScene(scene);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event ->
        {
            if (event.getCode() == KeyCode.F11)
            {
                // 切换全屏状态 (如果是全屏就退出的，如果是窗口就全屏)
                stage.setFullScreen(!stage.isFullScreen());
                event.consume(); // 吞掉事件，防止传递给其他组件
            }
        });


        // --- 窗口与全屏设置 ---
        // 1. 初始设为全屏
        stage.setFullScreen(true);
        // 2. 更新提示文字，告诉用户现在的操作逻辑
        stage.setFullScreenExitHint("按 ESC 退出全屏 | 按 F11 切换全屏");
        // 3. 监听全屏变化 (用于退出全屏时恢复合理的窗口大小)
        stage.fullScreenProperty().addListener((obs, wasFullScreen, isFullScreen) ->
        {
            if (!isFullScreen)
            {
                // 退出全屏后，恢复到一个舒适的窗口大小并居中
                stage.setWidth(1024);
                stage.setHeight(768);
                stage.centerOnScreen();
            }
        });

        stage.show();
    }

    @Override
    public void stop()
    {
        if (kernel != null)
        {
            kernel.shutdown();
        }
    }

    public static void main(String[] args)
    {
        launch();
    }
}
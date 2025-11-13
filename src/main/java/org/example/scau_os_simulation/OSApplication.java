package org.example.scau_os_simulation;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.scau_os_simulation.kernel.Kernel;

import java.io.IOException;

public class OSApplication extends Application {
    private Kernel kernel;
    
    @Override
    public void start(Stage stage) throws IOException {
        // 初始化操作系统内核
        kernel = new Kernel();
        kernel.initialize();
        
        // 加载UI
        FXMLLoader fxmlLoader = new FXMLLoader(OSApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        stage.setTitle("操作系统模拟器");
        stage.setScene(scene);
        stage.show();
    }
    
    @Override
    public void stop() {
        // 关闭操作系统内核
        if (kernel != null) {
            kernel.shutdown();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}



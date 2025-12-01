/**********************************************************************************************
 * 项目名称：操作系统模拟器
 *
 * 文件说明：
 * 这个文件是整个操作系统模拟器的入口点。它负责启动应用程序、初始化操作系统内核、
 * 加载用户界面，以及在程序关闭时进行清理工作。
 *
 * 对于零基础的同学：
 * - 这个文件就像是你打开电脑的电源按钮，它负责启动整个系统
 * - 它创建了操作系统的"大脑"（内核），然后打开了一个窗口让你可以看到系统的运行状态
 * - 当你关闭窗口时，它会安全地关闭整个系统
 **********************************************************************************************/

// 包声明：告诉Java这个文件属于哪个包（文件夹）
package org.example.scau_os_simulation;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.scau_os_simulation.kernel.Kernel;

import java.io.IOException;

/**
 * 操作系统模拟器的主应用程序类
 * <p>
 * 这个类继承了JavaFX的Application类，意味着它是一个图形界面应用程序
 * 它负责整个应用程序的生命周期管理：启动、运行、关闭
 * <p>
 * 想象这个类就像一个电影导演，它负责：
 * 1. 搭建舞台（创建窗口）
 * 2. 安排演员（初始化内核）
 * 3. 开始演出（显示界面）
 * 4. 演出结束后的清理工作
 */
public class OSApplication extends Application
{
    /**
     * 操作系统内核实例
     * 内核是操作系统的核心部分，负责管理：
     * - 进程（正在运行的程序）
     * - 内存（程序使用的存储空间）
     * - 文件系统（文件和文件夹的管理）
     * - 设备（键盘、鼠标、打印机等硬件设备）
     * 想象内核就像一个管家，它管理着整个计算机系统的所有资源
     */
    private Kernel kernel;

    @Override
    public void start(Stage stage) throws IOException
    {
        // 第一步：创建并初始化操作系统内核
        // 内核就像操作系统的大脑，负责管理所有系统资源
        kernel = new Kernel();
        kernel.initialize();


        // 第二步：加载用户界面
        // FXML是一种专门用来描述用户界面的XML格式文件
        // 就像建筑图纸一样，它描述了窗口应该长什么样
        FXMLLoader fxmlLoader = new FXMLLoader(OSApplication.class.getResource("main-view.fxml"));
        // 场景就像舞台上的布景，包含了所有的界面元素

        // 创建场景，设置窗口大小为800x600像素
        // 场景就像舞台上的布景，包含了所有的界面元素
        // 创建场景，设置窗口大小为800x600像素
        // 场景就像舞台上的布景，包含了所有的界面元素
        Scene scene = new Scene(fxmlLoader.load(), 800, 600);
        // 应用全局样式表，统一配色与控件圆角
        java.net.URL css = OSApplication.class.getResource("style.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        // 第三步：配置并显示主窗口
        stage.setTitle("操作系统模拟器");  // 设置窗口标题
        stage.setScene(scene);            // 将场景放入舞台
        stage.show();                     // 显示窗口
    }

    @Override
    public void stop()
    {
        // 关闭操作系统内核
        if (kernel != null)
        {
            kernel.shutdown();
        }
    }

    /**
     * 应用程序的入口点
     * <p>
     * 这是Java程序的标准入口方法，当你运行程序时，Java虚拟机会从这里开始执行
     * launch()方法是JavaFX提供的，它会启动JavaFX应用程序框架
     * <p>
     * 想象这个方法就像电影的"开始播放"按钮：
     * 1. 你点击播放（运行程序）
     * 2. 系统开始加载（Java虚拟机启动）
     * 3. 电影开始播放（JavaFX框架启动，调用start()方法）
     *
     * @param args 命令行参数，可以从命令行传入参数（目前我们没有使用）
     */
    public static void main(String[] args)
    {
        launch();  // 启动JavaFX应用程序
    }
}



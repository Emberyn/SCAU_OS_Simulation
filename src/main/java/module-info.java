module org.example.scau_os_simulation {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.swing;
    requires java.desktop;
    requires org.jfree.jfreechart;

    // 【新增】引入 AtlantaFX 模块
    requires atlantafx.base;

    opens org.example.scau_os_simulation.controller to javafx.fxml;
    exports org.example.scau_os_simulation;
}
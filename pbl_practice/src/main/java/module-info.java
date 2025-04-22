module com.example.pbl_practice {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires com.google.gson;
    requires org.apache.commons.io;
    
    opens com.example to javafx.fxml;
    exports com.example;
}

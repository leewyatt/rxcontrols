module rxcontrols.samples {
    requires rxcontrols;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;

    opens app to javafx.graphics;
    opens app.ui to javafx.graphics, javafx.fxml;
    opens app.controller to javafx.fxml;
}

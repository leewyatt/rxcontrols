module rxcontrols.samples {
    requires rxcontrols;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;

    opens io.github.leewyatt.rxcontrols.samples to javafx.graphics;
    opens io.github.leewyatt.rxcontrols.samples.controller to javafx.fxml;
}

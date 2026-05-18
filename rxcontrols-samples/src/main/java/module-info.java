module rxcontrols.samples {
    requires rxcontrols;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;
    requires javafx.swing;
    requires java.desktop;
    requires org.controlsfx.controls;
    requires org.scenicview.scenicview;

    exports io.github.leewyatt.rxcontrols.samples.carousel;
    exports io.github.leewyatt.rxcontrols.samples.carousel.control;

    opens io.github.leewyatt.rxcontrols.samples to javafx.graphics;
    opens io.github.leewyatt.rxcontrols.samples.controller to javafx.fxml;
    opens io.github.leewyatt.rxcontrols.samples.carousel to javafx.graphics;
    opens io.github.leewyatt.rxcontrols.samples.playground to javafx.graphics;
}

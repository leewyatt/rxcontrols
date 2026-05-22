open module rxcontrols.samples {
    requires rxcontrols;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;
    requires javafx.swing;
    requires java.desktop;
    requires java.logging;
    requires org.controlsfx.controls;
    requires org.scenicview.scenicview;

    exports io.github.leewyatt.rxcontrols.samples.demo.carousel;

    exports io.github.leewyatt.rxcontrols.samples;
    exports io.github.leewyatt.rxcontrols.samples.showcase;
    exports io.github.leewyatt.rxcontrols.samples.support;
    exports io.github.leewyatt.rxcontrols.samples.test;
}

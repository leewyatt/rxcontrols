open module rxcontrols.samples {
    requires rxcontrols;
    requires rxcontrols.icons.bootstrap;
    requires rxcontrols.icons.material;

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.swing;
    requires java.desktop;
    requires java.logging;
    requires java.prefs;
    requires atlantafx.base;

    exports io.github.leewyatt.rxcontrols.samples.demo.carousel;

    exports io.github.leewyatt.rxcontrols.samples.sampler;
    exports io.github.leewyatt.rxcontrols.samples.showcase;
    exports io.github.leewyatt.rxcontrols.samples.support;
    exports io.github.leewyatt.rxcontrols.samples.test;
}

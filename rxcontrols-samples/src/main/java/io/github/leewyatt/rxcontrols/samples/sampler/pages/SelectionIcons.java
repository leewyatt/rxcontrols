package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXIcon;
import javafx.scene.control.Label;

/**
 * Icons, small text helpers and the stylesheet shared by the selection pages. Each icon
 * is a shape-backed region whose shape comes from its style class in the stylesheet.
 */
final class SelectionIcons {

    static final String STYLESHEET =
            SelectionIcons.class.getResource("rx-selection-samples.css").toExternalForm();

    private SelectionIcons() {
    }

    static RXIcon icon(String styleClass) {
        RXIcon icon = new RXIcon();
        icon.getStyleClass().addAll("symbol", styleClass);
        return icon;
    }

    static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }
}

package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 * Icons, small text helpers and the stylesheet shared by the selection pages. Each icon
 * is a shape-backed region whose shape comes from its style class in the stylesheet.
 */
final class SelectionIcons {

    static final String STYLESHEET =
            SelectionIcons.class.getResource("rx-selection-samples.css").toExternalForm();

    private SelectionIcons() {
    }

    static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("symbol", styleClass);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        return label;
    }
}

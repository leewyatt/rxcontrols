package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.utils.RXSizing;

import javafx.scene.layout.Region;

/**
 * Icons and the stylesheet shared by the button pages. Each icon is a shape-backed
 * region whose shape comes from its style class in the stylesheet.
 */
final class ButtonIcons {

    static final String STYLESHEET =
            ButtonIcons.class.getResource("rx-button-samples.css").toExternalForm();

    private ButtonIcons() {
    }

    static Region icon(String styleClass) {
        Region icon = new Region();
        icon.getStyleClass().addAll("icon", styleClass);
        RXSizing.lockToPrefSize(icon);
        return icon;
    }
}

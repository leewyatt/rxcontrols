package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXIcon;

/**
 * Icons and the stylesheet shared by the button pages. Each icon is a shape-backed
 * region whose shape comes from its style class in the stylesheet.
 */
final class ButtonIcons {

    static final String STYLESHEET =
            ButtonIcons.class.getResource("rx-button-samples.css").toExternalForm();

    private ButtonIcons() {
    }

    static RXIcon icon(String styleClass) {
        RXIcon icon = new RXIcon();
        icon.getStyleClass().addAll("icon", styleClass);
        return icon;
    }
}

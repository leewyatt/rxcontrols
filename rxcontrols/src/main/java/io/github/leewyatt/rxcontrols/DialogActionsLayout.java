package io.github.leewyatt.rxcontrols;

/**
 * How an {@link io.github.leewyatt.rxcontrols.RXDialog RXDialog} lays out the action
 * buttons it builds from its {@code buttonTypes}.
 */
public enum DialogActionsLayout {

    /**
     * A plain {@link io.github.leewyatt.rxcontrols.layout.RXBox RXBox} row of buttons in
     * {@code buttonTypes} order, fully styled by CSS on the {@code .actions}
     * sub-structure: alignment ({@code -rx-alignment}), spacing ({@code -rx-spacing}),
     * and axis ({@code -rx-orientation}, e.g. a vertical full-width stack). For
     * equal-width buttons, give them an explicit {@code -fx-pref-width} in CSS. The
     * default (centered + spaced out of the box).
     */
    BOX,

    /**
     * Native {@code ButtonBar}: buttons are ordered per the OS convention
     * (e.g. Windows {@code OK|Cancel}, macOS {@code Cancel|OK}) and aligned to the
     * trailing edge.
     */
    PLATFORM
}

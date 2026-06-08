package io.github.leewyatt.rxcontrols.enums;

/**
 * How an {@link io.github.leewyatt.rxcontrols.RXDrawerPane RXDrawerPane} positions
 * its panel relative to the main content.
 */
public enum RXDrawerMode {

    /**
     * The panel floats over the content (which stays put) — a pure
     * {@code translate} slide, optionally over a scrim. The default; zero relayout.
     */
    OVERLAY,

    /**
     * The panel pushes the content aside to make room, shrinking the content area
     * as it opens. This relayouts the content tree every frame.
     */
    PUSH
}

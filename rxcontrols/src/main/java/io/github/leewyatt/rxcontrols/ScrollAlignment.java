package io.github.leewyatt.rxcontrols;

/**
 * Where a target item range should land in the viewport when scrolling to an
 * item via {@link RXGridView#scrollTo(int, ScrollAlignment)} or
 * {@link RXTileView#scrollTo(int, ScrollAlignment)}.
 */
public enum ScrollAlignment {

    /** Bring the target range to the top of the viewport. */
    START,

    /** Center the target range in the viewport. */
    CENTER,

    /** Bring the target range to the bottom of the viewport. */
    END,

    /**
     * Scroll the minimum distance needed to make the target range visible; if
     * it is already visible, do nothing.
     */
    NEAREST
}

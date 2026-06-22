package io.github.leewyatt.rxcontrols;

/**
 * Where a target row should land in the viewport when scrolling to an item via
 * {@link RXGridView#scrollTo(int, RXGridScrollAlignment)} or
 * {@link RXTileView#scrollTo(int, RXGridScrollAlignment)}.
 */
public enum RXGridScrollAlignment {

    /** Bring the target row to the top of the viewport. */
    START,

    /** Center the target row in the viewport. */
    CENTER,

    /** Bring the target row to the bottom of the viewport. */
    END,

    /**
     * Scroll the minimum distance needed to make the target row visible; if it
     * is already visible, do nothing.
     */
    NEAREST
}

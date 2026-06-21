package io.github.leewyatt.rxcontrols;

/**
 * Where a target row should land in the viewport when scrolling to an item via
 * {@link RXGridView#scrollTo(int, RXGridScrollAlignment)}.
 *
 * <p><b>V1 support.</b> Only {@link #START} and {@link #NEAREST} are fully
 * implemented. {@link #CENTER} and {@link #END} are accepted but currently
 * behave as {@link #START}; they gain their own placement (a post-layout
 * {@code scrollPixels} correction) in a later release. They are part of the enum
 * now so callers can target the final semantics without an API change.
 */
public enum RXGridScrollAlignment {

    /** Bring the target row to the top of the viewport. */
    START,

    /** Center the target row in the viewport. Interim: behaves as {@link #START}. */
    CENTER,

    /** Bring the target row to the bottom of the viewport. Interim: behaves as {@link #START}. */
    END,

    /**
     * Scroll the minimum distance needed to make the target row visible; if it
     * is already visible, do nothing.
     */
    NEAREST
}

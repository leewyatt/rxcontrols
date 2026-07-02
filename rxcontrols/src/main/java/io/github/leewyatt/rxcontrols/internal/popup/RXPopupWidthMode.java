package io.github.leewyatt.rxcontrols.internal.popup;

/**
 * Strategy for sizing an anchored popup's width relative to its anchor. Single
 * entry point for width policy, replacing scattered "match width" booleans whose
 * name reads ambiguously (force-equal vs. minimum).
 *
 * <p>Kept internal until an anchored-popup public API stabilizes.
 */
public enum RXPopupWidthMode {

    /**
     * Anchor width is a lower bound; the content may grow wider than the anchor
     * (equivalent to the native combo box {@code max(anchorWidth, contentPref)}).
     * The default.
     */
    PREFER_ANCHOR_WIDTH,

    /**
     * The popup is forced to exactly the anchor width.
     */
    MATCH_ANCHOR_WIDTH,

    /**
     * The popup uses its content's preferred width, ignoring the anchor width.
     */
    PREF_CONTENT
}

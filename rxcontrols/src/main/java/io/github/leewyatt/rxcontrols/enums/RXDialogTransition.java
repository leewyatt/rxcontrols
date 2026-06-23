package io.github.leewyatt.rxcontrols.enums;

/**
 * Enter / exit transition styles for an
 * {@link io.github.leewyatt.rxcontrols.RXDialog RXDialog} card. A single scalar
 * progress in {@code [0, 1]} drives all of them through the skin's
 * {@code applyPose} dispatch: {@link #CENTER} scales and fades, the four
 * {@code SLIDE_*} variants translate from the named edge and fade.
 */
public enum RXDialogTransition {

    /**
     * The card scales up from a slightly smaller size while fading in (and the
     * reverse on close). The default.
     */
    CENTER,

    /**
     * The card slides in from the left edge (by its own width) while fading in.
     */
    SLIDE_LEFT,

    /**
     * The card slides in from the right edge (by its own width) while fading in.
     */
    SLIDE_RIGHT,

    /**
     * The card slides in from the top edge (by its own height) while fading in.
     */
    SLIDE_TOP,

    /**
     * The card slides in from the bottom edge (by its own height) while fading in.
     */
    SLIDE_BOTTOM
}

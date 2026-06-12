package io.github.leewyatt.rxcontrols.animation.line;

/**
 * Growth origin for extending line effects: where a bar starts growing along
 * its edge.
 */
public enum LineOrigin {

    /**
     * Grows from the left end (horizontal bars) or the top end (vertical
     * bars).
     */
    START,

    /**
     * Grows from the right end (horizontal bars) or the bottom end (vertical
     * bars).
     */
    END,

    /**
     * Grows from the center toward both ends.
     */
    CENTER,

    /**
     * Two segments grow from both ends and meet at the center.
     */
    EDGES
}

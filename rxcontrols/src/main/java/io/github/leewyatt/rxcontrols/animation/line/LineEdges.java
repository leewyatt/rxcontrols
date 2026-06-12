package io.github.leewyatt.rxcontrols.animation.line;

/**
 * Edge selection for line effects: which sides of the reference box carry an
 * animated line bar.
 */
public enum LineEdges {

    /**
     * One line above the reference box.
     */
    TOP,

    /**
     * One line below the reference box (the classic underline).
     */
    BOTTOM,

    /**
     * One line left of the reference box.
     */
    LEFT,

    /**
     * One line right of the reference box.
     */
    RIGHT,

    /**
     * A pair of lines above and below the reference box.
     */
    TOP_BOTTOM,

    /**
     * A pair of lines left and right of the reference box.
     */
    LEFT_RIGHT
}

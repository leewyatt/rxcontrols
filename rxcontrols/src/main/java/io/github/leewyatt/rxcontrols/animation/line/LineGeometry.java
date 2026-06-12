package io.github.leewyatt.rxcontrols.animation.line;

import javafx.geometry.Bounds;

/**
 * Shared edge geometry for the built-in line effects: side decomposition and
 * resting-bar coordinates relative to the reference box.
 */
final class LineGeometry {

    /**
     * Overlap of segments meeting at a shared point; abutting antialiased
     * bars would otherwise leave a hairline seam.
     */
    static final double SEAM_OVERLAP = 0.5;

    private LineGeometry() {
    }

    /**
     * Decomposes an edge selection into its single sides.
     *
     * @param edges the edge selection
     * @return the single sides, in top/bottom or left/right order
     */
    static LineEdges[] sidesOf(LineEdges edges) {
        switch (edges) {
            case TOP_BOTTOM:
                return new LineEdges[] {LineEdges.TOP, LineEdges.BOTTOM};
            case LEFT_RIGHT:
                return new LineEdges[] {LineEdges.LEFT, LineEdges.RIGHT};
            default:
                return new LineEdges[] {edges};
        }
    }

    /**
     * Returns whether bars on this side run horizontally.
     *
     * @param side a single side
     * @return {@code true} for top/bottom, {@code false} for left/right
     */
    static boolean isHorizontal(LineEdges side) {
        return side == LineEdges.TOP || side == LineEdges.BOTTOM;
    }

    /**
     * Returns the cross-axis coordinate of a resting bar: its top edge for
     * horizontal bars, its left edge for vertical bars.
     *
     * @param side      a single side
     * @param reference the reference box
     * @param thickness the bar thickness
     * @param gap       the gap between bar and reference box
     * @return the cross-axis coordinate
     */
    static double crossOf(LineEdges side, Bounds reference, double thickness, double gap) {
        switch (side) {
            case TOP:
                return reference.getMinY() - gap - thickness;
            case BOTTOM:
                return reference.getMaxY() + gap;
            case LEFT:
                return reference.getMinX() - gap - thickness;
            default:
                return reference.getMaxX() + gap;
        }
    }

    /**
     * Returns the cross-axis sign pointing away from the reference box.
     *
     * @param side a single side
     * @return {@code -1.0} for top/left, {@code 1.0} for bottom/right
     */
    static double outwardSignOf(LineEdges side) {
        return side == LineEdges.TOP || side == LineEdges.LEFT ? -1.0 : 1.0;
    }
}

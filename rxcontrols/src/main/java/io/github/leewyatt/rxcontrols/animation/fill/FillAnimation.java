package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;

import java.util.Locale;

/**
 * Strategy for the fill sweep geometry of {@code RXFillButton}.
 *
 * <p>Implementations are immutable geometry providers: {@link #createClip()}
 * builds a clip node for one skin instance, and
 * {@link #update(Node, double, double, double)} reshapes it as a pure
 * function of progress and size. The timing machinery (single reversible
 * timeline, trigger, duration) lives in the button skin and is shared by all
 * fill animations.</p>
 *
 * <p>The built-in presets are exposed as constants and selectable from CSS
 * via {@code -rx-fill-animation} keywords; parameterized or custom variants
 * are created in Java, e.g. {@code new FillAnimZigzag(6)}.</p>
 */
public interface FillAnimation {

    /**
     * Creates the clip node used by one skin instance.
     *
     * @return a fresh clip node
     */
    Node createClip();

    /**
     * Reshapes the clip for the given progress and fill area size.
     *
     * @param clip     the node returned by {@link #createClip()}
     * @param progress the fill progress in {@code [0, 1]}
     * @param width    the fill area width
     * @param height   the fill area height
     */
    void update(Node clip, double progress, double width, double height);

    // ==================== Built-in Presets ====================

    /**
     * Fill sweeps from the left edge to the right edge.
     */
    FillAnimation LEFT_TO_RIGHT = new FillAnimLeftToRight();

    /**
     * Fill sweeps from the right edge to the left edge.
     */
    FillAnimation RIGHT_TO_LEFT = new FillAnimRightToLeft();

    /**
     * Fill sweeps from the top edge to the bottom edge.
     */
    FillAnimation TOP_TO_BOTTOM = new FillAnimTopToBottom();

    /**
     * Fill sweeps from the bottom edge to the top edge.
     */
    FillAnimation BOTTOM_TO_TOP = new FillAnimBottomToTop();

    /**
     * Fill expands horizontally from the center to both edges.
     */
    FillAnimation CENTER_OUT = new FillAnimCenterOut();

    /**
     * Fill expands vertically from the center to both edges.
     */
    FillAnimation CENTER_OUT_VERTICAL = new FillAnimCenterOutVertical();

    /**
     * Two fills close in horizontally from the left and right edges.
     */
    FillAnimation EDGES_IN = new FillAnimEdgesIn();

    /**
     * Two fills close in vertically from the top and bottom edges.
     */
    FillAnimation EDGES_IN_VERTICAL = new FillAnimEdgesInVertical();

    /**
     * Fill expands as a circle from the center.
     */
    FillAnimation CIRCLE = new FillAnimCircle();

    /**
     * Four fills close in from the corners to the center.
     */
    FillAnimation CORNERS_IN = new FillAnimCornersIn();

    /**
     * Horizontal stripes sweep in from alternating sides.
     */
    FillAnimation ZIGZAG = new FillAnimZigzag();

    /**
     * Vertical stripes sweep in from alternating sides.
     */
    FillAnimation ZIGZAG_VERTICAL = new FillAnimZigzagVertical();

    /**
     * Returns the built-in preset for a CSS keyword such as
     * {@code left-to-right} or {@code zigzag}. Matching is case-insensitive
     * and tolerates underscores in place of hyphens.
     *
     * @param keyword the keyword, may be {@code null}
     * @return the preset, or {@code null} if the keyword is unknown
     */
    static FillAnimation valueOf(String keyword) {
        if (keyword == null) {
            return null;
        }
        switch (keyword.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "left-to-right":
                return LEFT_TO_RIGHT;
            case "right-to-left":
                return RIGHT_TO_LEFT;
            case "top-to-bottom":
                return TOP_TO_BOTTOM;
            case "bottom-to-top":
                return BOTTOM_TO_TOP;
            case "center-out":
                return CENTER_OUT;
            case "center-out-vertical":
                return CENTER_OUT_VERTICAL;
            case "edges-in":
                return EDGES_IN;
            case "edges-in-vertical":
                return EDGES_IN_VERTICAL;
            case "circle":
                return CIRCLE;
            case "corners-in":
                return CORNERS_IN;
            case "zigzag":
                return ZIGZAG;
            case "zigzag-vertical":
                return ZIGZAG_VERTICAL;
            default:
                return null;
        }
    }
}

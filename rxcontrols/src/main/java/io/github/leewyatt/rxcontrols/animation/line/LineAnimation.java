package io.github.leewyatt.rxcontrols.animation.line;

import javafx.geometry.Bounds;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Locale;

/**
 * Strategy for the line geometry of {@code RXLineButton}.
 *
 * <p>Implementations are immutable geometry providers: the hosting decoration
 * creates {@link #barCount()} bar nodes (styled {@code .line} regions, one
 * per visible line) and
 * {@link #update(List, double, Bounds, double, double) update} positions them
 * as a pure function of progress — each call must fully determine every
 * bar's position, size and opacity. Coordinates are expressed in the host's
 * local space; bars may extend beyond the host bounds (the line layer is
 * never clipped). The timing machinery (single reversible timeline, trigger,
 * duration) lives in the decoration and is shared by all line effects.</p>
 *
 * <p>The built-in presets are exposed as constants and selectable from CSS
 * via {@code -rx-line-animation} keywords; parameterized or custom variants
 * are created in Java, e.g.
 * {@code new LineAnimSlide(LineEdges.BOTTOM, 20.0)}.</p>
 */
public interface LineAnimation {

    /**
     * Returns the number of bar nodes this effect drives. Must stay constant
     * over the instance's lifetime; the hosting decoration creates this many
     * bars when the effect is applied.
     *
     * @return the bar count
     */
    int barCount();

    /**
     * Positions the bars for the given progress over the reference box. Each
     * call must set every bar's position, size and opacity.
     *
     * @param bars      the {@link #barCount()} bars created by the host
     * @param progress  the progress in {@code [0, 1]}
     * @param reference the reference box in host-local coordinates
     * @param thickness the bar thickness
     * @param gap       the gap between a resting bar and the reference box
     */
    void update(List<? extends Region> bars, double progress,
                Bounds reference, double thickness, double gap);

    // ==================== Built-in Presets ====================

    /**
     * Underline extends from the left end to the right.
     */
    LineAnimation UNDERLINE_LEFT_TO_RIGHT =
            new LineAnimExtend(LineEdges.BOTTOM, LineOrigin.START);

    /**
     * Underline extends from the right end to the left.
     */
    LineAnimation UNDERLINE_RIGHT_TO_LEFT =
            new LineAnimExtend(LineEdges.BOTTOM, LineOrigin.END);

    /**
     * Underline extends from the center toward both ends.
     */
    LineAnimation UNDERLINE_CENTER_OUT =
            new LineAnimExtend(LineEdges.BOTTOM, LineOrigin.CENTER);

    /**
     * Two underline segments extend from both ends and meet at the center.
     */
    LineAnimation UNDERLINE_EDGES_IN =
            new LineAnimExtend(LineEdges.BOTTOM, LineOrigin.EDGES);

    /**
     * Underline slides up into place from below while fading in.
     */
    LineAnimation UNDERLINE_SLIDE_UP =
            new LineAnimSlide(LineEdges.BOTTOM, LineAnimSlide.DEFAULT_OFFSET);

    /**
     * Underline slides down into place from above while fading in.
     */
    LineAnimation UNDERLINE_SLIDE_DOWN =
            new LineAnimSlide(LineEdges.BOTTOM, -LineAnimSlide.DEFAULT_OFFSET);

    /**
     * Underline fades in place.
     */
    LineAnimation UNDERLINE_FADE =
            new LineAnimSlide(LineEdges.BOTTOM, 0.0);

    /**
     * Lines above and below extend from their centers toward both ends.
     */
    LineAnimation TOP_BOTTOM_CENTER_OUT =
            new LineAnimExtend(LineEdges.TOP_BOTTOM, LineOrigin.CENTER);

    /**
     * Lines above and below converge onto the content from outside while
     * fading in.
     */
    LineAnimation TOP_BOTTOM_CONVERGE =
            new LineAnimSlide(LineEdges.TOP_BOTTOM, LineAnimSlide.DEFAULT_OFFSET);

    /**
     * Lines left and right extend from their centers toward both ends.
     */
    LineAnimation LEFT_RIGHT_CENTER_OUT =
            new LineAnimExtend(LineEdges.LEFT_RIGHT, LineOrigin.CENTER);

    /**
     * Lines left and right converge onto the content from outside while
     * fading in.
     */
    LineAnimation LEFT_RIGHT_CONVERGE =
            new LineAnimSlide(LineEdges.LEFT_RIGHT, LineAnimSlide.DEFAULT_OFFSET);

    /**
     * Returns the built-in preset for a CSS keyword such as
     * {@code underline-center-out} or {@code top-bottom-converge}. Matching
     * is case-insensitive and tolerates underscores in place of hyphens.
     *
     * @param keyword the keyword, may be {@code null}
     * @return the preset, or {@code null} if the keyword is unknown
     */
    static LineAnimation valueOf(String keyword) {
        if (keyword == null) {
            return null;
        }
        switch (keyword.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "underline-left-to-right":
                return UNDERLINE_LEFT_TO_RIGHT;
            case "underline-right-to-left":
                return UNDERLINE_RIGHT_TO_LEFT;
            case "underline-center-out":
                return UNDERLINE_CENTER_OUT;
            case "underline-edges-in":
                return UNDERLINE_EDGES_IN;
            case "underline-slide-up":
                return UNDERLINE_SLIDE_UP;
            case "underline-slide-down":
                return UNDERLINE_SLIDE_DOWN;
            case "underline-fade":
                return UNDERLINE_FADE;
            case "top-bottom-center-out":
                return TOP_BOTTOM_CENTER_OUT;
            case "top-bottom-converge":
                return TOP_BOTTOM_CONVERGE;
            case "left-right-center-out":
                return LEFT_RIGHT_CENTER_OUT;
            case "left-right-converge":
                return LEFT_RIGHT_CONVERGE;
            default:
                return null;
        }
    }
}

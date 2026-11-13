package io.github.leewyatt.rxcontrols.animation.burst;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;

/**
 * Easing, timing and shape helpers shared by the burst presets.
 */
final class BurstShapes {

    /**
     * Sizes below this many pixels are skipped: drawing them would leave
     * faint anti-aliased specks at the end of a burst.
     */
    static final double MIN_VISIBLE_SIZE = 0.05;

    /**
     * Progress by which every preset has faded out completely. Ending a
     * little before 1 keeps the last frames empty however large the burst
     * is, rather than relying on sizes shrinking under a pixel threshold.
     */
    static final double END = 0.97;

    /**
     * The angle of the first particle: straight up from the center.
     */
    static final double TOP = -Math.PI / 2.0;

    private BurstShapes() {
    }

    // ==================== Timing ====================

    /**
     * Maps the overall progress onto a sub-interval: 0 before {@code start},
     * 1 after {@code end}, linear in between.
     */
    static double segment(double progress, double start, double end) {
        if (end <= start) {
            return progress >= end ? 1.0 : 0.0;
        }
        return RXMath.clamp0To1((progress - start) / (end - start));
    }

    /**
     * Distance from the center at which particles start: the anchor's edge,
     * but never past half the reach.
     */
    static double startDistance(BurstFrame frame) {
        return Math.min(frame.anchorSize() * 0.5, frame.radius() * 0.5);
    }

    static double easeOutCubic(double t) {
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    }

    static double easeOutQuad(double t) {
        return 1.0 - (1.0 - t) * (1.0 - t);
    }

    static double easeInQuad(double t) {
        return t * t;
    }

    static double easeInOutQuad(double t) {
        return t < 0.5 ? 2.0 * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 2.0) / 2.0;
    }

    /**
     * 0 at both ends of the interval, 1 in the middle: a swell that grows and
     * shrinks again.
     */
    static double swell(double t) {
        return Math.sin(Math.PI * RXMath.clamp0To1(t));
    }

    // ==================== Shapes ====================

    /**
     * Fills a star with {@code points} tips around ({@code cx}, {@code cy}).
     */
    static void fillStar(GraphicsContext gc, double cx, double cy, int points,
                         double outerRadius, double innerRadius, double rotation) {
        int count = points * 2;
        double[] xs = new double[count];
        double[] ys = new double[count];
        for (int i = 0; i < count; i++) {
            double r = i % 2 == 0 ? outerRadius : innerRadius;
            double angle = rotation + TOP + i * Math.PI / points;
            xs[i] = cx + r * Math.cos(angle);
            ys[i] = cy + r * Math.sin(angle);
        }
        gc.fillPolygon(xs, ys, count);
    }
}

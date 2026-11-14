package io.github.leewyatt.rxcontrols.animation.burst;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;

/**
 * Easing, timing and shape helpers shared by the burst presets.
 */
final class BurstSupport {

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

    /**
     * How far past the reach a particle may paint, in anchor sizes: room for
     * a particle's own size at the end of its path.
     */
    static final double OVERSHOOT = 0.4;

    private BurstSupport() {
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
        return Math.min(frame.anchorSize() * 0.5, frame.reach() * 0.5);
    }

    /**
     * The farthest distance from the center any painted pixel may reach.
     */
    static double limit(BurstFrame frame) {
        return frame.reach() + frame.anchorSize() * OVERSHOOT;
    }

    /**
     * Shrinks a round particle at ({@code x}, {@code y}) so that it stays
     * within {@link #limit(BurstFrame)}; the result may be zero or negative
     * when the particle's center is already past the limit.
     */
    static double fitRadius(BurstFrame frame, double x, double y, double radius) {
        double room = limit(frame) - Math.hypot(x - frame.centerX(), y - frame.centerY());
        return Math.min(radius, room);
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

    /**
     * Fills a heart of the given width centered on ({@code cx}, {@code cy}),
     * built from the same curves as the reaction button's 24 x 24 heart icon.
     */
    static void fillHeart(GraphicsContext gc, double cx, double cy, double width) {
        double s = width / 18.0;
        double ox = cx - 12.0 * s;
        double oy = cy - 12.1 * s;
        gc.beginPath();
        gc.moveTo(ox + 12.0 * s, oy + 20.5 * s);
        gc.bezierCurveTo(ox + 12.0 * s, oy + 20.5 * s, ox + 3.0 * s, oy + 14.5 * s, ox + 3.0 * s, oy + 8.75 * s);
        gc.bezierCurveTo(ox + 3.0 * s, oy + 5.85 * s, ox + 5.3 * s, oy + 3.75 * s, ox + 7.9 * s, oy + 3.75 * s);
        gc.bezierCurveTo(ox + 9.7 * s, oy + 3.75 * s, ox + 11.2 * s, oy + 4.75 * s, ox + 12.0 * s, oy + 6.2 * s);
        gc.bezierCurveTo(ox + 12.8 * s, oy + 4.75 * s, ox + 14.3 * s, oy + 3.75 * s, ox + 16.1 * s, oy + 3.75 * s);
        gc.bezierCurveTo(ox + 18.7 * s, oy + 3.75 * s, ox + 21.0 * s, oy + 5.85 * s, ox + 21.0 * s, oy + 8.75 * s);
        gc.bezierCurveTo(ox + 21.0 * s, oy + 14.5 * s, ox + 12.0 * s, oy + 20.5 * s, ox + 12.0 * s, oy + 20.5 * s);
        gc.closePath();
        gc.fill();
    }

    /**
     * Fills a triangle of the given circumradius, turned by {@code rotation}
     * radians around its center.
     */
    static void fillTriangle(GraphicsContext gc, double cx, double cy, double radius, double rotation) {
        double[] xs = new double[3];
        double[] ys = new double[3];
        for (int i = 0; i < 3; i++) {
            double angle = rotation + TOP + i * 2.0 * Math.PI / 3.0;
            xs[i] = cx + radius * Math.cos(angle);
            ys[i] = cy + radius * Math.sin(angle);
        }
        gc.fillPolygon(xs, ys, 3);
    }

    static void fillCircle(GraphicsContext gc, double cx, double cy, double radius) {
        gc.fillOval(cx - radius, cy - radius, radius * 2.0, radius * 2.0);
    }
}

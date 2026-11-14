package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * Pairs of dots fly straight out from the anchor and shrink away near the
 * end; each pair has a larger dot and a smaller one slightly behind it.
 */
public final class BurstAnimDots implements BurstAnimation {

    private static final int DEFAULT_PAIRS = 7;
    private static final int MIN_PAIRS = 1;
    private static final int MAX_PAIRS = 24;

    private final int pairs;

    /**
     * Creates the preset with seven pairs of dots.
     */
    public BurstAnimDots() {
        this(DEFAULT_PAIRS);
    }

    /**
     * Creates the preset with the given number of dot pairs.
     *
     * @param pairs the number of pairs, clamped into {@code [1, 24]}
     */
    public BurstAnimDots(int pairs) {
        this.pairs = Math.max(MIN_PAIRS, Math.min(MAX_PAIRS, pairs));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = frame.progress();
        if (t >= BurstSupport.END) {
            return;
        }
        double anchor = frame.anchorSize();
        double startDistance = BurstSupport.startDistance(frame) * 0.9;
        double travel = BurstSupport.easeOutCubic(t);
        double shrink = 1.0 - BurstSupport.segment(t, 0.55, BurstSupport.END);
        double big = anchor * 0.16 * shrink;
        double small = anchor * 0.1 * shrink;
        if (big < BurstSupport.MIN_VISIBLE_SIZE) {
            return;
        }
        double step = 2.0 * Math.PI / pairs;
        gc.save();
        for (int i = 0; i < pairs; i++) {
            double jitter = (frame.random(i, 0) - 0.5) * step * 0.25;
            double angle = BurstSupport.TOP + i * step + jitter;
            double reach = frame.reach() * (0.88 + 0.12 * frame.random(i, 1));
            double distance = startDistance + (reach - startDistance) * travel;
            dot(gc, frame, i * 2, angle, distance, big);
            dot(gc, frame, i * 2 + 1, angle + step * 0.3, distance * 0.82, small);
        }
        gc.restore();
    }

    private static void dot(GraphicsContext gc, BurstFrame frame, int colorIndex,
                            double angle, double distance, double radius) {
        double x = frame.centerX() + distance * Math.cos(angle);
        double y = frame.centerY() + distance * Math.sin(angle);
        gc.setFill(frame.color(colorIndex));
        gc.fillOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
    }
}

package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * Small hearts float up and out from the anchor in a fan, swaying from side
 * to side and fading as they rise.
 */
public final class BurstAnimHearts implements BurstAnimation {

    private static final int DEFAULT_COUNT = 6;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 24;
    // The fan opens 120 degrees around straight up.
    private static final double FAN = Math.toRadians(120.0);

    private final int count;

    /**
     * Creates the preset with six hearts.
     */
    public BurstAnimHearts() {
        this(DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of hearts.
     *
     * @param count the number of hearts, clamped into {@code [1, 24]}
     */
    public BurstAnimHearts(int count) {
        this.count = Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = frame.progress();
        if (t >= BurstShapes.END) {
            return;
        }
        double anchor = frame.anchorSize();
        double start = BurstShapes.startDistance(frame);
        gc.save();
        for (int i = 0; i < count; i++) {
            double delay = frame.random(i, 0) * 0.2;
            double local = BurstShapes.segment(t, delay, delay + (BurstShapes.END - 0.2));
            if (local <= 0.0 || local >= 1.0) {
                continue;
            }
            double size = anchor * (0.35 + 0.15 * frame.random(i, 1))
                    * BurstShapes.easeOutCubic(BurstShapes.segment(local, 0.0, 0.2));
            double alpha = 1.0 - BurstShapes.segment(local, 0.55, 1.0);
            if (size < BurstShapes.MIN_VISIBLE_SIZE || alpha <= 0.0) {
                continue;
            }
            // One slot of the fan per heart, jittered within it, so they
            // never all lean one way.
            double angle = BurstShapes.TOP + ((i + frame.random(i, 2)) / count - 0.5) * FAN;
            double distance = start + (frame.radius() * 0.8 - start) * BurstShapes.easeOutQuad(local);
            double sway = Math.sin(local * Math.PI * 2.0 + frame.random(i, 3) * Math.PI * 2.0) * anchor * 0.2 * local;
            double x = frame.centerX() + distance * Math.cos(angle) - sway * Math.sin(angle);
            double y = frame.centerY() + distance * Math.sin(angle) + sway * Math.cos(angle);
            gc.setGlobalAlpha(alpha);
            gc.setFill(frame.color(i));
            BurstShapes.fillHeart(gc, x, y, size);
        }
        gc.restore();
    }
}

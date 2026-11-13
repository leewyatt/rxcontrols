package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * Hollow bubbles rise from around the anchor, wobbling as they go, and pop:
 * they swell and vanish at the end of their climb.
 */
public final class BurstAnimBubbles implements BurstAnimation {

    private static final int DEFAULT_COUNT = 7;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 24;

    private final int count;

    /**
     * Creates the preset with seven bubbles.
     */
    public BurstAnimBubbles() {
        this(DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of bubbles.
     *
     * @param count the number of bubbles, clamped into {@code [1, 24]}
     */
    public BurstAnimBubbles(int count) {
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
        double lineWidth = anchor * 0.06;
        if (lineWidth < BurstShapes.MIN_VISIBLE_SIZE) {
            return;
        }
        gc.save();
        gc.setLineWidth(lineWidth);
        for (int i = 0; i < count; i++) {
            double delay = frame.random(i, 0) * 0.25;
            double local = BurstShapes.segment(t, delay, delay + (BurstShapes.END - 0.25));
            if (local <= 0.0 || local >= 1.0) {
                continue;
            }
            double pop = BurstShapes.segment(local, 0.8, 1.0);
            double radius = anchor * (0.12 + 0.1 * frame.random(i, 1))
                    * BurstShapes.easeOutCubic(BurstShapes.segment(local, 0.0, 0.15)) * (1.0 + 0.6 * pop);
            double alpha = 1.0 - pop;
            if (radius < BurstShapes.MIN_VISIBLE_SIZE || alpha <= 0.0) {
                continue;
            }
            // One slot across the anchor per bubble, drifting apart as they
            // rise, so they do not climb in one column.
            double side = (i + frame.random(i, 2)) / count - 0.5;
            double eased = BurstShapes.easeOutQuad(local);
            double rise = frame.radius() * (0.55 + 0.3 * frame.random(i, 3)) * eased;
            double wobble = Math.sin(local * Math.PI * 3.0 + frame.random(i, 4) * Math.PI * 2.0) * anchor * 0.1;
            double spread = Math.min(anchor * 1.4, frame.radius() * 0.5) + frame.radius() * 0.8 * eased;
            double x = frame.centerX() + side * spread + wobble;
            double y = frame.centerY() - rise;
            radius = BurstShapes.fitRadius(frame, x, y, radius + lineWidth / 2.0) - lineWidth / 2.0;
            if (radius < BurstShapes.MIN_VISIBLE_SIZE) {
                continue;
            }
            gc.setStroke(frame.color(i));
            gc.setFill(frame.color(i));
            gc.setGlobalAlpha(alpha * 0.18);
            BurstShapes.fillCircle(gc, x, y, radius);
            gc.setGlobalAlpha(alpha);
            gc.strokeOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        }
        gc.restore();
    }
}

package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * Four-pointed sparkles twinkle around the anchor: each appears at its own
 * moment, swells and shrinks away while drifting outward and turning.
 */
public final class BurstAnimSparkles implements BurstAnimation {

    private static final int DEFAULT_COUNT = 8;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 32;

    private final int count;

    /**
     * Creates the preset with eight sparkles.
     */
    public BurstAnimSparkles() {
        this(DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of sparkles.
     *
     * @param count the number of sparkles, clamped into {@code [1, 32]}
     */
    public BurstAnimSparkles(int count) {
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
        double step = 2.0 * Math.PI / count;
        gc.save();
        for (int i = 0; i < count; i++) {
            double start = frame.random(i, 0) * 0.3;
            double local = BurstShapes.segment(t, start, start + (BurstShapes.END - 0.3));
            double size = anchor * (0.26 + 0.14 * frame.random(i, 1)) * BurstShapes.swell(local);
            if (size < BurstShapes.MIN_VISIBLE_SIZE) {
                continue;
            }
            double angle = BurstShapes.TOP + i * step + (frame.random(i, 2) - 0.5) * step * 0.6;
            double distance = frame.radius() * (0.5 + 0.25 * frame.random(i, 3))
                    + frame.radius() * 0.2 * BurstShapes.easeOutQuad(local);
            double rotation = (frame.random(i, 4) - 0.5) * Math.PI / 2.0 * local;
            gc.setFill(frame.color(i));
            BurstShapes.fillStar(gc, frame.centerX() + distance * Math.cos(angle),
                    frame.centerY() + distance * Math.sin(angle), 4, size, size * 0.3, rotation);
        }
        gc.restore();
    }
}

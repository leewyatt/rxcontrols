package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * Confetti: small paper strips scatter from the anchor, spinning and turning
 * over as they fall back under gravity.
 */
public final class BurstAnimConfetti implements BurstAnimation {

    private static final int DEFAULT_COUNT = 24;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 80;

    private final int count;

    /**
     * Creates the preset with 24 pieces of confetti.
     */
    public BurstAnimConfetti() {
        this(DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of pieces.
     *
     * @param count the number of pieces, clamped into {@code [1, 80]}
     */
    public BurstAnimConfetti(int count) {
        this.count = Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = BurstShapes.segment(frame.progress(), 0.0, BurstShapes.END);
        if (t >= 1.0) {
            return;
        }
        double anchor = frame.anchorSize();
        double alpha = 1.0 - BurstShapes.segment(t, 0.7, 1.0);
        double length = anchor * 0.32;
        double breadth = anchor * 0.15;
        if (breadth < BurstShapes.MIN_VISIBLE_SIZE) {
            return;
        }
        // Throw distance and fall stay within the reach together.
        double throwReach = frame.radius() * 0.65;
        double fall = frame.radius() * 0.3 * t * t;
        double travel = BurstShapes.easeOutCubic(t);
        gc.save();
        gc.setGlobalAlpha(alpha);
        for (int i = 0; i < count; i++) {
            double angle = frame.random(i, 0) * 2.0 * Math.PI;
            // Biased upward, as if tossed.
            double dx = Math.cos(angle);
            double dy = Math.sin(angle) - 0.35;
            double norm = Math.hypot(dx, dy);
            double speed = 0.6 + 0.4 * frame.random(i, 1);
            double x = frame.centerX() + dx / norm * throwReach * speed * travel;
            double y = frame.centerY() + dy / norm * throwReach * speed * travel + fall;
            double spin = frame.random(i, 2) * 360.0 + (frame.random(i, 3) - 0.5) * 1080.0 * t;
            // Turning over: the strip's width follows the cosine of a second spin.
            double flip = Math.cos((frame.random(i, 4) + t * (1.5 + frame.random(i, 5))) * 2.0 * Math.PI);
            double width = length * Math.max(0.15, Math.abs(flip));
            gc.save();
            gc.translate(x, y);
            gc.rotate(spin);
            gc.setFill(frame.color(i));
            gc.fillRect(-width / 2.0, -breadth / 2.0, width, breadth);
            gc.restore();
        }
        gc.restore();
    }
}

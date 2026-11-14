package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;

/**
 * Fireworks: a flash, then sparks shoot out in two rings, each drawing a
 * fading trail behind it, droop under gravity and flicker out.
 */
public final class BurstAnimFireworks implements BurstAnimation {

    private static final int DEFAULT_SPARKS = 14;
    private static final int MIN_SPARKS = 3;
    private static final int MAX_SPARKS = 48;
    // How far back along its path a spark's trail reaches, in local progress.
    private static final double TRAIL = 0.16;
    // Spark head radius and trail width, in anchor sizes.
    private static final double SPARK_SIZE = 0.07;

    private final int sparks;

    /**
     * Creates the preset with 14 sparks in the outer ring.
     */
    public BurstAnimFireworks() {
        this(DEFAULT_SPARKS);
    }

    /**
     * Creates the preset with the given number of sparks in the outer ring;
     * the inner ring has half as many, at least three.
     *
     * @param sparks the outer ring's spark count, clamped into {@code [3, 48]}
     */
    public BurstAnimFireworks(int sparks) {
        this.sparks = Math.max(MIN_SPARKS, Math.min(MAX_SPARKS, sparks));
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
        if (anchor * SPARK_SIZE < BurstSupport.MIN_VISIBLE_SIZE) {
            return;
        }
        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);
        double flash = 1.0 - BurstSupport.segment(t, 0.0, 0.12);
        if (flash > 0.0) {
            gc.setGlobalAlpha(0.5 * flash);
            gc.setFill(frame.color(1));
            BurstSupport.fillCircle(gc, frame.centerX(), frame.centerY(),
                    Math.min(anchor * (0.4 + 0.5 * (1.0 - flash)), BurstSupport.limit(frame)));
        }
        ring(gc, frame, BurstSupport.segment(t, 0.0, BurstSupport.END), sparks, frame.reach(), 0, 0.0);
        ring(gc, frame, BurstSupport.segment(t, 0.12, BurstSupport.END), Math.max(MIN_SPARKS, sparks / 2),
                frame.reach() * 0.55, sparks, Math.PI / sparks);
        gc.restore();
    }

    private static void ring(GraphicsContext gc, BurstFrame frame, double local, int count,
                             double reach, int indexOffset, double angleOffset) {
        if (local <= 0.0 || local >= 1.0) {
            return;
        }
        double anchor = frame.anchorSize();
        double fade = 1.0 - BurstSupport.segment(local, 0.55, 1.0);
        double size = anchor * SPARK_SIZE * fade;
        if (size < BurstSupport.MIN_VISIBLE_SIZE) {
            return;
        }
        // Eases the flicker in, so the brightness does not jump when it starts.
        double flickerDepth = 0.45 * BurstSupport.segment(local, 0.5, 0.65);
        double step = 2.0 * Math.PI / count;
        for (int k = 0; k < count; k++) {
            int i = k + indexOffset;
            double angle = BurstSupport.TOP + angleOffset + k * step + (frame.random(i, 0) - 0.5) * step * 0.4;
            // Leaves room for the droop below, so a spark stays within the reach.
            double sparkReach = reach * (0.7 + 0.15 * frame.random(i, 1));
            // Late flicker: each spark blinks on its own phase as it dies.
            double flicker = 1.0 - flickerDepth
                    + flickerDepth * Math.sin(local * 70.0 + frame.random(i, 2) * Math.PI * 2.0);
            double alpha = fade * flicker;
            if (alpha <= 0.0) {
                continue;
            }
            double headX = sparkX(frame, angle, sparkReach, local);
            double headY = sparkY(frame, angle, sparkReach, local);
            double tail = Math.max(0.0, local - TRAIL);
            gc.setGlobalAlpha(alpha * 0.6);
            gc.setStroke(frame.color(i));
            gc.setLineWidth(size);
            gc.strokeLine(sparkX(frame, angle, sparkReach, tail), sparkY(frame, angle, sparkReach, tail), headX, headY);
            gc.setGlobalAlpha(alpha);
            gc.setFill(frame.color(i));
            BurstSupport.fillCircle(gc, headX, headY, size);
        }
    }

    private static double sparkX(BurstFrame frame, double angle, double reach, double local) {
        return frame.centerX() + reach * BurstSupport.easeOutCubic(local) * Math.cos(angle);
    }

    // Gravity pulls the sparks down a little as they slow.
    private static double sparkY(BurstFrame frame, double angle, double reach, double local) {
        return frame.centerY() + reach * BurstSupport.easeOutCubic(local) * Math.sin(angle)
                + reach * 0.15 * local * local;
    }
}

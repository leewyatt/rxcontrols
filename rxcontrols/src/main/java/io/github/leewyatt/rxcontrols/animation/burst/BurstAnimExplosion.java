package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * An explosion: a bright flash, a shock wave, spinning shards flung outward
 * and puffs of smoke that swell and drift away.
 */
public final class BurstAnimExplosion implements BurstAnimation {

    private static final int DEFAULT_SHARDS = 14;
    private static final int MIN_SHARDS = 1;
    private static final int MAX_SHARDS = 48;
    private static final int SMOKE_PUFFS = 4;
    private static final Color SMOKE = Color.gray(0.55);

    private final int shards;

    /**
     * Creates the preset with 14 shards.
     */
    public BurstAnimExplosion() {
        this(DEFAULT_SHARDS);
    }

    /**
     * Creates the preset with the given number of shards.
     *
     * @param shards the number of shards, clamped into {@code [1, 48]}
     */
    public BurstAnimExplosion(int shards) {
        this.shards = Math.max(MIN_SHARDS, Math.min(MAX_SHARDS, shards));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = frame.progress();
        if (t >= BurstShapes.END || frame.anchorSize() < BurstShapes.MIN_VISIBLE_SIZE) {
            return;
        }
        gc.save();
        smoke(gc, frame, BurstShapes.segment(t, 0.2, BurstShapes.END));
        flash(gc, frame, BurstShapes.segment(t, 0.0, 0.18));
        shockWave(gc, frame, BurstShapes.segment(t, 0.03, 0.6));
        drawShards(gc, frame, BurstShapes.segment(t, 0.04, BurstShapes.END));
        gc.restore();
    }

    private static void flash(GraphicsContext gc, BurstFrame frame, double local) {
        if (local >= 1.0) {
            return;
        }
        double radius = Math.min(frame.anchorSize() * (0.5 + 0.9 * BurstShapes.easeOutCubic(local)),
                BurstShapes.limit(frame));
        gc.setGlobalAlpha(0.85 * (1.0 - local));
        gc.setFill(Color.WHITE);
        BurstShapes.fillCircle(gc, frame.centerX(), frame.centerY(), radius);
        gc.setGlobalAlpha(0.6 * (1.0 - local));
        gc.setFill(frame.color(1));
        BurstShapes.fillCircle(gc, frame.centerX(), frame.centerY(), radius * 0.7);
    }

    private static void shockWave(GraphicsContext gc, BurstFrame frame, double local) {
        if (local <= 0.0 || local >= 1.0) {
            return;
        }
        double width = frame.anchorSize() * 0.14 * (1.0 - local);
        if (width < BurstShapes.MIN_VISIBLE_SIZE) {
            return;
        }
        double start = BurstShapes.startDistance(frame);
        double radius = start + (frame.radius() - start) * BurstShapes.easeOutCubic(local);
        gc.setGlobalAlpha(1.0 - local);
        gc.setStroke(frame.color(0));
        gc.setLineWidth(width);
        gc.strokeOval(frame.centerX() - radius, frame.centerY() - radius, radius * 2.0, radius * 2.0);
    }

    private void drawShards(GraphicsContext gc, BurstFrame frame, double local) {
        if (local <= 0.0 || local >= 1.0) {
            return;
        }
        double anchor = frame.anchorSize();
        double shrink = 1.0 - BurstShapes.segment(local, 0.55, 1.0);
        double travel = BurstShapes.easeOutCubic(local);
        gc.setGlobalAlpha(1.0);
        for (int i = 0; i < shards; i++) {
            double size = anchor * (0.12 + 0.12 * frame.random(i, 0)) * shrink;
            if (size < BurstShapes.MIN_VISIBLE_SIZE) {
                continue;
            }
            double angle = frame.random(i, 1) * 2.0 * Math.PI;
            double distance = frame.radius() * (0.55 + 0.4 * frame.random(i, 2)) * travel;
            double spin = (frame.random(i, 3) - 0.5) * 4.0 * Math.PI * local;
            gc.setFill(frame.color(i));
            BurstShapes.fillTriangle(gc, frame.centerX() + distance * Math.cos(angle),
                    frame.centerY() + distance * Math.sin(angle), size, spin);
        }
    }

    private static void smoke(GraphicsContext gc, BurstFrame frame, double local) {
        if (local <= 0.0 || local >= 1.0) {
            return;
        }
        double anchor = frame.anchorSize();
        gc.setFill(SMOKE);
        for (int i = 0; i < SMOKE_PUFFS; i++) {
            double angle = BurstShapes.TOP + i * 2.0 * Math.PI / SMOKE_PUFFS + (frame.random(i, 7) - 0.5) * 0.8;
            // Puffs start beside the anchor rather than on it, so the smoke
            // frames the icon.
            double drift = Math.min(anchor * 0.7, frame.radius() * 0.5)
                    + frame.radius() * 0.3 * BurstShapes.easeOutQuad(local);
            double x = frame.centerX() + drift * Math.cos(angle);
            double y = frame.centerY() + drift * Math.sin(angle) - drift * 0.2;
            double radius = BurstShapes.fitRadius(frame, x, y,
                    anchor * (0.25 + 0.4 * BurstShapes.easeOutCubic(local)));
            if (radius < BurstShapes.MIN_VISIBLE_SIZE) {
                continue;
            }
            gc.setGlobalAlpha(0.16 * (1.0 - local));
            BurstShapes.fillCircle(gc, x, y, radius);
        }
    }
}

package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * A custom shape flung out like {@link BurstAnimDots}: give it any SVG path
 * drawn in a 24 x 24 box, such as a star, a note or a logo mark.
 */
public final class BurstAnimShapes implements BurstAnimation {

    private static final int DEFAULT_COUNT = 10;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 32;
    // The path's design box; shapes are scaled from it.
    private static final double PATH_BOX = 24.0;

    private final String svgPath;
    private final int count;

    /**
     * Creates the preset with ten copies of the shape.
     *
     * @param svgPath the shape as SVG path data in a 24 x 24 box; {@code null}
     *                or invalid data draws nothing
     */
    public BurstAnimShapes(String svgPath) {
        this(svgPath, DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of copies of the shape.
     *
     * @param svgPath the shape as SVG path data in a 24 x 24 box; {@code null}
     *                or invalid data draws nothing
     * @param count   the number of copies, clamped into {@code [1, 32]}
     */
    public BurstAnimShapes(String svgPath, int count) {
        this.svgPath = svgPath;
        this.count = Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = frame.progress();
        if (t >= BurstShapes.END || svgPath == null || svgPath.isBlank()) {
            return;
        }
        double anchor = frame.anchorSize();
        double start = BurstShapes.startDistance(frame);
        double travel = BurstShapes.easeOutCubic(t);
        double shrink = 1.0 - BurstShapes.segment(t, 0.6, BurstShapes.END);
        double step = 2.0 * Math.PI / count;
        gc.save();
        for (int i = 0; i < count; i++) {
            double size = anchor * (0.32 + 0.16 * frame.random(i, 0)) * shrink;
            if (size < BurstShapes.MIN_VISIBLE_SIZE) {
                continue;
            }
            double angle = BurstShapes.TOP + i * step + (frame.random(i, 1) - 0.5) * step * 0.4;
            double reach = frame.radius() * (0.85 + 0.15 * frame.random(i, 2));
            double distance = start + (reach - start) * travel;
            double turn = (frame.random(i, 3) - 0.5) * 90.0 * t;
            gc.save();
            gc.translate(frame.centerX() + distance * Math.cos(angle), frame.centerY() + distance * Math.sin(angle));
            gc.rotate(turn);
            gc.scale(size / PATH_BOX, size / PATH_BOX);
            gc.translate(-PATH_BOX / 2.0, -PATH_BOX / 2.0);
            gc.setFill(frame.color(i));
            gc.beginPath();
            gc.appendSVGPath(svgPath);
            gc.fill();
            gc.restore();
        }
        gc.restore();
    }
}

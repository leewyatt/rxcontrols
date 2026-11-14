package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;

/**
 * Radiating strokes: short lines shoot outward from the anchor, stretching
 * first and then collapsing toward their outer ends.
 */
public final class BurstAnimRays implements BurstAnimation {

    private static final int DEFAULT_COUNT = 12;
    private static final int MIN_COUNT = 2;
    private static final int MAX_COUNT = 36;

    private final int count;

    /**
     * Creates the preset with twelve rays.
     */
    public BurstAnimRays() {
        this(DEFAULT_COUNT);
    }

    /**
     * Creates the preset with the given number of rays.
     *
     * @param count the number of rays, clamped into {@code [2, 36]}
     */
    public BurstAnimRays(int count) {
        this.count = Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));
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
        // Round caps keep a dot at each end even as a ray collapses, so the
        // width fades out as well.
        double width = frame.anchorSize() * 0.09 * (1.0 - BurstSupport.segment(t, 0.7, BurstSupport.END));
        if (width < BurstSupport.MIN_VISIBLE_SIZE) {
            return;
        }
        double base = BurstSupport.startDistance(frame);
        double span = frame.reach() - base;
        double outerT = BurstSupport.easeOutCubic(BurstSupport.segment(t, 0.0, 0.65));
        double innerT = BurstSupport.easeInOutQuad(BurstSupport.segment(t, 0.15, BurstSupport.END));
        double step = 2.0 * Math.PI / count;
        gc.save();
        gc.setLineCap(StrokeLineCap.ROUND);
        for (int i = 0; i < count; i++) {
            double scale = i % 2 == 0 ? 1.0 : 0.75;
            double outer = base + span * scale * outerT;
            double inner = base + span * scale * innerT;
            if (outer - inner < BurstSupport.MIN_VISIBLE_SIZE) {
                continue;
            }
            double angle = BurstSupport.TOP + i * step;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            gc.setStroke(frame.color(i));
            gc.setLineWidth(i % 2 == 0 ? width : width * 0.78);
            gc.strokeLine(frame.centerX() + inner * cos, frame.centerY() + inner * sin,
                    frame.centerX() + outer * cos, frame.centerY() + outer * sin);
        }
        gc.restore();
    }
}

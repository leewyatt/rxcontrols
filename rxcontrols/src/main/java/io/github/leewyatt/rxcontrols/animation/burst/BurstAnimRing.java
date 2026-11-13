package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

/**
 * A shock ring: a circle grows from the edge of the anchor to the full reach,
 * thinning and fading as it goes.
 */
public final class BurstAnimRing implements BurstAnimation {

    /**
     * {@inheritDoc}
     */
    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = BurstShapes.segment(frame.progress(), 0.0, BurstShapes.END);
        if (t >= 1.0) {
            return;
        }
        double reach = frame.radius();
        double startRadius = BurstShapes.startDistance(frame);
        double radius = startRadius + (reach - startRadius) * BurstShapes.easeOutCubic(t);
        double width = frame.anchorSize() * 0.2 * (1.0 - t);
        if (width < BurstShapes.MIN_VISIBLE_SIZE) {
            return;
        }
        gc.save();
        gc.setGlobalAlpha(1.0 - BurstShapes.easeInQuad(t));
        gc.setStroke(frame.color(0));
        gc.setLineWidth(width);
        gc.strokeOval(frame.centerX() - radius, frame.centerY() - radius, radius * 2.0, radius * 2.0);
        gc.restore();
    }
}

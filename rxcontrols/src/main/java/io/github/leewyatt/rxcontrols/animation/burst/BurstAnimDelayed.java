package io.github.leewyatt.rxcontrols.animation.burst;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;

/**
 * Plays another burst over the later part of the progress only, so that
 * combined layers can start one after another.
 */
final class BurstAnimDelayed implements BurstAnimation {

    private final BurstAnimation inner;
    private final double start;

    BurstAnimDelayed(BurstAnimation inner, double start) {
        this.inner = inner;
        this.start = RXMath.clamp0To1(start);
    }

    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        double t = frame.progress();
        if (t < start || inner == null) {
            return;
        }
        inner.draw(gc, frame.withProgress(BurstShapes.segment(t, start, BurstShapes.END)));
    }
}

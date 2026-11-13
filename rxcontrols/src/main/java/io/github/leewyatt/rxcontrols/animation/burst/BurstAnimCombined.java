package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws several bursts on top of each other, in order.
 */
final class BurstAnimCombined implements BurstAnimation {

    private final List<BurstAnimation> layers;

    BurstAnimCombined(BurstAnimation... layers) {
        List<BurstAnimation> kept = new ArrayList<>();
        if (layers != null) {
            for (BurstAnimation layer : layers) {
                if (layer != null) {
                    kept.add(layer);
                }
            }
        }
        this.layers = List.copyOf(kept);
    }

    @Override
    public void draw(GraphicsContext gc, BurstFrame frame) {
        for (BurstAnimation layer : layers) {
            // Each layer gets a clean context, so one layer's fill, stroke or
            // alpha never leaks into the next.
            gc.save();
            layer.draw(gc, frame);
            gc.restore();
        }
    }
}

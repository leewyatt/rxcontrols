package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Floors layout values to the device pixel grid, for widths that must fit a fixed
 * budget (stretched tiles, filled columns, equal tab strips).
 */
public final class PixelGrid {

    // In device pixels. A value on the grid can land a little below it after a few
    // subtractions and a division; that error can exceed one ulp, and the floor would
    // then drop a whole pixel.
    private static final double FLOOR_TOLERANCE = 1.0e-9;

    private PixelGrid() {
    }

    /**
     * Floors a value to the grid of the given render scale.
     *
     * @param value the value in layout coordinates
     * @param scale the render scale; zero, negative or non-finite disables snapping
     * @return the largest grid value not above {@code value}, or {@code value} itself
     *         when snapping is disabled
     */
    public static double floor(double value, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return value;
        }
        return Math.floor(value * scale + FLOOR_TOLERANCE) / scale;
    }

    /**
     * Floors a horizontal value to the pixel grid of the region's window.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value the value in layout coordinates
     * @return the floored value, or {@code value} when the region does not snap to pixels
     */
    public static double floorX(Region region, double value) {
        if (!region.isSnapToPixel()) {
            return value;
        }
        Window window = windowOf(region);
        return floor(value, window == null ? 1.0 : safeScale(window.getRenderScaleX()));
    }

    /**
     * Floors a vertical value to the pixel grid of the region's window.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value the value in layout coordinates
     * @return the floored value, or {@code value} when the region does not snap to pixels
     */
    public static double floorY(Region region, double value) {
        if (!region.isSnapToPixel()) {
            return value;
        }
        Window window = windowOf(region);
        return floor(value, window == null ? 1.0 : safeScale(window.getRenderScaleY()));
    }

    private static Window windowOf(Region region) {
        Scene scene = region.getScene();
        return scene == null ? null : scene.getWindow();
    }

    private static double safeScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }
}

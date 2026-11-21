package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Floors layout values to the device pixel grid, for widths that must fit a fixed
 * budget (stretched tiles, filled columns, equal tab strips), and compares sums of grid
 * sizes against such a budget without counting rounding noise as overflow.
 */
public final class PixelGrid {

    // In device pixels. A value on the grid can land a little off it after a few
    // additions, subtractions and a division; that error can exceed one ulp, and a floor
    // would then drop a whole pixel, or a fit test report an overflow that is not there.
    private static final double GRID_TOLERANCE = 1.0e-9;

    private PixelGrid() {
    }

    /**
     * Floors a value to the grid of the given render scale. A value within 1e-9 device
     * pixels below a grid line counts as on it and returns that line, so the result can
     * exceed {@code value} by that much.
     *
     * @param value the value in layout coordinates
     * @param scale the render scale; zero, negative or non-finite disables snapping
     * @return the floored grid value, or {@code value} itself when snapping is disabled
     */
    public static double floor(double value, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return value;
        }
        return Math.floor(value * scale + GRID_TOLERANCE) / scale;
    }

    /**
     * Returns whether a value exceeds a limit by more than rounding noise. A value at most
     * 1e-9 device pixels above the limit counts as reaching it exactly, so a sum of grid
     * sizes that carries floating-point noise (multiples of 0.8 at a 1.25 render scale,
     * say) still fits a limit it exactly reaches.
     *
     * @param value the value in layout coordinates
     * @param limit the limit in layout coordinates
     * @param scale the render scale; zero, negative or non-finite measures at scale 1
     * @return whether {@code value} is above {@code limit} by more than the tolerance
     */
    public static boolean exceeds(double value, double limit, double scale) {
        return (value - limit) * safeScale(scale) > GRID_TOLERANCE;
    }

    /**
     * Returns whether a horizontal value exceeds a limit by more than rounding noise,
     * measured on the pixel grid of the region's window.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid; a
     *               region that does not snap to pixels is measured at scale 1
     * @param value  the value in layout coordinates
     * @param limit  the limit in layout coordinates
     * @return whether {@code value} is above {@code limit} by more than the tolerance
     */
    public static boolean exceedsX(Region region, double value, double limit) {
        return exceeds(value, limit, snapScale(region, true));
    }

    /**
     * Returns whether a vertical value exceeds a limit by more than rounding noise,
     * measured on the pixel grid of the region's window.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid; a
     *               region that does not snap to pixels is measured at scale 1
     * @param value  the value in layout coordinates
     * @param limit  the limit in layout coordinates
     * @return whether {@code value} is above {@code limit} by more than the tolerance
     */
    public static boolean exceedsY(Region region, double value, double limit) {
        return exceeds(value, limit, snapScale(region, false));
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

    private static double snapScale(Region region, boolean horizontal) {
        if (!region.isSnapToPixel()) {
            return 1.0;
        }
        Window window = windowOf(region);
        if (window == null) {
            return 1.0;
        }
        return safeScale(horizontal ? window.getRenderScaleX() : window.getRenderScaleY());
    }

    private static Window windowOf(Region region) {
        Scene scene = region.getScene();
        return scene == null ? null : scene.getWindow();
    }

    private static double safeScale(double scale) {
        return Double.isFinite(scale) && scale > 0.0 ? scale : 1.0;
    }
}

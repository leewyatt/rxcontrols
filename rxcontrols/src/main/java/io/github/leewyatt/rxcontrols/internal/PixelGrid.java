package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Floors layout values to the device pixel grid, for widths that must fit a fixed
 * budget (stretched tiles, filled columns, equal tab strips), and compares sums of grid
 * sizes against such a budget without counting rounding noise as overflow.
 *
 * <p>It also ceils sizes worked out from grid values. JavaFX 17 snaps a size with
 * {@code ceil(value * scale - 1e-14)}, and a sum or difference of grid values can
 * compute a hair above its grid line (a 225 device pixel slot at 1.75 is
 * 128.57142857142858, which times 1.75 is 225.00000000000003), which that ceiling turns
 * into a whole extra device pixel.</p>
 */
public final class PixelGrid {

    // In device pixels. A value on the grid can land a little off it after a few
    // additions, subtractions and a division; that error can exceed one ulp, and a floor
    // would then drop a whole pixel, or a fit test report an overflow that is not there.
    private static final double GRID_TOLERANCE = 1.0e-9;

    // In device pixels: what Region's own snapping subtracts before ceiling a size.
    private static final double REGION_EPSILON = 1.0e-14;

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
     * Ceils a value worked out from grid values (snapped sizes and gaps added up, a
     * snapped area less snapped margins) to the grid of the given render scale, counting
     * a value at most 1e-9 device pixels above a grid line as on it. What margins filling
     * an area leave above zero is such noise, and comes out as zero.
     *
     * @param value the value in layout coordinates
     * @param scale the render scale; zero, negative or non-finite disables snapping
     * @return the ceiled value, or {@code value} itself when snapping is disabled
     */
    public static double ceil(double value, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return value;
        }
        return Math.ceil(value * scale - GRID_TOLERANCE) / scale;
    }

    /**
     * Ceils a size of a node's own (its preferred, minimum or maximum size, which for a
     * container is itself a sum of snapped sizes) as {@link #ceil(double, double)} does,
     * except below one device pixel: there it ceils as Region's snapping does, so a size
     * far below a pixel still takes one.
     *
     * @param value the size in layout coordinates
     * @param scale the render scale; zero, negative or non-finite disables snapping
     * @return the ceiled size, or {@code value} itself when snapping is disabled
     */
    public static double ceilSize(double value, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return value;
        }
        double pixels = Math.ceil(value * scale - GRID_TOLERANCE);
        if (pixels < 1.0 && value * scale > REGION_EPSILON) {
            pixels = 1.0;
        }
        return pixels / scale;
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

    /**
     * Ceils a horizontal value worked out from grid values to the pixel grid of the
     * region's window; see {@link #ceil(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the value in layout coordinates
     * @return the ceiled value, or {@code value} when the region does not snap to pixels
     */
    public static double ceilX(Region region, double value) {
        return ceil(value, snappingScale(region, true));
    }

    /**
     * Ceils a vertical value worked out from grid values to the pixel grid of the
     * region's window; see {@link #ceil(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the value in layout coordinates
     * @return the ceiled value, or {@code value} when the region does not snap to pixels
     */
    public static double ceilY(Region region, double value) {
        return ceil(value, snappingScale(region, false));
    }

    /**
     * Ceils a horizontal size of a node's own to the pixel grid of the region's window;
     * see {@link #ceilSize(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the size in layout coordinates
     * @return the ceiled size, or {@code value} when the region does not snap to pixels
     */
    public static double ceilSizeX(Region region, double value) {
        return ceilSize(value, snappingScale(region, true));
    }

    /**
     * Ceils a vertical size of a node's own to the pixel grid of the region's window;
     * see {@link #ceilSize(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the size in layout coordinates
     * @return the ceiled size, or {@code value} when the region does not snap to pixels
     */
    public static double ceilSizeY(Region region, double value) {
        return ceilSize(value, snappingScale(region, false));
    }

    // The scale the region snaps to, or 0 (no snapping) when it does not snap to pixels.
    private static double snappingScale(Region region, boolean horizontal) {
        return region.isSnapToPixel() ? snapScale(region, horizontal) : 0.0;
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

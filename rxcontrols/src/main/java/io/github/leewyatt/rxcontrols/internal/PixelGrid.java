package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Floors layout values to the device pixel grid, for widths that must fit a fixed
 * budget (stretched tiles, filled columns, equal tab strips), and compares sums of grid
 * sizes against such a budget without counting rounding noise as overflow.
 *
 * <p>It also hands grid values to Region in a form its own snapping keeps. JavaFX 17
 * snaps a size with {@code ceil(value * scale - 1e-14)}, and the double nearest a grid
 * value can multiply back a hair above its line (900 device pixels at 1.75 is
 * 514.2857142857143, and 514.2857142857143 * 1.75 is 900.0000000000001), so snapping
 * such a value again, as a parent does with a preferred size and layoutInArea with the
 * size of a child it fills an area with, adds a whole device pixel.</p>
 */
public final class PixelGrid {

    // In device pixels. A value on the grid can land a little off it after a few
    // additions, subtractions and a division; that error can exceed one ulp, and a floor
    // would then drop a whole pixel, or a fit test report an overflow that is not there.
    private static final double GRID_TOLERANCE = 1.0e-9;

    // Ulps a grid value can need to come down before Region's snapping keeps it on its
    // line; the noise of a few additions and a division takes one or two.
    private static final int MAX_LOWERING_STEPS = 16;

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
     * Ceils a value to the grid of the given render scale, counting a value at most 1e-9
     * device pixels above a grid line as on it, and returns the grid value lowered by a
     * few ulps where needed, so that Region snapping it again keeps it on that line.
     *
     * @param value the value in layout coordinates
     * @param scale the render scale; zero, negative or non-finite disables snapping
     * @return the grid value, or {@code value} itself when snapping is disabled
     */
    public static double ceil(double value, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return value;
        }
        double pixels = Math.ceil(value * scale - GRID_TOLERANCE);
        return lowerOntoLine(pixels / scale, 0.0, 0.0, pixels, scale);
    }

    /**
     * Returns the extent to hand Region.layoutInArea for an area whose content, the
     * extent less the two margins as layoutInArea snaps them, lies on the grid, so that a
     * child filling the area gets exactly that content size. layoutInArea snaps the size
     * it computes, and content computing a hair above its grid line would come out a whole
     * device pixel larger; the extent is lowered by a few ulps until the content computes
     * onto the line. Content off the grid leaves the extent as it is.
     *
     * @param extent the area extent in layout coordinates
     * @param before the snapped margin before the content
     * @param after  the snapped margin after the content
     * @param scale  the render scale; zero, negative or non-finite disables snapping
     * @return the extent, at most a few ulps lower
     */
    public static double fillExtent(double extent, double before, double after, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0) {
            return extent;
        }
        double content = (extent - before - after) * scale;
        double pixels = Math.rint(content);
        if (content - pixels > GRID_TOLERANCE) {
            return extent;
        }
        return lowerOntoLine(extent, before, after, pixels, scale);
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
     * Ceils a horizontal value to the pixel grid of the region's window, in a form the
     * region's own snapping keeps; see {@link #ceil(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the value in layout coordinates
     * @return the grid value, or {@code value} when the region does not snap to pixels
     */
    public static double ceilX(Region region, double value) {
        return ceil(value, snappingScale(region, true));
    }

    /**
     * Ceils a vertical value to the pixel grid of the region's window, in a form the
     * region's own snapping keeps; see {@link #ceil(double, double)}.
     *
     * @param region the region whose {@code snapToPixel} and window decide the grid
     * @param value  the value in layout coordinates
     * @return the grid value, or {@code value} when the region does not snap to pixels
     */
    public static double ceilY(Region region, double value) {
        return ceil(value, snappingScale(region, false));
    }

    /**
     * Returns the width to hand the region's layoutInArea for an area that a child
     * should fill to exactly its content width; see
     * {@link #fillExtent(double, double, double, double)}.
     *
     * @param region the region laying the child out
     * @param width  the area width in layout coordinates
     * @param left   the snapped left margin
     * @param right  the snapped right margin
     * @return the width, at most a few ulps lower
     */
    public static double fillWidth(Region region, double width, double left, double right) {
        return fillExtent(width, left, right, snappingScale(region, true));
    }

    /**
     * Returns the height to hand the region's layoutInArea for an area that a child
     * should fill to exactly its content height; see
     * {@link #fillExtent(double, double, double, double)}.
     *
     * @param region the region laying the child out
     * @param height the area height in layout coordinates
     * @param top    the snapped space layoutInArea takes off above the child
     * @param bottom the snapped space layoutInArea takes off below the child
     * @return the height, at most a few ulps lower
     */
    public static double fillHeight(Region region, double height, double top, double bottom) {
        return fillExtent(height, top, bottom, snappingScale(region, false));
    }

    // Computed as layoutInArea and Region's snapping compute it, left to right, so the
    // check sees the exact double they will ceil.
    private static double lowerOntoLine(double value, double before, double after, double pixels, double scale) {
        double lowered = value;
        for (int i = 0; i < MAX_LOWERING_STEPS && (lowered - before - after) * scale > pixels; i++) {
            lowered = Math.nextDown(lowered);
        }
        return lowered;
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

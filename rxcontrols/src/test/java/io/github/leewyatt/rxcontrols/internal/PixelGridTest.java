package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link PixelGrid}.
 */
public class PixelGridTest {

    private static final double DELTA = 1.0e-12;

    /** Verifies a zero, negative or non-finite scale returns the value unchanged. */
    @Test
    public void invalidScaleDisablesSnapping() {
        for (double scale : new double[] {0.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertEquals(100.3, PixelGrid.floor(100.3, scale), DELTA, "scale " + scale);
        }
    }

    /** Verifies a value off the grid floors to the grid line below it. */
    @Test
    public void offGridValueFloorsDown() {
        assertEquals(100.0, PixelGrid.floor(100.5, 1.5), DELTA);
        assertEquals(100.0, PixelGrid.floor(100.99, 1.0), DELTA);
        assertEquals(100.0, PixelGrid.floor(100.6, 1.25), DELTA);
    }

    /** Verifies a grid value carrying a little negative rounding error keeps its pixel. */
    @Test
    public void gridValueBelowByRoundingErrorKeepsItsPixel() {
        // A few ulps below the grid line at 300 device pixels.
        assertEquals(200.0, PixelGrid.floor(200.0 - 1.0e-13, 1.5), DELTA);
        assertEquals(200.0, PixelGrid.floor(200.0 - 1.0e-12, 1.0), DELTA);
    }

    /** Verifies a value within the tolerance below a grid line returns that line, above the input. */
    @Test
    public void valueJustBelowAGridLineSnapsUpToIt() {
        assertEquals(1.0, PixelGrid.floor(1.0 - 5.0e-10, 1.0), 0.0);
        assertEquals(0.0, PixelGrid.floor(1.0 - 2.0e-9, 1.0), 0.0);
    }

    /** Verifies a region that does not snap to pixels keeps the value. */
    @Test
    public void regionWithoutPixelSnappingKeepsTheValue() {
        Region region = new Region();
        region.setSnapToPixel(false);
        assertEquals(100.5, PixelGrid.floorX(region, 100.5), DELTA);
        assertEquals(100.5, PixelGrid.floorY(region, 100.5), DELTA);
    }

    /** Verifies a region outside a window snaps at scale 1. */
    @Test
    public void regionOutsideAWindowSnapsAtScaleOne() {
        Region region = new Region();
        assertEquals(100.0, PixelGrid.floorX(region, 100.5), DELTA);
        assertEquals(100.0, PixelGrid.floorY(region, 100.5), DELTA);
    }

    /**
     * Verifies a sum of grid sizes that reaches the limit only up to rounding noise does
     * not exceed it, while a value a fraction of a device pixel over the limit does.
     */
    @Test
    public void exactFitWithRoundingNoiseDoesNotExceed() {
        double sum = 20.8 + 20.8 + 20.8;
        assertTrue(sum > 62.4, "the fixture must carry the rounding noise");
        assertFalse(PixelGrid.exceeds(sum, 62.4, 1.25));
        assertTrue(PixelGrid.exceeds(62.4, 62.39, 1.25));
        assertFalse(PixelGrid.exceeds(62.39, 62.4, 1.25));
    }

    /** Verifies the tolerance is 1e-9 device pixels, and an invalid scale measures at scale 1. */
    @Test
    public void exceedsToleranceIsInDevicePixels() {
        for (double scale : new double[] {1.0, 0.0, -1.5, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertFalse(PixelGrid.exceeds(1.0 + 5.0e-10, 1.0, scale), "scale " + scale);
            assertTrue(PixelGrid.exceeds(1.0 + 2.0e-9, 1.0, scale), "scale " + scale);
        }
        assertTrue(PixelGrid.exceeds(1.0 + 5.0e-10, 1.0, 4.0), "5e-10 is 2e-9 device pixels at 4x");
    }

    /**
     * Verifies ceil counts noise above a grid line as on it: a 225 device pixel slot at
     * 1.75 is 128.57142857142858, which Region's own snapping, ceil(v * s - 1e-14), takes
     * to 226. Anything more than 1e-9 device pixels above a line still ceils past it.
     */
    @Test
    public void ceilCountsNoiseAboveAGridLineAsOnIt() {
        double slot = 225.0 / 1.75;
        assertTrue(Math.ceil(slot * 1.75 - 1.0e-14) > 225.0, "the fixture must carry the noise Region ceils up");
        assertEquals(slot, PixelGrid.ceil(slot, 1.75), 0.0, "on its line, it stays");
        assertEquals(161.0 / 1.25, PixelGrid.ceil(128.5, 1.25), 0.0, "an off-grid value ceils");
        assertEquals(129.0, PixelGrid.ceil(128.0 + 1.0e-6, 1.0), 0.0, "more than noise ceils to the next line");
        assertEquals(100.3, PixelGrid.ceil(100.3, 0.0), 0.0, "an invalid scale disables snapping");
        // 6.4 - 4.0 - 2.4 at 1.25: margins filling an 8 device pixel area leave 4.4e-16.
        double leftOver = 8.0 / 1.25 - 5.0 / 1.25 - 3.0 / 1.25;
        assertTrue(leftOver > 0.0, "the fixture must leave noise above zero");
        assertEquals(0.0, PixelGrid.ceil(leftOver, 1.25), 0.0, "noise above zero is zero");
    }

    /**
     * Verifies ceilSize ceils as ceil does, except that a size of its own far below a
     * pixel still takes one, as Region's snapping gives it; at or below Region's own 1e-14
     * device pixel epsilon it is zero, as there.
     */
    @Test
    public void ceilSizeGivesAPositiveSizeAPixel() {
        double slot = 225.0 / 1.75;
        assertEquals(slot, PixelGrid.ceilSize(slot, 1.75), 0.0, "noise above a line counts as on it");
        assertEquals(1.0 / 1.75, PixelGrid.ceilSize(1.0e-10, 1.75), 0.0, "a size far below a pixel takes one");
        assertEquals(0.0, PixelGrid.ceilSize(1.0e-15, 1.0), 0.0, "within Region's epsilon it is zero");
        assertEquals(0.0, PixelGrid.ceilSize(0.0, 1.75), 0.0, "zero stays zero");
        assertEquals(1.0e-10, PixelGrid.ceilSize(1.0e-10, Double.NaN), 0.0, "an invalid scale disables snapping");
    }
}

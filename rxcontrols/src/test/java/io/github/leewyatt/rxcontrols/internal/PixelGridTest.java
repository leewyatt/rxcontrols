package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

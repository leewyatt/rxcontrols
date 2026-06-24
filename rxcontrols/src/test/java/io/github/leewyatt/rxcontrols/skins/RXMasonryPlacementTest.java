package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.skins.RXMasonryPlacement.Geometry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Headless tests for {@link RXMasonryPlacement}: placement geometry, the per-column
 * binary-search visible window, the visible index bounds, hit-testing, multi-column
 * spans and rectangle intersection. Pure arithmetic — no JavaFX toolkit required.
 */
public class RXMasonryPlacementTest {

    private static final double DELTA = 1.0e-9;

    private static RXMasonryPlacement basic() {
        // 3 columns, track 100, hgap 10, vgap 5, startX 0.
        return new RXMasonryPlacement(3, 100.0, 10.0, 5.0, 0.0,
                new double[]{140.0, 220.0, 160.0, 120.0}, new int[]{1, 1, 1, 1});
    }

    @Test
    public void geometryReflectsColumnAssignmentAndTrackWidth() {
        RXMasonryPlacement placement = basic();
        assertEquals(4, placement.itemCount());
        assertEquals(3, placement.columns());
        // i3 lands in the shortest column (col0 at 145) under the vgap of 5.
        assertEquals(265.0, placement.contentHeight(), DELTA);

        assertGeometry(placement.geometryOf(0), 0.0, 0.0, 100.0, 140.0);
        assertGeometry(placement.geometryOf(1), 110.0, 0.0, 100.0, 220.0);
        assertGeometry(placement.geometryOf(2), 220.0, 0.0, 100.0, 160.0);
        assertGeometry(placement.geometryOf(3), 0.0, 145.0, 100.0, 120.0);
        assertNull(placement.geometryOf(4));
        assertNull(placement.geometryOf(-1));
    }

    @Test
    public void startXOffsetsEveryColumn() {
        RXMasonryPlacement placement = new RXMasonryPlacement(2, 100.0, 10.0, 0.0, 30.0,
                new double[]{50.0, 60.0}, new int[]{1, 1});
        assertGeometry(placement.geometryOf(0), 30.0, 0.0, 100.0, 50.0);
        assertGeometry(placement.geometryOf(1), 140.0, 0.0, 100.0, 60.0);
    }

    @Test
    public void visibleItemsBinarySearchesEachColumn() {
        RXMasonryPlacement placement = basic();
        // Whole content visible.
        assertArrayEquals(new int[]{0, 1, 2, 3}, placement.visibleItems(0.0, 300.0));
        // Window [200, 300): only the tall col1 item and the second col0 item.
        assertArrayEquals(new int[]{1, 3}, placement.visibleItems(200.0, 100.0));
        // Window entirely below content.
        assertArrayEquals(new int[]{}, placement.visibleItems(1000.0, 100.0));
        // A thin window [150,155) below item0's bottom (140): the tall col1 (220) and
        // col2 (160) items still span that y, and item3 (col0, starts 145) has begun.
        assertArrayEquals(new int[]{1, 2, 3}, placement.visibleItems(150.0, 5.0));
    }

    @Test
    public void itemAtPointResolvesColumnAndStack() {
        RXMasonryPlacement placement = basic();
        assertEquals(0, placement.itemAtPoint(50.0, 50.0));
        assertEquals(1, placement.itemAtPoint(110.0, 50.0));
        assertEquals(2, placement.itemAtPoint(305.0, 50.0));
        // Below item0 in col0, y lands in item3.
        assertEquals(3, placement.itemAtPoint(50.0, 200.0));
        // In the gap between col0 and col1 (no spanning item) -> nothing.
        assertEquals(-1, placement.itemAtPoint(105.0, 50.0));
        // Below all content / past the last column.
        assertEquals(-1, placement.itemAtPoint(50.0, 300.0));
        assertEquals(-1, placement.itemAtPoint(1000.0, 50.0));
    }

    @Test
    public void spanItemOccupiesEveryColumnItCovers() {
        // item0 spans 2 columns; item1 fills col2; item2 lands in the shortest column.
        RXMasonryPlacement placement = new RXMasonryPlacement(3, 100.0, 10.0, 0.0, 0.0,
                new double[]{50.0, 80.0, 30.0}, new int[]{2, 1, 1});

        assertGeometry(placement.geometryOf(0), 0.0, 0.0, 210.0, 50.0);
        assertGeometry(placement.geometryOf(1), 220.0, 0.0, 100.0, 80.0);
        assertGeometry(placement.geometryOf(2), 0.0, 50.0, 100.0, 30.0);
        assertEquals(80.0, placement.contentHeight(), DELTA);

        // A point in the span item's second column still hits it.
        assertEquals(0, placement.itemAtPoint(150.0, 20.0));
        // The window query reports the span item once, not twice.
        assertArrayEquals(new int[]{0, 1, 2}, placement.visibleItems(0.0, 100.0));
    }

    @Test
    public void itemsIntersectingRectFiltersByBothAxes() {
        RXMasonryPlacement placement = basic();
        // Top-left 50x50 corner only touches item0.
        assertArrayEquals(new int[]{0}, placement.itemsIntersecting(0.0, 0.0, 50.0, 50.0));
        // The whole content rectangle catches everything, distinct and sorted.
        assertArrayEquals(new int[]{0, 1, 2, 3}, placement.itemsIntersecting(0.0, 0.0, 320.0, 300.0));
        // A column-1 strip catches only item1.
        assertArrayEquals(new int[]{1}, placement.itemsIntersecting(140.0, 0.0, 160.0, 300.0));
    }

    // 3 columns, track 100, hgap 10, vgap 0, two tiers of equal-height items so the
    // columns line up: tier 0 = items 0,1,2; tier 1 = items 3,4,5.
    private static RXMasonryPlacement twoTier() {
        return new RXMasonryPlacement(3, 100.0, 10.0, 0.0, 0.0,
                new double[]{100.0, 100.0, 100.0, 50.0, 50.0, 50.0}, new int[]{1, 1, 1, 1, 1, 1});
    }

    @Test
    public void verticalNeighborDownPicksAlignedColumn() {
        RXMasonryPlacement placement = twoTier();
        // NaN reference uses the source center; down from each tier-0 item lands on the
        // tier-1 item in the same column.
        assertEquals(3, placement.verticalNeighbor(0, 1, Double.NaN));
        assertEquals(4, placement.verticalNeighbor(1, 1, Double.NaN));
        assertEquals(5, placement.verticalNeighbor(2, 1, Double.NaN));
    }

    @Test
    public void verticalNeighborUpReturnsAlignedColumnAbove() {
        RXMasonryPlacement placement = twoTier();
        assertEquals(0, placement.verticalNeighbor(3, -1, Double.NaN));
        assertEquals(1, placement.verticalNeighbor(4, -1, Double.NaN));
    }

    @Test
    public void verticalNeighborAtEdgesReturnsMinusOne() {
        RXMasonryPlacement placement = twoTier();
        // Nothing above the top tier, nothing below the bottom tier.
        assertEquals(-1, placement.verticalNeighbor(0, -1, Double.NaN));
        assertEquals(-1, placement.verticalNeighbor(5, 1, Double.NaN));
    }

    @Test
    public void verticalNeighborHonorsPreferredReferenceX() {
        RXMasonryPlacement placement = twoTier();
        // Holding x = 160 (column 1's band) makes down from item 0 land in column 1
        // (item 4) instead of column 0 (item 3) — the anti-drift behavior.
        assertEquals(4, placement.verticalNeighbor(0, 1, 160.0));
        // The source item's own center is exposed for seeding the reference.
        assertEquals(50.0, placement.itemCenterX(0), DELTA);
        assertEquals(160.0, placement.itemCenterX(1), DELTA);
    }

    @Test
    public void emptyPlacementAnswersAllQueriesSafely() {
        RXMasonryPlacement placement = new RXMasonryPlacement(3, 100.0, 10.0, 5.0, 0.0,
                new double[]{}, new int[]{});
        assertEquals(0, placement.itemCount());
        assertEquals(0.0, placement.contentHeight(), DELTA);
        assertArrayEquals(new int[]{}, placement.visibleItems(0.0, 300.0));
        assertEquals(-1, placement.itemAtPoint(10.0, 10.0));
        assertArrayEquals(new int[]{}, placement.itemsIntersecting(0.0, 0.0, 100.0, 100.0));
        assertNull(placement.geometryOf(0));
    }

    private static void assertGeometry(Geometry geometry, double x, double y, double width, double height) {
        assertEquals(x, geometry.x(), DELTA, "x");
        assertEquals(y, geometry.y(), DELTA, "y");
        assertEquals(width, geometry.width(), DELTA, "width");
        assertEquals(height, geometry.height(), DELTA, "height");
    }
}

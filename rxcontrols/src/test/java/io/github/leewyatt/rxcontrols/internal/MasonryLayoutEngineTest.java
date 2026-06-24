package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine.OutlineResult;
import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine.Result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link MasonryLayoutEngine}, locking the shortest-column placement
 * algorithm that both the node-based pane and the future virtualized view rely on.
 */
public class MasonryLayoutEngineTest {

    private static final double DELTA = 1.0e-9;

    /**
     * Verifies a single column stacks items top to bottom with one vgap between
     * each, and that the reported content height excludes the trailing vgap.
     */
    @Test
    public void singleColumnStacksWithVgap() {
        Result result = MasonryLayoutEngine.place(1, 5.0,
                new int[]{1, 1, 1}, new double[]{10.0, 20.0, 30.0});

        assertArrayEquals(new int[]{0, 0, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 15.0, 40.0}, result.tops(), DELTA);
        assertEquals(70.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies the canonical shortest-column result: the first three items fill
     * the three empty columns, and the fourth lands in the currently shortest one.
     */
    @Test
    public void placesEachItemInCurrentShortestColumn() {
        Result result = MasonryLayoutEngine.place(3, 0.0,
                new int[]{1, 1, 1, 1}, new double[]{140.0, 220.0, 160.0, 120.0});

        assertArrayEquals(new int[]{0, 1, 2, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 140.0}, result.tops(), DELTA);
        assertEquals(260.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies that when several columns are equally short, the leftmost one wins,
     * keeping placement deterministic.
     */
    @Test
    public void leftmostColumnWinsTies() {
        Result result = MasonryLayoutEngine.place(3, 0.0,
                new int[]{1, 1, 1, 1}, new double[]{10.0, 10.0, 10.0, 10.0});

        assertArrayEquals(new int[]{0, 1, 2, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 10.0}, result.tops(), DELTA);
        assertEquals(20.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies the content height of a single item equals its own height, never
     * including a phantom trailing vgap.
     */
    @Test
    public void contentHeightExcludesTrailingVgap() {
        Result result = MasonryLayoutEngine.place(1, 8.0,
                new int[]{1}, new double[]{100.0});

        assertEquals(100.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies an empty run produces empty placement arrays and zero height.
     */
    @Test
    public void emptyInputProducesZeroHeight() {
        Result result = MasonryLayoutEngine.place(3, 5.0, new int[]{}, new double[]{});

        assertArrayEquals(new int[]{}, result.startColumns());
        assertArrayEquals(new double[]{}, result.tops(), DELTA);
        assertEquals(0.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies a span greater than the column count is clamped to the full row,
     * raising every column to the item's bottom.
     */
    @Test
    public void spanClampsToColumnCount() {
        Result result = MasonryLayoutEngine.place(3, 0.0,
                new int[]{5, 1}, new double[]{50.0, 30.0});

        assertArrayEquals(new int[]{0, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 50.0}, result.tops(), DELTA);
        assertEquals(80.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies a multi-column span picks the start window with the smallest
     * maximum height, leftmost on ties, and raises all spanned columns.
     */
    @Test
    public void spanChoosesShortestWindowLeftmost() {
        Result result = MasonryLayoutEngine.place(3, 10.0,
                new int[]{1, 2, 1}, new double[]{100.0, 50.0, 80.0});

        assertArrayEquals(new int[]{0, 1, 1}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 0.0, 60.0}, result.tops(), DELTA);
        assertEquals(140.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies a span below one is treated as a single column.
     */
    @Test
    public void nonPositiveSpanTreatedAsOne() {
        Result result = MasonryLayoutEngine.place(3, 0.0,
                new int[]{0, -1}, new double[]{10.0, 20.0});

        assertArrayEquals(new int[]{0, 1}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 0.0}, result.tops(), DELTA);
        assertEquals(20.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies a non-positive column count is treated as a single column rather
     * than crashing on a zero-length array.
     */
    @Test
    public void nonPositiveColumnsTreatedAsOne() {
        Result result = MasonryLayoutEngine.place(0, 5.0,
                new int[]{1, 1}, new double[]{10.0, 20.0});

        assertArrayEquals(new int[]{0, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 15.0}, result.tops(), DELTA);
        assertEquals(35.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies the engine rejects malformed input rather than producing garbage.
     */
    @Test
    public void placeRejectsInvalidInput() {
        assertThrows(NullPointerException.class,
                () -> MasonryLayoutEngine.place(3, 0.0, null, new double[]{1.0}));
        assertThrows(NullPointerException.class,
                () -> MasonryLayoutEngine.place(3, 0.0, new int[]{1}, null));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(3, 0.0, new int[]{1, 1}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(3, Double.NaN, new int[]{1}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(3, 0.0, new int[]{1}, new double[]{Double.NaN}));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(3, 0.0, new int[]{1}, new double[]{-5.0}));
    }

    @Test
    public void placeAcceptsNegativeVgapForOverlap() {
        // A finite negative vgap overlaps items vertically (like HBox/VBox negative
        // spacing): item B sits at 100 + (-20) = 80, content height = 80 + 50 = 130.
        Result result = MasonryLayoutEngine.place(1, -20.0,
                new int[]{1, 1}, new double[]{100.0, 50.0});

        assertArrayEquals(new double[]{0.0, 80.0}, result.tops(), DELTA);
        assertEquals(130.0, result.contentHeight(), DELTA);
    }

    @Test
    public void negativeVgapDoesNotInflateContentHeight() {
        // A single item has no inter-item gap; content height is the item's own
        // height (10), not a phantom |vgap| of 20.
        Result single = MasonryLayoutEngine.place(1, -20.0, new int[]{1}, new double[]{10.0});
        assertEquals(10.0, single.contentHeight(), DELTA, "single item has no phantom gap");

        // Overlap larger than the item height does not inflate the reported height.
        Result pair = MasonryLayoutEngine.place(1, -20.0, new int[]{1, 1}, new double[]{10.0, 10.0});
        assertEquals(10.0, pair.contentHeight(), DELTA, "deep overlap does not inflate height");
    }

    // ==================== Outline overload ====================

    /**
     * Verifies the all-zero start outline reproduces the fixed-column placement and
     * additionally reports the end outline (each column's bottom after the run).
     */
    @Test
    public void emptyOutlineMatchesFixedColumns() {
        OutlineResult result = MasonryLayoutEngine.place(new double[3], 0.0,
                new int[]{1, 1, 1, 1}, new double[]{140.0, 220.0, 160.0, 120.0});

        assertArrayEquals(new int[]{0, 1, 2, 0}, result.startColumns());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 140.0}, result.tops(), DELTA);
        assertArrayEquals(new double[]{260.0, 220.0, 160.0}, result.endOutline(), DELTA);
        assertEquals(260.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies continuing a second batch from the first batch's end outline produces
     * exactly the placement a single full pass would, so incremental == full.
     */
    @Test
    public void outlineContinuationMatchesSinglePass() {
        int[] spans = {1, 1, 1, 1, 1};
        double[] heights = {100.0, 50.0, 80.0, 40.0, 30.0};

        OutlineResult full = MasonryLayoutEngine.place(new double[3], 10.0, spans, heights);

        OutlineResult batch1 = MasonryLayoutEngine.place(new double[3], 10.0,
                new int[]{1, 1, 1}, new double[]{100.0, 50.0, 80.0});
        OutlineResult batch2 = MasonryLayoutEngine.place(batch1.endOutline(), 10.0,
                new int[]{1, 1}, new double[]{40.0, 30.0});

        // The continued batch's geometry equals the tail of the single full pass.
        assertArrayEquals(new int[]{full.startColumns()[3], full.startColumns()[4]},
                batch2.startColumns());
        assertArrayEquals(new double[]{full.tops()[3], full.tops()[4]}, batch2.tops(), DELTA);
        // The continued end outline equals the full end outline.
        assertArrayEquals(full.endOutline(), batch2.endOutline(), DELTA);
        // Per-batch content heights combine with max to the full content height.
        assertEquals(full.contentHeight(),
                Math.max(batch1.contentHeight(), batch2.contentHeight()), DELTA);
    }

    /**
     * Verifies a span continued from a non-trivial outline still picks the shortest
     * spanning window and raises every spanned column.
     */
    @Test
    public void spanContinuesFromStartOutline() {
        // Columns currently sit at [60, 0, 0]; a span-2 item of height 40 prefers the
        // window {col1,col2} (max bottom 0) over {col0,col1} (max bottom 60).
        OutlineResult result = MasonryLayoutEngine.place(new double[]{60.0, 0.0, 0.0}, 0.0,
                new int[]{2}, new double[]{40.0});

        assertArrayEquals(new int[]{1}, result.startColumns());
        assertArrayEquals(new double[]{0.0}, result.tops(), DELTA);
        assertArrayEquals(new double[]{60.0, 40.0, 40.0}, result.endOutline(), DELTA);
        assertEquals(40.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies the outline overload never mutates the caller's start outline.
     */
    @Test
    public void outlineOverloadDoesNotMutateStartOutline() {
        double[] startOutline = {10.0, 20.0, 30.0};
        MasonryLayoutEngine.place(startOutline, 5.0, new int[]{1}, new double[]{50.0});

        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, startOutline, DELTA);
    }

    /**
     * Verifies the end outline carries the trailing vgap below each column's last
     * item, while contentHeight excludes it.
     */
    @Test
    public void endOutlineCarriesTrailingVgapButContentHeightExcludesIt() {
        OutlineResult result = MasonryLayoutEngine.place(new double[3], 10.0,
                new int[]{1, 1, 1, 1}, new double[]{140.0, 220.0, 160.0, 120.0});

        // col0: 140 + vgap, then +120 +vgap -> 280; col1: 220 +vgap; col2: 160 +vgap.
        assertArrayEquals(new double[]{280.0, 230.0, 170.0}, result.endOutline(), DELTA);
        // Tallest item bottom is col0's second item at 150 + 120 = 270, no trailing vgap.
        assertEquals(270.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies an empty run returns the start outline unchanged with zero height.
     */
    @Test
    public void emptyRunReturnsStartOutlineUnchanged() {
        OutlineResult result = MasonryLayoutEngine.place(new double[]{10.0, 20.0, 30.0}, 5.0,
                new int[]{}, new double[]{});

        assertEquals(0, result.startColumns().length);
        assertArrayEquals(new double[]{10.0, 20.0, 30.0}, result.endOutline(), DELTA);
        assertEquals(0.0, result.contentHeight(), DELTA);
    }

    /**
     * Verifies multi-batch continuation matches a single full pass even when items
     * span multiple columns, so spans do not break the increment == full property.
     */
    @Test
    public void outlineContinuationWithSpansMatchesSinglePass() {
        int[] spans = {1, 2, 1, 2, 1};
        double[] heights = {100.0, 50.0, 80.0, 40.0, 30.0};

        OutlineResult full = MasonryLayoutEngine.place(new double[3], 10.0, spans, heights);

        OutlineResult batch1 = MasonryLayoutEngine.place(new double[3], 10.0,
                new int[]{1, 2}, new double[]{100.0, 50.0});
        OutlineResult batch2 = MasonryLayoutEngine.place(batch1.endOutline(), 10.0,
                new int[]{1, 2, 1}, new double[]{80.0, 40.0, 30.0});

        assertArrayEquals(
                new int[]{full.startColumns()[2], full.startColumns()[3], full.startColumns()[4]},
                batch2.startColumns());
        assertArrayEquals(
                new double[]{full.tops()[2], full.tops()[3], full.tops()[4]}, batch2.tops(), DELTA);
        assertArrayEquals(full.endOutline(), batch2.endOutline(), DELTA);
        assertEquals(full.contentHeight(),
                Math.max(batch1.contentHeight(), batch2.contentHeight()), DELTA);
    }

    /**
     * Verifies the outline overload rejects an empty outline and non-finite outline
     * values rather than producing garbage.
     */
    @Test
    public void outlineOverloadRejectsInvalidInput() {
        assertThrows(NullPointerException.class,
                () -> MasonryLayoutEngine.place((double[]) null, 0.0, new int[]{1}, new double[]{1.0}));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(new double[0], 0.0, new int[]{}, new double[]{}));
        assertThrows(IllegalArgumentException.class,
                () -> MasonryLayoutEngine.place(new double[]{Double.NaN}, 0.0, new int[]{1}, new double[]{1.0}));
    }
}

package io.github.leewyatt.rxcontrols.internal;

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
}

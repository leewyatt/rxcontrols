package io.github.leewyatt.rxcontrols.skins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MasonryHeightCache}: the estimated-path height state — record /
 * idempotence, index-shift alignment through list mutations, geometry invalidation,
 * and committed-column round-trip. Pure arithmetic, no JavaFX toolkit.
 */
public class MasonryHeightCacheTest {

    private static final double EPSILON = 0.5;
    private static final double EST = 100.0;

    private static MasonryHeightCache cacheOf(int count) {
        MasonryHeightCache cache = new MasonryHeightCache();
        cache.ensureCapacity(count, EST);
        return cache;
    }

    @Test
    public void recordReturnsChangedAndIsIdempotent() {
        MasonryHeightCache cache = cacheOf(5);
        assertEquals(EST, cache.heightAt(2, EST), EPSILON, "unmeasured returns the estimate");

        assertTrue(cache.record(2, 150.0, EPSILON), "first measurement changes the cache");
        assertEquals(150.0, cache.heightAt(2, EST), EPSILON);
        assertFalse(cache.record(2, 150.0, EPSILON), "re-measuring the same value is a no-op");
        assertTrue(cache.record(2, 220.0, EPSILON), "a different value changes again");
        assertEquals(1, cache.measuredCount());
    }

    @Test
    public void shiftInsertKeepsMeasuredAlignedWithItems() {
        MasonryHeightCache cache = cacheOf(5);
        cache.record(3, 200.0, EPSILON);

        // Insert 2 items at index 1: item previously at index 3 moves to index 5.
        cache.shift(1, 0, 2, EST);
        assertEquals(7, cache.size());
        assertEquals(200.0, cache.heightAt(5, EST), EPSILON, "the measured item followed its shift");
        assertEquals(EST, cache.heightAt(1, EST), EPSILON, "inserted slots are fresh estimates");
        assertEquals(EST, cache.heightAt(3, EST), EPSILON, "the old index 3 slot is now a different item");
    }

    @Test
    public void shiftRemoveKeepsMeasuredAlignedWithItems() {
        MasonryHeightCache cache = cacheOf(5);
        cache.record(4, 200.0, EPSILON);

        // Remove 2 items at index 1: item previously at index 4 moves to index 2.
        cache.shift(1, 2, 0, EST);
        assertEquals(3, cache.size());
        assertEquals(200.0, cache.heightAt(2, EST), EPSILON);
    }

    @Test
    public void columnsChangeDropsMeasured() {
        MasonryHeightCache cache = cacheOf(4);
        cache.onColumnsChanged(3);
        cache.record(1, 180.0, EPSILON);
        assertEquals(180.0, cache.heightAt(1, EST), EPSILON);

        // Same column count (e.g. a scroll-bar toggle changed only the track width): the
        // measurement survives, so the layout cannot oscillate.
        assertFalse(cache.onColumnsChanged(3));
        assertEquals(180.0, cache.heightAt(1, EST), EPSILON);

        // Column count change: drop every measured height (re-measure at the new width).
        assertTrue(cache.onColumnsChanged(2));
        assertEquals(EST, cache.heightAt(1, EST), EPSILON, "measured height discarded at the new column count");
    }

    @Test
    public void clearResetsToEstimates() {
        MasonryHeightCache cache = cacheOf(5);
        cache.record(2, 150.0, EPSILON);

        cache.clear();
        assertEquals(0, cache.size());
        cache.ensureCapacity(3, EST);
        assertEquals(EST, cache.heightAt(0, EST), EPSILON, "cleared then re-grown is all estimates");
    }
}

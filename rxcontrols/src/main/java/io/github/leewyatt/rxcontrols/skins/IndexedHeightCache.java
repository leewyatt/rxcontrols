package io.github.leewyatt.rxcontrols.skins;

import java.util.Arrays;

/**
 * Persistent per-item height state for an index-based virtualizing viewport on its
 * estimated (measure-on-scroll) path. It survives layout passes — the immutable
 * geometry plan is rebuilt each pass but reads its heights from here — so a cell's
 * real height, once measured, is not lost on the next re-fill.
 *
 * <p>Indexed by item position, it shifts and invalidates with the items list the same
 * way the index-based selection / focus models do: a list mutation splices the arrays
 * at the mutation point so each slot keeps describing the same logical item. A measured
 * height is content-width dependent (a wrapping cell reflows), so a structural width
 * change can discard every measured height via {@link #invalidateAllMeasured()} and let
 * the cells re-measure at the new width (the masonry viewport uses this on a column-count
 * change; the single-column list never needs it).</p>
 *
 * <p>Pure data and arithmetic, no JavaFX, so it is headless-testable in isolation. Shared
 * by the variable-height list viewport and the masonry viewport's estimated-height path.</p>
 */
final class IndexedHeightCache {

    private double[] heights = new double[0];
    private boolean[] measured = new boolean[0];
    private int size;

    /**
     * Resets the cache entirely — every index meaning is gone (an items-list swap).
     */
    void clear() {
        size = 0;
        heights = new double[0];
        measured = new boolean[0];
    }

    /**
     * Reconciles the array length with the live item count, seeding any new tail slots
     * as unmeasured estimates. A coarse length guard; the precise per-mutation alignment
     * is done by {@link #shift(int, int, int, double)}.
     *
     * @param itemCount the live item count
     * @param estimated the estimated height for unmeasured slots
     */
    void ensureCapacity(int itemCount, double estimated) {
        if (itemCount == size) {
            return;
        }
        int overlap = Math.min(size, itemCount);
        double[] newHeights = new double[itemCount];
        boolean[] newMeasured = new boolean[itemCount];
        System.arraycopy(heights, 0, newHeights, 0, overlap);
        System.arraycopy(measured, 0, newMeasured, 0, overlap);
        for (int i = overlap; i < itemCount; i++) {
            newHeights[i] = estimated;
        }
        heights = newHeights;
        measured = newMeasured;
        size = itemCount;
    }

    /**
     * Splices the cache for a list add/remove at {@code from}: removed entries are
     * dropped, added entries are inserted as unmeasured estimates, and the tail shifts
     * to stay aligned with its items.
     *
     * @param from      the mutation index
     * @param removed   the number of removed items
     * @param added     the number of added items
     * @param estimated the estimated height for new slots
     */
    void shift(int from, int removed, int added, double estimated) {
        if (from < 0 || removed < 0 || added < 0 || from > size) {
            return;
        }
        int clampedRemoved = Math.min(removed, size - from);
        int newSize = size - clampedRemoved + added;
        double[] newHeights = new double[newSize];
        boolean[] newMeasured = new boolean[newSize];
        System.arraycopy(heights, 0, newHeights, 0, from);
        System.arraycopy(measured, 0, newMeasured, 0, from);
        for (int i = from; i < from + added; i++) {
            newHeights[i] = estimated;
        }
        int tailFrom = from + clampedRemoved;
        int tailTo = from + added;
        int tailLength = size - tailFrom;
        System.arraycopy(heights, tailFrom, newHeights, tailTo, tailLength);
        System.arraycopy(measured, tailFrom, newMeasured, tailTo, tailLength);
        heights = newHeights;
        measured = newMeasured;
        size = newSize;
    }

    /**
     * Invalidates a range whose items changed (an in-place update or a permutation): the
     * stored height may no longer describe the slot, so re-estimate and re-measure.
     *
     * @param from      the first changed index
     * @param to        the exclusive end
     * @param estimated the estimated height for re-seeded slots
     */
    void invalidateRange(int from, int to, double estimated) {
        int lo = Math.max(0, from);
        int hi = Math.min(size, to);
        for (int i = lo; i < hi; i++) {
            heights[i] = estimated;
            measured[i] = false;
        }
    }

    /**
     * Drops every measured height (a structural width reflow, e.g. a masonry column-count
     * change): the cells re-measure at the new width as they reconcile. The estimates stay
     * in place so the scroll bar keeps a stable total until the visible cells report their
     * new heights.
     */
    void invalidateAllMeasured() {
        Arrays.fill(measured, 0, size, false);
    }

    /**
     * Records a measured height for an item. A no-op (returning {@code false}) when the
     * item is already measured to the same value within {@code epsilon}, which stops a
     * re-measure feedback loop in steady state.
     *
     * @param index          the item index
     * @param measuredHeight the measured pref height
     * @param epsilon        the no-change tolerance
     * @return {@code true} when the stored height actually changed
     */
    boolean record(int index, double measuredHeight, double epsilon) {
        if (index < 0 || index >= size) {
            return false;
        }
        if (measured[index] && Math.abs(heights[index] - measuredHeight) <= epsilon) {
            return false;
        }
        heights[index] = measuredHeight;
        measured[index] = true;
        return true;
    }

    /**
     * The stored height for an item: its measured value when known, otherwise the
     * supplied estimate. Out-of-range indices return the estimate.
     *
     * @param index     the item index
     * @param estimated the fallback for an unmeasured / out-of-range slot
     * @return the effective height
     */
    double heightAt(int index, double estimated) {
        if (index < 0 || index >= size) {
            return estimated;
        }
        return measured[index] ? heights[index] : estimated;
    }

    int size() {
        return size;
    }

    int measuredCount() {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (measured[i]) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "IndexedHeightCache{size=" + size + ", measured=" + measuredCount() + "}";
    }
}

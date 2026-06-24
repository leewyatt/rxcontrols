package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine;
import io.github.leewyatt.rxcontrols.internal.MasonryLayoutEngine.OutlineResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable geometry oracle for {@link RXMasonryViewport}: it runs the shortest
 * column placement once for a given column count, track width and per-item height,
 * then answers the spatial queries the viewport needs — each item's rectangle, the
 * items intersecting a vertical window or an arbitrary rectangle, the item under a
 * point, and the total content height. It is the masonry analog of
 * {@code RXTileRowPlan}, but where that uses closed-form row arithmetic this stores
 * the placement and answers visibility by a per-column binary search.
 *
 * <p>Within any one column the items stack top to bottom in source order, so their
 * tops and bottoms ascend and a window query is {@code O(columns * (log n + hits))}.
 * The visible-window and hit queries rely on that monotonic stacking, so callers must
 * pass a non-negative vertical gap (the masonry view clamps it to zero); a negative
 * gap would let the per-column binary search drop genuinely visible items.</p>
 *
 * <p>A multi-column item belongs to each column it spans, so window and rectangle
 * queries de-duplicate before returning a sorted, distinct index array.</p>
 */
final class RXMasonryPlacement {

    /**
     * An item's rectangle in content coordinates (before the scroll offset is folded
     * in).
     *
     * @param x      the left edge
     * @param y      the top edge
     * @param width  the slot width (spanning its columns)
     * @param height the slot height
     */
    record Geometry(double x, double y, double width, double height) {
    }

    private final int columns;
    private final double trackWidth;
    private final double hgap;
    private final double startX;
    private final int[] startColumns;
    private final double[] tops;
    private final double[] heights;
    private final int[] spans;
    private final double contentHeight;

    // Per-column parallel arrays in ascending-top order for binary search. A spanning
    // item appears in each column it covers.
    private final int[][] columnItems;
    private final double[][] columnTops;
    private final double[][] columnBottoms;

    /**
     * Builds the placement.
     *
     * @param columns    the number of columns, at least one
     * @param trackWidth the width of a single column track
     * @param hgap       the horizontal gap between columns
     * @param vgap       the vertical gap between stacked items
     * @param startX     the content-space x offset of the first column (alignment)
     * @param heights    the effective height per item; must be finite and non-negative
     * @param spans      the column span per item
     */
    RXMasonryPlacement(int columns, double trackWidth, double hgap, double vgap, double startX,
                       double[] heights, int[] spans) {
        this.columns = Math.max(1, columns);
        this.trackWidth = trackWidth;
        this.hgap = hgap;
        this.startX = startX;
        this.heights = heights.clone();
        this.spans = spans.clone();

        OutlineResult result = MasonryLayoutEngine.place(new double[this.columns], vgap, spans, heights);
        this.startColumns = result.startColumns();
        this.tops = result.tops();
        this.contentHeight = result.contentHeight();

        this.columnItems = new int[this.columns][];
        this.columnTops = new double[this.columns][];
        this.columnBottoms = new double[this.columns][];
        buildColumnIndex();
    }

    private void buildColumnIndex() {
        int[] counts = new int[columns];
        for (int i = 0; i < startColumns.length; i++) {
            int span = clampSpan(spans[i]);
            for (int c = startColumns[i]; c < startColumns[i] + span; c++) {
                counts[c]++;
            }
        }
        for (int c = 0; c < columns; c++) {
            columnItems[c] = new int[counts[c]];
            columnTops[c] = new double[counts[c]];
            columnBottoms[c] = new double[counts[c]];
        }
        int[] cursor = new int[columns];
        for (int i = 0; i < startColumns.length; i++) {
            int span = clampSpan(spans[i]);
            double top = tops[i];
            double bottom = top + heights[i];
            for (int c = startColumns[i]; c < startColumns[i] + span; c++) {
                int k = cursor[c]++;
                columnItems[c][k] = i;
                columnTops[c][k] = top;
                columnBottoms[c][k] = bottom;
            }
        }
    }

    private int clampSpan(int span) {
        if (span < 1) {
            return 1;
        }
        return Math.min(span, columns);
    }

    // ==================== Queries ====================

    int itemCount() {
        return startColumns.length;
    }

    int columns() {
        return columns;
    }

    double contentHeight() {
        return contentHeight;
    }

    /**
     * Returns the content-space rectangle of the item at {@code index}, or
     * {@code null} when the index is out of range.
     *
     * @param index the item index
     * @return the geometry, or {@code null}
     */
    Geometry geometryOf(int index) {
        if (index < 0 || index >= startColumns.length) {
            return null;
        }
        int span = clampSpan(spans[index]);
        double x = startX + startColumns[index] * (trackWidth + hgap);
        double width = span * trackWidth + (span - 1) * hgap;
        return new Geometry(x, tops[index], width, heights[index]);
    }

    int startColumnOf(int index) {
        return index >= 0 && index < startColumns.length ? startColumns[index] : -1;
    }

    int spanOf(int index) {
        return index >= 0 && index < spans.length ? clampSpan(spans[index]) : 1;
    }

    /**
     * Returns the item indices intersecting the vertical window
     * {@code [scrollY, scrollY + viewportHeight]}, sorted ascending and distinct.
     *
     * @param scrollY        the window top in content coordinates
     * @param viewportHeight the window height
     * @return the visible item indices
     */
    int[] visibleItems(double scrollY, double viewportHeight) {
        double top = scrollY;
        double bottom = scrollY + viewportHeight;
        if (startColumns.length == 0 || viewportHeight <= 0.0 || bottom <= 0.0 || top >= contentHeight) {
            return new int[0];
        }
        // De-duplicate spanning items via a marker; collect in ascending index order.
        boolean[] seen = new boolean[startColumns.length];
        List<Integer> result = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            int lo = firstIndexGreater(columnBottoms[c], top);
            int hi = firstIndexAtLeast(columnTops[c], bottom);
            for (int k = lo; k < hi; k++) {
                int item = columnItems[c][k];
                if (!seen[item]) {
                    seen[item] = true;
                    result.add(item);
                }
            }
        }
        return sortedToArray(result);
    }

    /**
     * Returns the item whose rectangle contains the content-space point, or
     * {@code -1} when none does.
     *
     * @param x the content-space x
     * @param y the content-space y
     * @return the item index, or {@code -1}
     */
    int itemAtPoint(double x, double y) {
        if (startColumns.length == 0 || y < 0.0 || y >= contentHeight) {
            return -1;
        }
        double step = trackWidth + hgap;
        if (step <= 0.0 || x < startX) {
            return -1;
        }
        int column = (int) Math.floor((x - startX) / step);
        if (column < 0 || column >= columns) {
            return -1;
        }
        int k = lastIndexAtMost(columnTops[column], y);
        if (k < 0 || y >= columnBottoms[column][k]) {
            return -1;
        }
        int item = columnItems[column][k];
        Geometry geometry = geometryOf(item);
        if (geometry == null) {
            return -1;
        }
        boolean insideX = x >= geometry.x() && x <= geometry.x() + geometry.width();
        return insideX ? item : -1;
    }

    /**
     * Returns the item indices whose rectangles intersect the content-space rectangle,
     * sorted ascending and distinct.
     *
     * @param minX the rectangle's left edge
     * @param minY the rectangle's top edge
     * @param maxX the rectangle's right edge
     * @param maxY the rectangle's bottom edge
     * @return the intersecting item indices
     */
    int[] itemsIntersecting(double minX, double minY, double maxX, double maxY) {
        if (startColumns.length == 0 || maxY <= 0.0 || minY >= contentHeight || maxX < startX) {
            return new int[0];
        }
        boolean[] seen = new boolean[startColumns.length];
        List<Integer> result = new ArrayList<>();
        for (int c = 0; c < columns; c++) {
            int lo = firstIndexGreater(columnBottoms[c], minY);
            int hi = firstIndexAtLeast(columnTops[c], maxY);
            for (int k = lo; k < hi; k++) {
                int item = columnItems[c][k];
                if (seen[item]) {
                    continue;
                }
                Geometry geometry = geometryOf(item);
                if (geometry != null && geometry.x() <= maxX && geometry.x() + geometry.width() >= minX) {
                    seen[item] = true;
                    result.add(item);
                }
            }
        }
        return sortedToArray(result);
    }

    // ==================== Binary search ====================

    // First index i with arr[i] > value, or arr.length when none (arr is ascending).
    private static int firstIndexGreater(double[] arr, double value) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] > value) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    // First index i with arr[i] >= value, or arr.length when none (arr is ascending).
    private static int firstIndexAtLeast(double[] arr, double value) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] >= value) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    // Largest index i with arr[i] <= value, or -1 when none (arr is ascending).
    private static int lastIndexAtMost(double[] arr, double value) {
        int lo = 0;
        int hi = arr.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (arr[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo - 1;
    }

    private static int[] sortedToArray(List<Integer> values) {
        int[] array = new int[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        // Columns are visited left to right and items appended in column order, so the
        // combined list is not globally sorted; sort to honor the ascending contract.
        Arrays.sort(array);
        return array;
    }
}

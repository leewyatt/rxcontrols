package io.github.leewyatt.rxcontrols.internal;

import java.util.Objects;

/**
 * Stateless shortest-column placement engine for masonry / waterfall layouts.
 *
 * <p>Given a fixed number of equal-width columns and the vertical extent each
 * item occupies, {@link #place(int, double, int[], double[])} assigns every item
 * to the currently shortest column (the leftmost column wins ties) in source
 * order. Source order is preserved so the caller's scene-graph, focus traversal
 * and accessibility order stay intact.</p>
 *
 * <p>The {@link #place(double[], double, int[], double[])} overload starts from an
 * explicit per-column outline (each column's current bottom) instead of empty
 * columns, and additionally reports the resulting end outline. This lets a caller
 * continue a prior run in {@code O(batch)} by feeding the previous end outline as
 * the next start outline, rather than re-folding every item from index zero. The
 * fixed-column overload is the all-zero-outline special case.</p>
 *
 * <p>The engine is pure arithmetic with no JavaFX dependency, so it can be unit
 * tested directly and shared by both the node-based pane and a future virtualized
 * view.</p>
 */
public final class MasonryLayoutEngine {

    private static final double EPSILON = 1.0e-6;

    /**
     * Placement result for a run of items, indexed by source order.
     *
     * @param startColumns  the chosen leftmost column index per item
     * @param tops          the top offset within the content box per item
     * @param contentHeight the total stacked content height
     */
    public record Result(int[] startColumns, double[] tops, double contentHeight) {
    }

    /**
     * Placement result for a run continued from a start outline.
     *
     * @param startColumns  the chosen leftmost column index per item
     * @param tops          the top offset within the content box per item
     * @param endOutline    each column's bottom after this run, ready to seed a
     *                      following run as its start outline
     * @param contentHeight the tallest item bottom produced by this run (excluding
     *                      any trailing vgap), or {@code 0} when no items were
     *                      placed; a multi-batch caller combines per-batch values
     *                      with {@code max}
     */
    public record OutlineResult(int[] startColumns, double[] tops, double[] endOutline,
                                double contentHeight) {
    }

    private MasonryLayoutEngine() {
    }

    /**
     * Places items into columns using the stable shortest-column strategy.
     *
     * @param columns      the number of columns; values below one are treated as one
     * @param vgap         the vertical gap inserted below each placed item; may be negative to overlap
     * @param spans        the column span per item, each clamped to {@code [1, columns]}
     * @param blockHeights the vertical extent per item, including its margins
     * @return the placement result
     * @throws NullPointerException     if {@code spans} or {@code blockHeights} is {@code null}
     * @throws IllegalArgumentException if the arrays differ in length, {@code vgap} is not
     *                                  finite, or any block height is not finite and
     *                                  non-negative
     */
    public static Result place(int columns, double vgap, int[] spans, double[] blockHeights) {
        OutlineResult result = place(new double[Math.max(1, columns)], vgap, spans, blockHeights);
        return new Result(result.startColumns(), result.tops(), result.contentHeight());
    }

    /**
     * Places items into columns using the stable shortest-column strategy, starting
     * from an explicit per-column outline.
     *
     * @param startOutline each column's current bottom; its length is the column
     *                     count, and an all-zero outline reproduces empty columns
     * @param vgap         the vertical gap inserted below each placed item; may be negative to overlap
     * @param spans        the column span per item, each clamped to {@code [1, columns]}
     * @param blockHeights the vertical extent per item, including its margins
     * @return the placement result, including the end outline for continuation
     * @throws NullPointerException     if {@code startOutline}, {@code spans} or
     *                                  {@code blockHeights} is {@code null}
     * @throws IllegalArgumentException if {@code startOutline} is empty, {@code spans}
     *                                  and {@code blockHeights} differ in length,
     *                                  {@code vgap} or any outline value is not finite,
     *                                  or any block height is not finite and non-negative
     */
    public static OutlineResult place(double[] startOutline, double vgap, int[] spans, double[] blockHeights) {
        return place(startOutline, vgap, spans, blockHeights, null);
    }

    /**
     * Places items into columns starting from an explicit outline, honoring already
     * committed column assignments (commit-once). For each item, a committed column
     * ({@code >= 0}) is used as its start column without re-scanning; an uncommitted
     * entry ({@code -1}) falls back to the stable shortest-column choice. A
     * {@code null} {@code committedColumns} means every item is chosen by the
     * shortest-column strategy (identical to {@link #place(double[], double, int[], double[])}).
     *
     * <p>This is the engine half of the estimated path's measure-time re-pack: passing
     * the previously resolved columns back as {@code committedColumns} re-derives every
     * item's top from new heights while never re-routing a column.</p>
     *
     * @param startOutline    each column's current bottom; its length is the column count
     * @param vgap            the vertical gap inserted below each placed item; may be negative
     * @param spans           the column span per item, each clamped to {@code [1, columns]}
     * @param blockHeights    the vertical extent per item
     * @param committedColumns the committed start column per item ({@code -1} = choose by
     *                        shortest column), or {@code null} to choose every column
     * @return the placement result, whose {@code startColumns} echo the resolved columns
     * @throws NullPointerException     if {@code startOutline}, {@code spans} or
     *                                  {@code blockHeights} is {@code null}
     * @throws IllegalArgumentException if {@code startOutline} is empty, the array lengths
     *                                  disagree, {@code vgap} or any outline value is not
     *                                  finite, any block height is not finite and non-negative,
     *                                  or a committed column is out of {@code [-1, columns)}
     */
    public static OutlineResult place(double[] startOutline, double vgap, int[] spans, double[] blockHeights,
                                      int[] committedColumns) {
        Objects.requireNonNull(startOutline, "startOutline cannot be null");
        Objects.requireNonNull(spans, "spans cannot be null");
        Objects.requireNonNull(blockHeights, "blockHeights cannot be null");
        if (startOutline.length < 1) {
            throw new IllegalArgumentException("startOutline must have at least one column");
        }
        if (spans.length != blockHeights.length) {
            throw new IllegalArgumentException("spans and blockHeights must have the same length");
        }
        if (committedColumns != null && committedColumns.length != blockHeights.length) {
            throw new IllegalArgumentException("committedColumns must match the item count");
        }
        if (!Double.isFinite(vgap)) {
            throw new IllegalArgumentException("vgap must be finite");
        }
        for (double bottom : startOutline) {
            if (!Double.isFinite(bottom)) {
                throw new IllegalArgumentException("start outline values must be finite");
            }
        }
        for (double blockHeight : blockHeights) {
            if (!Double.isFinite(blockHeight) || blockHeight < 0.0) {
                throw new IllegalArgumentException("block heights must be finite and non-negative");
            }
        }
        int columnCount = startOutline.length;
        if (committedColumns != null) {
            for (int committed : committedColumns) {
                if (committed < -1 || committed >= columnCount) {
                    throw new IllegalArgumentException("committed columns must be in [-1, columns)");
                }
            }
        }
        int itemCount = blockHeights.length;
        int[] startColumns = new int[itemCount];
        double[] tops = new double[itemCount];
        // Copy so the caller's start outline is never mutated; this becomes the end outline.
        double[] columnBottoms = startOutline.clone();
        double maxItemBottom = 0.0;

        for (int i = 0; i < itemCount; i++) {
            int span = clampSpan(spans[i], columnCount);
            int committed = committedColumns == null ? -1 : committedColumns[i];
            int start;
            double top;
            if (committed >= 0) {
                // Commit-once: keep the committed column; clamp the start leftward only if a
                // narrower column count from an earlier pass would push the span out of range.
                start = Math.min(committed, columnCount - span);
                top = spanTop(columnBottoms, start, span);
            } else {
                int lastStart = columnCount - span;
                start = 0;
                top = Double.POSITIVE_INFINITY;
                for (int candidate = 0; candidate <= lastStart; candidate++) {
                    double candidateTop = spanTop(columnBottoms, candidate, span);
                    if (candidateTop < top - EPSILON) {
                        top = candidateTop;
                        start = candidate;
                    }
                }
            }
            startColumns[i] = start;
            tops[i] = top;
            double itemBottom = top + blockHeights[i];
            if (itemBottom > maxItemBottom) {
                maxItemBottom = itemBottom;
            }
            double newBottom = itemBottom + vgap;
            for (int c = start; c < start + span; c++) {
                columnBottoms[c] = newBottom;
            }
        }

        return new OutlineResult(startColumns, tops, columnBottoms, maxItemBottom);
    }

    private static int clampSpan(int span, int columnCount) {
        if (span < 1) {
            return 1;
        }
        if (span > columnCount) {
            return columnCount;
        }
        return span;
    }

    private static double spanTop(double[] columnBottoms, int start, int span) {
        double top = 0.0;
        for (int c = start; c < start + span; c++) {
            if (columnBottoms[c] > top) {
                top = columnBottoms[c];
            }
        }
        return top;
    }
}

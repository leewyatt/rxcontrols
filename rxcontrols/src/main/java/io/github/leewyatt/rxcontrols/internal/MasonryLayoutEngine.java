package io.github.leewyatt.rxcontrols.internal;

/**
 * Stateless shortest-column placement engine for masonry / waterfall layouts.
 *
 * <p>Given a fixed number of equal-width columns and the vertical extent each
 * item occupies, {@link #place(int, double, int[], double[])} assigns every item
 * to the currently shortest column (the leftmost column wins ties) in source
 * order. Source order is preserved so the caller's scene-graph, focus traversal
 * and accessibility order stay intact.</p>
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

    private MasonryLayoutEngine() {
    }

    /**
     * Places items into columns using the stable shortest-column strategy.
     *
     * @param columns      the number of columns; values below one are treated as one
     * @param vgap         the vertical gap inserted below each placed item
     * @param spans        the column span per item, each clamped to {@code [1, columns]}
     * @param blockHeights the vertical extent per item, including its margins
     * @return the placement result
     */
    public static Result place(int columns, double vgap, int[] spans, double[] blockHeights) {
        int columnCount = Math.max(1, columns);
        int itemCount = blockHeights.length;
        int[] startColumns = new int[itemCount];
        double[] tops = new double[itemCount];
        double[] columnBottoms = new double[columnCount];

        for (int i = 0; i < itemCount; i++) {
            int span = clampSpan(spans[i], columnCount);
            int lastStart = columnCount - span;
            int bestStart = 0;
            double bestTop = Double.POSITIVE_INFINITY;
            for (int start = 0; start <= lastStart; start++) {
                double top = spanTop(columnBottoms, start, span);
                if (top < bestTop - EPSILON) {
                    bestTop = top;
                    bestStart = start;
                }
            }
            startColumns[i] = bestStart;
            tops[i] = bestTop;
            double newBottom = bestTop + blockHeights[i] + vgap;
            for (int c = bestStart; c < bestStart + span; c++) {
                columnBottoms[c] = newBottom;
            }
        }

        double maxBottom = 0.0;
        for (double bottom : columnBottoms) {
            if (bottom > maxBottom) {
                maxBottom = bottom;
            }
        }
        return new Result(startColumns, tops, Math.max(0.0, maxBottom - vgap));
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

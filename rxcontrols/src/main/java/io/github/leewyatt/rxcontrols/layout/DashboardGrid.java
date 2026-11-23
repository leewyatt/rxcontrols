package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.internal.MasonryColumns;

/**
 * The grid lines of one {@link RXDashboardPane} layout pass, in the pane's coordinates. Cards, the placeholder and
 * the translation of a pointer into cells all read the same instance, so a card edge drawn on screen is exactly the
 * line a pointer is compared with.
 *
 * <p>Columns come from {@link MasonryColumns} with a fixed count filling the content width: every column edge is on
 * the device-pixel grid and the spare pixels widen the gaps. Rows use the same rule along y: the row height is
 * ceiled to the device grid, and the top of row {@code r} is {@code r * (rowHeight + vgap)} rounded to it.</p>
 */
final class DashboardGrid {

    private final MasonryColumns.Resolution columns;
    private final int columnCount;
    private final double left;
    private final double top;
    private final double rowHeight;
    private final double vgap;
    private final double scaleY;

    /**
     * Creates the grid of a pass.
     *
     * @param columns     the resolved column tracks, relative to the left edge of the content area
     * @param columnCount the column count
     * @param left        the x of the content area, on the device grid
     * @param top         the y of the content area, on the device grid
     * @param rowHeight   the row height, ceiled to the device grid
     * @param vgap        the gap between rows, on the device grid
     * @param scaleY      the vertical render scale rows snap to, or 0 for none
     */
    DashboardGrid(MasonryColumns.Resolution columns, int columnCount, double left, double top, double rowHeight,
                  double vgap, double scaleY) {
        this.columns = columns;
        this.columnCount = columnCount;
        this.left = left;
        this.top = top;
        this.rowHeight = rowHeight;
        this.vgap = vgap;
        this.scaleY = Double.isFinite(scaleY) && scaleY > 0.0 ? scaleY : 0.0;
    }

    /**
     * Returns the column count.
     *
     * @return the column count
     */
    int columnCount() {
        return columnCount;
    }

    /**
     * Returns the distance from one column's left edge to the next one's, averaged over the grid: the column width
     * and the gap after it. With a single column, its width.
     *
     * @return the horizontal pitch
     */
    double columnPitch() {
        return columnCount > 1 ? (columnX(columnCount - 1) - columnX(0)) / (columnCount - 1) : spanWidth(0, 1);
    }

    /**
     * Returns the distance from one row's top edge to the next one's: the row height and the gap after it.
     *
     * @return the vertical pitch
     */
    double rowPitch() {
        return rowHeight + vgap;
    }

    /**
     * Returns the left edge of a column.
     *
     * @param column the column
     * @return its left edge
     */
    double columnX(int column) {
        return left + columns.columnX(0.0, column);
    }

    /**
     * Returns the width of an area from the left edge of {@code column} to the right edge of its last column.
     *
     * @param column the first column
     * @param span   the column span, at least 1
     * @return the area's width
     */
    double spanWidth(int column, int span) {
        return columns.spanWidth(0.0, column, span);
    }

    /**
     * Returns the right edge of an area.
     *
     * @param column the first column
     * @param span   the column span, at least 1
     * @return its right edge
     */
    double columnRight(int column, int span) {
        return columnX(column) + spanWidth(column, span);
    }

    /**
     * Returns the top edge of a row.
     *
     * @param row the row, at least 0
     * @return its top edge
     */
    double rowY(int row) {
        return top + snap(row * (rowHeight + vgap));
    }

    /**
     * Returns the height of an area from the top edge of {@code row} to the bottom edge of its last row.
     *
     * @param row  the first row
     * @param span the row span, at least 1
     * @return the area's height
     */
    double spanHeight(int row, int span) {
        return snap(rowY(row + Math.max(1, span) - 1) + rowHeight - rowY(row));
    }

    /**
     * Returns the bottom edge of an area.
     *
     * @param row  the first row
     * @param span the row span, at least 1
     * @return its bottom edge
     */
    double rowBottom(int row, int span) {
        return rowY(row) + spanHeight(row, span);
    }

    /**
     * Returns the height of a block of rows, without a gap after the last one.
     *
     * @param rows the number of rows
     * @return the height, 0 for no rows
     */
    double heightOfRows(int rows) {
        return rows <= 0 ? 0.0 : rowBottom(0, rows) - top;
    }

    /**
     * Returns the column whose left edge is nearest to {@code x} among the columns an area of the given span can
     * start at.
     *
     * @param x    the x of the area's left edge
     * @param span the area's column span
     * @return the nearest column, from 0 to {@code columnCount - span}
     */
    int nearestColumn(double x, int span) {
        int last = Math.max(0, columnCount - Math.max(1, span));
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int column = 0; column <= last; column++) {
            double distance = Math.abs(x - columnX(column));
            if (distance < bestDistance) {
                best = column;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Returns the row whose top edge is nearest to {@code y}.
     *
     * @param y the y of the area's top edge
     * @return the nearest row, at least 0
     */
    int nearestRow(double y) {
        double step = rowHeight + vgap;
        if (!(step > 0.0)) {
            return 0;
        }
        int guess = (int) Math.max(0, Math.min(DashboardSolver.MAX_ROW, Math.round((y - top) / step)));
        int best = guess;
        for (int row = Math.max(0, guess - 1); row <= guess + 1; row++) {
            if (Math.abs(y - rowY(row)) < Math.abs(y - rowY(best))) {
                best = row;
            }
        }
        return best;
    }

    /**
     * Returns the column span whose right edge is nearest to {@code right} for an area starting at {@code column}.
     *
     * @param column the area's first column
     * @param right  the x of the right edge asked for
     * @return the nearest span, from 1 to {@code columnCount - column}
     */
    int nearestColumnSpan(int column, double right) {
        int best = 1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int span = 1; column + span <= columnCount; span++) {
            double distance = Math.abs(right - columnRight(column, span));
            if (distance < bestDistance) {
                best = span;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Returns the row span whose bottom edge is nearest to {@code bottom} for an area starting at {@code row}.
     *
     * @param row    the area's first row
     * @param bottom the y of the bottom edge asked for
     * @return the nearest span, at least 1
     */
    int nearestRowSpan(int row, double bottom) {
        double step = rowHeight + vgap;
        if (!(step > 0.0)) {
            return 1;
        }
        int guess = (int) Math.max(1, Math.min(DashboardSolver.MAX_ROW_SPAN,
                Math.round((bottom - rowY(row) + vgap) / step)));
        int best = guess;
        for (int span = Math.max(1, guess - 1); span <= guess + 1; span++) {
            if (Math.abs(bottom - rowBottom(row, span)) < Math.abs(bottom - rowBottom(row, best))) {
                best = span;
            }
        }
        return best;
    }

    private double snap(double value) {
        return scaleY > 0.0 ? Math.round(value * scaleY) / scaleY : value;
    }
}

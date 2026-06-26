package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTileSection;

import java.util.List;

/**
 * Immutable visual-row plan for {@link RXTileViewport}: the interleaved sequence
 * of section-header rows and data rows, with a per-section offset index so that
 * row&#8596;Y and item&#8596;row lookups cost O(log sections) plus arithmetic
 * (O(sections) memory — never one entry per row, so it scales to millions of
 * items).
 *
 * <p>A flat view (no {@code sectionKeyFactory}) is the degenerate case: the
 * {@code sections} list is empty, no header rows exist, and every lookup reduces
 * to the same closed-form arithmetic the flat engine used before sections.
 *
 * <p>Two fixed row heights: a section-header row is {@code headerHeight}, a data
 * row is {@code dataSlotHeight} (cell height + vgap). A data row is short-clamped
 * to <em>its own section's</em> last item, so a section whose item count is not a
 * multiple of the column count leaves a partial final row and the next section
 * starts a fresh row.
 *
 * <p>{@code sectionSpacing} is extra blank content-space inserted before every
 * group after the first (it is not a visual row, just added to the section's top
 * Y). It only has a visible effect with two or more sections; a flat view or a
 * single group never shows it.
 */
final class RXTileRowPlan {

    /**
     * Resolved description of one visual row, produced by {@link #rowInfo(int)}
     * with a single section lookup.
     *
     * @param header        whether this row is a section header (vs a data row)
     * @param section       the section containing this row, or {@code null} when flat
     * @param firstItemIndex the first item index of a data row, or {@code -1} for a header
     * @param cellCount     the number of cells in a data row, or {@code 0} for a header
     * @param top           the content-space Y of the row's top
     * @param height        the row's height (header height or data-slot height)
     * @param dataRowIndex  the global data-row index (header rows excluded), or
     *                      {@code -1} for a header row
     */
    record RowInfo(boolean header, RXTileSection section, int firstItemIndex, int cellCount,
                   double top, double height, int dataRowIndex) {
    }

    record ItemPosition(int dataRow, int column) {

        boolean valid() {
            return dataRow >= 0 && column >= 0;
        }
    }

    private final List<RXTileSection> sections;
    private final boolean grouped;
    private final boolean headersShown;
    private final int columns;
    private final double headerHeight;
    private final double dataSlotHeight;
    private final int itemCount;

    // Per-section offset index (length == section count; empty when flat).
    private final int[] sectionFirstVisualRow;
    private final double[] sectionTopY;
    private final int[] sectionDataRowStart;

    private final int totalVisualRows;
    private final int totalDataRows;
    private final double contentHeight;

    RXTileRowPlan(List<RXTileSection> sections, boolean showHeaders, int columns,
                  double headerHeight, double dataSlotHeight, int itemCount, double sectionSpacing) {
        this.sections = List.copyOf(sections);
        this.grouped = !this.sections.isEmpty();
        this.headersShown = grouped && showHeaders;
        this.columns = Math.max(1, columns);
        this.headerHeight = headerHeight;
        this.dataSlotHeight = dataSlotHeight;
        this.itemCount = Math.max(0, itemCount);

        int count = this.sections.size();
        sectionFirstVisualRow = new int[count];
        sectionTopY = new double[count];
        sectionDataRowStart = new int[count];

        if (!grouped) {
            int rows = this.itemCount == 0 ? 0 : (int) Math.ceil((double) this.itemCount / this.columns);
            totalVisualRows = rows;
            totalDataRows = rows;
            contentHeight = rows * dataSlotHeight;
        } else {
            int visualRow = 0;
            double y = 0.0;
            int dataRowStart = 0;
            for (int s = 0; s < count; s++) {
                // Blank separator before every group after the first — not a visual
                // row, just extra content-space Y folded into the section's top.
                if (s > 0) {
                    y += sectionSpacing;
                }
                sectionFirstVisualRow[s] = visualRow;
                sectionTopY[s] = y;
                sectionDataRowStart[s] = dataRowStart;
                if (headersShown) {
                    visualRow += 1;
                    y += headerHeight;
                }
                int dataRows = (int) Math.ceil((double) this.sections.get(s).itemCount() / this.columns);
                visualRow += dataRows;
                y += dataRows * dataSlotHeight;
                dataRowStart += dataRows;
            }
            totalVisualRows = visualRow;
            totalDataRows = dataRowStart;
            contentHeight = y;
        }
    }

    int totalVisualRows() {
        return totalVisualRows;
    }

    double contentHeight() {
        return contentHeight;
    }

    int columns() {
        return columns;
    }

    boolean headersShown() {
        return headersShown;
    }

    int sectionCount() {
        return sections.size();
    }

    double headerHeight() {
        return headerHeight;
    }

    /**
     * The content-space top Y of the section at {@code sectionIndex} — the top of
     * its header row (it already folds in {@code sectionSpacing}). Used by the
     * sticky-header handoff math.
     *
     * @param sectionIndex a valid section index
     * @return the section's content-space top Y, or {@code 0} when out of range / flat
     */
    double sectionTop(int sectionIndex) {
        if (!grouped || sectionIndex < 0 || sectionIndex >= sectionTopY.length) {
            return 0.0;
        }
        return sectionTopY[sectionIndex];
    }

    /**
     * The visual row whose band contains content-space {@code y}, clamped to the
     * valid range, or {@code -1} when there are no rows.
     *
     * @param y content-space offset (already resolved from the scroll position)
     * @return the visual-row index, or {@code -1} when empty
     */
    int firstVisualRowAt(double y) {
        if (totalVisualRows == 0) {
            return -1;
        }
        if (!grouped) {
            int row = (int) Math.floor(y / dataSlotHeight);
            return clamp(row, 0, totalVisualRows - 1);
        }
        int s = sectionAtY(y);
        double dy = y - sectionTopY[s];
        int visualRow = sectionFirstVisualRow[s];
        if (headersShown) {
            if (dy < headerHeight) {
                return visualRow;
            }
            dy -= headerHeight;
            visualRow += 1;
        }
        int dataRows = dataRowsIn(s);
        int local = clamp((int) Math.floor(dy / dataSlotHeight), 0, dataRows - 1);
        return visualRow + local;
    }

    /**
     * Resolves a visual row to its kind, geometry and (for data rows) item range.
     *
     * @param visualRow a valid visual-row index
     * @return the row description
     */
    RowInfo rowInfo(int visualRow) {
        if (!grouped) {
            int rowStart = visualRow * columns;
            int cells = Math.max(0, Math.min(columns, itemCount - rowStart));
            return new RowInfo(false, null, rowStart, cells,
                    visualRow * dataSlotHeight, dataSlotHeight, visualRow);
        }
        int s = sectionAtVisualRow(visualRow);
        RXTileSection section = sections.get(s);
        int local = visualRow - sectionFirstVisualRow[s];
        if (headersShown && local == 0) {
            return new RowInfo(true, section, -1, 0, sectionTopY[s], headerHeight, -1);
        }
        int dataLocal = headersShown ? local - 1 : local;
        int rowStart = section.firstItemIndex() + dataLocal * columns;
        int cells = Math.max(0, Math.min(columns, section.endItemIndex() - rowStart));
        double top = sectionTopY[s] + (headersShown ? headerHeight : 0.0) + dataLocal * dataSlotHeight;
        int dataRowIndex = sectionDataRowStart[s] + dataLocal;
        return new RowInfo(false, section, rowStart, cells, top, dataSlotHeight, dataRowIndex);
    }

    /**
     * The visual row holding the data item at {@code itemIndex}.
     *
     * @param itemIndex a valid item index
     * @return the visual-row index of the data row containing the item
     */
    int visualRowOfItem(int itemIndex) {
        if (!grouped) {
            return itemIndex / columns;
        }
        int s = sectionAtItem(itemIndex);
        RXTileSection section = sections.get(s);
        int localRow = (itemIndex - section.firstItemIndex()) / columns;
        return sectionFirstVisualRow[s] + (headersShown ? 1 : 0) + localRow;
    }

    /**
     * The data-row / column slot currently occupied by {@code itemIndex}.
     *
     * @param itemIndex a valid item index
     * @return the item's current data-row and column, or an invalid position
     */
    ItemPosition itemPositionOf(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount || totalVisualRows == 0) {
            return new ItemPosition(-1, -1);
        }
        RowInfo info = rowInfo(visualRowOfItem(itemIndex));
        return new ItemPosition(info.dataRowIndex(), itemIndex - info.firstItemIndex());
    }

    /**
     * The item in the next visual data row above or below {@code itemIndex}, using
     * {@code preferredColumn} as the user's intended horizontal slot.
     *
     * @param itemIndex a valid item index
     * @param direction positive for the row below, negative for the row above
     * @param preferredColumn the intended column; a negative value uses the item's current column
     * @return the neighbor item index, or {@code -1} when no suitable cell exists
     */
    int verticalNeighborOfItem(int itemIndex, int direction, int preferredColumn) {
        if (itemIndex < 0 || itemIndex >= itemCount || direction == 0 || totalVisualRows == 0) {
            return -1;
        }
        int step = direction > 0 ? 1 : -1;
        int visualRow = visualRowOfItem(itemIndex);
        RowInfo current = rowInfo(visualRow);
        int column = preferredColumn >= 0 ? preferredColumn : itemIndex - current.firstItemIndex();
        for (int targetRow = visualRow + step;
             targetRow >= 0 && targetRow < totalVisualRows;
             targetRow += step) {
            RowInfo target = rowInfo(targetRow);
            if (target.header()) {
                continue;
            }
            if (target.cellCount() <= 0) {
                return -1;
            }
            if (column >= target.cellCount()) {
                if (target.section() != null) {
                    // Grouped navigation must cross uneven section tails instead of
                    // trapping focus at the previous section boundary.
                    return target.firstItemIndex() + target.cellCount() - 1;
                }
                // In flat mode the only short row is the final row; keep strict
                // column navigation rather than jumping sideways to the last item.
                return -1;
            }
            return target.firstItemIndex() + column;
        }
        return -1;
    }

    /**
     * The first visual row belonging to the section at {@code sectionIndex}.
     *
     * @param sectionIndex the section index
     * @return the section's first visual row, or {@code -1} when unavailable
     */
    int visualRowOfSection(int sectionIndex) {
        if (!grouped || sectionIndex < 0 || sectionIndex >= sectionFirstVisualRow.length) {
            return -1;
        }
        return sectionFirstVisualRow[sectionIndex];
    }

    /**
     * The section containing the given visual row, or {@code null} when flat.
     *
     * @param visualRow a valid visual-row index
     * @return the containing section, or {@code null}
     */
    RXTileSection sectionOf(int visualRow) {
        if (!grouped) {
            return null;
        }
        return sections.get(sectionAtVisualRow(visualRow));
    }

    // ==================== Section lookups ====================

    private int dataRowsIn(int sectionIndex) {
        int next = sectionIndex + 1 < sectionDataRowStart.length
                ? sectionDataRowStart[sectionIndex + 1]
                : totalDataRows;
        return next - sectionDataRowStart[sectionIndex];
    }

    // Last section whose top Y is <= y (so an exact boundary selects the new section).
    private int sectionAtY(double y) {
        int lo = 0;
        int hi = sectionTopY.length - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sectionTopY[mid] <= y) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    // Last section whose first visual row is <= visualRow.
    private int sectionAtVisualRow(int visualRow) {
        int lo = 0;
        int hi = sectionFirstVisualRow.length - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sectionFirstVisualRow[mid] <= visualRow) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    // Last section whose first item index is <= itemIndex.
    private int sectionAtItem(int itemIndex) {
        int lo = 0;
        int hi = sections.size() - 1;
        int result = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sections.get(mid).firstItemIndex() <= itemIndex) {
                result = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

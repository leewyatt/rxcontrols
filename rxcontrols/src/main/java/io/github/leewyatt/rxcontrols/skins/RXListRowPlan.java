package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.utils.RXMath;

import java.util.List;
import java.util.Objects;

/**
 * Immutable visual-row plan for {@link RXListViewport}: the interleaved sequence
 * of section-header rows and single-item data rows, answering row&#8596;Y and
 * item&#8596;row lookups for either a uniform fixed row height or per-item variable
 * heights.
 *
 * <p>A flat view (no {@code sectionKeyFactory}) is the degenerate case: the
 * {@code sections} list is empty, no header rows exist, and (in fixed mode) every
 * lookup reduces to closed-form arithmetic (data row index == item index).
 *
 * <p><b>Fixed mode</b> ({@link #fixed}) keeps only the per-section offset index, so
 * row&#8596;Y and item&#8596;row cost O(log sections) plus arithmetic and the memory
 * is O(sections) — it scales to millions of items. A section-header row is
 * {@code headerHeight}; a data row is the uniform {@code rowHeight}.
 *
 * <p><b>Variable mode</b> ({@link #variable(List, boolean, double, double[], double, boolean[])})
 * stores a per-item prefix-sum of the supplied heights (measured or estimated), so it is
 * O(items) in memory — the cost of non-uniform rows — and answers a content-space query
 * by a binary search over the per-item tops. Section-header rows stay the fixed
 * {@code headerHeight}; only data rows vary. The structural lookups
 * ({@link #visualRowOfItem}, {@link #visualRowOfSection}, {@link #sectionOf}) are
 * height-independent and identical in both modes.
 *
 * <p>Grouped variable mode requires the {@code sections} to exactly partition
 * {@code [0, itemCount)} (each section a contiguous run, every item in one section) —
 * the invariant {@code recomputeSections} guarantees. Both the prefix-sum build and the
 * per-item lookups rely on it.
 *
 * <p>{@code sectionSpacing} is extra blank content-space inserted before every group
 * after the first (it is not a visual row, just added to the section's top Y). It only
 * has a visible effect with two or more sections.
 *
 * <p><b>Collapsed sections.</b> A per-section {@code collapsed} flag reduces that
 * section to its header row: its items get no data row, no height and no Y, and the
 * item lookups report them as hidden ({@code -1}). The flag only takes effect while
 * header rows are shown — without a header a collapsed section would leave no row to
 * expand it from, so a plan with hidden headers lays every section out expanded. The
 * shown data rows are numbered contiguously ({@link #dataRowOfItem} /
 * {@link #itemOfDataRow}), skipping header rows and hidden items; with nothing
 * collapsed a data row number equals the item index.
 */
final class RXListRowPlan {

    /**
     * Resolved description of one visual row, produced by {@link #rowInfo(int)}
     * with a single section lookup.
     *
     * @param header    whether this row is a section header (vs a data row)
     * @param section   the section containing this row, or {@code null} when flat
     * @param itemIndex the item index of a data row, or {@code -1} for a header
     * @param top       the content-space Y of the row's top
     * @param height    the row's height (header height or data-row height)
     */
    record RowInfo(boolean header, RXListSection section, int itemIndex, double top, double height) {
    }

    private final List<RXListSection> sections;
    private final boolean grouped;
    private final boolean headersShown;
    private final double headerHeight;
    private final double rowHeight;
    private final int itemCount;
    private final boolean variable;

    // Per-section offset index (length == section count; empty when flat).
    private final int[] sectionFirstVisualRow;
    private final double[] sectionTopY;
    // Shown data rows before each section, and whether the section is laid out
    // collapsed (only ever true while headers are shown).
    private final int[] sectionFirstDataRow;
    private final boolean[] sectionCollapsed;

    // Variable mode only (null when fixed): per-item content-space top and height,
    // both length itemCount. itemTops is ascending so a content-space query binary-searches it.
    private final double[] itemTops;
    private final double[] itemHeights;

    private final int totalVisualRows;
    private final int dataRowCount;
    private final double contentHeight;

    /**
     * Creates a fixed (uniform row height) plan.
     *
     * @param sections       the sections (empty for a flat view)
     * @param showHeaders    whether header rows are rendered (only honored when grouped)
     * @param headerHeight   the height of one section-header row
     * @param rowHeight      the fixed data-row height; a non-positive value is clamped
     *                       to {@code 1} so the row math never divides by zero
     * @param itemCount      the total item count
     * @param sectionSpacing the extra blank space inserted before each section after the first
     * @param collapsed      per-section collapsed flags, indexed like {@code sections}; may be
     *                       {@code null} or shorter (missing entries are expanded)
     * @return the fixed-height plan
     */
    static RXListRowPlan fixed(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                               double rowHeight, int itemCount, double sectionSpacing, boolean[] collapsed) {
        return new RXListRowPlan(sections, showHeaders, headerHeight, rowHeight, itemCount, null, sectionSpacing,
                collapsed);
    }

    /**
     * Creates a variable (per-item height) plan. The item count is taken from
     * {@code itemHeights.length}; the heights are the effective per-item heights
     * (measured or estimated).
     *
     * @param sections       the sections (empty for a flat view)
     * @param showHeaders    whether header rows are rendered (only honored when grouped)
     * @param headerHeight   the height of one section-header row
     * @param itemHeights    the effective per-item data-row heights
     * @param sectionSpacing the extra blank space inserted before each section after the first
     * @param collapsed      per-section collapsed flags, indexed like {@code sections}; may be
     *                       {@code null} or shorter (missing entries are expanded)
     * @return the variable-height plan
     */
    static RXListRowPlan variable(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                                  double[] itemHeights, double sectionSpacing, boolean[] collapsed) {
        return new RXListRowPlan(sections, showHeaders, headerHeight, 0.0, itemHeights.length, itemHeights,
                sectionSpacing, collapsed);
    }

    private RXListRowPlan(List<RXListSection> sections, boolean showHeaders, double headerHeight,
                          double rowHeight, int itemCount, double[] itemHeights, double sectionSpacing,
                          boolean[] collapsed) {
        this.sections = List.copyOf(sections);
        this.grouped = !this.sections.isEmpty();
        this.headersShown = grouped && showHeaders;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight > 0.0 ? rowHeight : 1.0;
        this.itemCount = Math.max(0, itemCount);
        this.variable = itemHeights != null;

        if (variable) {
            this.itemHeights = new double[this.itemCount];
            this.itemTops = new double[this.itemCount];
            for (int i = 0; i < this.itemCount; i++) {
                double h = i < itemHeights.length ? itemHeights[i] : 0.0;
                // Heights enter the prefix sum, so coerce non-finite / negative to 0 to
                // keep itemTops monotonic (the binary-search visibility query needs it).
                this.itemHeights[i] = Double.isFinite(h) && h > 0.0 ? h : 0.0;
            }
        } else {
            this.itemHeights = null;
            this.itemTops = null;
        }

        int count = this.sections.size();
        sectionFirstVisualRow = new int[count];
        sectionTopY = new double[count];
        sectionFirstDataRow = new int[count];
        sectionCollapsed = new boolean[count];

        if (!grouped) {
            totalVisualRows = this.itemCount;
            dataRowCount = this.itemCount;
            contentHeight = variable ? fillFlatItemTops() : this.itemCount * this.rowHeight;
        } else {
            int visualRow = 0;
            int dataRow = 0;
            double y = 0.0;
            for (int s = 0; s < count; s++) {
                // Blank separator before every group after the first — not a visual
                // row, just extra content-space Y folded into the section's top.
                if (s > 0) {
                    y += sectionSpacing;
                }
                sectionFirstVisualRow[s] = visualRow;
                sectionTopY[s] = y;
                sectionFirstDataRow[s] = dataRow;
                if (headersShown) {
                    visualRow += 1;
                    y += this.headerHeight;
                }
                boolean hidden = headersShown && collapsed != null && s < collapsed.length && collapsed[s];
                sectionCollapsed[s] = hidden;
                RXListSection section = this.sections.get(s);
                int dataRows = hidden ? 0 : section.itemCount();
                if (variable) {
                    // Sections partition [0, itemCount) exactly (class doc), so every item
                    // index is in range and each itemTops slot is written exactly once. A
                    // hidden item keeps the section's data start as its top and adds no
                    // height, so itemTops stays ascending.
                    int firstItem = section.firstItemIndex();
                    for (int k = 0; k < section.itemCount(); k++) {
                        int itemIndex = firstItem + k;
                        itemTops[itemIndex] = y;
                        if (!hidden) {
                            y += this.itemHeights[itemIndex];
                        }
                    }
                } else {
                    y += dataRows * this.rowHeight;
                }
                visualRow += dataRows;
                dataRow += dataRows;
            }
            totalVisualRows = visualRow;
            dataRowCount = dataRow;
            contentHeight = y;
        }
    }

    // Lays out the flat (ungrouped) per-item tops and returns the total content height.
    private double fillFlatItemTops() {
        double y = 0.0;
        for (int i = 0; i < itemCount; i++) {
            itemTops[i] = y;
            y += itemHeights[i];
        }
        return y;
    }

    int itemCount() {
        return itemCount;
    }

    boolean variable() {
        return variable;
    }

    double rowHeight() {
        return rowHeight;
    }

    double contentHeight() {
        return contentHeight;
    }

    int totalVisualRows() {
        return totalVisualRows;
    }

    /**
     * The number of shown data rows: the item count minus the items of collapsed
     * sections.
     *
     * @return the shown data-row count
     */
    int dataRowCount() {
        return dataRowCount;
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
     * The section at {@code sectionIndex}.
     *
     * @param sectionIndex a valid section index
     * @return the section
     */
    RXListSection section(int sectionIndex) {
        return sections.get(sectionIndex);
    }

    /**
     * Whether the section at {@code sectionIndex} is laid out collapsed (header row
     * only). Always {@code false} while headers are hidden.
     *
     * @param sectionIndex a section index
     * @return {@code true} if the section is collapsed; {@code false} when out of range
     */
    boolean sectionCollapsed(int sectionIndex) {
        return sectionIndex >= 0 && sectionIndex < sectionCollapsed.length && sectionCollapsed[sectionIndex];
    }

    /**
     * Whether the item at {@code itemIndex} has a data row — it exists and its
     * section is not collapsed.
     *
     * @param itemIndex an item index
     * @return {@code true} if the item is shown
     */
    boolean isItemShown(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) {
            return false;
        }
        return !grouped || !sectionCollapsed[sectionAtItem(itemIndex)];
    }

    /**
     * The index of the section containing the item at {@code itemIndex}.
     *
     * @param itemIndex an item index
     * @return the section index, or {@code -1} when flat or out of range
     */
    int sectionIndexOfItem(int itemIndex) {
        if (!grouped || itemIndex < 0 || itemIndex >= itemCount) {
            return -1;
        }
        return sectionAtItem(itemIndex);
    }

    /**
     * The index of the section whose band contains content-space {@code y}: the last
     * section whose top is at or above it (so the spacing gap before a section still
     * belongs to the previous one), clamped to the first section.
     *
     * @param y a content-space offset
     * @return the section index, or {@code -1} when flat
     */
    int sectionIndexAt(double y) {
        if (!grouped) {
            return -1;
        }
        return sectionAtY(y);
    }

    /**
     * The shown data-row number of the item at {@code itemIndex}: its position among
     * the shown data rows, which skip header rows and the items of collapsed sections.
     *
     * @param itemIndex an item index
     * @return the data-row number, or {@code -1} when out of range or hidden
     */
    int dataRowOfItem(int itemIndex) {
        if (itemIndex < 0 || itemIndex >= itemCount) {
            return -1;
        }
        if (!grouped) {
            return itemIndex;
        }
        int s = sectionAtItem(itemIndex);
        if (sectionCollapsed[s]) {
            return -1;
        }
        return sectionFirstDataRow[s] + itemIndex - sections.get(s).firstItemIndex();
    }

    /**
     * The item shown on the given data row; the inverse of {@link #dataRowOfItem}.
     *
     * @param dataRow a shown data-row number
     * @return the item index, or {@code -1} when out of range
     */
    int itemOfDataRow(int dataRow) {
        if (dataRow < 0 || dataRow >= dataRowCount) {
            return -1;
        }
        if (!grouped) {
            return dataRow;
        }
        // The last section starting at or before dataRow always has rows here: a
        // collapsed one shares its start with the section after it, which wins the tie.
        int lo = 0;
        int hi = sectionFirstDataRow.length - 1;
        int s = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (sectionFirstDataRow[mid] <= dataRow) {
                s = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return sections.get(s).firstItemIndex() + dataRow - sectionFirstDataRow[s];
    }

    /**
     * The content-space top Y of the data row holding {@code itemIndex}, used by the
     * variable-height anchor pin.
     *
     * @param itemIndex an item index
     * @return the data row's content-space top, or {@code -1} when out of range or hidden
     */
    double itemTop(int itemIndex) {
        if (!isItemShown(itemIndex)) {
            return -1.0;
        }
        if (variable) {
            return itemTops[itemIndex];
        }
        return rowInfo(visualRowOfItem(itemIndex)).top();
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
            if (variable) {
                return RXMath.clamp(lastItemAtMost(y, 0, itemCount), 0, totalVisualRows - 1);
            }
            int row = (int) Math.floor(y / rowHeight);
            return RXMath.clamp(row, 0, totalVisualRows - 1);
        }
        int s = sectionAtY(y);
        double dy = y - sectionTopY[s];
        int baseVisualRow = sectionFirstVisualRow[s];
        // A collapsed section is its header row alone, so the spacing gap after it
        // also resolves to the header.
        if (headersShown && (dy < headerHeight || sectionCollapsed[s])) {
            return baseVisualRow;
        }
        int firstDataVisualRow = baseVisualRow + (headersShown ? 1 : 0);
        RXListSection section = sections.get(s);
        int firstItem = section.firstItemIndex();
        int dataRows = section.itemCount();
        if (variable) {
            int item = lastItemAtMost(y, firstItem, firstItem + dataRows);
            return firstDataVisualRow + RXMath.clamp(item - firstItem, 0, Math.max(0, dataRows - 1));
        }
        double dataDy = dy - (headersShown ? headerHeight : 0.0);
        int local = RXMath.clamp((int) Math.floor(dataDy / rowHeight), 0, dataRows - 1);
        return firstDataVisualRow + local;
    }

    /**
     * Resolves a visual row to its kind, geometry and (for data rows) item index.
     *
     * @param visualRow a valid visual-row index
     * @return the row description
     * @throws IndexOutOfBoundsException if {@code visualRow} is not in
     *                                   {@code [0, totalVisualRows())} — for example the
     *                                   {@code -1} that {@link #visualRowOfItem} returns
     *                                   for a hidden item
     */
    RowInfo rowInfo(int visualRow) {
        Objects.checkIndex(visualRow, totalVisualRows);
        if (!grouped) {
            if (variable) {
                return new RowInfo(false, null, visualRow, itemTops[visualRow], itemHeights[visualRow]);
            }
            return new RowInfo(false, null, visualRow, visualRow * rowHeight, rowHeight);
        }
        int s = sectionAtVisualRow(visualRow);
        RXListSection section = sections.get(s);
        int local = visualRow - sectionFirstVisualRow[s];
        if (headersShown && local == 0) {
            return new RowInfo(true, section, -1, sectionTopY[s], headerHeight);
        }
        int dataLocal = headersShown ? local - 1 : local;
        int itemIndex = section.firstItemIndex() + dataLocal;
        if (variable) {
            return new RowInfo(false, section, itemIndex, itemTops[itemIndex], itemHeights[itemIndex]);
        }
        double top = sectionTopY[s] + (headersShown ? headerHeight : 0.0) + dataLocal * rowHeight;
        return new RowInfo(false, section, itemIndex, top, rowHeight);
    }

    /**
     * The visual row holding the data item at {@code itemIndex}.
     *
     * @param itemIndex a valid item index
     * @return the visual-row index of the data row containing the item, or {@code -1}
     *         when the item is hidden in a collapsed section
     */
    int visualRowOfItem(int itemIndex) {
        if (!grouped) {
            return itemIndex;
        }
        int s = sectionAtItem(itemIndex);
        if (sectionCollapsed[s]) {
            return -1;
        }
        RXListSection section = sections.get(s);
        int localRow = itemIndex - section.firstItemIndex();
        return sectionFirstVisualRow[s] + (headersShown ? 1 : 0) + localRow;
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
    RXListSection sectionOf(int visualRow) {
        if (!grouped) {
            return null;
        }
        return sections.get(sectionAtVisualRow(visualRow));
    }

    // ==================== Lookups ====================

    // Largest index i in [lo, hiExclusive) with itemTops[i] <= y, or lo when y is below
    // the first (itemTops is ascending). Variable mode only.
    private int lastItemAtMost(double y, int lo, int hiExclusive) {
        int low = lo;
        int high = hiExclusive - 1;
        int result = lo;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (itemTops[mid] <= y) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
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

}

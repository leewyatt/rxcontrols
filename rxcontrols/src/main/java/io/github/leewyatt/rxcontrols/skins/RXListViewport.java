package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-built virtualizing viewport for {@link RXListViewSkin}, the list-shaped
 * member of the {@link RXVirtualViewportBase} family: a single column, one item
 * per row, uniform height. On top of the shell it adds an independent
 * section-header pool so the interleaved sequence of header rows and data rows
 * (described by the {@link RXListRowPlan}) virtualizes by row.
 *
 * <p>The visual-row geometry is supplied by a {@link RXListRowPlan} that the skin
 * builds (sections + resolved row / header heights); because the content height is
 * width-independent there is no two-pass column / scroll-bar interplay — this
 * viewport derives the bar straight from the plan. The visible item range and the
 * top section are reported back so the skin can publish the read-only metrics.
 *
 * @param <T> the item type
 */
final class RXListViewport<T> extends RXVirtualViewportBase<T, RXListCell<T>> {

    private final RXListView<T> control;

    private final List<RXListSectionCell> headerPool = new ArrayList<>();

    // Supplied by the skin (sections + resolved row height) as the geometry source
    // of truth; this viewport then decides the scroll bar from it.
    private RXListRowPlan rowPlan;

    // Published to the skin after each pass (null when flat or empty).
    private RXListSection topSection;

    RXListViewport(RXListView<T> control) {
        this.control = control;
    }

    // ==================== Skin-facing API ====================

    /**
     * Installs the geometry plan for this pass. The skin builds it (sections +
     * resolved row height) as the single geometry source; this viewport derives
     * its scroll bar and visible window from it.
     *
     * @param plan the row plan, never {@code null}
     */
    void setRowPlan(RXListRowPlan plan) {
        this.rowPlan = plan;
    }

    /**
     * The section at the top of the viewport after the most recent pass, or
     * {@code null} when the view is flat or empty.
     *
     * @return the top section, or {@code null}
     */
    RXListSection getTopSection() {
        return topSection;
    }

    /**
     * Scrolls so the row holding {@code index} lands per {@code alignment}, using
     * the current row plan so it is correct even before this pass's
     * {@link #layoutChildren()} runs (the pending-scroll path).
     *
     * @param index     a valid item index
     * @param alignment where the target row should land
     * @return {@code true} if the request was applied; {@code false} when the
     *         viewport has no height yet, so the caller should keep it pending
     */
    boolean scrollToIndex(int index, ScrollAlignment alignment) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            // Geometry is not known yet; leave the request armed for a sized pass.
            return false;
        }
        RXListRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return true;
        }
        int visualRow = plan.visualRowOfItem(index);
        RXListRowPlan.RowInfo info = plan.rowInfo(visualRow);
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = targetScrollFor(info.top(), info.height(), viewportHeight, alignment);
        scrollY = clamp(target, 0.0, maxScroll);
        requestLayout();
        return true;
    }

    /**
     * Scrolls so the section's first visual row lands per {@code alignment}. When
     * headers are shown that first row is the header; otherwise it is the first
     * data row of the section.
     *
     * @param sectionIndex the section index
     * @param alignment    where the target row should land
     * @return {@code true} if the request was applied; {@code false} when the
     *         viewport has no height yet, so the caller should keep it pending
     */
    boolean scrollToSectionIndex(int sectionIndex, ScrollAlignment alignment) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            return false;
        }
        RXListRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return true;
        }
        int visualRow = plan.visualRowOfSection(sectionIndex);
        if (visualRow < 0) {
            return true;
        }
        RXListRowPlan.RowInfo info = plan.rowInfo(visualRow);
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = targetScrollFor(info.top(), info.height(), viewportHeight, alignment);
        scrollY = clamp(target, 0.0, maxScroll);
        requestLayout();
        return true;
    }

    /**
     * Applies a relative pixel scroll, clamped to the scrollable range, computing
     * the range fresh from the current plan and height so it is correct on the
     * pending-scroll path (before this pass's {@link #layoutChildren()} runs).
     *
     * @param deltaY the signed pixel delta (positive scrolls down)
     * @return {@code true} if the request was applied (so the caller can clear it);
     *         {@code false} when the viewport has no height yet, so the caller
     *         should keep it pending
     */
    @Override
    protected boolean scrollByPixels(double deltaY) {
        double viewportHeight = getHeight();
        if (viewportHeight <= 0.0) {
            // Geometry is not known yet; leave the request armed for a sized pass.
            return false;
        }
        RXListRowPlan plan = rowPlan;
        if (plan == null || plan.totalVisualRows() == 0) {
            return true;
        }
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = clamp(scrollY + deltaY, 0.0, maxScroll);
        if (target != scrollY) {
            scrollY = target;
            requestLayout();
        }
        return true;
    }

    /**
     * Discards the section-header pool; used when the section-header factory
     * changes. A normal layout repopulates it.
     */
    void recreateHeaders() {
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        requestLayout();
    }

    @Override
    protected void dispose() {
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        super.dispose();
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        syncViewportClip(w, h);

        RXListRowPlan plan = rowPlan;
        if (w <= 0.0 || h <= 0.0 || plan == null) {
            resetToEmptyState(0.0, 0.0, false);
            return;
        }
        if (plan.totalVisualRows() == 0) {
            resetToEmptyState(w, h, true);
            return;
        }

        double contentHeight = plan.contentHeight();
        double maxScroll = Math.max(0.0, contentHeight - h);
        cachedMaxScroll = maxScroll;
        scrollY = clamp(scrollY, 0.0, maxScroll);

        double barBreadth = configureAndPositionScrollBar(maxScroll, w, h);
        double contentWidth = Math.max(0.0, w - barBreadth);
        layoutContentLayer(contentWidth, h);

        int first = plan.firstVisualRowAt(scrollY);
        int last = plan.firstVisualRowAt(scrollY + h - 1.0);
        int maxRow = plan.totalVisualRows() - 1;
        if (last > maxRow) {
            last = maxRow;
        }
        if (last < first) {
            last = first;
        }

        topSection = plan.sectionOf(first);
        fillVisibleRows(plan, first, last, contentWidth);
    }

    private void fillVisibleRows(RXListRowPlan plan, int first, int last, double contentWidth) {
        double rowHeight = snapSizeY(plan.rowHeight());
        int cellCursor = 0;
        int headerCursor = 0;
        int firstItem = -1;
        int lastItem = -1;
        for (int visualRow = first; visualRow <= last; visualRow++) {
            RXListRowPlan.RowInfo info = plan.rowInfo(visualRow);
            double rowTop = snapPositionY(info.top() - scrollY);
            if (info.header()) {
                RXListSectionCell header = acquireHeader(headerCursor++);
                String oldStyle = header.getStyle();
                header.updateSection(info.section());
                header.setVisible(true);
                applyCssAfterCellUpdate(header, oldStyle);
                header.resizeRelocate(snapPositionX(0.0), rowTop, contentWidth, snapSizeY(info.height()));
            } else {
                int itemIndex = info.itemIndex();
                if (firstItem < 0) {
                    firstItem = itemIndex;
                }
                lastItem = itemIndex;
                RXListCell<T> cell = acquireCell(cellCursor++);
                String oldStyle = cell.getStyle();
                cell.updateIndex(itemIndex);
                cell.setVisible(true);
                applyCellState(cell, itemIndex);
                applyCssAfterCellUpdate(cell, oldStyle);
                cell.resizeRelocate(snapPositionX(0.0), rowTop, contentWidth, rowHeight);
            }
        }
        parkCellsFrom(cellCursor);
        parkHeadersFrom(headerCursor);
        visibleFirstIndex = firstItem;
        visibleLastIndex = lastItem;
    }

    @Override
    protected void clearVisibleMetrics() {
        super.clearVisibleMetrics();
        topSection = null;
    }

    @Override
    protected void parkAllRealized() {
        parkCellsFrom(0);
        parkHeadersFrom(0);
    }

    // ==================== Header pool ====================

    private RXListSectionCell acquireHeader(int slotIndex) {
        while (headerPool.size() <= slotIndex) {
            RXListSectionCell header = createHeader();
            headerPool.add(header);
            contentLayer.getChildren().add(header);
        }
        return headerPool.get(slotIndex);
    }

    private void parkHeadersFrom(int from) {
        for (int i = from; i < headerPool.size(); i++) {
            RXListSectionCell header = headerPool.get(i);
            if (header.isVisible() || header.getItem() != null) {
                header.setVisible(false);
                header.updateSection(null);
            }
        }
    }

    private RXListSectionCell createHeader() {
        Callback<RXListView<T>, RXListSectionCell> factory = control.getSectionHeaderFactory();
        RXListSectionCell header = factory != null ? factory.call(control) : createDefaultHeader();
        header.setManaged(false);
        return header;
    }

    private RXListSectionCell createDefaultHeader() {
        return new RXListSectionCell() {
            @Override
            protected void updateItem(RXListSection section, boolean empty) {
                super.updateItem(section, empty);
                if (!empty && section != null) {
                    setText(section.key() == null ? "" : String.valueOf(section.key()));
                }
            }
        };
    }

    // ==================== Geometry hooks ====================

    @Override
    protected double unitScrollIncrement() {
        // The base only asks for this past the plan / empty guards, so rowPlan is
        // non-null at every current call site; the fallback keeps the contract (a
        // positive increment) for any future caller on the not-sized path.
        RXListRowPlan plan = rowPlan;
        return plan != null ? plan.rowHeight() : RXListView.DEFAULT_FIXED_CELL_SIZE;
    }

    @Override
    protected boolean isOwnCell(Node node) {
        return node instanceof RXListCell;
    }

    @Override
    protected void applyCellState(RXListCell<T> cell, int index) {
        MultipleSelectionModel<T> selectionModel = control.getSelectionModel();
        cell.updateSelected(selectionModel != null && selectionModel.isSelected(index));
        // The keyboard focus ring tracks the focused index regardless of whether the
        // control currently owns scene focus (the list keeps its cursor position).
        cell.updateListFocus(focusModel != null && focusModel.getFocusedIndex() == index);
    }

    @Override
    protected RXListCell<T> createCell() {
        Callback<RXListView<T>, RXListCell<T>> factory = control.getCellFactory();
        RXListCell<T> cell = factory != null ? factory.call(control) : createDefaultCell();
        cell.updateListView(control);
        cell.setManaged(false);
        return cell;
    }

    private RXListCell<T> createDefaultCell() {
        return new RXListCell<>();
    }
}

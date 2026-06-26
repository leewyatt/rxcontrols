package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.util.Callback;

/**
 * Self-built virtualizing viewport for {@link RXListViewSkin}, the list-shaped
 * member of the {@link RXVirtualViewportBase} family: a single column, one item
 * per row, uniform height, with no multi-column / justify / reorder machinery.
 *
 * <p>The single-column fixed-height geometry is supplied by a
 * {@link RXListRowPlan} that the skin builds (item count + resolved row height);
 * because the content height is width-independent there is no two-pass
 * column / scroll-bar interplay — this viewport derives the bar straight from the
 * plan. The visible item range is reported back so the skin can publish metrics.
 *
 * @param <T> the item type
 */
final class RXListViewport<T> extends RXVirtualViewportBase<T, RXListCell<T>> {

    private final RXListView<T> control;

    // Supplied by the skin (item count + resolved row height) as the geometry
    // source of truth; this viewport then decides the scroll bar from it.
    private RXListRowPlan rowPlan;

    RXListViewport(RXListView<T> control) {
        this.control = control;
    }

    // ==================== Skin-facing API ====================

    /**
     * Installs the geometry plan for this pass. The skin builds it (item count +
     * resolved row height) as the single geometry source; this viewport derives
     * its scroll bar and visible window from it.
     *
     * @param plan the row plan, never {@code null}
     */
    void setRowPlan(RXListRowPlan plan) {
        this.rowPlan = plan;
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
        if (plan == null || plan.itemCount() == 0) {
            return true;
        }
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        double target = targetScrollFor(plan.topOfItem(index), plan.rowHeight(), viewportHeight, alignment);
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
        if (plan == null || plan.itemCount() == 0) {
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
        if (plan.itemCount() == 0) {
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

        int first = plan.firstItemAt(scrollY);
        int last = plan.firstItemAt(scrollY + h - 1.0);
        int maxItem = plan.itemCount() - 1;
        if (last > maxItem) {
            last = maxItem;
        }
        if (last < first) {
            last = first;
        }
        fillVisibleRows(plan, first, last, contentWidth);
    }

    private void fillVisibleRows(RXListRowPlan plan, int first, int last, double contentWidth) {
        double rowHeight = snapSizeY(plan.rowHeight());
        int cellCursor = 0;
        for (int index = first; index <= last; index++) {
            RXListCell<T> cell = acquireCell(cellCursor++);
            String oldStyle = cell.getStyle();
            cell.updateIndex(index);
            cell.setVisible(true);
            applyCellState(cell, index);
            applyCssAfterCellUpdate(cell, oldStyle);
            double y = snapPositionY(plan.topOfItem(index) - scrollY);
            cell.resizeRelocate(snapPositionX(0.0), y, contentWidth, rowHeight);
        }
        parkCellsFrom(cellCursor);
        visibleFirstIndex = first;
        visibleLastIndex = last;
    }

    // ==================== Geometry hooks ====================

    @Override
    protected double unitScrollIncrement() {
        return rowPlan.rowHeight();
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

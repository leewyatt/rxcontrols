package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSectionCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.css.PseudoClass;
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

    // Sub-pixel threshold for the sticky header's :pinned state: it only elevates
    // once content has actually scrolled under it, not when a section rests at the top.
    private static final double STICKY_PINNED_EPSILON = 0.5;

    private static final PseudoClass PINNED_PSEUDO_CLASS = PseudoClass.getPseudoClass("pinned");

    // Below this px difference a measured row height matches the placed (cached /
    // estimated) height, so no correction is reported — stops a re-measure churn loop.
    private static final double MEASURE_EPSILON = 0.5;

    /**
     * Receives a measured cell height for the variable-height path. Primitive
     * {@code int} / {@code double} to keep the hot measure loop free of boxing.
     */
    @FunctionalInterface
    interface HeightSink {
        void onMeasured(int index, double measuredHeight);
    }

    private final RXListView<T> control;

    private final List<RXListSectionCell> headerPool = new ArrayList<>();
    // The single pinned section header (sticky subheader). Created lazily only when
    // enabled, removed when disabled, recreated on a header-factory change. It lives
    // in the overlay layer (above content, below the scroll bar), so it never joins
    // the recycling pool.
    private RXListSectionCell stickyHeader;

    // Supplied by the skin (sections + resolved row height) as the geometry source
    // of truth; this viewport then decides the scroll bar from it.
    private RXListRowPlan rowPlan;

    // Published to the skin after each pass (null when flat or empty).
    private RXListSection topSection;

    // ==================== Variable-height measure / anchor ====================

    // Variable-height path only: when the gate is open, realized data cells are measured
    // and any changed height reported to the sink for the skin to re-pack. Both stay off
    // on the fixed-height fast path.
    private boolean measureGate;
    private HeightSink heightSink;

    // Anchor pin (variable-height only): the previously top-most visible item and its
    // on-screen offset, re-captured each fill and re-applied before the next reflow so the
    // view does not jump when an off-screen estimate is corrected to a real height.
    private int anchorIndex = -1;
    private double anchorOffset;

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
     * Installs the sink that receives measured data-row heights on the variable-height
     * path. The skin records them into its height cache.
     *
     * @param heightSink the measure sink
     */
    void setHeightSink(HeightSink heightSink) {
        this.heightSink = heightSink;
    }

    /**
     * Opens or closes the measure gate. The skin opens it for the variable-height path
     * (so realized cells are measured) and closes it for the fixed-height fast path.
     *
     * @param measureGate whether to measure realized cells
     */
    void setMeasureGate(boolean measureGate) {
        this.measureGate = measureGate;
    }

    /**
     * Drops the anchor pin so the next fill re-pins fresh. The skin calls this on an
     * items-list change, whose index shifts would otherwise make the pin re-target a
     * different item and jump the scroll.
     */
    void resetAnchor() {
        anchorIndex = -1;
        anchorOffset = 0.0;
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
        // Clamp defensively (the variable-height plan indexes per-item arrays directly);
        // the sole caller already clamps, but this keeps the method safe on its own.
        int clamped = Math.max(0, Math.min(index, plan.itemCount() - 1));
        int visualRow = plan.visualRowOfItem(clamped);
        RXListRowPlan.RowInfo info = plan.rowInfo(visualRow);
        double maxScroll = Math.max(0.0, plan.contentHeight() - viewportHeight);
        // Land the item within the area below an active sticky header instead of under it.
        double target = targetScrollFor(info.top(), info.height(), viewportHeight,
                stickyOverlayHeight(plan), alignment);
        scrollY = RXMath.clamp(target, 0.0, maxScroll);
        // An explicit scroll target overrides the variable-height anchor pin for this pass.
        explicitScrollPending = true;
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
        scrollY = RXMath.clamp(target, 0.0, maxScroll);
        explicitScrollPending = true;
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
        double target = RXMath.clamp(scrollY + deltaY, 0.0, maxScroll);
        if (target != scrollY) {
            scrollY = target;
            explicitScrollPending = true;
            requestLayout();
        }
        return true;
    }

    /**
     * Adds or removes the pinned sticky header in response to the control's
     * {@code stickySectionHeader} property; the skin wires its listener and an
     * initial sync to this.
     *
     * @param enabled whether sticky headers are enabled
     */
    void setStickyEnabled(boolean enabled) {
        // The node is built lazily on the first pass where the sticky is actually
        // active (layoutStickyHeader -> ensureStickyHeader), so a flat list — the
        // default, with stickySectionHeader == true — never allocates one. Only the
        // disable path acts here, dropping any node a prior grouped pass created.
        // (Unlike RXTileViewport, the list has no marquee overlay, so there is no
        // z-order reason to create it eagerly.)
        if (!enabled && stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
        requestLayout();
    }

    /**
     * Discards the section-header pool; used when the section-header factory
     * changes. A normal layout repopulates it.
     */
    void recreateHeaders() {
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        // Drop the sticky too; the next layout rebuilds it with the new factory.
        if (stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
        requestLayout();
    }

    @Override
    protected void dispose() {
        contentLayer.getChildren().removeAll(headerPool);
        headerPool.clear();
        if (stickyHeader != null) {
            getChildren().remove(stickyHeader);
            stickyHeader = null;
        }
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

        // Anchor pin (variable-height only): keep the previously top-most visible item at
        // its old screen offset across a reflow (an off-screen estimate corrected to a real
        // height shifts the prefix sum). Skipped right after an explicit scroll (wheel, bar
        // drag, scrollTo / scrollBy). Self-correcting: a no-op when the geometry is unchanged.
        if (plan.variable() && !explicitScrollPending && anchorIndex >= 0 && anchorIndex < plan.itemCount()) {
            double anchorTop = plan.itemTop(anchorIndex);
            if (anchorTop >= 0.0) {
                scrollY = anchorTop - anchorOffset;
            }
        }
        explicitScrollPending = false;
        scrollY = RXMath.clamp(scrollY, 0.0, maxScroll);

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
        layoutStickyHeader(plan, contentWidth);
    }

    private void fillVisibleRows(RXListRowPlan plan, int first, int last, double contentWidth) {
        boolean variable = plan.variable();
        // Fixed mode: every data row is the uniform height (snapped once); variable mode
        // reads each row's height from the plan's prefix sum.
        double uniformRowHeight = variable ? 0.0 : snapSizeY(plan.rowHeight());
        // When the sticky overlay is active it is the sole renderer of the top
        // section's header, so the in-flow copy of that header row is skipped here.
        boolean stickyActive = isStickyHeaderActive(plan);
        int cellCursor = 0;
        int headerCursor = 0;
        int firstItem = -1;
        int lastItem = -1;
        int anchorCandidate = -1;
        double anchorCandidateTop = 0.0;
        for (int visualRow = first; visualRow <= last; visualRow++) {
            RXListRowPlan.RowInfo info = plan.rowInfo(visualRow);
            double rowTop = snapPositionY(info.top() - scrollY);
            if (info.header()) {
                if (stickyActive && info.section().sectionIndex() == topSection.sectionIndex()) {
                    continue;
                }
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
                    // The first (top-most) visible data row is the anchor: pin its
                    // content-space top so a reflow keeps it on screen.
                    anchorCandidate = itemIndex;
                    anchorCandidateTop = info.top();
                }
                lastItem = itemIndex;
                double cellHeight = variable ? snapSizeY(info.height()) : uniformRowHeight;
                RXListCell<T> cell = acquireCell(cellCursor++);
                String oldStyle = cell.getStyle();
                cell.updateIndex(itemIndex);
                cell.setVisible(true);
                applyCellState(cell, itemIndex);
                applyCssAfterCellUpdate(cell, oldStyle);
                cell.resizeRelocate(snapPositionX(0.0), rowTop, contentWidth, cellHeight);
                if (variable) {
                    measureCell(cell, itemIndex, contentWidth, info.height());
                }
            }
        }
        parkCellsFrom(cellCursor);
        parkHeadersFrom(headerCursor);
        visibleFirstIndex = firstItem;
        visibleLastIndex = lastItem;
        anchorIndex = anchorCandidate;
        anchorOffset = anchorCandidate >= 0 ? anchorCandidateTop - scrollY : 0.0;
    }

    // Variable-height path: measure the realized cell's real pref height at its content
    // width and, when it differs from the placed (cached / estimated) height, report the
    // correction so the skin's height cache can re-pack on the next converge iteration.
    private void measureCell(RXListCell<T> cell, int itemIndex, double width, double placedHeight) {
        if (!measureGate || heightSink == null) {
            return;
        }
        // A custom cell may switch a style class (padding / font / wrap) in updateItem
        // without dirtying layout, which the inline-style check does not catch; force CSS
        // now (cheap when already clean) so the measured height is not stale.
        if (cell.getScene() != null) {
            cell.applyCss();
        }
        double real = snapSizeY(cell.prefHeight(width));
        if (Math.abs(real - placedHeight) > MEASURE_EPSILON) {
            heightSink.onMeasured(itemIndex, real);
        }
    }

    @Override
    protected void clearVisibleMetrics() {
        super.clearVisibleMetrics();
        topSection = null;
        anchorIndex = -1;
        anchorOffset = 0.0;
    }

    @Override
    protected void parkAllRealized() {
        parkCellsFrom(0);
        parkHeadersFrom(0);
        parkStickyHeader();
    }

    // ==================== Sticky section header ====================

    // Single creation entry. The sticky shares the section-header factory and style
    // class but adds a 'sticky' class so tests / CSS can target it; pickOnBounds
    // makes the whole strip block clicks from reaching the cells scrolled under it.
    private void ensureStickyHeader() {
        if (stickyHeader == null) {
            stickyHeader = createHeader();
            stickyHeader.getStyleClass().add("sticky");
            stickyHeader.setPickOnBounds(true);
            addOverlay(stickyHeader);
        }
    }

    // The single gate shared by the fill skip (fillVisibleRows) and the sticky
    // placement so the two never disagree (no "skipped the in-flow header but the
    // sticky did not show" hole).
    private boolean isStickyHeaderActive(RXListRowPlan plan) {
        return control.isStickySectionHeader()
                && plan != null && plan.headersShown()
                && plan.totalVisualRows() > 0 && topSection != null;
    }

    // The height the sticky overlay occupies at the top after a scroll (0 when it
    // will not be shown). Used by item scroll-to so the target lands below the
    // pinned header. Deliberately independent of the current topSection (which is
    // stale on the pending-scroll path) — only whether the sticky will be shown.
    private double stickyOverlayHeight(RXListRowPlan plan) {
        return control.isStickySectionHeader() && plan.headersShown()
                ? snapSizeY(plan.headerHeight()) : 0.0;
    }

    private void layoutStickyHeader(RXListRowPlan plan, double contentWidth) {
        if (!isStickyHeaderActive(plan)) {
            parkStickyHeader();
            return;
        }
        ensureStickyHeader();
        double stickyH = snapSizeY(plan.headerHeight());
        int topIndex = topSection.sectionIndex();

        // Handoff: once the next section's header rises into the [0, stickyH] band it
        // pushes the pinned header up; before that the pinned header rests at the top.
        double stickyY = 0.0;
        if (topIndex + 1 < plan.sectionCount()) {
            double nextY = plan.sectionTop(topIndex + 1) - scrollY;
            if (nextY < stickyH) {
                stickyY = nextY - stickyH;
            }
        }

        // Re-bind only when the pinned section actually changes; the sticky usually
        // shows the same section across many scroll frames, so this skips a per-frame
        // updateItem on the (possibly heavy) factory cell. Section records are fresh
        // instances after a recompute, so reference inequality is the right signal.
        if (stickyHeader.getItem() != topSection) {
            String oldStyle = stickyHeader.getStyle();
            stickyHeader.updateSection(topSection);
            applyCssAfterCellUpdate(stickyHeader, oldStyle);
        }
        stickyHeader.setVisible(true);
        stickyHeader.resizeRelocate(snapPositionX(0.0), snapPositionY(stickyY), contentWidth, stickyH);

        boolean pinned = scrollY > plan.sectionTop(topIndex) + STICKY_PINNED_EPSILON;
        stickyHeader.pseudoClassStateChanged(PINNED_PSEUDO_CLASS, pinned);
    }

    private void parkStickyHeader() {
        if (stickyHeader == null) {
            return;
        }
        if (stickyHeader.isVisible() || stickyHeader.getItem() != null) {
            stickyHeader.setVisible(false);
            stickyHeader.updateSection(null);
        }
        stickyHeader.pseudoClassStateChanged(PINNED_PSEUDO_CLASS, false);
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
        if (plan != null && plan.variable()) {
            // Variable rows have no single height; step by the estimate (always positive)
            // so the wheel / arrow increment is independent of which rows are measured.
            return RXListViewSkin.estimatedCellSizeOrDefault(control);
        }
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

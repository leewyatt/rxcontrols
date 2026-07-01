package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import io.github.leewyatt.rxcontrols.internal.smooth.RXSmoothScrollEngine;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared shell for the self-built virtualizing viewports of the RxControls
 * scrolling-list family ({@code RXListViewport}, {@code RXTileViewport},
 * {@code RXMasonryViewport}). It owns everything that is geometry-independent:
 * the content host {@link Pane}, a vertical {@link ScrollBar}, the viewport and
 * content clips, the reflow scroll offset (folded into each cell's layout
 * position, never {@code translateY}), the chrome insets, the data-cell reuse
 * pool and the wheel / scroll-bar wiring.
 *
 * <p>Subclasses supply the control-specific geometry: each keeps its own
 * {@link #layoutChildren()} (the null-plan / empty-plan guard treatment and the
 * fill loop differ per control, so there is deliberately no template method
 * here) and implements the geometry hooks below. The shell exposes composable
 * helpers — {@link #syncViewportClip}, {@link #configureAndPositionScrollBar},
 * {@link #layoutContentLayer}, {@link #resetToEmptyState}, {@link #targetScrollFor},
 * {@link #acquireCell}, {@link #parkCellsFrom} — so each {@code layoutChildren}
 * override stays thin.
 *
 * @param <T> the item type
 * @param <C> the realized cell type, an {@link IndexedCell} of {@code T}
 */
abstract class RXVirtualViewportBase<T, C extends IndexedCell<T>> extends Region {

    // ==================== Constants ====================

    // Below this px difference the bar value already matches the scroll offset, so
    // a programmatic setValue would be a redundant no-op (and a feedback risk).
    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;

    // ==================== Shell nodes ====================

    private final ScrollBar vbar = new ScrollBar();
    protected final Pane contentLayer = new Pane();
    private final Rectangle viewportClip = new Rectangle();
    private final Rectangle contentClip = new Rectangle();

    // ==================== Cell pool ====================

    protected final List<C> cellPool = new ArrayList<>();

    // ==================== Scroll state ====================

    protected double scrollY;
    protected double cachedMaxScroll;
    // Written by the shared wheel / bar / scroll-by handlers; consumed by the animated
    // subclasses' layoutChildren (to suppress the anchor pin + reorder detection for one
    // pass) and by the list viewport's variable-height layoutChildren (to suppress its
    // anchor pin for one pass after an explicit scroll).
    protected boolean explicitScrollPending;
    private boolean adjustingScrollBar;
    private final RXSmoothScrollEngine smoothScrollEngine;

    // ==================== Chrome insets ====================

    private double chromeLeft;
    private double chromeTop;
    private double chromeRight;
    private double chromeBottom;

    // ==================== Focus model ====================

    // The skin's internal focus model; selection comes from the (control-typed)
    // control via the applyCellState hook. Both feed the per-cell :selected /
    // focus-ring state, re-applied on every (re)bind.
    protected RXIndexedFocusModel<T> focusModel;

    // ==================== Published metrics ====================

    // Published to the skin after each pass (-1 when nothing is visible).
    protected int visibleFirstIndex = -1;
    protected int visibleLastIndex = -1;

    // ==================== Listeners ====================

    private final ChangeListener<Number> scrollBarValueListener;
    private final EventHandler<ScrollEvent> scrollHandler;

    // ==================== Constructor ====================

    protected RXVirtualViewportBase() {
        getStyleClass().add("viewport");
        setPickOnBounds(true);
        setClip(viewportClip);

        contentLayer.getStyleClass().add("content");
        contentLayer.setManaged(false);
        contentLayer.setPickOnBounds(true);
        contentLayer.setClip(contentClip);

        vbar.setOrientation(Orientation.VERTICAL);
        vbar.setManaged(false);
        vbar.setVisible(false);
        vbar.setMin(0.0);
        getChildren().addAll(contentLayer, vbar);

        scrollBarValueListener = (obs, oldValue, newValue) -> onScrollBarValue();
        scrollHandler = this::onScroll;
        vbar.valueProperty().addListener(scrollBarValueListener);
        addEventHandler(ScrollEvent.SCROLL, scrollHandler);
        smoothScrollEngine = new RXSmoothScrollEngine(new VirtualViewportSmoothScrollable(this));
    }

    // ==================== Geometry hooks (control-specific) ====================

    /**
     * Creates one realized data cell, wired to its control (factory or default
     * cell + {@code updateXxxView} + {@code setManaged(false)}).
     *
     * @return a new cell for the pool
     */
    protected abstract C createCell();

    /**
     * Re-applies the {@code :selected} state and keyboard focus ring of one cell
     * from the control's selection model and the shared {@link #focusModel}.
     *
     * @param cell  the cell to update
     * @param index the item index it currently renders
     */
    protected abstract void applyCellState(C cell, int index);

    /**
     * The vertical scroll-bar unit (wheel / arrow) increment in pixels. Fixed-row
     * viewports return the row height; the variable-height viewport returns a
     * height-independent estimate.
     *
     * @return the unit increment, {@code > 0}
     */
    protected abstract double unitScrollIncrement();

    /**
     * Whether the node is one of this viewport's own realized data cells (used by
     * {@link #cellAt} to stop the walk at the right cell type).
     *
     * @param node a node on the walk up from an event target
     * @return {@code true} if it is this viewport's cell type
     */
    protected abstract boolean isOwnCell(Node node);

    // ==================== Override-default hooks ====================

    /**
     * Resets a cell's auxiliary state when it is parked, <em>before</em> the empty
     * {@code updateIndex(-1)} update so {@code updateItem(empty)} never sees stale
     * focus / translate / grid state. Default is a no-op; the tile / masonry
     * viewports reset their focus ring, glide translate and slot position here.
     *
     * @param cell the cell being parked
     */
    protected void onCellParked(C cell) {
    }

    /**
     * Whether a cell must be skipped by the recycler because it is mid-glide.
     * Default {@code false}; the animated viewports return {@code true} for cells
     * in their in-flight set.
     *
     * @param cell a pooled cell
     * @return {@code true} to skip parking it
     */
    protected boolean isPinnedForAnimation(C cell) {
        return false;
    }

    /**
     * Parks every realized renderable into the empty state. Default parks the data
     * cell pool; the tile viewport also parks its header pool and sticky header.
     */
    protected void parkAllRealized() {
        parkCellsFrom(0);
    }

    /**
     * Clears the published visible range. Default resets the item-index metrics;
     * subclasses override (calling {@code super}) to also clear their row /
     * section / anchor state.
     */
    protected void clearVisibleMetrics() {
        visibleFirstIndex = -1;
        visibleLastIndex = -1;
    }

    /**
     * Applies a relative pixel scroll, clamped to the cached scrollable range
     * (so it is only valid after a sized layout pass). This is the tile / masonry
     * contract; the list viewport overrides with a plan-fresh, pending-aware
     * variant.
     *
     * @param deltaY the signed pixel delta (positive scrolls down)
     * @return {@code true} if the offset actually changed
     */
    protected boolean scrollByPixels(double deltaY) {
        double maxScroll = cachedMaxScroll;
        if (maxScroll <= 0.0 || deltaY == 0.0) {
            return false;
        }
        double target = RXMath.clamp(scrollY + deltaY, 0.0, maxScroll);
        stopSmoothScrolling();
        return setVerticalScrollOffset(target, ScrollOffsetWriteReason.PROGRAMMATIC_JUMP);
    }

    /**
     * Discards the data cell pool (the only path that drops cell instances); used
     * when the cell factory changes. A normal layout repopulates it. Subclasses
     * with in-flight glides override to snap them first.
     */
    protected void recreateCells() {
        contentLayer.getChildren().removeAll(cellPool);
        cellPool.clear();
        requestLayout();
    }

    /**
     * Releases the shell: removes the listeners, drops the cell pool and clears the
     * clips. Subclasses with extra pools / glides override to snap and drop those
     * first, then call {@code super.dispose()}.
     */
    protected void dispose() {
        smoothScrollEngine.dispose();
        vbar.valueProperty().removeListener(scrollBarValueListener);
        removeEventHandler(ScrollEvent.SCROLL, scrollHandler);
        contentLayer.getChildren().removeAll(cellPool);
        cellPool.clear();
        contentLayer.setClip(null);
        setClip(null);
    }

    // ==================== Skin-facing API ====================

    /**
     * The vertical scroll-bar's CSS-preferred breadth (never a hard-coded guess),
     * so a multi-column skin can subtract it when deciding column count.
     *
     * @return the scroll-bar breadth in pixels
     */
    final double scrollBarBreadth() {
        return snapSizeX(vbar.prefWidth(-1));
    }

    /**
     * Inserts an overlay node directly below the scroll bar (above content).
     *
     * @param overlay the overlay node
     */
    final void addOverlay(Node overlay) {
        getChildren().add(getChildren().indexOf(vbar), overlay);
    }

    /**
     * Sets the chrome insets the viewport clip and scroll bar expand into.
     *
     * @param left   the left inset
     * @param top    the top inset
     * @param right  the right inset
     * @param bottom the bottom inset
     */
    final void setChromeInsets(double left, double top, double right, double bottom) {
        chromeLeft = Math.max(0.0, left);
        chromeTop = Math.max(0.0, top);
        chromeRight = Math.max(0.0, right);
        chromeBottom = Math.max(0.0, bottom);
    }

    /**
     * Sets the skin's focus model, used for the per-cell keyboard focus ring.
     *
     * @param focusModel the focus model
     */
    final void setFocusModel(RXIndexedFocusModel<T> focusModel) {
        this.focusModel = focusModel;
    }

    /**
     * @return the first visible item index, or {@code -1} when nothing is visible
     */
    final int getVisibleFirstIndex() {
        return visibleFirstIndex;
    }

    /**
     * @return the last visible item index, or {@code -1} when nothing is visible
     */
    final int getVisibleLastIndex() {
        return visibleLastIndex;
    }

    /**
     * The current scroll offset in pixels.
     *
     * @return the scroll offset
     */
    final double scrollOffset() {
        return scrollY;
    }

    /**
     * Returns the vertical pixel offset used by the smooth scrolling adapter.
     *
     * @return the vertical scroll offset
     */
    final double verticalScrollOffset() {
        return scrollY;
    }

    /**
     * Returns the maximum vertical pixel offset used by the smooth scrolling adapter.
     *
     * @return the maximum vertical scroll offset
     */
    final double maxVerticalScrollOffset() {
        return Math.max(0.0, computeMaxVerticalScrollOffset());
    }

    /**
     * Returns the line-sized vertical increment used for text-unit wheel deltas.
     *
     * @return the vertical unit increment
     */
    final double verticalUnitIncrement() {
        return unitScrollIncrement();
    }

    /**
     * Writes the vertical pixel offset and requests layout.
     *
     * @param value  the requested scroll offset
     * @param reason why the offset is being written
     * @return {@code true} if the offset changed
     */
    final boolean setVerticalScrollOffset(double value, ScrollOffsetWriteReason reason) {
        double target = RXMath.clamp(value, 0.0, maxVerticalScrollOffset());
        if (target == scrollY) {
            return false;
        }
        scrollY = target;
        explicitScrollPending = true;
        requestLayout();
        return true;
    }

    /**
     * The content area width (viewport width minus the bar column when shown),
     * derived from the cached scroll range so it is valid after a layout pass.
     *
     * @return the content width
     */
    final double contentWidth() {
        double barBreadth = cachedMaxScroll > 0.0 ? scrollBarBreadth() : 0.0;
        return Math.max(0.0, getWidth() - barBreadth);
    }

    /**
     * Re-applies the {@code :selected} state and keyboard focus ring to every
     * realized cell. Cheap path for selection / focus changes that do not need a
     * full relayout.
     */
    final void refreshSelectionAndFocus() {
        for (C cell : cellPool) {
            int index = cell.getIndex();
            if (cell.isVisible() && index >= 0) {
                applyCellState(cell, index);
            }
        }
    }

    /**
     * The realized data cell under the given event target, or {@code null} when
     * the target is not a (non-empty) cell of this viewport.
     *
     * @param target the event target (typically {@code MouseEvent.getTarget()})
     * @return the hit cell, or {@code null}
     */
    @SuppressWarnings("unchecked")
    final C cellAt(EventTarget target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != this) {
            if (isOwnCell(node)) {
                C cell = (C) node;
                if (!cell.isEmpty() && cell.getIndex() >= 0) {
                    return cell;
                }
                return null;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Scrolling ====================

    private void onScrollBarValue() {
        if (adjustingScrollBar) {
            return;
        }
        stopSmoothScrolling();
        setVerticalScrollOffset(vbar.getValue(), ScrollOffsetWriteReason.SCROLL_BAR);
    }

    private void onScroll(ScrollEvent event) {
        boolean consume = smoothScrollEngine.handleScroll(event, ScrollAxis.VERTICAL,
                RXSmoothScrollOptions.DEFAULT_DURATION, RXSmoothScrollOptions.DEFAULT_INTERPOLATOR,
                RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER, smoothScrollMode(), ScrollBoundaryPolicy.CHAIN,
                true, false, smoothScrollingEnabled() && !event.isDirect(), true);
        if (consume) {
            event.consume();
        }
    }

    // ==================== Layout helpers ====================

    /**
     * Sizes the viewport clip to the chrome-expanded bounds so cells may paint
     * into the padding band. Called first by each subclass {@code layoutChildren}.
     *
     * @param w the viewport width
     * @param h the viewport height
     */
    protected final void syncViewportClip(double w, double h) {
        viewportClip.setX(-chromeLeft);
        viewportClip.setY(-chromeTop);
        viewportClip.setWidth(w + chromeLeft + chromeRight);
        viewportClip.setHeight(h + chromeTop + chromeBottom);
    }

    /**
     * Configures and positions the vertical scroll bar for this pass, or hides it
     * when nothing overflows. All bar mutations run under the value-sync guard.
     *
     * @param maxScroll the maximum scroll offset ({@code contentHeight - h})
     * @param w         the viewport width
     * @param h         the viewport height
     * @return the bar breadth in pixels (0 when no bar is shown)
     */
    protected final double configureAndPositionScrollBar(double maxScroll, double w, double h) {
        boolean needBar = maxScroll > 0.0;
        double barBreadth = needBar ? scrollBarBreadth() : 0.0;
        if (needBar) {
            adjustingScrollBar = true;
            vbar.setMax(maxScroll);
            vbar.setVisibleAmount(h);
            vbar.setUnitIncrement(unitScrollIncrement());
            vbar.setBlockIncrement(h);
            if (Math.abs(vbar.getValue() - scrollY) > SCROLL_BAR_SYNC_EPSILON) {
                vbar.setValue(scrollY);
            }
            adjustingScrollBar = false;
            double barX = w - barBreadth + (chromeRight < 1.0 ? 0.0 : chromeRight - 1.0);
            vbar.resizeRelocate(barX, -chromeTop, barBreadth, h + chromeTop + chromeBottom);
            vbar.setVisible(true);
        } else {
            vbar.setVisible(false);
        }
        return barBreadth;
    }

    /**
     * Positions the content host at the origin and syncs its clip to the content
     * area (the caller passes a width that already excludes the bar column).
     *
     * @param width  the content width
     * @param height the content height
     */
    protected final void layoutContentLayer(double width, double height) {
        contentLayer.resizeRelocate(0.0, 0.0, width, height);
        contentClip.setX(0.0);
        contentClip.setY(0.0);
        contentClip.setWidth(width);
        contentClip.setHeight(height);
    }

    /**
     * Drives the viewport to its empty / not-sized state: sizes the content host,
     * zeroes the scroll range, optionally resets the offset and syncs the bar to
     * the top, parks every renderable, hides the bar and clears the metrics.
     *
     * @param contentWidth  the content host width for this state
     * @param contentHeight the content host height for this state
     * @param resetScroll   {@code true} to reset {@code scrollY} to 0 and sync the
     *                      bar value to 0 (the genuinely-empty case); {@code false}
     *                      to preserve the offset (the not-yet-sized case)
     */
    protected final void resetToEmptyState(double contentWidth, double contentHeight, boolean resetScroll) {
        layoutContentLayer(contentWidth, contentHeight);
        if (resetScroll) {
            stopSmoothScrolling();
            scrollY = 0.0;
        }
        cachedMaxScroll = 0.0;
        explicitScrollPending = false;
        adjustingScrollBar = true;
        vbar.setMax(0.0);
        vbar.setVisibleAmount(0.0);
        if (resetScroll && Math.abs(vbar.getValue()) > SCROLL_BAR_SYNC_EPSILON) {
            vbar.setValue(0.0);
        }
        adjustingScrollBar = false;
        parkAllRealized();
        vbar.setVisible(false);
        clearVisibleMetrics();
    }

    /**
     * The clamped scroll offset that lands a target row / item per
     * {@code alignment}, given its top and height, against the full viewport.
     * Shared alignment math; each subclass reduces its plan entry to
     * {@code (top, height)} first.
     *
     * @param targetTop      the target's top in content coordinates
     * @param targetHeight   the target's height
     * @param viewportHeight the viewport height
     * @param alignment      where the target should land
     * @return the desired (unclamped) scroll offset
     */
    protected final double targetScrollFor(double targetTop, double targetHeight,
                                           double viewportHeight, ScrollAlignment alignment) {
        return targetScrollFor(targetTop, targetHeight, viewportHeight, 0.0, alignment);
    }

    /**
     * Variant that treats the top {@code topInset} pixels of the viewport as
     * occluded (e.g. by a pinned sticky header), landing the target within the
     * usable area {@code [topInset, viewportHeight)} so it is not hidden behind the
     * overlay. {@code START} aligns to {@code topInset}, {@code CENTER} centers in
     * the usable area and {@code NEAREST} measures visibility against it;
     * {@code END} is unaffected (it is bottom-aligned, and the overlay sits at the
     * top). A {@code topInset} of {@code 0} reduces to the full-viewport variant.
     *
     * @param targetTop      the target's top in content coordinates
     * @param targetHeight   the target's height
     * @param viewportHeight the viewport height
     * @param topInset       the occluded height at the top of the viewport
     * @param alignment      where the target should land
     * @return the desired (unclamped) scroll offset
     */
    protected final double targetScrollFor(double targetTop, double targetHeight,
                                           double viewportHeight, double topInset, ScrollAlignment alignment) {
        double targetBottom = targetTop + targetHeight;
        return switch (alignment) {
            case CENTER -> targetTop - topInset - (viewportHeight - topInset - targetHeight) / 2.0;
            case END -> targetBottom - viewportHeight;
            case NEAREST -> {
                if (targetTop < scrollY + topInset) {
                    yield targetTop - topInset;
                }
                if (targetBottom > scrollY + viewportHeight) {
                    yield targetBottom - viewportHeight;
                }
                yield scrollY;
            }
            default -> targetTop - topInset;
        };
    }

    /**
     * Flushes any CSS mutated by {@code updateItem} before this frame is painted,
     * mirroring {@code VirtualFlow.setCellIndex}.
     *
     * @param cell           the cell that was just (re)bound
     * @param oldInlineStyle its inline style before the bind
     */
    protected static void applyCssAfterCellUpdate(Control cell, String oldInlineStyle) {
        if (cell.getScene() != null
                && (cell.isNeedsLayout() || !Objects.equals(oldInlineStyle, cell.getStyle()))) {
            cell.applyCss();
        }
    }

    /**
     * Computes the current maximum vertical scroll offset.
     *
     * @return the maximum vertical scroll offset
     */
    protected double computeMaxVerticalScrollOffset() {
        return cachedMaxScroll;
    }

    /**
     * Returns whether wheel input should animate for this viewport.
     *
     * @return {@code true} to animate indirect wheel input
     */
    protected boolean smoothScrollingEnabled() {
        return true;
    }

    /**
     * Returns the smooth scroll animation mode for indirect wheel input.
     *
     * @return the smooth scroll mode
     */
    protected SmoothScrollMode smoothScrollMode() {
        return RXSmoothScrollOptions.DEFAULT_MODE;
    }

    /**
     * Stops the active smooth wheel animation without writing another offset.
     */
    protected final void stopSmoothScrolling() {
        smoothScrollEngine.stop();
    }

    /**
     * Stops and syncs the smooth wheel animation state to the current offset.
     */
    protected final void resetSmoothScrolling() {
        smoothScrollEngine.stop();
        smoothScrollEngine.snapToCurrentOffsets();
    }

    /**
     * Shifts the active smooth vertical value by a correction already applied to
     * {@link #scrollY}.
     *
     * @param delta the applied scroll correction
     */
    protected final void shiftSmoothScrollBy(double delta) {
        smoothScrollEngine.shiftVerticalBy(delta);
    }

    // ==================== Cell pool ====================

    /**
     * Returns the cell for the {@code slotIndex}-th visible row of this pass
     * (sequential assignment), growing the pool as needed.
     *
     * @param slotIndex the zero-based position in this pass's visible cell sequence
     * @return a cell to bind
     */
    protected final C acquireCell(int slotIndex) {
        while (cellPool.size() <= slotIndex) {
            C cell = createCell();
            cellPool.add(cell);
            contentLayer.getChildren().add(cell);
        }
        return cellPool.get(slotIndex);
    }

    /**
     * Parks every pool cell from {@code from} to the end, skipping any cell pinned
     * by an in-flight glide.
     *
     * @param from the first pool index to park
     */
    protected final void parkCellsFrom(int from) {
        for (int i = from; i < cellPool.size(); i++) {
            C cell = cellPool.get(i);
            if (isPinnedForAnimation(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    /**
     * Parks one cell: hides it, resets auxiliary state via {@link #onCellParked}
     * (before the empty update), then delivers the empty {@code updateIndex(-1)}.
     *
     * @param cell the cell to park
     */
    protected final void parkCell(C cell) {
        if (cell.isVisible() || cell.getIndex() != -1) {
            cell.setVisible(false);
            // Reset focus / translate / slot BEFORE updateIndex(-1), so the empty
            // update never sees stale auxiliary state.
            onCellParked(cell);
            cell.updateIndex(-1);
        }
    }

    // ==================== Sizing ====================

    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }
}

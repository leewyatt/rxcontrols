package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Interpolator;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Self-built virtualizing viewport for one kanban column: a single column of
 * uniform, fixed-height cards. It is the {@link RXVirtualViewportBase} family
 * member for {@link RXKanbanView}, adding two DnD-aware hooks whose layout belongs
 * to the viewport (the skin cannot reach into the fill loop):
 *
 * <ul>
 *   <li>{@link #setLiftedIndex(int)} collapses a card's slot while it is being
 *       dragged out of this column (so the column reflows as if that card were
 *       removed — the source-removed coordinate system of the drop math), and</li>
 *   <li>{@link #setDropGap(int)} opens a one-row empty slot at a drop index (in that
 *       same source-removed coordinate system) so cards below slide down.</li>
 * </ul>
 *
 * <p>Fixed-height geometry is pure arithmetic: with {@code stride = rowHeight +
 * cardSpacing}, slot {@code p} sits at {@code p * stride} and the first visible
 * slot is {@code floor(scrollY / stride)}. The scroll offset is folded into each
 * cell's layout position (never {@code translateY}), leaving {@code translate} free
 * for the reorder settle glide.
 *
 * <p>Settle glide: on any non-scroll pass where a card that a cell rendered last
 * pass now lands in a different slot (a drop gap opening / closing, a card added /
 * removed, a lift), the same node glides from its old position to the new one via
 * {@link ViewportReorderAnimator} (FLIP). Scroll passes snap. Cells mid-glide are
 * pinned so the recycler leaves them alone until they land.
 *
 * @param <T> the card type
 */
final class KanbanColumnViewport<T> extends RXVirtualViewportBase<T, RXKanbanCardCell<T>> {

    private final RXKanbanView<T> control;
    private final RXKanbanColumn<T> column;

    // The card currently lifted out of this column by a drag; its slot collapses so
    // the column reflows as if it were absent. -1 means none.
    private int liftedIndex = -1;
    // A one-row empty slot opened at this index (in the lifted-collapsed coordinate
    // system) so a dragged card has somewhere to land. -1 means none.
    private int dropGapIndex = -1;

    private final ViewportReorderAnimator reorderAnimator = new ViewportReorderAnimator();
    // Cells mid-glide, pinned so the recycler skips them until they land.
    private final Set<RXKanbanCardCell<T>> animating = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean reorderPass;
    // One-shot glide trigger: set only when the slot arrangement actually changed (a
    // card added / removed / reordered, a gap opening / closing, a lift). It gates the
    // reorder pass so a plain relayout (or the requestLayout a finished glide fires)
    // does not itself keep re-triggering glides.
    private boolean settleDirty;

    private final ListChangeListener<T> cardsListener = change -> {
        settleDirty = true;
        requestLayout();
    };

    KanbanColumnViewport(RXKanbanView<T> control, RXKanbanColumn<T> column) {
        this.control = control;
        this.column = column;
        // The base already adds "viewport"; "content" scopes it as the column's card
        // area for CSS (.rx-kanban-view > .columns > .column > .content).
        getStyleClass().add("content");
        // Each column's card area is an honest virtualized list to assistive tech; the
        // board root is the PARENT, the cards are LIST_ITEMs.
        setAccessibleRole(AccessibleRole.LIST_VIEW);
        setAccessibleText(column.getTitle());
        column.getCards().addListener(cardsListener);
    }

    // ==================== Skin-facing API ====================

    /**
     * The column this viewport renders.
     *
     * @return the column model
     */
    RXKanbanColumn<T> getColumn() {
        return column;
    }

    /**
     * Collapses the slot of a card lifted out of this column by a drag, or restores
     * all slots.
     *
     * @param index the lifted card index, or {@code -1} for none
     */
    void setLiftedIndex(int index) {
        if (index != liftedIndex) {
            liftedIndex = index;
            settleDirty = true;
            requestLayout();
        }
    }

    /**
     * Opens (or closes) a one-row drop gap at the given index (in the lifted-
     * collapsed coordinate system), sliding cards from that index down by one row.
     *
     * @param index the gap index in {@code [0, effectiveCardCount]}, or {@code -1} for no gap
     */
    void setDropGap(int index) {
        if (index != dropGapIndex) {
            dropGapIndex = index;
            settleDirty = true;
            requestLayout();
        }
    }

    /**
     * @return the current drop-gap index, or {@code -1} when no gap is open
     */
    int getDropGap() {
        return dropGapIndex;
    }

    /**
     * The number of cards this column shows after the lifted card (if any) is
     * removed — the size of the coordinate system used by {@link #setDropGap(int)}
     * and by the drop commit index.
     *
     * @return the effective (lifted-collapsed) card count
     */
    int effectiveCardCount() {
        int count = column.getCards().size();
        return liftedIndex >= 0 && liftedIndex < count ? count - 1 : count;
    }

    /**
     * The uniform row stride (card height + card spacing) used to place cards.
     *
     * @return the row stride in pixels, {@code > 0}
     */
    double rowStride() {
        return snapSizeY(RXKanbanViewSkin.prefCardHeightOrDefault(control))
                + snapSizeY(RXKanbanViewSkin.cardSpacingOrDefault(control));
    }

    /**
     * The card height (row height) used to place cards.
     *
     * @return the card height in pixels, {@code > 0}
     */
    double rowHeight() {
        return snapSizeY(RXKanbanViewSkin.prefCardHeightOrDefault(control));
    }

    /**
     * Maps a content-space y coordinate to a drop index in the lifted-collapsed
     * coordinate system, splitting each row at its mid-point. The result is in
     * {@code [0, effectiveCardCount]}.
     *
     * @param contentY the y coordinate in this viewport's content space
     * @return the drop index
     */
    int dropIndexAt(double contentY) {
        int effective = effectiveCardCount();
        double stride = rowStride();
        if (stride <= 0.0 || effective == 0) {
            return 0;
        }
        int index = (int) Math.floor((contentY + stride / 2.0) / stride);
        return RXMath.clamp(index, 0, effective);
    }

    /**
     * Scrolls so the card at {@code index} lands per {@code alignment}, using the
     * fixed-height geometry so it is correct after a sized layout pass.
     *
     * @param index     a card index
     * @param alignment where the target card should land
     */
    void scrollToCard(int index, ScrollAlignment alignment) {
        double h = getHeight();
        int count = column.getCards().size();
        if (h <= 0.0 || index < 0 || index >= count) {
            return;
        }
        double target = targetScrollFor(index * rowStride(), rowHeight(), h, alignment);
        stopSmoothScrolling();
        setVerticalScrollOffset(RXMath.clamp(target, 0.0, cachedMaxScroll),
                ScrollOffsetWriteReason.PROGRAMMATIC_JUMP);
        requestLayout();
    }

    /**
     * Stops any in-flight settle glide and snaps every cell to its final position.
     * Called by the skin when animation is switched off.
     */
    void onAnimationSettingsChanged() {
        if (!animationEnabled()) {
            snapAllGlides();
            requestLayout();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        syncViewportClip(w, h);

        if (w <= 0.0 || h <= 0.0) {
            resetToEmptyState(0.0, 0.0, false);
            return;
        }

        ObservableList<T> cards = column.getCards();
        int count = cards.size();
        int lifted = liftedIndex >= 0 && liftedIndex < count ? liftedIndex : -1;
        int effectiveCount = count - (lifted >= 0 ? 1 : 0);
        int gapIndex = dropGapIndex >= 0 && dropGapIndex <= effectiveCount ? dropGapIndex : -1;
        int slots = effectiveCount + (gapIndex >= 0 ? 1 : 0);
        if (slots == 0) {
            snapAllGlides();
            resetToEmptyState(w, h, true);
            return;
        }

        double rowH = snapSizeY(RXKanbanViewSkin.prefCardHeightOrDefault(control));
        double gap = snapSizeY(RXKanbanViewSkin.cardSpacingOrDefault(control));
        double stride = rowH + gap;
        double contentHeight = slots * rowH + (slots - 1) * gap;
        double maxScroll = Math.max(0.0, contentHeight - h);
        cachedMaxScroll = maxScroll;

        // A settle glide runs only on a non-scroll pass whose slot arrangement actually
        // changed; a scroll pass snaps so the content tracks the pointer / wheel exactly,
        // and a plain relayout (or a finished glide's requestLayout) does not re-glide.
        reorderPass = settleDirty && animationEnabled() && !explicitScrollPending;
        settleDirty = false;
        explicitScrollPending = false;
        double correctedScroll = RXMath.clamp(scrollY, 0.0, maxScroll);
        double correction = correctedScroll - scrollY;
        scrollY = correctedScroll;
        if (correction != 0.0) {
            shiftSmoothScrollBy(correction);
        }

        double barBreadth = configureAndPositionScrollBar(maxScroll, w, h);
        double contentWidth = Math.max(0.0, w - barBreadth);
        layoutContentLayer(contentWidth, h);

        int firstSlot = (int) Math.floor(scrollY / stride);
        int lastSlot = (int) Math.floor((scrollY + h - 1.0) / stride);
        if (firstSlot < 0) {
            firstSlot = 0;
        }
        if (lastSlot > slots - 1) {
            lastSlot = slots - 1;
        }
        if (lastSlot < firstSlot) {
            lastSlot = firstSlot;
        }

        // On a reorder pass, snapshot which cell rendered each item BEFORE rebinding,
        // so the same node can be re-found for its item and glide to the new slot.
        Map<Integer, RXKanbanCardCell<T>> priorItemToCell = null;
        Set<RXKanbanCardCell<T>> usedThisPass = null;
        if (reorderPass) {
            priorItemToCell = new HashMap<>();
            for (RXKanbanCardCell<T> cell : cellPool) {
                if (cell.getIndex() >= 0) {
                    priorItemToCell.put(cell.getIndex(), cell);
                }
            }
            usedThisPass = new HashSet<>();
        }

        int cellCursor = 0;
        int firstItem = -1;
        int lastItem = -1;
        for (int slot = firstSlot; slot <= lastSlot; slot++) {
            if (slot == gapIndex) {
                continue;
            }
            int effectiveIndex = gapIndex < 0 || slot < gapIndex ? slot : slot - 1;
            if (effectiveIndex < 0 || effectiveIndex >= effectiveCount) {
                continue;
            }
            int itemIndex = lifted < 0 || effectiveIndex < lifted ? effectiveIndex : effectiveIndex + 1;
            double rowTop = snapPositionY(slot * stride - scrollY);
            RXKanbanCardCell<T> prior = reorderPass ? priorItemToCell.get(itemIndex) : null;
            RXKanbanCardCell<T> cell = reorderPass
                    ? acquireCellForItem(itemIndex, priorItemToCell, usedThisPass)
                    : acquireCell(cellCursor++);
            String oldStyle = cell.getStyle();
            cell.updateIndex(itemIndex);
            cell.setVisible(true);
            applyCellState(cell, itemIndex);
            applyCssAfterCellUpdate(cell, oldStyle);
            // Only a carry-over cell (the one that rendered this item last pass) glides;
            // a freshly repurposed or entering cell pops in at its slot.
            placeCell(cell, rowTop, contentWidth, rowH, cell == prior);
            if (firstItem < 0) {
                firstItem = itemIndex;
            }
            lastItem = itemIndex;
        }
        if (reorderPass) {
            parkUnusedCells(usedThisPass);
        } else {
            parkCellsFrom(cellCursor);
        }
        visibleFirstIndex = firstItem;
        visibleLastIndex = lastItem;
    }

    // ==================== Cell pool ====================

    // Reorder pass: reuse the node that rendered this item last pass so the SAME node
    // glides to its new slot; otherwise take a free, non-gliding pool cell.
    private RXKanbanCardCell<T> acquireCellForItem(int itemIndex, Map<Integer, RXKanbanCardCell<T>> prior,
                                                   Set<RXKanbanCardCell<T>> used) {
        RXKanbanCardCell<T> cell = prior.get(itemIndex);
        if (cell != null && !used.contains(cell)) {
            used.add(cell);
            return cell;
        }
        for (RXKanbanCardCell<T> candidate : cellPool) {
            if (!used.contains(candidate) && !animating.contains(candidate)) {
                used.add(candidate);
                return candidate;
            }
        }
        RXKanbanCardCell<T> created = createCell();
        cellPool.add(created);
        contentLayer.getChildren().add(created);
        used.add(created);
        return created;
    }

    // Sets a cell's final geometry. A carry-over cell on a reorder pass captures its
    // old visual position and glides translate back to zero (FLIP). A fresh cell
    // entering on a reorder pops in (translate cleared). A non-reorder pass places
    // directly and never touches translate, so an in-flight glide keeps running.
    private void placeCell(RXKanbanCardCell<T> cell, double y, double width, double height, boolean glide) {
        double x = snapPositionX(0.0);
        if (glide) {
            double oldVisualY = cell.getLayoutY() + cell.getTranslateY();
            cell.resizeRelocate(x, y, width, height);
            animating.add(cell);
            reorderAnimator.animate(cell, 0.0, oldVisualY - cell.getLayoutY(),
                    control.getAnimationDuration(), interpolatorOrDefault(), this::onGlideFinished);
            return;
        }
        if (reorderPass) {
            cell.setTranslateX(0.0);
            cell.setTranslateY(0.0);
        }
        cell.resizeRelocate(x, y, width, height);
    }

    private void onGlideFinished(Node node) {
        animating.remove(node);
        if (animating.isEmpty()) {
            requestLayout();
        }
    }

    private void parkUnusedCells(Set<RXKanbanCardCell<T>> used) {
        for (RXKanbanCardCell<T> cell : cellPool) {
            if (used.contains(cell) || animating.contains(cell)) {
                continue;
            }
            parkCell(cell);
        }
    }

    private void snapAllGlides() {
        reorderAnimator.snapAll();
        animating.clear();
    }

    // ==================== Animation gating ====================

    private boolean animationEnabled() {
        return control.isAnimated() && getScene() != null && isAnimationDurationPositive();
    }

    private boolean isAnimationDurationPositive() {
        Duration duration = control.getAnimationDuration();
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        Interpolator value = control.getAnimationInterpolator();
        return value == null ? Interpolator.EASE_BOTH : value;
    }

    // ==================== Accessibility ====================

    /**
     * Keeps this list's accessible label in sync with the column title (the header is
     * this list's label). Called by the box when the title changes.
     *
     * @param title the column title
     */
    void updateAccessibleLabel(String title) {
        setAccessibleText(title == null ? "" : title);
    }

    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        // The virtual-list attributes project the board-level position-key model onto
        // this column's subset (this column only answers for its own cards).
        return switch (attribute) {
            case ITEM_COUNT -> column.getCards().size();
            case ITEM_AT_INDEX -> {
                int index = parameters != null && parameters.length > 0 && parameters[0] instanceof Integer i ? i : -1;
                yield cellForIndex(index);
            }
            case FOCUS_ITEM -> control.getFocusedColumn() == column
                    ? cellForIndex(control.getFocusedCardIndex()) : null;
            case SELECTED_ITEMS -> {
                Node selected = control.getSelectedColumn() == column
                        ? cellForIndex(control.getSelectedCardIndex()) : null;
                yield selected == null ? List.of() : List.of(selected);
            }
            case MULTIPLE_SELECTION -> false;
            default -> super.queryAccessibleAttribute(attribute, parameters);
        };
    }

    // The realized cell rendering the given card index, or null when the index is not
    // currently virtualized into view (honest for a virtual list).
    private Node cellForIndex(int index) {
        if (index < 0) {
            return null;
        }
        for (RXKanbanCardCell<T> cell : cellPool) {
            if (cell.isVisible() && cell.getIndex() == index) {
                return cell;
            }
        }
        return null;
    }

    // ==================== Geometry hooks ====================

    @Override
    protected double unitScrollIncrement() {
        return Math.max(1.0, snapSizeY(RXKanbanViewSkin.prefCardHeightOrDefault(control)));
    }

    @Override
    protected boolean smoothScrollingEnabled() {
        return control.isSmoothScrolling();
    }

    @Override
    protected SmoothScrollMode smoothScrollMode() {
        return control.getSmoothScrollMode();
    }

    @Override
    protected boolean isOwnCell(Node node) {
        return node instanceof RXKanbanCardCell;
    }

    @Override
    protected boolean isPinnedForAnimation(RXKanbanCardCell<T> cell) {
        return animating.contains(cell);
    }

    @Override
    protected void onCellParked(RXKanbanCardCell<T> cell) {
        cell.updateCardFocus(false);
        cell.setTranslateX(0.0);
        cell.setTranslateY(0.0);
    }

    @Override
    protected void applyCellState(RXKanbanCardCell<T> cell, int index) {
        cell.updateSelected(control.getSelectedColumn() == column && control.getSelectedCardIndex() == index);
        cell.updateCardFocus(control.getFocusedColumn() == column && control.getFocusedCardIndex() == index);
    }

    @Override
    protected void recreateCells() {
        // Snap any in-flight settle glide before the pool is torn down, so no Timeline
        // is left tweening a cell removed from the scene (base contract for animated
        // viewports; mirrors RXTileViewport / RXMasonryViewport).
        snapAllGlides();
        super.recreateCells();
    }

    @Override
    protected RXKanbanCardCell<T> createCell() {
        Callback<RXKanbanView<T>, RXKanbanCardCell<T>> factory = control.getCardCellFactory();
        RXKanbanCardCell<T> cell = factory != null ? factory.call(control) : new RXKanbanCardCell<>();
        cell.updateColumn(column);
        cell.updateKanbanView(control);
        cell.setManaged(false);
        return cell;
    }

    @Override
    protected void dispose() {
        snapAllGlides();
        column.getCards().removeListener(cardsListener);
        super.dispose();
    }
}

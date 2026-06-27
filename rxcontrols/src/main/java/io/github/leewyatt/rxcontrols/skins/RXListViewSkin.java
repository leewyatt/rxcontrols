package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXIndexedSelectionModel;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXListVisibleRange;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.event.RXListViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXIndexedSelectionMutationGuard;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * Skin for {@link RXListView}. It assembles the self-built virtualizing
 * {@link RXListViewport}, builds the single-column fixed-height
 * {@link RXListRowPlan} and shares it with the viewport, drives the placeholder
 * and the {@code :empty} state, consumes pending scroll requests, publishes the
 * read-only {@code rowCount} / {@code visibleRange} metrics after every pass, and
 * carries the keyboard (including type-ahead) and mouse selection behavior.
 *
 * @param <T> the item type
 */
public class RXListViewSkin<T> extends RXSkinBase<RXListView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass SELECTION_ROW_PSEUDO_CLASS = PseudoClass.getPseudoClass("selection-row");
    private static final PseudoClass SELECTION_CHECKMARK_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("selection-checkmark");
    private static final PseudoClass SELECTION_CHECKBOX_PSEUDO_CLASS =
            PseudoClass.getPseudoClass("selection-checkbox");

    private static final double FALLBACK_FIXED_CELL_SIZE = RXListView.DEFAULT_FIXED_CELL_SIZE;
    // Fallback content extents when the control is laid out unconstrained; real
    // sizes come from the parent / explicit pref sizes in practice.
    private static final int DEFAULT_VISIBLE_ROW_COUNT = 10;
    private static final double DEFAULT_CONTENT_WIDTH = 200.0;
    private static final double MIN_VIEWPORT_CONTENT_WIDTH = 2.0;
    // A first-letter type-ahead burst resets after this idle gap.
    private static final long TYPE_AHEAD_RESET_MS = 1000L;

    // Variable-height measure: below this px difference a re-measured row matches the
    // cached height (no churn). Cap consecutive no-progress re-pack passes so a non-
    // deterministic-height cell can never spin layout forever; a pass that measures a new
    // item resets the counter, so a genuine multi-step convergence still completes.
    private static final double HEIGHT_EPSILON = 0.5;
    private static final int MAX_STALLED_REPACK_PASSES = 8;

    // Key for the shift-range selection anchor, stashed in the control's property
    // map (mirrors ListView's CellBehaviorBase anchor) so it needs no new API.
    private static final String ANCHOR_KEY = "rx-list-view-selection-anchor";

    private final RXListViewport<T> viewport;
    private final StackPane placeholderRegion;

    // Persistent variable-height (measure-on-scroll) state; unused on the fixed path. A
    // measured row height reflects the row's rendering (content + selection slot) at its
    // last-visible width. The skin deliberately does NOT eagerly invalidate measured
    // heights when something that *might* change a row's height varies — a content width
    // change (resize / scroll-bar toggle), a converter change, or a selection-visual-mode /
    // selection-mode change (which swaps the leading checkbox / checkmark / nothing). In all
    // of these the SAME cell re-renders: the visible rows re-measure at the new rendering
    // every pass while off-screen rows keep their last measurement and re-measure when they
    // next scroll into view, so only the off-screen scroll extent / scrollTo offset is
    // approximate (the rendered content is correct; the anchor pin keeps it put). Eager
    // wiping would revert every off-screen row to the estimate and jump the scroll bar even
    // when nothing actually changed height (the common case — variable content dominates the
    // row height, so the leading slot rarely affects it). The cache is cleared only on the
    // wholesale changes where old measurements are wrong rather than merely approximate: a
    // cellFactory change (a different renderer), an items-list swap, and a fixed/variable
    // mode flip.
    private final IndexedHeightCache heightCache = new IndexedHeightCache();
    private boolean heightsDirty;
    // Tracks the fixedCellSize variable/fixed mode so a boundary crossing can drop the
    // now-stale height cache (the cache is dormant — its splice skipped — in fixed mode).
    private boolean lastVariable;

    private final ListChangeListener<T> itemsContentListener = this::onItemsContentChanged;
    private ObservableList<T> observedItems;

    private final RXIndexedFocusModel<T> focusModel;
    private MultipleSelectionModel<T> observedSelectionModel;
    private final ListChangeListener<Integer> selectionListener = change -> onSelectionChanged();
    private final ChangeListener<Number> selectedIndexListener = (obs, oldIndex, newIndex) -> syncFocusSelectionLead();
    private final ChangeListener<SelectionMode> selectionModeListener =
            (obs, oldMode, newMode) -> onSelectionModeChanged();

    private final StringBuilder typeAheadBuffer = new StringBuilder();
    private long lastTypeAheadTime;

    // Row-plan cache: the plan is width-independent (single column), so it only
    // changes when the revision (sections content), header flag, header / row
    // height, item count or section spacing change. The revision is bumped on a
    // sections change so a same-size regrouping still rebuilds.
    private int rowPlanRevision;
    private RowPlanKey cachedRowPlanKey;
    private RXListRowPlan cachedRowPlan;

    private record RowPlanKey(int revision, boolean showHeaders, double headerHeight,
                              double rowHeight, int itemCount, double sectionSpacing) {
    }

    /**
     * Creates the skin for the given list view.
     *
     * @param control the list view this skin is attached to
     */
    public RXListViewSkin(RXListView<T> control) {
        super(control);

        viewport = new RXListViewport<>(control);
        getChildren().add(viewport);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setVisible(false);
        getChildren().add(placeholderRegion);

        focusModel = new RXIndexedFocusModel<>(control.itemsProperty(), control::getSelectionModel);
        viewport.setFocusModel(focusModel);
        viewport.setHeightSink(this::recordMeasuredHeight);
        lastVariable = isVariableHeight(control);

        attachItems(control.getItems());
        updatePlaceholder();
        registerListeners(control);
        // Initial sync: the property listener only fires on change, so honor a value
        // set before the skin existed (FXML / builder).
        viewport.setStickyEnabled(control.isStickySectionHeader());
        attachSelectionModel(control.getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        updateControlPseudoClasses();
        disposer.registerDisposeTask(this::detachSelectionModel);
    }

    private void registerListeners(RXListView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.cellFactoryProperty(), this::onCellFactoryChanged);
        disposer.registerListener(control.converterProperty(), this::requestLayoutPass);
        disposer.registerListener(control.fixedCellSizeProperty(), this::onFixedCellSizeChanged);
        disposer.registerListener(control.estimatedCellSizeProperty(), this::requestLayoutPass);
        disposer.registerListener(control.placeholderProperty(), this::onPlaceholderChanged);
        // Sections: a sections-content change bumps the row-plan revision; the header
        // flag / heights / spacing flow through the row-plan key; a section-header
        // factory change rebuilds the header pool.
        disposer.registerListener(control.sectionsProperty(), this::onSectionsChanged);
        disposer.registerListener(control.showSectionHeadersProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionHeaderHeightProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionSpacingProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionHeaderFactoryProperty(), this::onSectionHeaderFactoryChanged);
        disposer.registerListener(control.stickySectionHeaderProperty(), this::onStickyChanged);
        // Visual mode: re-render the cells (their selection slot is derived) and
        // refresh the control-root pseudo-classes.
        disposer.registerListener(control.selectionVisualModeProperty(), this::onDecorationStateChanged);
        // Selection / focus: re-apply the per-cell state on change; re-wire on a
        // selection-model swap; install keyboard and mouse handlers on the control.
        disposer.registerListener(focusModel.focusedIndexProperty(), this::refreshSelectionAndFocus);
        disposer.registerListener(control.selectionModelProperty(), this::onSelectionModelSwapped);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        disposer.registerDisposeTask(this::detachItems);
    }

    // Dirties the viewport (forcing a re-fill) and, by propagation, the control
    // (so the skin rebuilds the row plan and republishes the metrics).
    private void requestLayoutPass() {
        viewport.requestLayout();
    }

    // A fixedCellSize change that crosses the variable/fixed boundary invalidates the
    // height cache: measured heights from the other mode no longer apply, and items may
    // have shifted while the cache lay dormant in fixed mode (where its splice is skipped).
    // Mirrors RXMasonryViewSkin.onHeightSourceChanged for the analogous source switch.
    private void onFixedCellSizeChanged() {
        boolean variable = isVariableHeight(getSkinnable());
        if (variable != lastVariable) {
            heightCache.clear();
            viewport.resetAnchor();
            lastVariable = variable;
        }
        requestLayoutPass();
    }

    // ==================== Items ====================

    private void onItemsListSwapped() {
        attachItems(getSkinnable().getItems());
        // The shift-range anchor referred to the previous list; drop it so a later
        // Shift+arrow / Shift+click starts a fresh range instead of a stale origin.
        resetAnchor();
        // Every index meaning is gone with the old list (selection anchor + height pin).
        viewport.resetAnchor();
        heightCache.clear();
        updatePlaceholder();
        viewport.requestLayout();
    }

    private void onItemsContentChanged(ListChangeListener.Change<? extends T> change) {
        resetAnchor();
        viewport.resetAnchor();
        // Keep the variable-height cache index-aligned with the spliced list.
        applyHeightCacheChange(change);
        updatePlaceholder();
        viewport.requestLayout();
    }

    private void attachItems(ObservableList<T> items) {
        detachItems();
        observedItems = items;
        if (items != null) {
            items.addListener(itemsContentListener);
        }
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(itemsContentListener);
            observedItems = null;
        }
    }

    // ==================== Refresh paths ====================

    private void onCellFactoryChanged() {
        // New cells produce new heights, so every cached measured height is stale.
        heightCache.clear();
        // recreateCells() discards the pool and requests a viewport layout.
        viewport.recreateCells();
    }

    private void onSectionsChanged() {
        // The sections list content changed; the row-plan key cannot cheaply hash it,
        // so a revision bump forces a rebuild.
        rowPlanRevision++;
        requestLayoutPass();
    }

    private void onSectionHeaderFactoryChanged() {
        // recreateHeaders() discards the header pool and requests a viewport layout.
        viewport.recreateHeaders();
    }

    private void onStickyChanged() {
        // Adds or removes the pinned sticky header and requests a viewport layout.
        viewport.setStickyEnabled(getSkinnable().isStickySectionHeader());
    }

    private void onPlaceholderChanged() {
        updatePlaceholder();
        viewport.requestLayout();
    }

    // ==================== Row plan ====================

    private void updateRowPlan() {
        RXListView<T> control = getSkinnable();
        RXListRowPlan plan = buildPlan();
        if (control.getRowCount() != plan.itemCount()) {
            control.setRowCount(plan.itemCount());
        }
        viewport.setRowPlan(plan);
    }

    // The fixed-mode plan is purely a function of the sections + resolved heights + item
    // count (width-independent), so a single cached slot keyed on those reuses it across
    // scroll passes and rebuilds only when something it depends on changes. Variable mode
    // reads mutable per-item heights, so it bypasses the cache (see buildVariablePlan).
    private RXListRowPlan buildPlan() {
        RXListView<T> control = getSkinnable();
        if (isVariableHeight(control)) {
            return buildVariablePlan(control);
        }
        RowPlanKey key = new RowPlanKey(rowPlanRevision, control.isShowSectionHeaders(),
                snapSizeY(sectionHeaderHeightOrDefault(control)), snapSizeY(fixedCellSizeOrDefault(control)),
                itemCount(), snapSizeY(sectionSpacingOrZero(control.getSectionSpacing())));
        if (key.equals(cachedRowPlanKey)) {
            return cachedRowPlan;
        }
        RXListRowPlan plan = RXListRowPlan.fixed(control.getSections(), control.isShowSectionHeaders(),
                key.headerHeight(), key.rowHeight(), key.itemCount(), key.sectionSpacing());
        cachedRowPlanKey = key;
        cachedRowPlan = plan;
        return plan;
    }

    // Variable mode: heights come from the cache (measured or estimated). The plan is
    // rebuilt fresh each pass because those heights are mutable — the converge loop relies
    // on rebuilding from the just-updated cache, and a pure scroll pays the O(items)
    // prefix-sum rebuild that variable rows inherently cost (opt-in via fixedCellSize <= 0).
    private RXListRowPlan buildVariablePlan(RXListView<T> control) {
        int count = itemCount();
        double estimated = snapSizeY(estimatedCellSizeOrDefault(control));
        heightCache.ensureCapacity(count, estimated);
        double[] heights = new double[count];
        for (int i = 0; i < count; i++) {
            heights[i] = snapSizeY(heightCache.heightAt(i, estimated));
        }
        return RXListRowPlan.variable(control.getSections(), control.isShowSectionHeaders(),
                snapSizeY(sectionHeaderHeightOrDefault(control)), heights,
                snapSizeY(sectionSpacingOrZero(control.getSectionSpacing())));
    }

    private RXListSelectionVisualMode effectiveSelectionVisualMode() {
        RXListView<T> control = getSkinnable();
        return RXListSelectionVisualMode.resolve(control.getSelectionVisualMode(), control.getSelectionMode());
    }

    // A visual-mode change re-renders the (derived) cell selection slots and
    // refreshes the control-root pseudo-classes used by the stylesheet.
    private void onDecorationStateChanged() {
        updateControlPseudoClasses();
        requestLayoutPass();
    }

    private void updateControlPseudoClasses() {
        RXListView<T> control = getSkinnable();
        RXListSelectionVisualMode mode = effectiveSelectionVisualMode();
        control.pseudoClassStateChanged(SELECTION_ROW_PSEUDO_CLASS, mode == RXListSelectionVisualMode.ROW);
        control.pseudoClassStateChanged(SELECTION_CHECKMARK_PSEUDO_CLASS, mode == RXListSelectionVisualMode.CHECKMARK);
        control.pseudoClassStateChanged(SELECTION_CHECKBOX_PSEUDO_CLASS, mode == RXListSelectionVisualMode.CHECKBOX);
    }

    // ==================== Visible range ====================

    private void updateVisibleRange() {
        RXListView<T> control = getSkinnable();
        int firstIndex = viewport.getVisibleFirstIndex();
        int lastIndex = viewport.getVisibleLastIndex();
        if (firstIndex < 0 || lastIndex < 0) {
            setVisibleRangeIfChanged(control, RXListVisibleRange.EMPTY);
            return;
        }
        // Single column: each data row holds one item, so row bounds mirror item bounds.
        setVisibleRangeIfChanged(control, new RXListVisibleRange(firstIndex, lastIndex,
                firstIndex, lastIndex, lastIndex - firstIndex + 1));
    }

    private void setVisibleRangeIfChanged(RXListView<T> control, RXListVisibleRange range) {
        if (!range.equals(control.getVisibleRange())) {
            control.setVisibleRange(range);
        }
    }

    // ==================== Visible section ====================

    private void updateVisibleSection() {
        // The viewport reports the section at the top of the window (null when flat).
        getSkinnable().setVisibleSection(viewport.getTopSection());
    }

    // ==================== Placeholder / :empty ====================

    private void updatePlaceholder() {
        RXListView<T> control = getSkinnable();
        boolean empty = itemCount() == 0;
        control.pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, empty);

        Node placeholder = control.getPlaceholder();
        boolean showPlaceholder = empty && placeholder != null;
        if (showPlaceholder) {
            placeholderRegion.getChildren().setAll(placeholder);
        } else {
            placeholderRegion.getChildren().clear();
        }
        placeholderRegion.setVisible(showPlaceholder);
        viewport.setVisible(!showPlaceholder);
    }

    // ==================== Scrolling ====================

    private void consumePendingScroll() {
        RXListView<T> control = getSkinnable();
        if (!control.hasPendingScroll()) {
            return;
        }
        int itemCount = itemCount();
        if (itemCount == 0) {
            control.clearPendingScroll();
            return;
        }
        int sectionIndex = control.getPendingScrollSectionIndex();
        if (sectionIndex >= 0) {
            // Section scroll takes priority; a stale section index (cleared sections)
            // is dropped rather than left armed.
            if (sectionIndex >= control.getSections().size()
                    || viewport.scrollToSectionIndex(sectionIndex, control.getPendingScrollAlignment())) {
                control.clearPendingScroll();
            }
            return;
        }
        int index = control.getPendingScrollIndex();
        if (index >= 0) {
            int clamped = Math.max(0, Math.min(index, itemCount - 1));
            // Clear only when the request was actually applied. On a zero-height pass
            // scrollToIndex cannot compute geometry and returns false; keeping the
            // request armed lets the first sized pass honor it.
            if (viewport.scrollToIndex(clamped, control.getPendingScrollAlignment())) {
                control.clearPendingScroll();
            }
            return;
        }
        if (viewport.scrollByPixels(control.getPendingScrollDelta())) {
            control.clearPendingScroll();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        RXListView<T> control = getSkinnable();
        double rightInset = Math.max(0.0, control.getWidth() - contentX - contentWidth);
        double bottomInset = Math.max(0.0, control.getHeight() - contentY - contentHeight);
        viewport.setChromeInsets(contentX, contentY, rightInset, bottomInset);
        updateRowPlan();
        viewport.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        placeholderRegion.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        consumePendingScroll();
        boolean variable = isVariableHeight(control);
        viewport.setMeasureGate(variable);
        heightsDirty = false;
        // Force the viewport to realize cells now so the scroll request and the published
        // visible range reflect this pass, not the previous one. On the variable path the
        // realized cells are also measured; a changed height re-packs within this pass.
        viewport.layout();
        if (variable) {
            convergeVariableHeights();
        }
        updateVisibleRange();
        updateVisibleSection();
    }

    // A measured height differing from the placed (estimated) one re-packs the layout.
    // Converge WITHIN this pass: rebuild the plan from the updated cache, re-fill and
    // re-measure, looping until nothing changes (a requestLayout issued from inside
    // layoutChildren is not reliably honored). A pass that measures a NEW item is progress
    // (resets the stall counter), so a genuine multi-step convergence runs to completion;
    // a pass that only re-flips known heights counts toward the cap so a non-deterministic-
    // height cell cannot spin forever. The viewport's anchor pin keeps the top visible item
    // put across each re-pack.
    private void convergeVariableHeights() {
        if (!heightsDirty) {
            return;
        }
        int lastMeasured = heightCache.measuredCount();
        int stalled = 0;
        while (heightsDirty && stalled < MAX_STALLED_REPACK_PASSES) {
            heightsDirty = false;
            updateRowPlan();
            viewport.requestLayout();
            viewport.layout();
            int measured = heightCache.measuredCount();
            stalled = measured > lastMeasured ? 0 : stalled + 1;
            lastMeasured = measured;
        }
    }

    // The variable-height measure sink (installed on the viewport): a changed measured
    // height marks the cache dirty so layoutChildren re-packs within the pass.
    private void recordMeasuredHeight(int index, double measuredHeight) {
        if (!isVariableHeight(getSkinnable()) || !Double.isFinite(measuredHeight) || measuredHeight < 0.0) {
            return;
        }
        if (heightCache.record(index, measuredHeight, HEIGHT_EPSILON)) {
            heightsDirty = true;
        }
    }

    // Keep the variable-height cache index-aligned with the list (mirrors the index shift
    // in the selection / focus models); a no-op on the fixed path.
    private void applyHeightCacheChange(ListChangeListener.Change<? extends T> change) {
        if (!isVariableHeight(getSkinnable())) {
            return;
        }
        double estimated = estimatedCellSizeOrDefault(getSkinnable());
        while (change.next()) {
            if (change.wasPermutated() || change.wasUpdated()) {
                // Reordered or changed in place: the stored heights no longer describe the
                // range, so drop them and re-measure.
                heightCache.invalidateRange(change.getFrom(), change.getTo(), estimated);
            } else {
                heightCache.shift(change.getFrom(), change.getRemovedSize(), change.getAddedSize(), estimated);
            }
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_CONTENT_WIDTH + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXListView<T> control = getSkinnable();
        double rowEstimate = isVariableHeight(control)
                ? estimatedCellSizeOrDefault(control) : fixedCellSizeOrDefault(control);
        return topInset + DEFAULT_VISIBLE_ROW_COUNT * rowEstimate + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + MIN_VIEWPORT_CONTENT_WIDTH + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return Double.MAX_VALUE;
    }

    @Override
    protected void disposeSkin() {
        // detachItems / detachSelectionModel run via the disposer (registerDisposeTask).
        viewport.dispose();
        placeholderRegion.getChildren().clear();
    }

    // ==================== Selection model wiring ====================

    // Routes through the viewport; defined as a method so field initializers and
    // listeners can reference it before the viewport field is assigned.
    private void refreshSelectionAndFocus() {
        viewport.refreshSelectionAndFocus();
    }

    private void onSelectionChanged() {
        syncFocusSelectionLead();
        refreshSelectionAndFocus();
    }

    private void syncFocusSelectionLead() {
        if (!RXIndexedSelectionMutationGuard.isActive(observedSelectionModel)) {
            focusModel.syncSelectionLeadState();
        }
    }

    private void onSelectionModelSwapped() {
        resetAnchor();
        attachSelectionModel(getSkinnable().getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        refreshSelectionAndFocus();
        // The new model's cardinality may change the AUTO visual mode.
        updateControlPseudoClasses();
        getSkinnable().requestLayout();
    }

    private void attachSelectionModel(MultipleSelectionModel<T> model) {
        detachSelectionModel();
        observedSelectionModel = model;
        if (model != null) {
            model.getSelectedIndices().addListener(selectionListener);
            model.selectedIndexProperty().addListener(selectedIndexListener);
            model.selectionModeProperty().addListener(selectionModeListener);
        }
    }

    private void onSelectionModeChanged() {
        resetAnchor();
        // The cardinality drives the AUTO visual mode, so refresh the pseudo-classes
        // and re-render the cells.
        updateControlPseudoClasses();
        requestLayoutPass();
    }

    private void detachSelectionModel() {
        if (observedSelectionModel != null) {
            observedSelectionModel.getSelectedIndices().removeListener(selectionListener);
            observedSelectionModel.selectedIndexProperty().removeListener(selectedIndexListener);
            observedSelectionModel.selectionModeProperty().removeListener(selectionModeListener);
            observedSelectionModel = null;
        }
    }

    // ==================== Anchor ====================

    private void setAnchor(int index) {
        getSkinnable().getProperties().put(ANCHOR_KEY, index);
    }

    private int getAnchor() {
        Object anchor = getSkinnable().getProperties().get(ANCHOR_KEY);
        return anchor instanceof Integer value ? value : focusModel.getFocusedIndex();
    }

    private void resetAnchor() {
        getSkinnable().getProperties().remove(ANCHOR_KEY);
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        RXListView<T> control = getSkinnable();
        MultipleSelectionModel<T> sm = control.getSelectionModel();
        int itemCount = itemCount();
        if (sm == null || itemCount == 0) {
            return;
        }
        int focus = focusModel.getFocusedIndex();
        boolean shift = event.isShiftDown();
        boolean shortcut = event.isShortcutDown();
        KeyCode code = event.getCode();
        // Select-all shortcut first, so Ctrl/Cmd+A is not consumed by type-ahead.
        if (shortcut && code == KeyCode.A) {
            sm.selectAll();
            focusModel.syncSelectionLeadState();
            event.consume();
            return;
        }
        switch (code) {
            case UP, KP_UP -> consume(event, () -> verticalArrow(focus, -1, shift, shortcut));
            case DOWN, KP_DOWN -> consume(event, () -> verticalArrow(focus, 1, shift, shortcut));
            case HOME -> consume(event, () -> moveTo(0, shift, shortcut));
            case END -> consume(event, () -> moveTo(itemCount - 1, shift, shortcut));
            case PAGE_UP -> consume(event, () -> page(-1, shift, shortcut));
            case PAGE_DOWN -> consume(event, () -> page(1, shift, shortcut));
            case SPACE -> {
                if (focus >= 0) {
                    toggleFocused(focus);
                    event.consume();
                }
            }
            case ENTER -> {
                if (focus >= 0) {
                    activate(focus);
                    event.consume();
                }
            }
            default -> {
                if (isTypeAheadKey(event)) {
                    typeAhead(event.getText());
                    event.consume();
                }
            }
        }
    }

    private static void consume(KeyEvent event, Runnable action) {
        action.run();
        event.consume();
    }

    private void verticalArrow(int focus, int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        if (focus < 0) {
            moveTo(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        int target = clampIndex(focus + direction, itemCount);
        if (target == focus) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    // PageUp/Down follow ListView's "sink then page": the first press moves the
    // focus to the item at the viewport's bottom (PageDown) / top (PageUp) visible
    // row without scrolling past it; only once the focus already sits on that edge
    // does a press page by a viewport-height of rows.
    private void page(int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int focus = focusModel.getFocusedIndex();
        if (focus < 0) {
            moveTo(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        RXListVisibleRange range = getSkinnable().getVisibleRange();
        int visibleRows = range.isEmpty() ? 1 : Math.max(1, range.lastIndex() - range.firstIndex() + 1);
        int pageStep = Math.max(1, visibleRows - 1);
        int target;
        if (direction > 0) {
            int bottom = range.isEmpty() ? focus : range.lastIndex();
            target = focus < bottom ? bottom : Math.min(itemCount - 1, focus + pageStep);
        } else {
            int top = range.isEmpty() ? focus : range.firstIndex();
            target = focus > top ? top : Math.max(0, focus - pageStep);
        }
        if (target == focus) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    // Navigation works on item indices and the focus model; selection follows the
    // modifier keys and the visual mode: Shortcut = move focus only; Shift = extend
    // range from the anchor; a plain arrow selects in ROW / single-selection
    // (highlight follows the cursor) but only moves focus in an accumulate mode
    // (checkbox / checkmark multiple), so arrowing never wipes the accumulated
    // selection — Space toggles there instead.
    private void moveTo(int target, boolean shift, boolean shortcut) {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null) {
            return;
        }
        // Capture the range anchor from the CURRENT focus before moving it.
        // getAnchor() falls back to the focused index, so reading it after
        // focus(target) would collapse a Shift-extend to a single row whenever no
        // explicit anchor is stored (mirrors CellBehaviorBase, which records the
        // anchor before fm.focus).
        int anchor = clampIndex(getAnchor(), itemCount());
        boolean accumulate = isAccumulateMode();
        focusModel.focus(target);
        if (shift && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            clearAndSelectRange(sm, anchor, target);
        } else if (shortcut || accumulate) {
            // Move focus only; selection is unchanged.
            setAnchor(target);
        } else {
            setAnchor(target);
            sm.clearAndSelect(target);
        }
        focusModel.syncSelectionLeadState();
        getSkinnable().scrollTo(target, ScrollAlignment.NEAREST);
    }

    private void toggleFocused(int focus) {
        if (focus < 0) {
            return;
        }
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null) {
            return;
        }
        // Space toggles the focused row's selection in every visual mode (§11.1).
        toggleAt(sm, focus);
        setAnchor(focus);
        focusModel.syncSelectionLeadState();
    }

    private void activate(int index) {
        RXListView<T> control = getSkinnable();
        if (index < 0 || index >= itemCount()) {
            return;
        }
        T item = control.getItems().get(index);
        control.fireEvent(new RXListViewActionEvent<>(control, item, index));
    }

    // ==================== Type-ahead ====================

    private boolean isTypeAheadKey(KeyEvent event) {
        if (event.isShortcutDown() || event.isAltDown()) {
            return false;
        }
        String text = event.getText();
        return text != null && text.length() == 1 && !Character.isISOControl(text.charAt(0));
    }

    private void typeAhead(String ch) {
        long now = System.currentTimeMillis();
        if (now - lastTypeAheadTime > TYPE_AHEAD_RESET_MS) {
            typeAheadBuffer.setLength(0);
        }
        lastTypeAheadTime = now;
        String lower = ch.toLowerCase(Locale.ROOT);
        if (typeAheadBuffer.length() > 0 && isAllSameChar(typeAheadBuffer, lower.charAt(0))) {
            // Pressing the same letter again cycles among its matches (start = focus+1)
            // rather than growing the prefix, which would stop matching.
            typeAheadBuffer.setLength(0);
        }
        typeAheadBuffer.append(lower);
        int match = findTypeAheadMatch(typeAheadBuffer.toString());
        if (match >= 0) {
            moveTo(match, false, false);
        }
    }

    private static boolean isAllSameChar(CharSequence buffer, char ch) {
        for (int i = 0; i < buffer.length(); i++) {
            if (buffer.charAt(i) != ch) {
                return false;
            }
        }
        return true;
    }

    private int findTypeAheadMatch(String prefixLower) {
        int itemCount = itemCount();
        if (itemCount == 0) {
            return -1;
        }
        // A single-character burst cycles to the next match; a growing buffer
        // refines from the current focus.
        int focus = focusModel.getFocusedIndex();
        int start = prefixLower.length() <= 1 ? focus + 1 : Math.max(0, focus);
        for (int i = 0; i < itemCount; i++) {
            int index = Math.floorMod(start + i, itemCount);
            if (cellText(index).toLowerCase(Locale.ROOT).startsWith(prefixLower)) {
                return index;
            }
        }
        return -1;
    }

    private String cellText(int index) {
        RXListView<T> control = getSkinnable();
        T item = control.getItems().get(index);
        StringConverter<T> converter = control.getConverter();
        if (converter != null) {
            String text = converter.toString(item);
            return text == null ? "" : text;
        }
        return item == null ? "" : item.toString();
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXListView<T> control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        RXListCell<T> cell = viewport.cellAt(event.getTarget());
        if (cell == null) {
            // Blank area below the items: no marquee in this milestone, so no-op.
            return;
        }
        int index = cell.getIndex();
        handlePointerSelect(event, index, isAccumulateMode());
    }

    // "Accumulate" modes show a per-item indicator (checkbox / checkmark) in
    // multiple-selection, where a plain click toggles that one row (and a plain
    // arrow only moves the cursor) — independent toggling, not "replace with the
    // clicked row". ROW (a whole-row highlight) and any single-selection mode use
    // the native replace-on-plain semantics instead.
    private boolean isAccumulateMode() {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null || sm.getSelectionMode() != SelectionMode.MULTIPLE) {
            return false;
        }
        RXListSelectionVisualMode mode = effectiveSelectionVisualMode();
        return mode == RXListSelectionVisualMode.CHECKBOX || mode == RXListSelectionVisualMode.CHECKMARK;
    }

    // Pointer selection: any press first updates focus. Ctrl toggles just that row
    // (priority over Shift, mirroring CellBehaviorBase); Shift extends the range
    // from the anchor (replace); a plain press toggles in an accumulate mode (so
    // rapid clicking selects / deselects freely) or replaces otherwise.
    private void handlePointerSelect(MouseEvent event, int index, boolean accumulate) {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null) {
            return;
        }
        // Capture the range anchor from the current focus before moving it (see moveTo).
        int anchor = clampIndex(getAnchor(), itemCount());
        focusModel.focus(index);
        if (event.isShortcutDown()) {
            toggleAt(sm, index);
            setAnchor(index);
        } else if (event.isShiftDown() && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            clearAndSelectRange(sm, anchor, index);
        } else if (accumulate) {
            toggleAt(sm, index);
            setAnchor(index);
        } else {
            setAnchor(index);
            sm.clearAndSelect(index);
        }
        focusModel.syncSelectionLeadState();
    }

    private void toggleAt(MultipleSelectionModel<T> sm, int index) {
        if (sm.isSelected(index)) {
            sm.clearSelection(index);
        } else {
            // In single-selection mode select() replaces, matching clearAndSelect.
            sm.select(index);
        }
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            RXListCell<T> cell = viewport.cellAt(event.getTarget());
            if (cell != null) {
                activate(cell.getIndex());
            }
        }
    }

    private void clearAndSelectRange(MultipleSelectionModel<T> selectionModel, int anchor, int target) {
        int end = target >= anchor ? target + 1 : target - 1;
        if (selectionModel instanceof RXIndexedSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXIndexedSelectionModel<T> typedModel = (RXIndexedSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectRange(anchor, end);
        } else {
            selectionModel.clearSelection();
            selectionModel.selectRange(anchor, end);
        }
    }

    // ==================== Shared helpers ====================

    private int itemCount() {
        ObservableList<T> items = getSkinnable().getItems();
        return items == null ? 0 : items.size();
    }

    private static int clampIndex(int index, int itemCount) {
        if (index < 0) {
            return 0;
        }
        if (index >= itemCount) {
            return itemCount - 1;
        }
        return index;
    }

    // Variable-height mode is the fixedCellSize <= 0 / non-finite sentinel (matching
    // ListView): each row is sized to its content rather than a uniform height.
    static boolean isVariableHeight(RXListView<?> control) {
        double value = control.getFixedCellSize();
        return !(Double.isFinite(value) && value > 0.0);
    }

    static double fixedCellSizeOrDefault(RXListView<?> control) {
        double value = control.getFixedCellSize();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_FIXED_CELL_SIZE;
    }

    static double estimatedCellSizeOrDefault(RXListView<?> control) {
        double value = control.getEstimatedCellSize();
        return Double.isFinite(value) && value > 0.0 ? value : RXListView.DEFAULT_ESTIMATED_CELL_SIZE;
    }

    static double sectionHeaderHeightOrDefault(RXListView<?> control) {
        double value = control.getSectionHeaderHeight();
        return Double.isFinite(value) && value > 0.0 ? value : RXListView.DEFAULT_SECTION_HEADER_HEIGHT;
    }

    static double sectionSpacingOrZero(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}

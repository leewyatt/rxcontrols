package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSelectionModel;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXTileVisibleRange;
import io.github.leewyatt.rxcontrols.event.RXTileViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXTileSelectionMutationGuard;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * Skin for {@link RXTileView}. It assembles the self-built virtualizing
 * {@link RXTileViewport}, resolves the column count from {@code cellWidth}, the
 * available content width and the measured vertical scroll-bar breadth, drives
 * the placeholder and the {@code :empty} state, consumes pending scroll requests
 * and publishes the read-only layout
 * metrics ({@code actualColumnCount}, {@code rowCount}, {@code visibleRange},
 * {@code visibleSection}) after every pass.
 *
 * <p>Grouping is supplied by the control's width-independent
 * {@link RXTileView#sectionsProperty() sections}; the skin builds a
 * {@link RXTileRowPlan} (header rows interleaved with data rows) and shares it
 * with the viewport. A flat view (no section-key factory) is the degenerate plan
 * with no header rows.
 *
 * @param <T> the item type
 */
public class RXTileViewSkin<T> extends RXSkinBase<RXTileView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final int MAX_RESOLVED_COLUMNS = 4096;
    private static final int DEFAULT_PREF_COLUMNS = 3;
    private static final int DEFAULT_VISIBLE_ROWS = 4;

    // Degenerate-case fallbacks for the geometry. cellWidth / cellHeight use a
    // coerce+throw strategy, so their getters are valid on every normal path;
    // these only guard a value left illegal by a bound source.
    private static final double FALLBACK_CELL_WIDTH = 100.0;
    private static final double FALLBACK_CELL_HEIGHT = 100.0;
    private static final double FALLBACK_SECTION_HEADER_HEIGHT = 32.0;
    private static final double MARQUEE_START_THRESHOLD = 4.0;
    private static final double MARQUEE_AUTO_SCROLL_EDGE = 32.0;
    private static final double MARQUEE_AUTO_SCROLL_MAX_STEP = 24.0;
    private static final Duration MARQUEE_AUTO_SCROLL_INTERVAL = Duration.millis(16.0);

    // Key for the shift-range selection anchor, stashed in the control's property
    // map (mirrors ListView's CellBehaviorBase anchor) so it needs no new API.
    private static final String ANCHOR_KEY = "rx-tile-view-selection-anchor";

    private final RXTileViewport<T> viewport;
    private final StackPane placeholderRegion;
    private final Rectangle selectionRectangle;
    private final Timeline marqueeAutoScroll;

    private final ListChangeListener<T> itemsContentListener = change -> onItemsContentChanged();
    private ObservableList<T> observedItems;

    private final RXTileFocusModel<T> focusModel;
    private MultipleSelectionModel<T> observedSelectionModel;
    private final ListChangeListener<Integer> selectionListener = change -> onSelectionChanged();
    private final ChangeListener<Number> selectedIndexListener = (obs, oldIndex, newIndex) -> syncFocusSelectionLead();
    private final ChangeListener<SelectionMode> selectionModeListener = (obs, oldMode, newMode) -> onSelectionModeChanged();

    private int preferredVerticalColumn = -1;
    private int preferredColumnDataRow = -1;
    private int preferredColumnActualColumn = -1;
    private int preferredColumnPlanColumns = -1;

    private boolean marqueeArmed;
    private boolean marqueeActive;
    private double marqueePressX;
    private double marqueePressY;
    private double marqueeAnchorX;
    private double marqueeAnchorContentY;
    private double marqueeLastX;
    private double marqueeLastY;

    /**
     * Creates the skin for the given tile view.
     *
     * @param control the tile view this skin is attached to
     */
    public RXTileViewSkin(RXTileView<T> control) {
        super(control);

        viewport = new RXTileViewport<>(control);
        getChildren().add(viewport);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setVisible(false);
        getChildren().add(placeholderRegion);

        selectionRectangle = new Rectangle();
        selectionRectangle.getStyleClass().add("selection-rectangle");
        selectionRectangle.setManaged(false);
        selectionRectangle.setMouseTransparent(true);
        selectionRectangle.setVisible(false);
        getChildren().add(selectionRectangle);

        marqueeAutoScroll = new Timeline(new KeyFrame(MARQUEE_AUTO_SCROLL_INTERVAL,
                event -> onMarqueeAutoScroll()));
        marqueeAutoScroll.setCycleCount(Animation.INDEFINITE);

        focusModel = new RXTileFocusModel<>(control);
        viewport.setFocusModel(focusModel);

        attachItems(control.getItems());
        updatePlaceholder();
        registerListeners(control);
        attachSelectionModel(control.getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        disposer.registerDisposeTask(this::detachSelectionModel);
    }

    private void registerListeners(RXTileView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.cellFactoryProperty(), this::onCellFactoryChanged);
        disposer.registerListener(control.cellWidthProperty(), this::requestLayoutPass);
        disposer.registerListener(control.maxCellWidthProperty(), this::requestLayoutPass);
        disposer.registerListener(control.hgapProperty(), this::requestLayoutPass);
        disposer.registerListener(control.maxColumnsProperty(), this::requestLayoutPass);
        disposer.registerListener(control.cellHeightProperty(), this::requestLayoutPass);
        disposer.registerListener(control.vgapProperty(), this::requestLayoutPass);
        disposer.registerListener(control.itemsJustifyProperty(), this::requestLayoutPass);
        disposer.registerListener(control.placeholderProperty(), this::onPlaceholderChanged);
        // Sections are derived on the control; the skin relayouts to rebuild the
        // row plan whenever the derived list, the header toggle or the header
        // height change. (sectionKeyFactory changes flow through sectionsProperty.)
        disposer.registerListener(control.sectionsProperty(), this::requestLayoutPass);
        disposer.registerListener(control.showSectionHeadersProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionHeaderHeightProperty(), this::requestLayoutPass);
        disposer.registerListener(control.sectionHeaderFactoryProperty(), this::onSectionHeaderFactoryChanged);
        // Reorder animation: snap any in-flight glide when it is turned off mid-flight.
        disposer.registerListener(control.animatedProperty(), viewport::onAnimationSettingsChanged);
        disposer.registerListener(control.animationDurationProperty(), viewport::onAnimationSettingsChanged);
        // Selection / focus: re-apply the per-cell state on change; re-wire on a
        // selection-model swap; install the keyboard (control) and mouse (viewport)
        // handlers. The control is the single Tab stop, so keys arrive on it.
        disposer.registerListener(focusModel.focusedIndexProperty(), this::refreshSelectionAndFocus);
        disposer.registerListener(control.selectionModelProperty(), this::onSelectionModelSwapped);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        disposer.registerDisposeTask(this::detachItems);
    }

    // Dirties the viewport (forcing a re-fill) and, by propagation, the control
    // (so the skin re-runs updateColumns + publishes the visible range). Calling
    // requestLayout on the control alone would not re-run the viewport's layout.
    private void requestLayoutPass() {
        viewport.requestLayout();
    }

    // ==================== Items ====================

    private void onItemsListSwapped() {
        finishMarquee();
        attachItems(getSkinnable().getItems());
        // The shift-range anchor referred to the previous list; drop it so a later
        // Shift+arrow / Shift+click starts a fresh range instead of a stale origin.
        resetAnchor();
        updatePlaceholder();
        viewport.requestLayout();
    }

    private void onItemsContentChanged() {
        finishMarquee();
        resetAnchor();
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
        // recreateCells() discards the pool and requests a viewport layout.
        viewport.recreateCells();
    }

    private void onSectionHeaderFactoryChanged() {
        // recreateHeaders() discards the header pool and requests a viewport layout.
        viewport.recreateHeaders();
    }

    private void onPlaceholderChanged() {
        updatePlaceholder();
        viewport.requestLayout();
    }

    // ==================== Column derivation ====================

    private void updateColumns(double availableWidth, double availableHeight) {
        RXTileView<T> control = getSkinnable();
        // Resolve the column count and the scroll-bar presence together in one
        // pass (VirtualFlow's 2-iteration approach): assume no bar, build the row
        // plan, and if its content (header rows included) overflows, re-derive with
        // the bar's breadth subtracted. Done here, not via a fragile cross-pass
        // "bar appeared -> relayout" dependency (requestLayout from within a layout
        // pass is suppressed). The plan is shared with the viewport so the two
        // never disagree on geometry.
        int columns = computeColumns(availableWidth);
        RXTileRowPlan plan = buildPlan(columns);
        if (plan.contentHeight() > availableHeight) {
            columns = computeColumns(availableWidth - viewport.scrollBarBreadth());
            plan = buildPlan(columns);
        }
        if (control.getActualColumnCount() != columns) {
            control.setActualColumnCount(columns);
        }
        int visualRows = plan.totalVisualRows();
        if (control.getRowCount() != visualRows) {
            control.setRowCount(visualRows);
        }
        viewport.setRowPlan(plan);
        syncPreferredColumnWithFocusedCell();
    }

    private RXTileRowPlan buildPlan(int columns) {
        RXTileView<T> control = getSkinnable();
        return new RXTileRowPlan(control.getSections(), control.isShowSectionHeaders(), columns,
                snapSizeY(sectionHeaderHeightOrDefault(control)), viewport.slotHeight(), itemCount());
    }

    private int computeColumns(double availableWidth) {
        RXTileView<T> control = getSkinnable();
        double track = snapSizeX(cellWidthOrDefault(control));
        double gap = snapSpaceX(gapOrZero(control.getHgap()));
        int columns;
        if (availableWidth <= 0.0 || track <= 0.0) {
            columns = 1;
        } else {
            columns = (int) Math.floor((availableWidth + gap) / (track + gap));
        }
        if (columns < 1) {
            columns = 1;
        }
        int max = control.getMaxColumns();
        if (max > 0 && columns > max) {
            columns = max;
        }
        if (columns > MAX_RESOLVED_COLUMNS) {
            columns = MAX_RESOLVED_COLUMNS;
        }
        return columns;
    }

    // ==================== Visible range ====================

    private void updateVisibleRange() {
        RXTileView<T> control = getSkinnable();
        int firstIndex = viewport.getVisibleFirstIndex();
        int lastIndex = viewport.getVisibleLastIndex();
        if (firstIndex < 0 || lastIndex < 0) {
            control.setVisibleRange(RXTileVisibleRange.EMPTY);
            return;
        }
        // firstRow / lastRow are DATA-row indices (header rows excluded), distinct
        // from the visual rowCount which counts header rows too.
        control.setVisibleRange(new RXTileVisibleRange(firstIndex, lastIndex,
                viewport.getVisibleFirstRow(), viewport.getVisibleLastRow(),
                control.getActualColumnCount()));
    }

    private void updateVisibleSection() {
        // The viewport reports the section at the top of the window (null when flat).
        getSkinnable().setVisibleSection(viewport.getTopSection());
    }

    // ==================== Placeholder / :empty ====================

    private void updatePlaceholder() {
        RXTileView<T> control = getSkinnable();
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
        RXTileView<T> control = getSkinnable();
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
            if (sectionIndex >= control.getSections().size()
                    || viewport.scrollToSectionIndex(sectionIndex, control.getPendingScrollAlignment())) {
                control.clearPendingScroll();
            }
            return;
        }
        int index = Math.max(0, Math.min(control.getPendingScrollIndex(), itemCount - 1));
        // Clear only when the request was actually applied. On a zero-height pass
        // scrollToIndex cannot compute geometry and returns false; keeping the
        // request armed lets the first sized pass honor it.
        if (viewport.scrollToIndex(index, control.getPendingScrollAlignment())) {
            control.clearPendingScroll();
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        RXTileView<T> control = getSkinnable();
        double rightInset = Math.max(0.0, control.getWidth() - contentX - contentWidth);
        double bottomInset = Math.max(0.0, control.getHeight() - contentY - contentHeight);
        viewport.setChromeInsets(contentX, contentY, rightInset, bottomInset);
        updateColumns(contentWidth, contentHeight);
        viewport.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        placeholderRegion.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        consumePendingScroll();
        // Force the viewport to realize cells now so the scroll request and the
        // published visible range reflect this pass, not the previous one.
        viewport.layout();
        updateVisibleRange();
        updateVisibleSection();
        layoutMarqueeRectangle();
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        RXTileView<T> control = getSkinnable();
        double cellWidth = cellWidthOrDefault(control);
        double gap = gapOrZero(control.getHgap());
        double content = rowWidth(prefWidthColumns(control), cellWidth, gap);
        return leftInset + content
                + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXTileView<T> control = getSkinnable();
        double slot = cellHeightOrDefault(control) + gapOrZero(control.getVgap());
        return topInset + DEFAULT_VISIBLE_ROWS * slot + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + viewport.scrollBarBreadth() + rightInset;
    }

    private int prefWidthColumns(RXTileView<?> control) {
        return capColumns(DEFAULT_PREF_COLUMNS, control.getMaxColumns());
    }

    private static int capColumns(int columns, int maxColumns) {
        int capped = Math.max(1, columns);
        if (maxColumns > 0 && capped > maxColumns) {
            capped = maxColumns;
        }
        return Math.min(capped, MAX_RESOLVED_COLUMNS);
    }

    private static double rowWidth(int columns, double cellWidth, double hgap) {
        return columns * cellWidth + (columns - 1) * hgap;
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
        finishMarquee();
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
        if (!RXTileSelectionMutationGuard.isActive(observedSelectionModel)) {
            focusModel.syncSelectionLeadState();
        }
    }

    private void onSelectionModelSwapped() {
        finishMarquee();
        resetAnchor();
        attachSelectionModel(getSkinnable().getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        refreshSelectionAndFocus();
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
        finishMarquee();
        resetAnchor();
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

    // ==================== Preferred vertical column ====================

    private int preferredColumnForVerticalNavigation(int focus) {
        RXTileRowPlan.ItemPosition position = viewport.itemPositionOf(focus);
        if (!position.valid()) {
            clearPreferredColumn();
            return -1;
        }
        int planColumns = viewport.columnCount();
        if (preferredVerticalColumn < 0
                || preferredColumnDataRow != position.dataRow()
                || preferredColumnActualColumn != position.column()
                || preferredColumnPlanColumns != planColumns) {
            resetPreferredColumn(position.column(), position);
        }
        return preferredVerticalColumn;
    }

    private void resetPreferredColumnToItem(int index) {
        RXTileRowPlan.ItemPosition position = viewport.itemPositionOf(index);
        if (position.valid()) {
            resetPreferredColumn(position.column(), position);
        } else {
            clearPreferredColumn();
        }
    }

    private void preservePreferredColumnForItem(int index, int column) {
        RXTileRowPlan.ItemPosition position = viewport.itemPositionOf(index);
        if (position.valid()) {
            rememberPreferredColumn(column, position);
        } else {
            clearPreferredColumn();
        }
    }

    private void syncPreferredColumnWithFocusedCell() {
        int focus = focusModel.getFocusedIndex();
        if (focus < 0 || focus >= itemCount()) {
            clearPreferredColumn();
            return;
        }
        preferredColumnForVerticalNavigation(focus);
    }

    private void resetPreferredColumn(int preferredColumn, RXTileRowPlan.ItemPosition position) {
        rememberPreferredColumn(preferredColumn, position);
    }

    private void rememberPreferredColumn(int preferredColumn, RXTileRowPlan.ItemPosition position) {
        preferredVerticalColumn = preferredColumn;
        preferredColumnDataRow = position.dataRow();
        preferredColumnActualColumn = position.column();
        preferredColumnPlanColumns = viewport.columnCount();
    }

    private void clearPreferredColumn() {
        preferredVerticalColumn = -1;
        preferredColumnDataRow = -1;
        preferredColumnActualColumn = -1;
        preferredColumnPlanColumns = -1;
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && (marqueeArmed || marqueeActive)) {
            finishMarquee();
            event.consume();
            return;
        }
        RXTileView<T> control = getSkinnable();
        MultipleSelectionModel<T> sm = control.getSelectionModel();
        int itemCount = itemCount();
        if (sm == null || itemCount == 0) {
            return;
        }
        int focus = focusModel.getFocusedIndex();
        boolean shift = event.isShiftDown();
        boolean shortcut = event.isShortcutDown();
        switch (event.getCode()) {
            case LEFT, KP_LEFT -> consume(event, () -> horizontalArrow(focus, -1, shift, shortcut));
            case RIGHT, KP_RIGHT -> consume(event, () -> horizontalArrow(focus, 1, shift, shortcut));
            case UP, KP_UP -> consume(event, () -> verticalArrow(focus, -1, shift, shortcut));
            case DOWN, KP_DOWN -> consume(event, () -> verticalArrow(focus, 1, shift, shortcut));
            case HOME -> consume(event, () -> moveToAndResetPreferredColumn(0, shift, shortcut));
            case END -> consume(event, () -> moveToAndResetPreferredColumn(itemCount - 1, shift, shortcut));
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
            case A -> {
                if (shortcut) {
                    sm.selectAll();
                    focusModel.syncSelectionLeadState();
                    event.consume();
                }
            }
            default -> { }
        }
    }

    private static void consume(KeyEvent event, Runnable action) {
        action.run();
        event.consume();
    }

    private void horizontalArrow(int focus, int delta, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int target;
        if (focus < 0) {
            // No focus yet: the first arrow lands on the leading / trailing item.
            target = delta > 0 ? 0 : itemCount - 1;
        } else {
            target = clampIndex(focus + delta, itemCount);
            if (target == focus) {
                return;
            }
        }
        moveToAndResetPreferredColumn(target, shift, shortcut);
    }

    private void verticalArrow(int focus, int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int target;
        if (focus < 0) {
            target = direction > 0 ? 0 : itemCount - 1;
            moveToAndResetPreferredColumn(target, shift, shortcut);
        } else {
            int preferredColumn = preferredColumnForVerticalNavigation(focus);
            target = viewport.verticalNeighborOf(focus, direction, preferredColumn);
            if (target < 0) {
                return;
            }
            moveTo(target, shift, shortcut);
            preservePreferredColumnForItem(target, preferredColumn);
        }
    }

    private void page(int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int cols = Math.max(1, getSkinnable().getActualColumnCount());
        RXTileVisibleRange range = getSkinnable().getVisibleRange();
        int visibleRows = range.isEmpty() ? 1 : Math.max(1, range.lastRow() - range.firstRow() + 1);
        int focus = focusModel.getFocusedIndex();
        int from = focus < 0 ? (direction > 0 ? 0 : itemCount - 1) : focus;
        int target = clampIndex(from + direction * visibleRows * cols, itemCount);
        moveToAndResetPreferredColumn(target, shift, shortcut);
    }

    private void moveToAndResetPreferredColumn(int target, boolean shift, boolean shortcut) {
        moveTo(target, shift, shortcut);
        resetPreferredColumnToItem(target);
    }

    // Navigation works on item indices, never visual rows, so it naturally skips
    // section-header rows (a header occupies a visual row but no item index).
    private void moveTo(int target, boolean shift, boolean shortcut) {
        MultipleSelectionModel<T> sm = getSkinnable().getSelectionModel();
        if (sm == null) {
            return;
        }
        focusModel.focus(target);
        if (shortcut) {
            // Ctrl/Cmd + navigation moves focus only; selection is unchanged.
            setAnchor(target);
        } else if (shift && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            int anchor = clampIndex(getAnchor(), itemCount());
            clearAndSelectRange(sm, anchor, target);
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
        if (sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            if (sm.isSelected(focus)) {
                sm.clearSelection(focus);
            } else {
                sm.select(focus);
            }
        } else {
            sm.clearAndSelect(focus);
        }
        setAnchor(focus);
        focusModel.syncSelectionLeadState();
    }

    private void activate(int index) {
        RXTileView<T> control = getSkinnable();
        if (index < 0 || index >= itemCount()) {
            return;
        }
        T item = control.getItems().get(index);
        control.fireEvent(new RXTileViewActionEvent<>(control, item, index));
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXTileView<T> control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        MultipleSelectionModel<T> sm = control.getSelectionModel();
        RXTileCell<T> cell = viewport.cellAt(event.getTarget());
        if (sm == null) {
            return;
        }
        if (cell == null) {
            Point2D point = viewportPoint(event);
            if (canStartMarquee(event, sm, point)) {
                armMarquee(point);
                event.consume();
            }
            return;
        }
        finishMarquee();
        int index = cell.getIndex();
        focusModel.focus(index);
        resetPreferredColumnToItem(index);
        if (event.isShortcutDown()) {
            if (sm.isSelected(index)) {
                sm.clearSelection(index);
            } else {
                sm.select(index);
            }
            setAnchor(index);
        } else if (event.isShiftDown() && sm.getSelectionMode() == SelectionMode.MULTIPLE) {
            int anchor = clampIndex(getAnchor(), itemCount());
            clearAndSelectRange(sm, anchor, index);
        } else {
            setAnchor(index);
            sm.clearAndSelect(index);
        }
        focusModel.syncSelectionLeadState();
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            RXTileCell<T> cell = viewport.cellAt(event.getTarget());
            if (cell != null) {
                activate(cell.getIndex());
            }
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (!marqueeArmed && !marqueeActive) {
            return;
        }
        if (!event.isPrimaryButtonDown()) {
            finishMarquee();
            return;
        }
        Point2D point = viewportPoint(event);
        marqueeLastX = point.getX();
        marqueeLastY = point.getY();
        if (!marqueeActive) {
            double dx = marqueeLastX - marqueePressX;
            double dy = marqueeLastY - marqueePressY;
            if (Math.hypot(dx, dy) < MARQUEE_START_THRESHOLD) {
                return;
            }
            marqueeActive = true;
            selectionRectangle.setVisible(true);
        }
        updateMarqueeSelection();
        updateMarqueeAutoScroll();
        event.consume();
    }

    private void onMouseReleased(MouseEvent event) {
        if (!marqueeArmed && !marqueeActive) {
            return;
        }
        if (marqueeActive) {
            Point2D point = viewportPoint(event);
            marqueeLastX = point.getX();
            marqueeLastY = point.getY();
            updateMarqueeSelection();
        }
        finishMarquee();
        event.consume();
    }

    private void clearAndSelectRange(MultipleSelectionModel<T> selectionModel, int anchor, int target) {
        int end = target >= anchor ? target + 1 : target - 1;
        if (selectionModel instanceof RXTileSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXTileSelectionModel<T> typedModel = (RXTileSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectRange(anchor, end);
        } else {
            selectionModel.clearSelection();
            selectionModel.selectRange(anchor, end);
        }
    }

    private void clearAndSelectIndices(MultipleSelectionModel<T> selectionModel, List<Integer> indices, int lead) {
        if (selectionModel instanceof RXTileSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXTileSelectionModel<T> typedModel = (RXTileSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectIndices(indices, lead);
            return;
        }
        selectionModel.clearSelection();
        if (indices.isEmpty()) {
            return;
        }
        int[] rest = new int[indices.size() - 1];
        for (int i = 1; i < indices.size(); i++) {
            rest[i - 1] = indices.get(i);
        }
        selectionModel.selectIndices(indices.get(0), rest);
        if (lead >= 0) {
            selectionModel.select(lead);
        }
    }

    private boolean canStartMarquee(MouseEvent event, MultipleSelectionModel<T> selectionModel, Point2D point) {
        return selectionModel.getSelectionMode() == SelectionMode.MULTIPLE
                && itemCount() > 0
                && !hasModifier(event)
                && viewport.isSelectableBlankPoint(point.getX(), point.getY());
    }

    private void armMarquee(Point2D point) {
        finishMarquee();
        marqueeArmed = true;
        marqueePressX = point.getX();
        marqueePressY = point.getY();
        marqueeAnchorX = point.getX();
        marqueeAnchorContentY = point.getY() + viewport.scrollOffset();
        marqueeLastX = point.getX();
        marqueeLastY = point.getY();
    }

    private void updateMarqueeSelection() {
        MultipleSelectionModel<T> selectionModel = getSkinnable().getSelectionModel();
        if (selectionModel == null) {
            return;
        }
        double currentContentY = marqueeLastY + viewport.scrollOffset();
        List<Integer> indices = viewport.itemIndicesIntersectingContentRect(
                marqueeAnchorX, marqueeAnchorContentY, marqueeLastX, currentContentY);
        int lead = marqueeLead(indices, currentContentY);
        clearAndSelectIndices(selectionModel, indices, lead);
        if (lead >= 0) {
            focusModel.focus(lead);
            resetPreferredColumnToItem(lead);
            setAnchor(lead);
        }
        focusModel.syncSelectionLeadState();
        layoutMarqueeRectangle();
    }

    private int marqueeLead(List<Integer> indices, double currentContentY) {
        if (indices.isEmpty()) {
            return -1;
        }
        int direct = viewport.itemIndexAtContentPoint(marqueeLastX, currentContentY);
        if (indices.contains(direct)) {
            return direct;
        }
        if (currentContentY > marqueeAnchorContentY
                || (currentContentY == marqueeAnchorContentY && marqueeLastX >= marqueeAnchorX)) {
            return indices.get(indices.size() - 1);
        }
        return indices.get(0);
    }

    private void updateMarqueeAutoScroll() {
        double delta = marqueeAutoScrollDelta();
        if (delta == 0.0) {
            marqueeAutoScroll.stop();
        } else if (marqueeAutoScroll.getStatus() != Animation.Status.RUNNING) {
            marqueeAutoScroll.play();
        }
    }

    private void onMarqueeAutoScroll() {
        if (!marqueeActive) {
            marqueeAutoScroll.stop();
            return;
        }
        double delta = marqueeAutoScrollDelta();
        if (delta == 0.0) {
            marqueeAutoScroll.stop();
            return;
        }
        if (!viewport.scrollByPixels(delta)) {
            marqueeAutoScroll.stop();
            return;
        }
        viewport.layout();
        updateVisibleRange();
        updateVisibleSection();
        updateMarqueeSelection();
    }

    private double marqueeAutoScrollDelta() {
        double height = viewport.getHeight();
        if (height <= 0.0) {
            return 0.0;
        }
        double distance;
        if (marqueeLastY < MARQUEE_AUTO_SCROLL_EDGE) {
            distance = MARQUEE_AUTO_SCROLL_EDGE - marqueeLastY;
            return -marqueeAutoScrollStep(distance);
        }
        if (marqueeLastY > height - MARQUEE_AUTO_SCROLL_EDGE) {
            distance = marqueeLastY - (height - MARQUEE_AUTO_SCROLL_EDGE);
            return marqueeAutoScrollStep(distance);
        }
        return 0.0;
    }

    private static double marqueeAutoScrollStep(double distance) {
        if (distance <= 0.0) {
            return 0.0;
        }
        double ratio = Math.min(1.0, distance / MARQUEE_AUTO_SCROLL_EDGE);
        return Math.max(1.0, ratio * MARQUEE_AUTO_SCROLL_MAX_STEP);
    }

    private void layoutMarqueeRectangle() {
        if (!marqueeActive) {
            selectionRectangle.setVisible(false);
            return;
        }
        double anchorY = marqueeAnchorContentY - viewport.scrollOffset();
        double minX = clamp(Math.min(marqueeAnchorX, marqueeLastX), 0.0, viewport.contentWidth());
        double maxX = clamp(Math.max(marqueeAnchorX, marqueeLastX), 0.0, viewport.contentWidth());
        double minY = clamp(Math.min(anchorY, marqueeLastY), 0.0, viewport.getHeight());
        double maxY = clamp(Math.max(anchorY, marqueeLastY), 0.0, viewport.getHeight());
        selectionRectangle.setX(viewport.getLayoutX() + minX);
        selectionRectangle.setY(viewport.getLayoutY() + minY);
        selectionRectangle.setWidth(maxX - minX);
        selectionRectangle.setHeight(maxY - minY);
        selectionRectangle.setVisible((maxX - minX) > 0.0 && (maxY - minY) > 0.0);
    }

    private void finishMarquee() {
        marqueeAutoScroll.stop();
        marqueeArmed = false;
        marqueeActive = false;
        selectionRectangle.setVisible(false);
    }

    private Point2D viewportPoint(MouseEvent event) {
        return viewport.sceneToLocal(event.getSceneX(), event.getSceneY());
    }

    private static boolean hasModifier(MouseEvent event) {
        return event.isShiftDown()
                || event.isShortcutDown()
                || event.isControlDown()
                || event.isAltDown()
                || event.isMetaDown();
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

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    // ==================== Shared helpers ====================

    private int itemCount() {
        ObservableList<T> items = getSkinnable().getItems();
        return items == null ? 0 : items.size();
    }

    static double cellWidthOrDefault(RXTileView<?> control) {
        return finitePositiveOrDefault(control.getCellWidth(), FALLBACK_CELL_WIDTH);
    }

    static double cellHeightOrDefault(RXTileView<?> control) {
        return finitePositiveOrDefault(control.getCellHeight(), FALLBACK_CELL_HEIGHT);
    }

    static double sectionHeaderHeightOrDefault(RXTileView<?> control) {
        return finitePositiveOrDefault(control.getSectionHeaderHeight(), FALLBACK_SECTION_HEADER_HEIGHT);
    }

    static double gapOrZero(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    static double finitePositiveOrDefault(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    static ItemsJustify justifyOrDefault(ItemsJustify justify) {
        return justify == null ? ItemsJustify.START : justify;
    }
}

package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.CellHeightContext;
import io.github.leewyatt.rxcontrols.CellHeightProvider;
import io.github.leewyatt.rxcontrols.RXIndexedSelectionModel;
import io.github.leewyatt.rxcontrols.RXMasonryCell;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.event.RXMasonryViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns;
import io.github.leewyatt.rxcontrols.internal.MasonryColumns.Resolution;
import io.github.leewyatt.rxcontrols.internal.RXIndexedSelectionMutationGuard;
import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * Skin for {@link RXMasonryView}. It hosts a virtualizing {@link RXMasonryViewport}
 * and a placeholder layer, builds the immutable {@link RXMasonryPlacement} each pass
 * (resolving the column count and track width, then each item's exact or estimated
 * height), publishes the read-only metrics, and owns the keyboard / mouse / marquee
 * interaction and the internal focus model.
 *
 * <p>It distinguishes two widths so the responsive decision stays stable: the active
 * breakpoint and the breakpoint column cascade always read the pre-scrollbar content
 * width, while the {@code columnWidth} auto floor and the track width read the width
 * actually available this pass. When the content overflows, a second pass re-resolves
 * against the width minus the scroll bar, exactly as the tile view does.</p>
 *
 * <p>Vertical arrow navigation is geometric (masonry has no row grid): up / down move
 * to the item whose horizontal range is nearest the held navigation x, then the
 * smallest vertical gap. Left / right step by source index.</p>
 *
 * @param <T> the item type
 */
public class RXMasonryViewSkin<T> extends RXSkinBase<RXMasonryView<T>> {

    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // Skin-local fallbacks coerce the control's lenient (non-positive / non-finite)
    // sizing values at the use site; the control's own defaults stay private.
    private static final double FALLBACK_COLUMN_WIDTH = 260.0;
    private static final double FALLBACK_GAP = 8.0;
    private static final double FALLBACK_ESTIMATED_CELL_HEIGHT = 200.0;
    private static final int DEFAULT_VISIBLE_ROWS = 3;
    private static final double MIN_VIEWPORT_CONTENT_WIDTH = 2.0;
    private static final int MAX_RESOLVED_COLUMNS = 4096;

    private static final double MARQUEE_START_THRESHOLD = 4.0;
    private static final double MARQUEE_AUTO_SCROLL_EDGE = 32.0;
    private static final double MARQUEE_AUTO_SCROLL_MAX_STEP = 24.0;
    private static final Duration MARQUEE_AUTO_SCROLL_INTERVAL = Duration.millis(16.0);

    // Key for the shift-range selection anchor, stashed in the control's property map
    // (mirrors ListView's CellBehaviorBase anchor) so it needs no new API.
    private static final String ANCHOR_KEY = "rx-masonry-view-selection-anchor";

    private final RXMasonryViewport<T> viewport;
    private final StackPane placeholderRegion;
    private final Rectangle selectionRectangle;
    private final Timeline marqueeAutoScroll;

    private final ListChangeListener<T> itemsContentListener = change -> onItemsContentChanged();
    private final WeakListChangeListener<T> weakItemsContentListener =
            new WeakListChangeListener<>(itemsContentListener);
    private ObservableList<T> observedItems;

    private final RXIndexedFocusModel<T> focusModel;
    private MultipleSelectionModel<T> observedSelectionModel;
    private final ListChangeListener<Integer> selectionListener = change -> onSelectionChanged();
    private final ChangeListener<Number> selectedIndexListener = (obs, oldI, newI) -> syncFocusSelectionLead();
    private final ChangeListener<SelectionMode> selectionModeListener = (obs, oldM, newM) -> onSelectionModeChanged();

    // The content-space x to hold while moving up / down so the focus does not drift
    // sideways through items of varying width; NaN means "use the focused item's own
    // center on the next vertical move".
    private double preferredNavX = Double.NaN;

    private boolean marqueeArmed;
    private boolean marqueeActive;
    private double marqueePressX;
    private double marqueePressY;
    private double marqueeAnchorX;
    private double marqueeAnchorContentY;
    private double marqueeLastX;
    private double marqueeLastY;

    private record PlacementResult(RXMasonryPlacement placement, RXBreakpoint activeBreakpoint) {
    }

    /**
     * Creates a skin for the given masonry view.
     *
     * @param control the masonry view
     */
    public RXMasonryViewSkin(RXMasonryView<T> control) {
        super(control);

        viewport = new RXMasonryViewport<>(control);
        getChildren().add(viewport);

        placeholderRegion = new StackPane();
        placeholderRegion.getStyleClass().add("placeholder");
        placeholderRegion.setManaged(false);
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

        // The control is the single Tab stop, so keys arrive on it; cells are not
        // focusable. The focus model reads the lead through the live selection model.
        focusModel = new RXIndexedFocusModel<>(control.itemsProperty(), control::getSelectionModel);
        viewport.setFocusModel(focusModel);

        registerListeners(control);
        attachItems(control.getItems());
        updatePlaceholder();
        attachSelectionModel(control.getSelectionModel());
        focusModel.moveItemsObserversToEnd();
        focusModel.syncSelectionLeadState();
        disposer.registerDisposeTask(this::detachItems);
        disposer.registerDisposeTask(this::detachSelectionModel);
    }

    private void registerListeners(RXMasonryView<T> control) {
        disposer.registerListener(control.itemsProperty(), this::onItemsListSwapped);
        disposer.registerListener(control.placeholderProperty(), this::updatePlaceholder);
        disposer.registerListener(control.cellFactoryProperty(), viewport::recreateCells);

        // Every property that changes the placement geometry asks for a relayout; the
        // control's plain (no-invalidated) styleable properties rely on this.
        disposer.registerListener(control.columnWidthProperty(), this::requestRelayout);
        disposer.registerListener(control.hgapProperty(), this::requestRelayout);
        disposer.registerListener(control.vgapProperty(), this::requestRelayout);
        disposer.registerListener(control.columnCountProperty(), this::requestRelayout);
        disposer.registerListener(control.maxColumnsProperty(), this::requestRelayout);
        disposer.registerListener(control.fillWidthProperty(), this::requestRelayout);
        disposer.registerListener(control.alignmentProperty(), this::requestRelayout);
        disposer.registerListener(control.breakpointProfileProperty(), this::requestRelayout);
        disposer.registerListener(control.estimatedCellHeightProperty(), this::requestRelayout);
        disposer.registerListener(control.cellHeightProviderProperty(), this::requestRelayout);
        disposer.registerListener(control.columnSpanFactoryProperty(), this::requestRelayout);
        // Breakpoint overrides live in an observable map, not a property, but change the
        // resolved column count just the same.
        disposer.registerListener(control.getBreakpointColumnOverrides(), this::requestRelayout);
        // Reorder animation: snap any in-flight glide when it is turned off mid-flight.
        disposer.registerListener(control.animatedProperty(), viewport::onAnimationSettingsChanged);
        disposer.registerListener(control.animationDurationProperty(), viewport::onAnimationSettingsChanged);
        // prefColumns only feeds computePrefWidth (a parent size hint), not the
        // placement, so it relays out the control rather than re-filling the viewport.
        disposer.registerListener(control.prefColumnsProperty(), () -> getSkinnable().requestLayout());

        // Selection / focus / interaction: re-apply the per-cell state on change; re-wire
        // on a selection-model swap; install the keyboard (control) and mouse (viewport)
        // handlers.
        disposer.registerListener(focusModel.focusedIndexProperty(), this::refreshSelectionAndFocus);
        disposer.registerListener(control.selectionModelProperty(), this::onSelectionModelSwapped);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        disposer.registerEventHandler(viewport, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
    }

    // Dirties the viewport (forcing a re-fill) and, by propagation, the control (so the
    // skin re-runs buildPlacement and republishes metrics).
    private void requestRelayout() {
        viewport.requestLayout();
    }

    // ==================== Items / Placeholder / :empty ====================

    private void onItemsListSwapped() {
        finishMarquee();
        attachItems(getSkinnable().getItems());
        // Both anchors referred to the previous list; drop them so a later fill /
        // Shift-action starts fresh instead of off a stale origin.
        viewport.resetAnchor();
        resetAnchor();
        resetPreferredNav();
        updatePlaceholder();
        requestRelayout();
    }

    private void onItemsContentChanged() {
        finishMarquee();
        viewport.resetAnchor();
        resetAnchor();
        resetPreferredNav();
        updatePlaceholder();
        requestRelayout();
    }

    private void attachItems(ObservableList<T> items) {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
        }
        observedItems = items;
        if (items != null) {
            items.addListener(weakItemsContentListener);
        }
    }

    private void detachItems() {
        if (observedItems != null) {
            observedItems.removeListener(weakItemsContentListener);
            observedItems = null;
        }
    }

    private void updatePlaceholder() {
        RXMasonryView<T> control = getSkinnable();
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

    private int itemCount() {
        ObservableList<T> items = getSkinnable().getItems();
        return items == null ? 0 : items.size();
    }

    // ==================== Selection model wiring ====================

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
        finishMarquee();
        resetAnchor();
        resetPreferredNav();
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

    // ==================== Anchor / preferred nav x ====================

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

    private void resetPreferredNav() {
        preferredNavX = Double.NaN;
    }

    // ==================== Keyboard ====================

    private void onKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE && (marqueeArmed || marqueeActive)) {
            finishMarquee();
            event.consume();
            return;
        }
        RXMasonryView<T> control = getSkinnable();
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
            case HOME -> consume(event, () -> moveToAndResetNav(0, shift, shortcut));
            case END -> consume(event, () -> moveToAndResetNav(itemCount - 1, shift, shortcut));
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
            default -> {
            }
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
            target = delta > 0 ? 0 : itemCount - 1;
        } else {
            target = clampIndex(focus + delta, itemCount);
            if (target == focus) {
                return;
            }
        }
        moveToAndResetNav(target, shift, shortcut);
    }

    private void verticalArrow(int focus, int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        if (focus < 0) {
            moveToAndResetNav(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        if (Double.isNaN(preferredNavX)) {
            preferredNavX = viewport.itemCenterX(focus);
        }
        int target = viewport.verticalNeighbor(focus, direction, preferredNavX);
        if (target < 0) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    // Masonry has no rows: a page walks the geometric neighbor downward / upward until a
    // viewport-height of content has been crossed (or the edge is reached), keeping the
    // held navigation x so the focus stays in the same column band.
    private void page(int direction, boolean shift, boolean shortcut) {
        int itemCount = itemCount();
        int focus = focusModel.getFocusedIndex();
        if (focus < 0) {
            moveToAndResetNav(direction > 0 ? 0 : itemCount - 1, shift, shortcut);
            return;
        }
        if (Double.isNaN(preferredNavX)) {
            preferredNavX = viewport.itemCenterX(focus);
        }
        double viewportHeight = viewport.getHeight();
        double startTop = viewport.itemTop(focus);
        int target = focus;
        while (true) {
            int next = viewport.verticalNeighbor(target, direction, preferredNavX);
            if (next < 0) {
                break;
            }
            target = next;
            double targetTop = viewport.itemTop(target);
            // A single step always counts; stop once a viewport-height is crossed. On an
            // unsized viewport or unavailable tops take a single step only, so a stray
            // page on a not-yet-laid-out control does not leap to the edge.
            if (viewportHeight <= 0.0 || Double.isNaN(startTop) || Double.isNaN(targetTop)) {
                break;
            }
            if (Math.abs(targetTop - startTop) >= viewportHeight) {
                break;
            }
        }
        if (target == focus) {
            return;
        }
        moveTo(target, shift, shortcut);
    }

    private void moveToAndResetNav(int target, boolean shift, boolean shortcut) {
        moveTo(target, shift, shortcut);
        resetPreferredNav();
    }

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
        RXMasonryView<T> control = getSkinnable();
        if (index < 0 || index >= itemCount()) {
            return;
        }
        T item = control.getItems().get(index);
        control.fireEvent(new RXMasonryViewActionEvent<>(control, item, index));
    }

    // ==================== Mouse ====================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXMasonryView<T> control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        MultipleSelectionModel<T> sm = control.getSelectionModel();
        RXMasonryCell<T> cell = viewport.cellAt(event.getTarget());
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
        resetPreferredNav();
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
            RXMasonryCell<T> cell = viewport.cellAt(event.getTarget());
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
        if (selectionModel instanceof RXIndexedSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXIndexedSelectionModel<T> typedModel = (RXIndexedSelectionModel<T>) rxSelectionModel;
            typedModel.clearAndSelectRange(anchor, end);
        } else {
            selectionModel.clearSelection();
            selectionModel.selectRange(anchor, end);
        }
    }

    private void clearAndSelectIndices(MultipleSelectionModel<T> selectionModel, List<Integer> indices, int lead) {
        if (selectionModel instanceof RXIndexedSelectionModel<?> rxSelectionModel) {
            @SuppressWarnings("unchecked")
            RXIndexedSelectionModel<T> typedModel = (RXIndexedSelectionModel<T>) rxSelectionModel;
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

    // ==================== Marquee ====================

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
            resetPreferredNav();
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
        publishVisibleBounds();
        updateMarqueeSelection();
    }

    private double marqueeAutoScrollDelta() {
        double height = viewport.getHeight();
        if (height <= 0.0) {
            return 0.0;
        }
        if (marqueeLastY < MARQUEE_AUTO_SCROLL_EDGE) {
            return -marqueeAutoScrollStep(MARQUEE_AUTO_SCROLL_EDGE - marqueeLastY);
        }
        if (marqueeLastY > height - MARQUEE_AUTO_SCROLL_EDGE) {
            return marqueeAutoScrollStep(marqueeLastY - (height - MARQUEE_AUTO_SCROLL_EDGE));
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
        double contentWidth = viewport.contentWidth();
        double minX = clamp(Math.min(marqueeAnchorX, marqueeLastX), 0.0, contentWidth);
        double maxX = clamp(Math.max(marqueeAnchorX, marqueeLastX), 0.0, contentWidth);
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
        return event.isShiftDown() || event.isShortcutDown() || event.isControlDown()
                || event.isAltDown() || event.isMetaDown();
    }

    private static int clampIndex(int index, int itemCount) {
        if (index < 0) {
            return 0;
        }
        return Math.min(index, itemCount - 1);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        RXMasonryView<T> control = getSkinnable();
        double rightInset = Math.max(0.0, control.getWidth() - contentX - contentWidth);
        double bottomInset = Math.max(0.0, control.getHeight() - contentY - contentHeight);
        viewport.setChromeInsets(contentX, contentY, rightInset, bottomInset);

        PlacementResult result = buildPlacement(contentWidth, contentHeight);
        int newColumns = result.placement().columns();
        if (newColumns != control.getActualColumnCount()) {
            // A column-count reflow shifts every column's x; drop the held navigation x
            // so the next vertical move re-seeds it from the focused item's fresh center.
            resetPreferredNav();
        }
        viewport.setPlacement(result.placement());
        control.setActualColumnCount(newColumns);
        control.setActiveBreakpoint(result.activeBreakpoint());

        viewport.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        placeholderRegion.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        consumePendingScroll();
        // Force the viewport to realize cells now so the scroll request and the
        // published visible bounds reflect this pass, not the previous one.
        viewport.layout();
        publishVisibleBounds();
        layoutMarqueeRectangle();
    }

    private void publishVisibleBounds() {
        RXMasonryView<T> control = getSkinnable();
        control.setFirstVisibleIndex(viewport.getVisibleFirstIndex());
        control.setLastVisibleIndex(viewport.getVisibleLastIndex());
    }

    private void consumePendingScroll() {
        RXMasonryView<T> control = getSkinnable();
        if (!control.hasPendingScroll()) {
            return;
        }
        int itemCount = itemCount();
        if (itemCount == 0) {
            control.clearPendingScroll();
            return;
        }
        int index = Math.max(0, Math.min(control.getPendingScrollIndex(), itemCount - 1));
        // Clear only when the request was actually applied. On a zero-height pass
        // scrollToIndex cannot compute geometry and returns false; keeping the request
        // armed lets the first sized pass honor it.
        if (viewport.scrollToIndex(index, control.getPendingScrollAlignment())) {
            control.clearPendingScroll();
        }
    }

    // The breakpoint-driving width is the pre-scrollbar content width, so the active
    // breakpoint never flips when the bar appears; the track-driving width drops the
    // bar breadth on the overflow pass.
    private PlacementResult buildPlacement(double breakpointWidth, double availableHeight) {
        RXMasonryView<T> control = getSkinnable();
        double snappedColumnWidth = snapSizeX(columnWidthOrDefault(control));
        double snappedHgap = snapSpaceX(gapOrDefault(control.getHgap()));
        // Clamp to zero: the per-column binary-search visibility query requires items
        // to stack monotonically, so the virtualized view does not overlap.
        double snappedVgap = Math.max(0.0, snapSpaceY(gapOrDefault(control.getVgap())));
        RXBreakpointProfile profile = breakpointProfileOrDefault(control);
        Map<String, Integer> overrides = control.getBreakpointColumnOverrides();

        Resolution first = MasonryColumns.resolve(breakpointWidth, breakpointWidth, control.getColumnCount(),
                snappedColumnWidth, snappedHgap, control.getMaxColumns(), control.isFillWidth(), profile, overrides);
        RXMasonryPlacement firstPlacement = placementFor(first, breakpointWidth, snappedHgap, snappedVgap);
        if (firstPlacement.contentHeight() <= availableHeight) {
            return new PlacementResult(firstPlacement, first.activeBreakpoint());
        }

        // Overflow: a vertical bar is needed, so re-resolve the columns / track against
        // the narrower width. The active breakpoint is unchanged (it ignores layoutWidth).
        double layoutWidth = Math.max(0.0, breakpointWidth - viewport.scrollBarBreadth());
        Resolution second = MasonryColumns.resolve(breakpointWidth, layoutWidth, control.getColumnCount(),
                snappedColumnWidth, snappedHgap, control.getMaxColumns(), control.isFillWidth(), profile, overrides);
        RXMasonryPlacement secondPlacement = placementFor(second, layoutWidth, snappedHgap, snappedVgap);
        return new PlacementResult(secondPlacement, second.activeBreakpoint());
    }

    private RXMasonryPlacement placementFor(Resolution resolution, double layoutWidth,
                                            double snappedHgap, double snappedVgap) {
        RXMasonryView<T> control = getSkinnable();
        ObservableList<T> items = control.getItems();
        int count = items == null ? 0 : items.size();
        int columns = resolution.columns();
        double track = resolution.trackWidth();
        double startX = horizontalAlignmentOffset(control, layoutWidth, resolution.usedWidth());

        double[] heights = new double[count];
        int[] spans = new int[count];
        CellHeightProvider<T> provider = control.getCellHeightProvider();
        double estimated = estimatedCellHeightOrDefault(control);
        Callback<T, Integer> spanFactory = control.getColumnSpanFactory();
        for (int i = 0; i < count; i++) {
            T item = items.get(i);
            int span = resolveSpan(spanFactory, item, columns);
            spans[i] = span;
            double cellWidth = span * track + (span - 1) * snappedHgap;
            double height = estimated;
            if (provider != null) {
                double provided = provider.computeHeight(
                        new CellHeightContext<>(item, i, cellWidth, span, track, columns));
                if (Double.isFinite(provided) && provided >= 0.0) {
                    height = provided;
                }
            }
            heights[i] = snapSizeY(height);
        }
        return new RXMasonryPlacement(columns, track, snappedHgap, snappedVgap, startX, heights, spans);
    }

    private static int resolveSpan(Callback<?, Integer> spanFactory, Object item, int columns) {
        int span = 1;
        if (spanFactory != null) {
            @SuppressWarnings("unchecked")
            Callback<Object, Integer> typed = (Callback<Object, Integer>) spanFactory;
            Integer value = typed.call(item);
            if (value != null) {
                span = value;
            }
        }
        if (span < 1) {
            return 1;
        }
        return Math.min(span, columns);
    }

    private static double horizontalAlignmentOffset(RXMasonryView<?> control, double layoutWidth,
                                                    double usedWidth) {
        double slack = Math.max(0.0, layoutWidth - usedWidth);
        HPos hpos = alignmentOrDefault(control).getHpos();
        return switch (hpos) {
            case CENTER -> slack / 2.0;
            case RIGHT -> slack;
            default -> 0.0;
        };
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        RXMasonryView<T> control = getSkinnable();
        double columnWidth = columnWidthOrDefault(control);
        double gap = gapOrDefault(control.getHgap());
        int columns = capColumns(control.getPrefColumns(), control.getMaxColumns());
        double content = columns * columnWidth + (columns - 1) * gap;
        return leftInset + content + viewport.scrollBarBreadth() + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        RXMasonryView<T> control = getSkinnable();
        double slot = estimatedCellHeightOrDefault(control) + gapOrDefault(control.getVgap());
        return topInset + DEFAULT_VISIBLE_ROWS * slot + bottomInset;
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
        finishMarquee();
        viewport.dispose();
        placeholderRegion.getChildren().clear();
    }

    // ==================== Lenient value helpers ====================

    static double columnWidthOrDefault(RXMasonryView<?> control) {
        double value = control.getColumnWidth();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_COLUMN_WIDTH;
    }

    static double estimatedCellHeightOrDefault(RXMasonryView<?> control) {
        double value = control.getEstimatedCellHeight();
        return Double.isFinite(value) && value > 0.0 ? value : FALLBACK_ESTIMATED_CELL_HEIGHT;
    }

    static double gapOrDefault(double value) {
        return Double.isFinite(value) ? value : FALLBACK_GAP;
    }

    private static Pos alignmentOrDefault(RXMasonryView<?> control) {
        Pos value = control.getAlignment();
        return value == null ? Pos.TOP_LEFT : value;
    }

    private static RXBreakpointProfile breakpointProfileOrDefault(RXMasonryView<?> control) {
        RXBreakpointProfile value = control.getBreakpointProfile();
        return value == null ? RXBreakpointProfile.ANT_DESIGN : value;
    }

    private static int capColumns(int columns, int maxColumns) {
        int capped = Math.max(1, columns);
        if (maxColumns > 0 && capped > maxColumns) {
            capped = maxColumns;
        }
        return Math.min(capped, MAX_RESOLVED_COLUMNS);
    }
}

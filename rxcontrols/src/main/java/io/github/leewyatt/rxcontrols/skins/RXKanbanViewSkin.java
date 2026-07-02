package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.RXKanbanCardCell;
import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.event.CardActionEvent;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Interpolator;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.WeakListChangeListener;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default skin for {@link RXKanbanView}. It orchestrates the horizontal row of
 * columns, the board-level horizontal scroll chrome, the board placeholder and
 * (in later phases) the board-top drag overlay and the board-level position-key
 * selection / focus — all built here, because the shared virtualizing viewport
 * base has no horizontal or board-level concepts.
 *
 * <p>Each column is a {@link KanbanColumnBox} holding a {@link KanbanColumnViewport}
 * (independent vertical scroll + fixed-height virtualization). The columns are laid
 * out left to right at {@link RXKanbanView#getPrefColumnWidth() prefColumnWidth}
 * inside the clipped {@code .columns} host; horizontal overflow is scrolled by
 * offsetting the boxes against a board-level horizontal {@link ScrollBar}.
 *
 * @param <T> the card type
 */
public class RXKanbanViewSkin<T> extends RXSkinBase<RXKanbanView<T>> {

    // Fallback sizing defaults (mirror RXKanbanView's private property defaults),
    // applied when a styleable size is non-positive / non-finite at layout time.
    private static final double DEFAULT_PREF_CARD_HEIGHT = 96.0;
    private static final double DEFAULT_CARD_SPACING = 8.0;
    private static final double DEFAULT_PREF_COLUMN_WIDTH = 280.0;
    private static final double DEFAULT_COLUMN_SPACING = 12.0;

    private static final double SCROLL_BAR_SYNC_EPSILON = 1.0e-4;

    private final Pane columnsBox = new Pane();
    private final Rectangle columnsClip = new Rectangle();
    private final StackPane placeholderHost = new StackPane();
    private final ScrollBar hbar = new ScrollBar();

    private final List<KanbanColumnBox<T>> boxes = new ArrayList<>();
    private final KanbanCardDragSupport<T> dragSupport;

    private double boardScrollX;
    private double cachedMaxBoardScrollX;
    private boolean adjustingHbar;

    // Column geometry captured on the last layout pass, so keyboard navigation can
    // scroll a column into horizontal view without recomputing the whole layout.
    // Per-column because hidden columns collapse to zero width, shown ones to full.
    private double[] cachedColumnX = new double[0];
    private double[] cachedColumnW = new double[0];
    private double cachedBoardWidth;

    // Leading offset and inter-column gap the last layout pass resolved from
    // columnsJustify (spare width may push the block right or widen the gaps). The
    // reorder-gap layout reuses them so the make-way slot stays aligned with the rest.
    private double layoutColumnStartX;
    private double layoutColumnGap;

    // Column reorder: FLIP the boxes from their pre-reorder visual x to the new layout
    // x. Populated by the column drag support on commit, consumed by the next layout.
    private final Map<KanbanColumnBox<T>, Double> pendingColumnFlipFromX = new IdentityHashMap<>();
    private final ViewportReorderAnimator columnAnimator = new ViewportReorderAnimator();
    private final KanbanColumnDragSupport<T> columnDragSupport;

    // Live make-way preview while a column header is being dragged: the dragged column
    // floats at the pointer while the others open a column-wide gap at the hover index.
    // -1 when no column drag is in progress.
    private int reorderDraggedIndex = -1;
    private int reorderPreviewIndex = -1;

    private final ListChangeListener<RXKanbanColumn<T>> columnsListListener = change -> reconcileColumns();
    private final WeakListChangeListener<RXKanbanColumn<T>> weakColumnsListListener =
            new WeakListChangeListener<>(columnsListListener);
    private ObservableList<RXKanbanColumn<T>> observedColumns;

    /**
     * Creates the skin.
     *
     * @param control the kanban view
     */
    public RXKanbanViewSkin(RXKanbanView<T> control) {
        super(control);
        columnsBox.getStyleClass().add("columns");
        columnsBox.setManaged(false);
        columnsBox.setClip(columnsClip);
        placeholderHost.getStyleClass().add("placeholder");
        placeholderHost.setManaged(false);
        placeholderHost.setVisible(false);
        hbar.getStyleClass().add("scroll-bar");
        hbar.setOrientation(Orientation.HORIZONTAL);
        hbar.setManaged(false);
        hbar.setVisible(false);
        hbar.setMin(0.0);
        dragSupport = new KanbanCardDragSupport<>(this, control);
        columnDragSupport = new KanbanColumnDragSupport<>(this, control);
        // The drag overlay sits above every column (and their clips) so the ghost is
        // never clipped by a column.
        getChildren().addAll(columnsBox, placeholderHost, hbar, dragSupport.getOverlay());

        disposer.registerListener(hbar.valueProperty(), this::onHbarValue);

        attachColumns(control.getColumns());
        disposer.registerListener(control.columnsProperty(), this::onColumnsPropertyChanged);
        disposer.registerListener(control.placeholderProperty(), this::onPlaceholderChanged);
        disposer.registerListener(control.columnHeaderFactoryProperty(), this::onHeaderFactoryChanged);
        disposer.registerListener(control.columnFooterFactoryProperty(), this::onFooterFactoryChanged);
        disposer.registerListener(control.emptyColumnPlaceholderFactoryProperty(), this::onPlaceholderFactoryChanged);
        disposer.registerListener(control.cardCellFactoryProperty(), this::onCardCellFactoryChanged);
        disposer.registerListener(control.prefColumnWidthProperty(), this::requestLayout);
        disposer.registerListener(control.minColumnWidthProperty(), this::requestLayout);
        disposer.registerListener(control.maxColumnWidthProperty(), this::requestLayout);
        disposer.registerListener(control.columnsJustifyProperty(), this::requestLayout);
        disposer.registerListener(control.columnSpacingProperty(), this::requestLayout);
        disposer.registerListener(control.prefCardHeightProperty(), this::onCardMetricsChanged);
        disposer.registerListener(control.cardSpacingProperty(), this::onCardMetricsChanged);
        disposer.registerListener(control.animatedProperty(), this::onAnimationSettingsChanged);
        disposer.registerListener(control.animationDurationProperty(), this::onAnimationSettingsChanged);
        disposer.registerEventHandler(control, ScrollEvent.SCROLL, this::onBoardScroll);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, dragSupport::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, columnDragSupport::onMousePressed);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_DRAGGED, dragSupport::onMouseDragged);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_DRAGGED, columnDragSupport::onMouseDragged);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, dragSupport::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_RELEASED, columnDragSupport::onMouseReleased);
        disposer.registerEventHandler(control, MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, this::onKeyPressed);
        disposer.registerDisposeTask(this::detachColumns);

        onPlaceholderChanged();
        reconcileColumns();
    }

    private void requestLayout() {
        getSkinnable().requestLayout();
    }

    // The row stride (cardHeight + cardSpacing) is consumed inside each column viewport's
    // layoutChildren, which the pulse skips while the viewport's own size is unchanged.
    // Dirty every viewport explicitly so a stride change takes effect immediately, rather
    // than waiting for another property (e.g. column width) to resize the boxes.
    private void onCardMetricsChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.getViewport().requestLayout();
        }
        getSkinnable().requestLayout();
    }

    // ==================== Sizing helpers ====================

    static double prefCardHeightOrDefault(RXKanbanView<?> control) {
        return finitePositiveOrDefault(control.getPrefCardHeight(), DEFAULT_PREF_CARD_HEIGHT);
    }

    static double cardSpacingOrDefault(RXKanbanView<?> control) {
        double value = control.getCardSpacing();
        return Double.isFinite(value) ? Math.max(0.0, value) : DEFAULT_CARD_SPACING;
    }

    static double prefColumnWidthOrDefault(RXKanbanView<?> control) {
        return finitePositiveOrDefault(control.getPrefColumnWidth(), DEFAULT_PREF_COLUMN_WIDTH);
    }

    static double columnSpacingOrDefault(RXKanbanView<?> control) {
        double value = control.getColumnSpacing();
        return Double.isFinite(value) ? Math.max(0.0, value) : DEFAULT_COLUMN_SPACING;
    }

    static ItemsJustify justifyOrDefault(RXKanbanView<?> control) {
        ItemsJustify value = control.getColumnsJustify();
        return value != null ? value : ItemsJustify.START;
    }

    // Shrink floor: a negative value (USE_COMPUTED_SIZE) or a non-finite value disables
    // shrinking (floor == pref); 0 is a real floor (shrink to nothing). Never above pref,
    // since min is a lower bound.
    static double minColumnWidthOrPref(RXKanbanView<?> control, double pref) {
        double value = control.getMinColumnWidth();
        if (!Double.isFinite(value) || value < 0.0) {
            return pref;
        }
        return Math.min(value, pref);
    }

    // STRETCH growth cap: a negative value (USE_COMPUTED_SIZE) or a non-finite value means
    // unbounded (returned as 0 — the caller's "no cap" sentinel); otherwise the cap, never
    // below prefColumnWidth (a smaller cap is degenerate).
    static double maxColumnWidthOrUnbounded(RXKanbanView<?> control, double pref) {
        double value = control.getMaxColumnWidth();
        if (!Double.isFinite(value) || value < 0.0) {
            return 0.0;
        }
        return Math.max(value, pref);
    }

    private static double finitePositiveOrDefault(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    // ==================== Columns wiring ====================

    private void onColumnsPropertyChanged() {
        detachColumns();
        attachColumns(getSkinnable().getColumns());
        reconcileColumns();
    }

    private void attachColumns(ObservableList<RXKanbanColumn<T>> columns) {
        observedColumns = columns;
        if (columns != null) {
            columns.addListener(weakColumnsListListener);
        }
    }

    private void detachColumns() {
        if (observedColumns != null) {
            observedColumns.removeListener(weakColumnsListListener);
            observedColumns = null;
        }
    }

    private void reconcileColumns() {
        Map<RXKanbanColumn<T>, KanbanColumnBox<T>> existing = new IdentityHashMap<>();
        for (KanbanColumnBox<T> box : boxes) {
            existing.put(box.getColumn(), box);
        }
        List<KanbanColumnBox<T>> rebuilt = new ArrayList<>();
        ObservableList<RXKanbanColumn<T>> columns = getSkinnable().getColumns();
        if (columns != null) {
            for (RXKanbanColumn<T> column : columns) {
                KanbanColumnBox<T> box = existing.remove(column);
                if (box == null) {
                    box = new KanbanColumnBox<>(getSkinnable(), column);
                }
                rebuilt.add(box);
            }
        }
        for (KanbanColumnBox<T> stale : existing.values()) {
            stale.dispose();
        }
        boxes.clear();
        boxes.addAll(rebuilt);
        for (KanbanColumnBox<T> box : rebuilt) {
            box.setModelListener(this::reconcileSelectionFocus);
            box.setBoardRelayout(this::requestLayout);
        }
        columnsBox.getChildren().setAll(rebuilt);
        reconcileSelectionFocus();
        updatePlaceholderVisibility();
        getSkinnable().requestLayout();
    }

    private void onHeaderFactoryChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.rebuildHeader();
        }
        getSkinnable().requestLayout();
    }

    private void onFooterFactoryChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.rebuildFooter();
        }
        getSkinnable().requestLayout();
    }

    private void onPlaceholderFactoryChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.rebuildPlaceholder();
        }
        getSkinnable().requestLayout();
    }

    private void onCardCellFactoryChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.rebuildCells();
        }
    }

    // ==================== Placeholder ====================

    private void onPlaceholderChanged() {
        Node placeholder = getSkinnable().getPlaceholder();
        if (placeholder == null) {
            placeholderHost.getChildren().clear();
        } else if (placeholderHost.getChildren().size() != 1 || placeholderHost.getChildren().get(0) != placeholder) {
            placeholderHost.getChildren().setAll(placeholder);
        }
        updatePlaceholderVisibility();
    }

    private boolean isBoardEmpty() {
        return boxes.isEmpty();
    }

    private void updatePlaceholderVisibility() {
        boolean showPlaceholder = isBoardEmpty() && getSkinnable().getPlaceholder() != null;
        placeholderHost.setVisible(showPlaceholder);
        columnsBox.setVisible(!isBoardEmpty());
    }

    // ==================== Horizontal scroll ====================

    private void onHbarValue() {
        if (adjustingHbar) {
            return;
        }
        boardScrollX = hbar.getValue();
        getSkinnable().requestLayout();
    }

    private void onBoardScroll(ScrollEvent event) {
        double deltaX = event.getDeltaX();
        if (deltaX == 0.0 || cachedMaxBoardScrollX <= 0.0) {
            return;
        }
        double target = RXMath.clamp(boardScrollX - deltaX, 0.0, cachedMaxBoardScrollX);
        if (target != boardScrollX) {
            boardScrollX = target;
            getSkinnable().requestLayout();
        }
        event.consume();
    }

    // ==================== Selection / focus ====================

    private void onMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        RXKanbanView<T> control = getSkinnable();
        if (control.isFocusTraversable()) {
            control.requestFocus();
        }
        RXKanbanCardCell<T> cell = cardCellAt(event.getTarget());
        if (cell != null) {
            selectAndFocus(cell.getColumn(), cell.getIndex());
        }
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            RXKanbanCardCell<T> cell = cardCellAt(event.getTarget());
            if (cell != null) {
                fireCardAction(cell.getColumn(), cell.getIndex());
            }
        }
    }

    private void onKeyPressed(KeyEvent event) {
        RXKanbanView<T> control = getSkinnable();
        boolean handled = true;
        switch (event.getCode()) {
            case UP -> moveFocusInColumn(-1);
            case DOWN -> moveFocusInColumn(1);
            case LEFT -> moveFocusToAdjacentColumn(-1);
            case RIGHT -> moveFocusToAdjacentColumn(1);
            case HOME -> focusEdgeOfColumn(true);
            case END -> focusEdgeOfColumn(false);
            case PAGE_UP -> pageFocus(-1);
            case PAGE_DOWN -> pageFocus(1);
            case ENTER -> fireCardAction(control.getFocusedColumn(), control.getFocusedCardIndex());
            case SPACE -> selectFocused();
            case ESCAPE -> {
                if (dragSupport.isDragging()) {
                    dragSupport.cancel();
                } else if (columnDragSupport.isDragging()) {
                    columnDragSupport.cancel();
                } else {
                    handled = false;
                }
            }
            default -> handled = false;
        }
        if (handled) {
            event.consume();
        }
    }

    private void selectAndFocus(RXKanbanColumn<T> column, int index) {
        getSkinnable().updateSelection(column, index);
        getSkinnable().updateFocus(column, index);
        refreshCellStates();
    }

    private void selectFocused() {
        RXKanbanView<T> control = getSkinnable();
        RXKanbanColumn<T> column = control.getFocusedColumn();
        if (column == null || !column.isVisible()) {
            return;
        }
        control.updateSelection(column, control.getFocusedCardIndex());
        refreshCellStates();
    }

    private void moveFocusInColumn(int delta) {
        RXKanbanView<T> control = getSkinnable();
        RXKanbanColumn<T> column = control.getFocusedColumn();
        if (column == null || !column.isVisible()) {
            // No focus, or the focused column was hidden after it was focused: recover onto
            // a visible column instead of moving focus within an invisible one.
            focusFirstAvailable();
            return;
        }
        int size = column.getCards().size();
        if (size == 0) {
            return;
        }
        int idx = control.getFocusedCardIndex();
        int newIdx = idx < 0 ? (delta > 0 ? 0 : size - 1) : RXMath.clamp(idx + delta, 0, size - 1);
        control.updateFocus(column, newIdx);
        refreshCellStates();
        scrollFocusedIntoView();
    }

    private void focusEdgeOfColumn(boolean first) {
        RXKanbanView<T> control = getSkinnable();
        RXKanbanColumn<T> column = control.getFocusedColumn();
        if (column == null || !column.isVisible()) {
            focusFirstAvailable();
            return;
        }
        int size = column.getCards().size();
        if (size == 0) {
            return;
        }
        control.updateFocus(column, first ? 0 : size - 1);
        refreshCellStates();
        scrollFocusedIntoView();
    }

    private void pageFocus(int direction) {
        RXKanbanView<T> control = getSkinnable();
        KanbanColumnBox<T> box = boxFor(control.getFocusedColumn());
        if (box == null) {
            focusFirstAvailable();
            return;
        }
        KanbanColumnViewport<T> viewport = box.getViewport();
        double stride = viewport.rowStride();
        int page = stride > 0.0 ? Math.max(1, (int) Math.floor(viewport.getHeight() / stride)) : 1;
        moveFocusInColumn(direction * page);
    }

    private void moveFocusToAdjacentColumn(int direction) {
        RXKanbanView<T> control = getSkinnable();
        ObservableList<RXKanbanColumn<T>> columns = control.getColumns();
        if (columns == null || columns.isEmpty()) {
            return;
        }
        RXKanbanColumn<T> column = control.getFocusedColumn();
        int columnIndex = column == null ? -1 : columns.indexOf(column);
        if (columnIndex < 0) {
            focusFirstAvailable();
            return;
        }
        int targetIndex = columnIndex + direction;
        while (targetIndex >= 0 && targetIndex < columns.size() && !columns.get(targetIndex).isVisible()) {
            // Skip hidden columns: they have no on-board presence, so focus jumps over them.
            targetIndex += direction;
        }
        if (targetIndex < 0 || targetIndex >= columns.size()) {
            return;
        }
        RXKanbanColumn<T> target = columns.get(targetIndex);
        int size = target.getCards().size();
        int newIdx = size == 0 ? -1 : RXMath.clamp(control.getFocusedCardIndex(), 0, size - 1);
        control.updateFocus(target, newIdx);
        refreshCellStates();
        scrollFocusedIntoView();
        scrollColumnIntoView(targetIndex);
    }

    private void focusFirstAvailable() {
        RXKanbanView<T> control = getSkinnable();
        ObservableList<RXKanbanColumn<T>> columns = control.getColumns();
        if (columns == null || columns.isEmpty()) {
            return;
        }
        RXKanbanColumn<T> firstVisible = null;
        for (RXKanbanColumn<T> column : columns) {
            if (!column.isVisible()) {
                continue;
            }
            if (firstVisible == null) {
                firstVisible = column;
            }
            if (!column.getCards().isEmpty()) {
                control.updateFocus(column, 0);
                refreshCellStates();
                scrollFocusedIntoView();
                return;
            }
        }
        if (firstVisible != null) {
            control.updateFocus(firstVisible, -1);
            refreshCellStates();
        }
    }

    private void fireCardAction(RXKanbanColumn<T> column, int index) {
        if (column == null || !column.isVisible() || index < 0 || index >= column.getCards().size()) {
            return;
        }
        T card = column.getCards().get(index);
        getSkinnable().fireEvent(new CardActionEvent<>(getSkinnable(), column, card, index));
    }

    private void reconcileSelectionFocus() {
        RXKanbanView<T> control = getSkinnable();
        ObservableList<RXKanbanColumn<T>> columns = control.getColumns();
        RXKanbanColumn<T> focused = control.getFocusedColumn();
        if (focused != null) {
            if (columns == null || !columns.contains(focused)) {
                control.updateFocus(null, -1);
            } else {
                int size = focused.getCards().size();
                int idx = control.getFocusedCardIndex();
                control.updateFocus(focused, idx >= size ? size - 1 : idx);
            }
        }
        RXKanbanColumn<T> selected = control.getSelectedColumn();
        if (selected != null) {
            if (columns == null || !columns.contains(selected)) {
                control.updateSelection(null, -1);
            } else {
                int size = selected.getCards().size();
                int idx = control.getSelectedCardIndex();
                control.updateSelection(selected, idx >= size ? size - 1 : idx);
            }
        }
        refreshCellStates();
    }

    void refreshCellStates() {
        for (KanbanColumnBox<T> box : boxes) {
            box.getViewport().refreshSelectionAndFocus();
        }
    }

    List<KanbanColumnBox<T>> columnBoxes() {
        return boxes;
    }

    // The column box whose header contains the target, when column reordering is
    // enabled — the arm point for a column drag. Returns null for a card / body press.
    KanbanColumnBox<T> headerBoxAt(EventTarget target) {
        if (!getSkinnable().isColumnReorderEnabled()) {
            return null;
        }
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            Node parent = node.getParent();
            if (parent instanceof KanbanColumnBox<?> box) {
                @SuppressWarnings("unchecked")
                KanbanColumnBox<T> typed = (KanbanColumnBox<T>) box;
                return typed.isHeaderNode(node) ? typed : null;
            }
            node = parent;
        }
        return null;
    }

    // Captures every box's current visual x so the next layout can FLIP them from there
    // into the reordered layout positions.
    void beginColumnFlip() {
        pendingColumnFlipFromX.clear();
        for (KanbanColumnBox<T> box : boxes) {
            pendingColumnFlipFromX.put(box, box.getLayoutX() + box.getTranslateX());
        }
    }

    Bounds getOverlayBoardBounds() {
        return columnsBox.localToScene(columnsBox.getBoundsInLocal());
    }

    private void onAnimationSettingsChanged() {
        for (KanbanColumnBox<T> box : boxes) {
            box.getViewport().onAnimationSettingsChanged();
        }
        if (!animationEnabled()) {
            columnAnimator.snapAll();
            pendingColumnFlipFromX.clear();
        }
    }

    void scrollBoardBy(double deltaX) {
        double target = RXMath.clamp(boardScrollX + deltaX, 0.0, cachedMaxBoardScrollX);
        if (target != boardScrollX) {
            boardScrollX = target;
            getSkinnable().requestLayout();
        }
    }

    private void scrollFocusedIntoView() {
        RXKanbanView<T> control = getSkinnable();
        KanbanColumnBox<T> box = boxFor(control.getFocusedColumn());
        int idx = control.getFocusedCardIndex();
        if (box != null && idx >= 0) {
            box.getViewport().scrollToCard(idx, ScrollAlignment.NEAREST);
        }
    }

    private void scrollColumnIntoView(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= cachedColumnX.length || cachedBoardWidth <= 0.0) {
            return;
        }
        double x = cachedColumnX[columnIndex];
        double width = cachedColumnW[columnIndex];
        if (x < boardScrollX) {
            boardScrollX = x;
        } else if (x + width > boardScrollX + cachedBoardWidth) {
            boardScrollX = x + width - cachedBoardWidth;
        }
        boardScrollX = RXMath.clamp(boardScrollX, 0.0, cachedMaxBoardScrollX);
        getSkinnable().requestLayout();
    }

    KanbanColumnBox<T> boxFor(RXKanbanColumn<T> column) {
        if (column == null) {
            return null;
        }
        for (KanbanColumnBox<T> box : boxes) {
            if (box.getColumn() == column) {
                return box;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    RXKanbanCardCell<T> cardCellAt(EventTarget target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            if (node instanceof RXKanbanCardCell<?> cell) {
                return (!cell.isEmpty() && cell.getIndex() >= 0) ? (RXKanbanCardCell<T>) cell : null;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        placeholderHost.resizeRelocate(contentX, contentY, contentWidth, contentHeight);
        dragSupport.layoutOverlay(contentX, contentY, contentWidth, contentHeight);
        if (isBoardEmpty()) {
            columnsBox.setVisible(false);
            hbar.setVisible(false);
            cachedMaxBoardScrollX = 0.0;
            return;
        }
        columnsBox.setVisible(true);

        double pref = snapSizeX(prefColumnWidthOrDefault(getSkinnable()));
        double baseGap = snapSizeX(columnSpacingOrDefault(getSkinnable()));
        int columnCount = boxes.size();

        if (cachedColumnX.length != columnCount) {
            cachedColumnX = new double[columnCount];
            cachedColumnW = new double[columnCount];
        }

        // A gap sits between a column and the next SHOWN column, so it counts only to
        // the degree that some later column is shown. shownAfter[i] = the largest shown
        // fraction among columns after i; without it, hiding the LAST column would leave
        // a phantom trailing gap (its left neighbor's gap never collapses).
        double[] shownAfter = new double[columnCount];
        double maxAfter = 0.0;
        for (int i = columnCount - 1; i >= 0; i--) {
            shownAfter[i] = maxAfter;
            maxAfter = Math.max(maxAfter, 1.0 - boxes.get(i).getHideProgress());
        }

        // Effective column / gap counts, weighted by each column's shown fraction: a
        // hiding column shrinks its width AND its trailing gap toward zero, so a fully
        // hidden column leaves no footprint and the board reflows to fill the gap.
        double sumWeight = 0.0;      // sum of (1 - hideProgress): the effective column count
        double sumGapWeight = 0.0;   // sum of weight_i * shownAfter[i]: the effective gap count
        int shownCount = 0;          // columns not fully hidden — justify denominators
        for (int i = 0; i < columnCount; i++) {
            double weight = 1.0 - boxes.get(i).getHideProgress();
            sumWeight += weight;
            sumGapWeight += weight * shownAfter[i];
            if (weight > 0.0) {
                shownCount++;
            }
        }

        // Responsive base width: grow to fill spare width (STRETCH, capped by
        // maxColumnWidth) or, when the board is too narrow, shrink toward
        // minColumnWidth; columnsJustify positions or spreads any remaining spare width.
        ItemsJustify justify = justifyOrDefault(getSkinnable());
        double minWidth = minColumnWidthOrPref(getSkinnable(), pref);
        double maxCap = maxColumnWidthOrUnbounded(getSkinnable(), pref);
        double baseWidth = pref;
        double startX = 0.0;
        double extraGap = 0.0;
        boolean overflow = false;
        if (sumWeight > 0.0) {
            double fill = (contentWidth - baseGap * sumGapWeight) / sumWeight;
            if (fill >= pref) {
                if (justify == ItemsJustify.STRETCH) {
                    baseWidth = (maxCap > 0.0 && fill > maxCap) ? maxCap : Math.max(0.0, fill);
                }
            } else {
                // Shrink to fit, no thinner than minColumnWidth; below that, scroll.
                baseWidth = Math.max(fill, minWidth);
                overflow = fill < minWidth - SCROLL_BAR_SYNC_EPSILON;
            }
            baseWidth = snapSizeX(baseWidth);
            double slack = Math.max(0.0, contentWidth - (baseWidth * sumWeight + baseGap * sumGapWeight));
            if (justify == ItemsJustify.STRETCH) {
                // An exact fill has no slack; a capped stretch centers the filled block.
                startX = slack / 2.0;
            } else {
                switch (justify) {
                    case CENTER -> startX = slack / 2.0;
                    case END -> startX = slack;
                    case SPACE_BETWEEN -> extraGap = shownCount > 1 ? slack / (shownCount - 1) : 0.0;
                    case SPACE_AROUND -> {
                        extraGap = shownCount > 0 ? slack / shownCount : 0.0;
                        startX = shownCount > 0 ? slack / (2.0 * shownCount) : 0.0;
                    }
                    case SPACE_EVENLY -> {
                        extraGap = slack / (shownCount + 1);
                        startX = slack / (shownCount + 1);
                    }
                    default -> {
                        // START: the columns hug the leading edge; trailing space stays empty.
                    }
                }
            }
        }

        double effectiveGap = baseGap + extraGap;
        double cursorX = startX;
        for (int i = 0; i < columnCount; i++) {
            double weight = 1.0 - boxes.get(i).getHideProgress();
            double width = snapSizeX(baseWidth * weight);
            cachedColumnX[i] = cursorX;
            cachedColumnW[i] = width;
            cursorX += width;
            // The trailing gap fades with this column AND collapses once nothing later
            // is shown (shownAfter[last] == 0), so the last visible column hugs the edge.
            cursorX += effectiveGap * weight * shownAfter[i];
        }
        layoutColumnStartX = startX;
        layoutColumnGap = effectiveGap;

        // Only genuine overflow (columns cannot fit even at minColumnWidth) scrolls; the
        // packed extent (base gaps, no justify spread) drives the scrollbar range.
        double packedWidth = baseWidth * sumWeight + baseGap * sumGapWeight;
        boolean needHbar = overflow;
        double hbarBreadth = needHbar ? snapSizeY(hbar.prefHeight(-1)) : 0.0;
        double columnsAreaHeight = Math.max(0.0, contentHeight - hbarBreadth);

        cachedMaxBoardScrollX = needHbar ? Math.max(0.0, packedWidth - contentWidth) : 0.0;
        boardScrollX = RXMath.clamp(boardScrollX, 0.0, cachedMaxBoardScrollX);
        cachedBoardWidth = contentWidth;

        columnsBox.resizeRelocate(contentX, contentY, contentWidth, columnsAreaHeight);
        columnsClip.setX(0.0);
        columnsClip.setY(0.0);
        columnsClip.setWidth(contentWidth);
        columnsClip.setHeight(columnsAreaHeight);

        if (reorderDraggedIndex >= 0 && reorderDraggedIndex < columnCount) {
            layoutColumnsWithReorderGap(columnCount, columnsAreaHeight);
        } else {
            layoutColumnsNormally(columnCount, columnsAreaHeight);
        }

        if (needHbar) {
            adjustingHbar = true;
            hbar.setMax(cachedMaxBoardScrollX);
            hbar.setVisibleAmount(contentWidth);
            hbar.setUnitIncrement(pref + baseGap);
            hbar.setBlockIncrement(contentWidth);
            if (Math.abs(hbar.getValue() - boardScrollX) > SCROLL_BAR_SYNC_EPSILON) {
                hbar.setValue(boardScrollX);
            }
            adjustingHbar = false;
            hbar.resizeRelocate(contentX, contentY + columnsAreaHeight, contentWidth, hbarBreadth);
            hbar.setVisible(true);
        } else {
            hbar.setVisible(false);
        }
    }

    // FLIP one column box: glide it from its pre-reorder visual x (captured at commit)
    // to its new layout x. Only invoked when animation is enabled (so the duration is
    // guaranteed non-null and positive).
    private void applyColumnFlip(KanbanColumnBox<T> box, double newLayoutX) {
        Double fromX = pendingColumnFlipFromX.get(box);
        if (fromX == null) {
            return;
        }
        Interpolator interpolator = getSkinnable().getAnimationInterpolator();
        columnAnimator.animate(box, fromX - newLayoutX, 0.0, getSkinnable().getAnimationDuration(),
                interpolator == null ? Interpolator.EASE_BOTH : interpolator, node -> { });
    }

    // Column glides are enabled only when the control opts in, is attached to a scene
    // and has a positive, concrete duration (a null / zero / indefinite duration means
    // "no animation" — mirrors KanbanColumnViewport's own settle gate).
    private boolean animationEnabled() {
        RXKanbanView<T> control = getSkinnable();
        Duration duration = control.getAnimationDuration();
        return control.isAnimated() && control.getScene() != null
                && duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    // Normal column layout: each box at its natural cumulative x, honoring any pending
    // post-commit FLIP.
    private void layoutColumnsNormally(int columnCount, double height) {
        boolean hasPending = !pendingColumnFlipFromX.isEmpty();
        boolean flip = hasPending && animationEnabled();
        for (int i = 0; i < columnCount; i++) {
            KanbanColumnBox<T> box = boxes.get(i);
            double x = snapPositionX(cachedColumnX[i] - boardScrollX);
            // A fully hidden column is invisible outright (no rendering, no hit target);
            // it becomes visible again the moment it starts to show.
            box.setVisible(box.getHideProgress() < 1.0);
            box.resizeRelocate(x, 0.0, cachedColumnW[i], height);
            if (flip) {
                applyColumnFlip(box, x);
            } else if (hasPending) {
                // Animation off / disabled: snap straight to the reordered position.
                box.setTranslateX(0.0);
            }
        }
        if (hasPending) {
            pendingColumnFlipFromX.clear();
        }
    }

    // Live reorder preview: the dragged column stays at its natural slot (its translate
    // follows the pointer) while every other visible column is repositioned to open a
    // column-wide gap at the hover index, so neighbours glide aside to make way.
    private void layoutColumnsWithReorderGap(int columnCount, double height) {
        int dragged = reorderDraggedIndex;
        double draggedWidth = cachedColumnW[dragged];
        double gap = layoutColumnGap;
        boolean flip = !pendingColumnFlipFromX.isEmpty() && animationEnabled();
        double cursor = layoutColumnStartX;
        int slot = 0;
        for (int i = 0; i < columnCount; i++) {
            KanbanColumnBox<T> box = boxes.get(i);
            if (i == dragged) {
                // Kept at its natural slot; the drag support's translate offsets it to the
                // pointer, so this layout x must stay fixed for that offset to hold.
                box.setVisible(true);
                box.resizeRelocate(snapPositionX(cachedColumnX[i] - boardScrollX), 0.0,
                        cachedColumnW[i], height);
                continue;
            }
            box.setVisible(box.getHideProgress() < 1.0);
            if (!box.isVisible()) {
                box.resizeRelocate(snapPositionX(cursor - boardScrollX), 0.0, cachedColumnW[i], height);
                continue;
            }
            if (slot == reorderPreviewIndex) {
                cursor += draggedWidth + gap;
            }
            double x = snapPositionX(cursor - boardScrollX);
            box.resizeRelocate(x, 0.0, cachedColumnW[i], height);
            if (flip) {
                applyColumnFlip(box, x);
            } else {
                box.setTranslateX(0.0);
            }
            cursor += cachedColumnW[i] + gap;
            slot++;
        }
        if (!pendingColumnFlipFromX.isEmpty()) {
            pendingColumnFlipFromX.clear();
        }
    }

    // Called each column-drag frame: opens (and glides neighbours to) a gap at the hover
    // insertion index. previewIndex is in the visible-non-dragged coordinate system, to
    // match the gap loop and computeTargetIndex.
    void setColumnReorderPreview(int draggedIndex, int previewIndex) {
        if (draggedIndex == reorderDraggedIndex && previewIndex == reorderPreviewIndex) {
            return;
        }
        // Capture the non-dragged columns' current x so they glide to the new gap layout.
        pendingColumnFlipFromX.clear();
        for (int i = 0; i < boxes.size(); i++) {
            if (i == draggedIndex) {
                continue;
            }
            KanbanColumnBox<T> box = boxes.get(i);
            pendingColumnFlipFromX.put(box, box.getLayoutX() + box.getTranslateX());
        }
        reorderDraggedIndex = draggedIndex;
        reorderPreviewIndex = previewIndex;
        getSkinnable().requestLayout();
    }

    void clearColumnReorderPreview() {
        reorderDraggedIndex = -1;
        reorderPreviewIndex = -1;
    }

    // ==================== Dispose ====================

    @Override
    protected void disposeSkin() {
        dragSupport.dispose();
        columnDragSupport.dispose();
        columnAnimator.snapAll();
        pendingColumnFlipFromX.clear();
        clearColumnReorderPreview();
        for (KanbanColumnBox<T> box : boxes) {
            box.dispose();
        }
        boxes.clear();
        columnsBox.getChildren().clear();
    }
}

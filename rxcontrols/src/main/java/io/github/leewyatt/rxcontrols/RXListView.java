package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXListViewActionEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXListViewSkin;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.StringConverter;

/**
 * A virtualized, single-column list of items with selection, keyboard navigation
 * and type-ahead, built on a self-contained viewport (not {@code VirtualFlow}).
 *
 * <p>{@code RXListView} lays a flat {@link #itemsProperty() items} list out as a
 * uniform single column and virtualizes by row, so only the visible rows hold
 * live cells. Each item is rendered by an {@link RXListCell} produced by the
 * {@link #cellFactoryProperty() cellFactory}; the built-in default cell renders
 * the item's primary text via the {@link #converterProperty() converter}. A
 * {@code null} item is a legal value — empty cells are decided by the index, not
 * by a {@code null} item.
 *
 * <p>Selection is held by a {@link #selectionModelProperty() selectionModel}
 * (single by default; switch with {@link #setSelectionMode(SelectionMode)}). The
 * view is a single Tab stop; once focused it navigates with the arrow keys,
 * {@code Home}/{@code End} and {@code Page Up}/{@code Down}, extends a range with
 * {@code Shift}, moves focus without selecting with {@code Shortcut}, toggles
 * with {@code Space}, selects all with {@code Shortcut+A}, jumps to an item by
 * first-letter type-ahead, and activates the focused item — firing
 * {@link #onActionProperty() onAction} — with {@code Enter} or a double-click.
 *
 * @param <T> the item type
 */
public class RXListView<T> extends Control {

    // ==================== Constants ====================

    /** Default fixed row height, in pixels. */
    public static final double DEFAULT_FIXED_CELL_SIZE = 28.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-list-view";

    // ==================== Constructors ====================

    /**
     * Creates an empty list view.
     */
    public RXListView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // The control is a single Tab stop; cells are not focus-traversable.
        setFocusTraversable(true);
    }

    /**
     * Creates a list view backed by the given items.
     *
     * @param items the items to display; may be {@code null}
     */
    public RXListView(ObservableList<T> items) {
        this();
        setItems(items);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXListViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Items ====================

    private final ObjectProperty<ObservableList<T>> items =
            new SimpleObjectProperty<>(this, "items", FXCollections.observableArrayList());

    /**
     * The items shown by the view. A {@code null} list is treated as empty.
     *
     * @return the items property
     */
    public final ObjectProperty<ObservableList<T>> itemsProperty() {
        return items;
    }

    /**
     * Returns the items shown by the view.
     *
     * @return the items list, or {@code null}
     */
    public final ObservableList<T> getItems() {
        return items.get();
    }

    /**
     * Sets the items shown by the view.
     *
     * @param value the items list, or {@code null} for an empty view
     */
    public final void setItems(ObservableList<T> value) {
        items.set(value);
    }

    // ==================== Cell Factory ====================

    private final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> cellFactory =
            new SimpleObjectProperty<>(this, "cellFactory");

    /**
     * Factory that creates the cells rendering each item. When {@code null}, the
     * view uses a default factory that renders the item's primary text via the
     * {@link #converterProperty() converter}.
     *
     * @return the cell-factory property
     */
    public final ObjectProperty<Callback<RXListView<T>, RXListCell<T>>> cellFactoryProperty() {
        return cellFactory;
    }

    /**
     * Returns the cell factory.
     *
     * @return the cell factory, or {@code null} for the default
     */
    public final Callback<RXListView<T>, RXListCell<T>> getCellFactory() {
        return cellFactory.get();
    }

    /**
     * Sets the cell factory.
     *
     * @param value the cell factory, or {@code null} for the default
     */
    public final void setCellFactory(Callback<RXListView<T>, RXListCell<T>> value) {
        cellFactory.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Converter supplying the primary text of the built-in default cell. When
     * {@code null}, the default cell falls back to {@code item.toString()} (and
     * the empty string for a {@code null} item). A custom
     * {@link #cellFactoryProperty() cellFactory} renders its own content and may
     * ignore this property.
     *
     * @return the converter property
     */
    public final ObjectProperty<StringConverter<T>> converterProperty() {
        return converter;
    }

    /**
     * Returns the converter.
     *
     * @return the converter, or {@code null}
     */
    public final StringConverter<T> getConverter() {
        return converter.get();
    }

    /**
     * Sets the converter.
     *
     * @param value the converter, or {@code null} for the {@code toString()} fallback
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Selection Model ====================

    private final ObjectProperty<MultipleSelectionModel<T>> selectionModel =
            new SimpleObjectProperty<>(this, "selectionModel", new RXIndexedSelectionModel<>(itemsProperty()));

    /**
     * The selection model. Defaults to a non-null {@link RXIndexedSelectionModel}
     * in single-selection mode; switch to multiple selection via
     * {@link #setSelectionMode(SelectionMode)} or
     * {@code getSelectionModel().setSelectionMode(...)}.
     *
     * @return the selection-model property
     */
    public final ObjectProperty<MultipleSelectionModel<T>> selectionModelProperty() {
        return selectionModel;
    }

    /**
     * Returns the selection model.
     *
     * @return the selection model, possibly {@code null} if explicitly cleared
     */
    public final MultipleSelectionModel<T> getSelectionModel() {
        return selectionModel.get();
    }

    /**
     * Sets the selection model.
     *
     * @param value the selection model, or {@code null} to disable selection
     */
    public final void setSelectionModel(MultipleSelectionModel<T> value) {
        selectionModel.set(value);
    }

    // ==================== Selection cardinality (delegating, no own property) ====================

    /**
     * Convenience pass-through to the current selection model's selection mode;
     * the cardinality axis lives solely on the selection model (no control-level
     * property), mirroring the native {@code ListView} idiom.
     *
     * @return the current selection mode, or {@code null} if there is no model
     */
    public final SelectionMode getSelectionMode() {
        MultipleSelectionModel<T> sm = getSelectionModel();
        return sm == null ? null : sm.getSelectionMode();
    }

    /**
     * Convenience pass-through to the current selection model's selection mode.
     *
     * @param value the selection mode to set
     */
    public final void setSelectionMode(SelectionMode value) {
        MultipleSelectionModel<T> sm = getSelectionModel();
        if (sm != null) {
            sm.setSelectionMode(value);
        }
    }

    // ==================== Selection Visual Mode ====================

    private final ObjectProperty<RXListSelectionVisualMode> selectionVisualMode =
            new SimpleObjectProperty<>(this, "selectionVisualMode", RXListSelectionVisualMode.AUTO);

    /**
     * The visual affordance used to express selection (the "how it looks" axis,
     * orthogonal to the cardinality on the selection model). {@code AUTO} (the
     * default) derives from the cardinality: single selection looks like
     * {@link RXListSelectionVisualMode#ROW ROW}, multiple like
     * {@link RXListSelectionVisualMode#CHECKBOX CHECKBOX}. A {@code null} value is
     * treated as {@code AUTO}. Not styleable: switching to {@code CHECKBOX} changes
     * which model the checked state is written to (a data-semantics change), which a
     * theme must not make.
     *
     * @return the selection-visual-mode property
     */
    public final ObjectProperty<RXListSelectionVisualMode> selectionVisualModeProperty() {
        return selectionVisualMode;
    }

    /**
     * Returns the selection visual mode.
     *
     * @return the selection visual mode, possibly {@code null}
     */
    public final RXListSelectionVisualMode getSelectionVisualMode() {
        return selectionVisualMode.get();
    }

    /**
     * Sets the selection visual mode.
     *
     * @param value the selection visual mode, or {@code null} for {@code AUTO}
     */
    public final void setSelectionVisualMode(RXListSelectionVisualMode value) {
        selectionVisualMode.set(value);
    }

    // ==================== Fixed Cell Size ====================

    private final DoubleProperty fixedCellSize =
            new SimpleDoubleProperty(this, "fixedCellSize", DEFAULT_FIXED_CELL_SIZE);

    /**
     * Fixed height of every row, in pixels (the cell fills it). A non-positive or
     * non-finite value is accepted and resolved to {@link #DEFAULT_FIXED_CELL_SIZE}
     * at layout time. Sized by the skin rather than CSS so the row geometry stays a
     * single source of truth.
     *
     * @return the fixed-cell-size property
     */
    public final DoubleProperty fixedCellSizeProperty() {
        return fixedCellSize;
    }

    /**
     * Returns the fixed row height.
     *
     * @return the fixed row height
     */
    public final double getFixedCellSize() {
        return fixedCellSize.get();
    }

    /**
     * Sets the fixed row height.
     *
     * @param value the fixed row height
     */
    public final void setFixedCellSize(double value) {
        fixedCellSize.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * Node shown when the view has no items. {@code null} shows nothing.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the placeholder node.
     *
     * @return the placeholder node, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the placeholder node.
     *
     * @param value the placeholder node, or {@code null} for none
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== On Action ====================

    private ObjectProperty<EventHandler<RXListViewActionEvent<T>>> onAction;

    /**
     * Handler invoked when an item is activated — by pressing {@code Enter} on the
     * focused row or double-clicking it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<RXListViewActionEvent<T>>> onActionProperty() {
        if (onAction == null) {
            onAction = new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(RXListViewActionEvent.actionType(), get());
                }

                @Override
                public Object getBean() {
                    return RXListView.this;
                }

                @Override
                public String getName() {
                    return "onAction";
                }
            };
        }
        return onAction;
    }

    /**
     * Returns the activation handler.
     *
     * @return the activation handler, or {@code null}
     */
    public final EventHandler<RXListViewActionEvent<T>> getOnAction() {
        return onAction == null ? null : onAction.get();
    }

    /**
     * Sets the activation handler.
     *
     * @param value the activation handler, or {@code null} for none
     */
    public final void setOnAction(EventHandler<RXListViewActionEvent<T>> value) {
        onActionProperty().set(value);
    }

    // ==================== Row Count (read-only) ====================

    private final ReadOnlyIntegerWrapper rowCount = new ReadOnlyIntegerWrapper(this, "rowCount", 0);

    /**
     * The number of data rows resolved by the most recent layout (the item count
     * for a flat single-column list).
     *
     * @return the read-only row-count property
     */
    public final ReadOnlyIntegerProperty rowCountProperty() {
        return rowCount.getReadOnlyProperty();
    }

    /**
     * Returns the number of data rows resolved by the most recent layout.
     *
     * @return the row count
     */
    public final int getRowCount() {
        return rowCount.get();
    }

    /**
     * Updates the resolved row count. Intended for skins / behaviors.
     *
     * @param value the resolved row count
     */
    public final void setRowCount(int value) {
        rowCount.set(value);
    }

    // ==================== Visible Range (read-only) ====================

    private final ReadOnlyObjectWrapper<RXListVisibleRange> visibleRange =
            new ReadOnlyObjectWrapper<>(this, "visibleRange", RXListVisibleRange.EMPTY);

    /**
     * The item / row range currently realized in the viewport, refreshed when the
     * realized range changes. Immutable snapshots keep listeners from seeing torn
     * reads.
     *
     * @return the read-only visible-range property
     */
    public final ReadOnlyObjectProperty<RXListVisibleRange> visibleRangeProperty() {
        return visibleRange.getReadOnlyProperty();
    }

    /**
     * Returns the current visible range.
     *
     * @return the visible range, never {@code null}
     */
    public final RXListVisibleRange getVisibleRange() {
        return visibleRange.get();
    }

    /**
     * Updates the visible range. Intended for skins / behaviors.
     *
     * @param value the visible range; {@code null} is coerced to
     *              {@link RXListVisibleRange#EMPTY}
     */
    public final void setVisibleRange(RXListVisibleRange value) {
        visibleRange.set(value == null ? RXListVisibleRange.EMPTY : value);
    }

    // ==================== Scrolling ====================

    private boolean pendingScroll;
    // >= 0 : scroll the row at this index per pendingScrollAlignment.
    // -1   : relative scroll by pendingScrollDelta pixels.
    private int pendingScrollIndex = -1;
    private double pendingScrollDelta;
    private ScrollAlignment pendingScrollAlignment = ScrollAlignment.NEAREST;

    /**
     * Scrolls the minimum distance needed to make the item at {@code index}
     * visible ({@link ScrollAlignment#NEAREST}); does nothing if it is already
     * visible.
     *
     * @param index the item index; out-of-range values are clamped during layout
     */
    public final void scrollTo(int index) {
        scrollTo(index, ScrollAlignment.NEAREST);
    }

    /**
     * Scrolls so the item at {@code index} is visible with the given alignment.
     * The request is applied on the next layout pass.
     *
     * @param index     the item index; out-of-range values are clamped during layout
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link ScrollAlignment#START}
     */
    public final void scrollTo(int index, ScrollAlignment alignment) {
        pendingScroll = true;
        pendingScrollIndex = index;
        pendingScrollDelta = 0.0;
        pendingScrollAlignment = alignment == null ? ScrollAlignment.START : alignment;
        requestLayout();
    }

    /**
     * Scrolls the minimum distance needed to make the given item visible
     * ({@link ScrollAlignment#NEAREST}). Does nothing if the item is not in the
     * list.
     *
     * @param item the item to scroll to
     */
    public final void scrollTo(T item) {
        scrollTo(item, ScrollAlignment.NEAREST);
    }

    /**
     * Scrolls so the given item is visible with the given alignment. Does nothing
     * if the item is not in the list (matched by {@code equals}; the first match
     * is used).
     *
     * @param item      the item to scroll to
     * @param alignment where the target row should land; {@code null} is treated
     *                  as {@link ScrollAlignment#START}
     */
    public final void scrollTo(T item, ScrollAlignment alignment) {
        ObservableList<T> list = getItems();
        if (list == null) {
            return;
        }
        int index = list.indexOf(item);
        if (index >= 0) {
            scrollTo(index, alignment);
        }
    }

    /**
     * Scrolls the viewport by a relative pixel delta (positive scrolls down,
     * negative up), clamped to the scrollable range on the next layout pass.
     * Multiple calls before a layout pass accumulate.
     *
     * @param deltaY the signed pixel delta
     */
    public final void scrollBy(double deltaY) {
        pendingScroll = true;
        pendingScrollIndex = -1;
        pendingScrollDelta += deltaY;
        requestLayout();
    }

    /**
     * Whether a scroll request is waiting to be applied. Intended for skins /
     * behaviors.
     *
     * @return {@code true} if a scroll request is pending
     */
    public final boolean hasPendingScroll() {
        return pendingScroll;
    }

    /**
     * The item index of the pending scroll request, or {@code -1} when the request
     * is a relative-delta scroll. Intended for skins / behaviors.
     *
     * @return the pending scroll item index, or {@code -1}
     */
    public final int getPendingScrollIndex() {
        return pendingScrollIndex;
    }

    /**
     * The accumulated relative-scroll delta of the pending request, in pixels.
     * Only meaningful when {@link #getPendingScrollIndex()} is {@code -1}.
     * Intended for skins / behaviors.
     *
     * @return the pending relative-scroll delta
     */
    public final double getPendingScrollDelta() {
        return pendingScrollDelta;
    }

    /**
     * The alignment of the pending scroll request. Intended for skins / behaviors.
     *
     * @return the pending scroll alignment, never {@code null}
     */
    public final ScrollAlignment getPendingScrollAlignment() {
        return pendingScrollAlignment;
    }

    /**
     * Clears the pending scroll request after it has been applied. Intended for
     * skins / behaviors.
     */
    public final void clearPendingScroll() {
        pendingScroll = false;
        pendingScrollIndex = -1;
        pendingScrollDelta = 0.0;
    }
}

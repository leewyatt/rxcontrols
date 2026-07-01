package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.CardActionEvent;
import io.github.leewyatt.rxcontrols.event.CardMovedEvent;
import io.github.leewyatt.rxcontrols.event.ColumnMovedEvent;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXKanbanViewSkin;
import javafx.animation.Interpolator;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.SizeConverter;
import javafx.event.EventHandler;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A kanban board control: a horizontal row of columns (e.g. TODO / DOING / DONE),
 * each holding a vertically scrolling, virtualized list of cards. Cards can be
 * reordered within a column and dragged across columns; columns can be reordered
 * by their headers. The control is a generic {@code T} container — cards are the
 * user's own type, rendered by the {@link #cardCellFactoryProperty() cardCellFactory}.
 *
 * <p>Data lives in {@link #getColumns() columns}, an observable list of
 * {@link RXKanbanColumn} models; each column embeds its own observable cards list.
 * Selection and focus are board-level and position-keyed
 * ({@code focusedColumn} + {@code focusedCardIndex}); the {@code selectedCard} /
 * {@code focusedCard} properties are read-only projections of those keys.
 *
 * <p>Drag-and-drop is pointer-based and configured via {@link #editableProperty()
 * editable}, {@link #cardDragEnabledProperty() cardDragEnabled} and
 * {@link #columnReorderEnabledProperty() columnReorderEnabled}. A drop fires a
 * vetoable {@link CardMovedEvent} before the built-in list mutation.
 *
 * @param <T> the card type
 */
public class RXKanbanView<T> extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-kanban-view";

    private static final double DEFAULT_COLUMN_SPACING = 12.0;
    private static final double DEFAULT_PREF_COLUMN_WIDTH = 280.0;
    private static final double DEFAULT_MIN_COLUMN_WIDTH = 0.0;
    private static final double DEFAULT_MAX_COLUMN_WIDTH = 0.0;
    private static final ItemsJustify DEFAULT_COLUMNS_JUSTIFY = ItemsJustify.START;
    private static final double DEFAULT_CARD_SPACING = 8.0;
    private static final double DEFAULT_PREF_CARD_HEIGHT = 96.0;
    private static final boolean DEFAULT_ANIMATED = true;
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);
    private static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    // ==================== Constructors ====================

    /**
     * Creates an empty kanban view.
     */
    public RXKanbanView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.PARENT);
        setAccessibleRoleDescription("kanban board");
        // The board is the single keyboard focus owner; cards are not Tab stops.
        setFocusTraversable(true);
    }

    /**
     * Creates a kanban view with the given columns.
     *
     * @param columns the initial columns
     */
    public RXKanbanView(ObservableList<RXKanbanColumn<T>> columns) {
        this();
        setColumns(columns);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXKanbanViewSkin<>(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Columns ====================

    private final ObjectProperty<ObservableList<RXKanbanColumn<T>>> columns =
            new SimpleObjectProperty<>(this, "columns", FXCollections.observableArrayList());

    /**
     * The board's columns, left to right. Never {@code null} by default; the whole
     * list may be replaced or its contents mutated.
     *
     * @return the columns property
     */
    public final ObjectProperty<ObservableList<RXKanbanColumn<T>>> columnsProperty() {
        return columns;
    }

    /**
     * Returns the columns list.
     *
     * @return the columns
     */
    public final ObservableList<RXKanbanColumn<T>> getColumns() {
        return columns.get();
    }

    /**
     * Sets the columns list.
     *
     * @param value the columns
     */
    public final void setColumns(ObservableList<RXKanbanColumn<T>> value) {
        columns.set(value);
    }

    // ==================== Card Cell Factory ====================

    private final ObjectProperty<Callback<RXKanbanView<T>, RXKanbanCardCell<T>>> cardCellFactory =
            new SimpleObjectProperty<>(this, "cardCellFactory");

    /**
     * Factory producing a card cell. The input is this view (not a single card); the
     * cell is recycled across cards via {@link RXKanbanCardCell#updateIndex(int)}.
     * When {@code null}, a default cell rendering the card's converter / {@code toString}
     * text is used.
     *
     * @return the card-cell-factory property
     */
    public final ObjectProperty<Callback<RXKanbanView<T>, RXKanbanCardCell<T>>> cardCellFactoryProperty() {
        return cardCellFactory;
    }

    /**
     * Returns the card cell factory.
     *
     * @return the card cell factory, or {@code null}
     */
    public final Callback<RXKanbanView<T>, RXKanbanCardCell<T>> getCardCellFactory() {
        return cardCellFactory.get();
    }

    /**
     * Sets the card cell factory.
     *
     * @param value the card cell factory, or {@code null} for the default cell
     */
    public final void setCardCellFactory(Callback<RXKanbanView<T>, RXKanbanCardCell<T>> value) {
        cardCellFactory.set(value);
    }

    // ==================== Converter ====================

    private final ObjectProperty<StringConverter<T>> converter =
            new SimpleObjectProperty<>(this, "converter");

    /**
     * Converter supplying the default cell's text. When {@code null}, the default
     * cell falls back to {@code card.toString()}.
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
     * @param value the converter, or {@code null}
     */
    public final void setConverter(StringConverter<T> value) {
        converter.set(value);
    }

    // ==================== Column Header Factory ====================

    private final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> columnHeaderFactory =
            new SimpleObjectProperty<>(this, "columnHeaderFactory");

    /**
     * Factory producing a column's header node (title, count pill, actions,
     * collapse arrow). The input is the column model. When {@code null}, a default
     * header binding the column's title and card count is used.
     *
     * @return the column-header-factory property
     */
    public final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> columnHeaderFactoryProperty() {
        return columnHeaderFactory;
    }

    /**
     * Returns the column header factory.
     *
     * @return the column header factory, or {@code null}
     */
    public final Callback<RXKanbanColumn<T>, Node> getColumnHeaderFactory() {
        return columnHeaderFactory.get();
    }

    /**
     * Sets the column header factory.
     *
     * @param value the column header factory, or {@code null} for the default header
     */
    public final void setColumnHeaderFactory(Callback<RXKanbanColumn<T>, Node> value) {
        columnHeaderFactory.set(value);
    }

    // ==================== Column Footer Factory ====================

    private final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> columnFooterFactory =
            new SimpleObjectProperty<>(this, "columnFooterFactory");

    /**
     * Factory producing a column's footer node (typically a "+ add card" slot). The
     * input is the column model. When {@code null}, no footer is rendered.
     *
     * @return the column-footer-factory property
     */
    public final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> columnFooterFactoryProperty() {
        return columnFooterFactory;
    }

    /**
     * Returns the column footer factory.
     *
     * @return the column footer factory, or {@code null}
     */
    public final Callback<RXKanbanColumn<T>, Node> getColumnFooterFactory() {
        return columnFooterFactory.get();
    }

    /**
     * Sets the column footer factory.
     *
     * @param value the column footer factory, or {@code null} for no footer
     */
    public final void setColumnFooterFactory(Callback<RXKanbanColumn<T>, Node> value) {
        columnFooterFactory.set(value);
    }

    // ==================== Placeholder ====================

    private final ObjectProperty<Node> placeholder = new SimpleObjectProperty<>(this, "placeholder");

    /**
     * Node shown when the board has no columns. When {@code null}, nothing is shown.
     *
     * @return the placeholder property
     */
    public final ObjectProperty<Node> placeholderProperty() {
        return placeholder;
    }

    /**
     * Returns the board placeholder.
     *
     * @return the placeholder, or {@code null}
     */
    public final Node getPlaceholder() {
        return placeholder.get();
    }

    /**
     * Sets the board placeholder.
     *
     * @param value the placeholder, or {@code null}
     */
    public final void setPlaceholder(Node value) {
        placeholder.set(value);
    }

    // ==================== Empty Column Placeholder Factory ====================

    private final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> emptyColumnPlaceholderFactory =
            new SimpleObjectProperty<>(this, "emptyColumnPlaceholderFactory");

    /**
     * Factory producing an empty column's placeholder ("drop here" hint). Must be a
     * factory rather than a single node because several columns may be empty at
     * once and a node has a single parent. When {@code null}, no per-column
     * placeholder is shown.
     *
     * @return the empty-column-placeholder-factory property
     */
    public final ObjectProperty<Callback<RXKanbanColumn<T>, Node>> emptyColumnPlaceholderFactoryProperty() {
        return emptyColumnPlaceholderFactory;
    }

    /**
     * Returns the empty-column placeholder factory.
     *
     * @return the empty-column placeholder factory, or {@code null}
     */
    public final Callback<RXKanbanColumn<T>, Node> getEmptyColumnPlaceholderFactory() {
        return emptyColumnPlaceholderFactory.get();
    }

    /**
     * Sets the empty-column placeholder factory.
     *
     * @param value the empty-column placeholder factory, or {@code null}
     */
    public final void setEmptyColumnPlaceholderFactory(Callback<RXKanbanColumn<T>, Node> value) {
        emptyColumnPlaceholderFactory.set(value);
    }

    // ==================== Selection (read-only, position-keyed) ====================

    private final ReadOnlyObjectWrapper<RXKanbanColumn<T>> selectedColumn =
            new ReadOnlyObjectWrapper<>(this, "selectedColumn");
    private final ReadOnlyIntegerWrapper selectedCardIndex =
            new ReadOnlyIntegerWrapper(this, "selectedCardIndex", -1);
    private final ReadOnlyObjectWrapper<T> selectedCard =
            new ReadOnlyObjectWrapper<>(this, "selectedCard");

    /**
     * The column of the currently selected card, or {@code null} when nothing is
     * selected. Authoritative half of the selection position key.
     *
     * @return the selected-column property
     */
    public final ReadOnlyObjectProperty<RXKanbanColumn<T>> selectedColumnProperty() {
        return selectedColumn.getReadOnlyProperty();
    }

    /**
     * Returns the selected card's column.
     *
     * @return the selected column, or {@code null}
     */
    public final RXKanbanColumn<T> getSelectedColumn() {
        return selectedColumn.get();
    }

    /**
     * The index within {@link #getSelectedColumn()} of the selected card, or
     * {@code -1} when nothing is selected.
     *
     * @return the selected-card-index property
     */
    public final ReadOnlyIntegerProperty selectedCardIndexProperty() {
        return selectedCardIndex.getReadOnlyProperty();
    }

    /**
     * Returns the selected card's index within its column.
     *
     * @return the selected card index, or {@code -1}
     */
    public final int getSelectedCardIndex() {
        return selectedCardIndex.get();
    }

    /**
     * The selected card — a read-only projection of the selection position key, or
     * {@code null} when nothing is selected or the key is out of range.
     *
     * @return the selected-card property
     */
    public final ReadOnlyObjectProperty<T> selectedCardProperty() {
        return selectedCard.getReadOnlyProperty();
    }

    /**
     * Returns the selected card.
     *
     * @return the selected card, or {@code null}
     */
    public final T getSelectedCard() {
        return selectedCard.get();
    }

    // ==================== Focus (read-only, position-keyed) ====================

    private final ReadOnlyObjectWrapper<RXKanbanColumn<T>> focusedColumn =
            new ReadOnlyObjectWrapper<>(this, "focusedColumn");
    private final ReadOnlyIntegerWrapper focusedCardIndex =
            new ReadOnlyIntegerWrapper(this, "focusedCardIndex", -1);
    private final ReadOnlyObjectWrapper<T> focusedCard =
            new ReadOnlyObjectWrapper<>(this, "focusedCard");

    /**
     * The column of the currently focused card, or {@code null} when nothing is
     * focused. Authoritative half of the focus position key.
     *
     * @return the focused-column property
     */
    public final ReadOnlyObjectProperty<RXKanbanColumn<T>> focusedColumnProperty() {
        return focusedColumn.getReadOnlyProperty();
    }

    /**
     * Returns the focused card's column.
     *
     * @return the focused column, or {@code null}
     */
    public final RXKanbanColumn<T> getFocusedColumn() {
        return focusedColumn.get();
    }

    /**
     * The index within {@link #getFocusedColumn()} of the focused card, or
     * {@code -1} when nothing is focused.
     *
     * @return the focused-card-index property
     */
    public final ReadOnlyIntegerProperty focusedCardIndexProperty() {
        return focusedCardIndex.getReadOnlyProperty();
    }

    /**
     * Returns the focused card's index within its column.
     *
     * @return the focused card index, or {@code -1}
     */
    public final int getFocusedCardIndex() {
        return focusedCardIndex.get();
    }

    /**
     * The focused card — a read-only projection of the focus position key, or
     * {@code null} when nothing is focused or the key is out of range.
     *
     * @return the focused-card property
     */
    public final ReadOnlyObjectProperty<T> focusedCardProperty() {
        return focusedCard.getReadOnlyProperty();
    }

    /**
     * Returns the focused card.
     *
     * @return the focused card, or {@code null}
     */
    public final T getFocusedCard() {
        return focusedCard.get();
    }

    /**
     * Updates the board selection position key and its card projection. Intended
     * for the skin / behavior, not application code.
     *
     * @param column    the selected column, or {@code null} to clear
     * @param cardIndex the selected card index, or {@code -1} to clear
     */
    public final void updateSelection(RXKanbanColumn<T> column, int cardIndex) {
        selectedColumn.set(column);
        selectedCardIndex.set(cardIndex);
        selectedCard.set(resolveCard(column, cardIndex));
    }

    /**
     * Updates the board focus position key and its card projection. Intended for
     * the skin / behavior, not application code.
     *
     * @param column    the focused column, or {@code null} to clear
     * @param cardIndex the focused card index, or {@code -1} to clear
     */
    public final void updateFocus(RXKanbanColumn<T> column, int cardIndex) {
        focusedColumn.set(column);
        focusedCardIndex.set(cardIndex);
        focusedCard.set(resolveCard(column, cardIndex));
    }

    private static <T> T resolveCard(RXKanbanColumn<T> column, int index) {
        if (column == null || index < 0) {
            return null;
        }
        ObservableList<T> cards = column.getCards();
        return index < cards.size() ? cards.get(index) : null;
    }

    // ==================== Editable ====================

    private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", true);

    /**
     * Master drag-and-drop switch. When {@code false}, all card and column dragging
     * is disabled and no move events are fired.
     *
     * @return the editable property
     */
    public final BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Returns whether drag-and-drop is enabled.
     *
     * @return whether the board is editable
     */
    public final boolean isEditable() {
        return editable.get();
    }

    /**
     * Sets whether drag-and-drop is enabled.
     *
     * @param value {@code true} to enable drag-and-drop
     */
    public final void setEditable(boolean value) {
        editable.set(value);
    }

    // ==================== Card Drag Enabled ====================

    private final BooleanProperty cardDragEnabled = new SimpleBooleanProperty(this, "cardDragEnabled", true);

    /**
     * Whether cards can be dragged (in-column reorder and cross-column move).
     * Gated by {@link #editableProperty() editable}.
     *
     * @return the card-drag-enabled property
     */
    public final BooleanProperty cardDragEnabledProperty() {
        return cardDragEnabled;
    }

    /**
     * Returns whether card dragging is enabled.
     *
     * @return whether cards can be dragged
     */
    public final boolean isCardDragEnabled() {
        return cardDragEnabled.get();
    }

    /**
     * Sets whether card dragging is enabled.
     *
     * @param value {@code true} to enable card dragging
     */
    public final void setCardDragEnabled(boolean value) {
        cardDragEnabled.set(value);
    }

    // ==================== Column Reorder Enabled ====================

    private final BooleanProperty columnReorderEnabled =
            new SimpleBooleanProperty(this, "columnReorderEnabled", false);

    /**
     * Whether columns can be reordered by dragging their headers. Gated by
     * {@link #editableProperty() editable}. Off by default.
     *
     * @return the column-reorder-enabled property
     */
    public final BooleanProperty columnReorderEnabledProperty() {
        return columnReorderEnabled;
    }

    /**
     * Returns whether column reordering is enabled.
     *
     * @return whether columns can be reordered
     */
    public final boolean isColumnReorderEnabled() {
        return columnReorderEnabled.get();
    }

    /**
     * Sets whether column reordering is enabled.
     *
     * @param value {@code true} to enable column reordering
     */
    public final void setColumnReorderEnabled(boolean value) {
        columnReorderEnabled.set(value);
    }

    // ==================== Drop Validator ====================

    private final ObjectProperty<Callback<RXKanbanCardDropContext<T>, Boolean>> dropValidator =
            new SimpleObjectProperty<>(this, "dropValidator");

    /**
     * Predicate consulted while a card is dragged over a candidate drop position.
     * Returning {@code Boolean.FALSE} rejects the drop (red / hidden indicator,
     * no-op bounce, no {@link CardMovedEvent}). When {@code null}, every drop is
     * allowed. WIP limits are soft and never rejected here.
     *
     * @return the drop-validator property
     */
    public final ObjectProperty<Callback<RXKanbanCardDropContext<T>, Boolean>> dropValidatorProperty() {
        return dropValidator;
    }

    /**
     * Returns the drop validator.
     *
     * @return the drop validator, or {@code null}
     */
    public final Callback<RXKanbanCardDropContext<T>, Boolean> getDropValidator() {
        return dropValidator.get();
    }

    /**
     * Sets the drop validator.
     *
     * @param value the drop validator, or {@code null} to allow every drop
     */
    public final void setDropValidator(Callback<RXKanbanCardDropContext<T>, Boolean> value) {
        dropValidator.set(value);
    }

    // ==================== On Card Action ====================

    private final ObjectProperty<EventHandler<CardActionEvent<T>>> onCardAction =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(CardActionEvent.cardActionType(), get());
                }

                @Override
                public Object getBean() {
                    return RXKanbanView.this;
                }

                @Override
                public String getName() {
                    return "onCardAction";
                }
            };

    /**
     * Handler for card activation (double-click / Enter).
     *
     * @return the on-card-action property
     */
    public final ObjectProperty<EventHandler<CardActionEvent<T>>> onCardActionProperty() {
        return onCardAction;
    }

    /**
     * Returns the card-action handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<CardActionEvent<T>> getOnCardAction() {
        return onCardAction.get();
    }

    /**
     * Sets the card-action handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnCardAction(EventHandler<CardActionEvent<T>> value) {
        onCardAction.set(value);
    }

    // ==================== On Card Moved ====================

    private final ObjectProperty<EventHandler<CardMovedEvent<T>>> onCardMoved =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(CardMovedEvent.cardMovedType(), get());
                }

                @Override
                public Object getBean() {
                    return RXKanbanView.this;
                }

                @Override
                public String getName() {
                    return "onCardMoved";
                }
            };

    /**
     * Handler for the vetoable card-move event fired before the built-in list
     * mutation. Consuming it skips the built-in {@code remove}/{@code add}.
     *
     * @return the on-card-moved property
     */
    public final ObjectProperty<EventHandler<CardMovedEvent<T>>> onCardMovedProperty() {
        return onCardMoved;
    }

    /**
     * Returns the card-moved handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<CardMovedEvent<T>> getOnCardMoved() {
        return onCardMoved.get();
    }

    /**
     * Sets the card-moved handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnCardMoved(EventHandler<CardMovedEvent<T>> value) {
        onCardMoved.set(value);
    }

    // ==================== On Column Moved ====================

    private final ObjectProperty<EventHandler<ColumnMovedEvent<T>>> onColumnMoved =
            new ObjectPropertyBase<>() {
                @Override
                protected void invalidated() {
                    setEventHandler(ColumnMovedEvent.columnMovedType(), get());
                }

                @Override
                public Object getBean() {
                    return RXKanbanView.this;
                }

                @Override
                public String getName() {
                    return "onColumnMoved";
                }
            };

    /**
     * Handler for the vetoable column-move event fired before the built-in reorder.
     *
     * @return the on-column-moved property
     */
    public final ObjectProperty<EventHandler<ColumnMovedEvent<T>>> onColumnMovedProperty() {
        return onColumnMoved;
    }

    /**
     * Returns the column-moved handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<ColumnMovedEvent<T>> getOnColumnMoved() {
        return onColumnMoved.get();
    }

    /**
     * Sets the column-moved handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnColumnMoved(EventHandler<ColumnMovedEvent<T>> value) {
        onColumnMoved.set(value);
    }

    // ==================== Column Spacing ====================

    private final DoubleProperty columnSpacing = new StyleableDoubleProperty(DEFAULT_COLUMN_SPACING) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.COLUMN_SPACING;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "columnSpacing";
        }
    };

    /**
     * Horizontal gap between columns, in pixels. A negative value is clamped to
     * {@code 0}, a non-finite value falls back to the default, at layout time.
     *
     * @return the column-spacing property
     */
    public final DoubleProperty columnSpacingProperty() {
        return columnSpacing;
    }

    /**
     * Returns the column spacing.
     *
     * @return the column spacing
     */
    public final double getColumnSpacing() {
        return columnSpacing.get();
    }

    /**
     * Sets the column spacing.
     *
     * @param value the column spacing
     */
    public final void setColumnSpacing(double value) {
        columnSpacing.set(value);
    }

    // ==================== Pref Column Width ====================

    private final DoubleProperty prefColumnWidth = new StyleableDoubleProperty(DEFAULT_PREF_COLUMN_WIDTH) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.PREF_COLUMN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "prefColumnWidth";
        }
    };

    /**
     * Target width of each column, in pixels (columns are never virtualized). A
     * non-positive or non-finite value is accepted but resolved to the default at
     * layout time.
     *
     * @return the pref-column-width property
     */
    public final DoubleProperty prefColumnWidthProperty() {
        return prefColumnWidth;
    }

    /**
     * Returns the preferred column width.
     *
     * @return the preferred column width
     */
    public final double getPrefColumnWidth() {
        return prefColumnWidth.get();
    }

    /**
     * Sets the preferred column width.
     *
     * @param value the preferred column width
     */
    public final void setPrefColumnWidth(double value) {
        prefColumnWidth.set(value);
    }

    // ==================== Min Column Width ====================

    private final DoubleProperty minColumnWidth = new StyleableDoubleProperty(DEFAULT_MIN_COLUMN_WIDTH) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.MIN_COLUMN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "minColumnWidth";
        }
    };

    /**
     * Lower bound a column shrinks to when the board is too narrow to hold every
     * column at {@link #prefColumnWidthProperty() prefColumnWidth}. Columns shrink
     * evenly down to this width to stay on screen; only when even this width does
     * not fit does a horizontal scrollbar appear. {@code 0} (the default) or any
     * value at or above {@code prefColumnWidth} disables shrinking (columns keep
     * their preferred width and the board scrolls instead), resolved at layout time.
     *
     * @return the min-column-width property
     */
    public final DoubleProperty minColumnWidthProperty() {
        return minColumnWidth;
    }

    /**
     * Returns the minimum column width.
     *
     * @return the minimum column width
     */
    public final double getMinColumnWidth() {
        return minColumnWidth.get();
    }

    /**
     * Sets the minimum column width.
     *
     * @param value the minimum column width
     */
    public final void setMinColumnWidth(double value) {
        minColumnWidth.set(value);
    }

    // ==================== Max Column Width ====================

    private final DoubleProperty maxColumnWidth = new StyleableDoubleProperty(DEFAULT_MAX_COLUMN_WIDTH) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.MAX_COLUMN_WIDTH;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "maxColumnWidth";
        }
    };

    /**
     * Upper bound a column grows to when {@link #columnsJustifyProperty()
     * columnsJustify} is {@link ItemsJustify#STRETCH} and the board is wider than
     * the columns need. {@code 0} (the default) or any non-positive value means no
     * cap (columns grow to fill all spare width); once the cap is reached the filled
     * block is centered. A cap below {@code prefColumnWidth} is degenerate and is
     * treated as {@code prefColumnWidth}. Resolved at layout time; it has no effect
     * unless {@code columnsJustify} is {@code STRETCH}.
     *
     * @return the max-column-width property
     */
    public final DoubleProperty maxColumnWidthProperty() {
        return maxColumnWidth;
    }

    /**
     * Returns the maximum column width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @return the maximum column width
     */
    public final double getMaxColumnWidth() {
        return maxColumnWidth.get();
    }

    /**
     * Sets the maximum column width used in {@link ItemsJustify#STRETCH} mode.
     *
     * @param value the maximum column width
     */
    public final void setMaxColumnWidth(double value) {
        maxColumnWidth.set(value);
    }

    // ==================== Columns Justify ====================

    private final ObjectProperty<ItemsJustify> columnsJustify =
            new StyleableObjectProperty<>(DEFAULT_COLUMNS_JUSTIFY) {
                @Override
                public CssMetaData<RXKanbanView<?>, ItemsJustify> getCssMetaData() {
                    return StyleableProperties.COLUMNS_JUSTIFY;
                }

                @Override
                public Object getBean() {
                    return RXKanbanView.this;
                }

                @Override
                public String getName() {
                    return "columnsJustify";
                }
            };

    /**
     * How the board uses spare horizontal width when it is wider than the columns
     * need: position the block ({@code START} / {@code CENTER} / {@code END}), grow
     * the gaps between columns ({@code SPACE_BETWEEN} / {@code SPACE_AROUND} /
     * {@code SPACE_EVENLY}) or grow the columns themselves ({@link ItemsJustify#STRETCH},
     * capped by {@link #maxColumnWidthProperty() maxColumnWidth}). A {@code null}
     * value is treated as {@link ItemsJustify#START}. This governs only the spare
     * width; when the board is too narrow, columns shrink toward
     * {@link #minColumnWidthProperty() minColumnWidth} regardless of this value.
     *
     * @return the columns-justify property
     */
    public final ObjectProperty<ItemsJustify> columnsJustifyProperty() {
        return columnsJustify;
    }

    /**
     * Returns the column justification.
     *
     * @return the column justification, possibly {@code null}
     */
    public final ItemsJustify getColumnsJustify() {
        return columnsJustify.get();
    }

    /**
     * Sets the column justification.
     *
     * @param value the column justification, or {@code null} for the default
     */
    public final void setColumnsJustify(ItemsJustify value) {
        columnsJustify.set(value);
    }

    // ==================== Card Spacing ====================

    private final DoubleProperty cardSpacing = new StyleableDoubleProperty(DEFAULT_CARD_SPACING) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.CARD_SPACING;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "cardSpacing";
        }
    };

    /**
     * Vertical gap between cards within a column, in pixels (the row gap of the
     * fixed-height virtualization). A negative value is clamped to {@code 0}, a
     * non-finite value falls back to the default, at layout time.
     *
     * @return the card-spacing property
     */
    public final DoubleProperty cardSpacingProperty() {
        return cardSpacing;
    }

    /**
     * Returns the card spacing.
     *
     * @return the card spacing
     */
    public final double getCardSpacing() {
        return cardSpacing.get();
    }

    /**
     * Sets the card spacing.
     *
     * @param value the card spacing
     */
    public final void setCardSpacing(double value) {
        cardSpacing.set(value);
    }

    // ==================== Pref Card Height ====================

    private final DoubleProperty prefCardHeight = new StyleableDoubleProperty(DEFAULT_PREF_CARD_HEIGHT) {
        @Override
        public CssMetaData<RXKanbanView<?>, Number> getCssMetaData() {
            return StyleableProperties.PREF_CARD_HEIGHT;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "prefCardHeight";
        }
    };

    /**
     * Fixed height of each card, in pixels (the row height of the fixed-height
     * virtualization). Must be a concrete positive value; a non-positive or
     * non-finite value is accepted but resolved to the default at layout time (it
     * is the divisor of the virtualization, so it can never be {@code 0} / NaN).
     *
     * @return the pref-card-height property
     */
    public final DoubleProperty prefCardHeightProperty() {
        return prefCardHeight;
    }

    /**
     * Returns the preferred card height.
     *
     * @return the preferred card height
     */
    public final double getPrefCardHeight() {
        return prefCardHeight.get();
    }

    /**
     * Sets the preferred card height.
     *
     * @param value the preferred card height
     */
    public final void setPrefCardHeight(double value) {
        prefCardHeight.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated = new StyleableBooleanProperty(DEFAULT_ANIMATED) {
        @Override
        public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
            return StyleableProperties.ANIMATED;
        }

        @Override
        public Object getBean() {
            return RXKanbanView.this;
        }

        @Override
        public String getName() {
            return "animated";
        }
    };

    /**
     * Whether neighbour cards glide to their new positions when a card is dropped
     * and columns reflow. On by default; turning it off snaps every settle.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether settle animation is enabled.
     *
     * @return whether settle animation is enabled
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether settle animation is enabled.
     *
     * @param value whether settle animation is enabled
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXKanbanView.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a single settle glide. A {@code null}, non-positive, unknown or
     * indefinite value is accepted and disables animation, exactly like
     * {@code animated=false}.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the settle-animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the settle-animation duration.
     *
     * @param value the duration; {@code null} or any non-positive value disables animation
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the settle glide. {@code null} falls back to
     * {@link Interpolator#EASE_BOTH}. Not styleable (no stable CSS converter).
     *
     * @return the animation-interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the animation interpolator.
     *
     * @return the animation interpolator
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the animation interpolator.
     *
     * @param value the interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXKanbanView<?>, Number> COLUMN_SPACING =
                new CssMetaData<>("-rx-column-spacing", SizeConverter.getInstance(), DEFAULT_COLUMN_SPACING) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.columnSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.columnSpacingProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Number> PREF_COLUMN_WIDTH =
                new CssMetaData<>("-rx-pref-column-width", SizeConverter.getInstance(), DEFAULT_PREF_COLUMN_WIDTH) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.prefColumnWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.prefColumnWidthProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Number> MIN_COLUMN_WIDTH =
                new CssMetaData<>("-rx-min-column-width", SizeConverter.getInstance(), DEFAULT_MIN_COLUMN_WIDTH) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.minColumnWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.minColumnWidthProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Number> MAX_COLUMN_WIDTH =
                new CssMetaData<>("-rx-max-column-width", SizeConverter.getInstance(), DEFAULT_MAX_COLUMN_WIDTH) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.maxColumnWidth.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.maxColumnWidthProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, ItemsJustify> COLUMNS_JUSTIFY =
                new CssMetaData<>("-rx-columns-justify",
                        new EnumConverter<>(ItemsJustify.class), DEFAULT_COLUMNS_JUSTIFY) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.columnsJustify.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ItemsJustify> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<ItemsJustify>) n.columnsJustifyProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Number> CARD_SPACING =
                new CssMetaData<>("-rx-card-spacing", SizeConverter.getInstance(), DEFAULT_CARD_SPACING) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.cardSpacing.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.cardSpacingProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Number> PREF_CARD_HEIGHT =
                new CssMetaData<>("-rx-pref-card-height", SizeConverter.getInstance(), DEFAULT_PREF_CARD_HEIGHT) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.prefCardHeight.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Number>) n.prefCardHeightProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Boolean>) n.animatedProperty();
                    }
                };

        private static final CssMetaData<RXKanbanView<?>, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration", DurationConverter.getInstance(),
                        DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXKanbanView<?> n) {
                        return !n.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXKanbanView<?> n) {
                        return (StyleableProperty<Duration>) n.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, COLUMN_SPACING, PREF_COLUMN_WIDTH, MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH,
                    COLUMNS_JUSTIFY, CARD_SPACING, PREF_CARD_HEIGHT, ANIMATED, ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}

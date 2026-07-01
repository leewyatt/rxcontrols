package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a card is dropped onto a new position in an
 * {@link RXKanbanView}. This is the single move event: a same-column reorder is
 * the degenerate case where {@code fromColumn == toColumn} (see {@link #isReorder()}).
 *
 * <p>The event is fired <em>before</em> the built-in list mutation and is
 * vetoable: calling {@link #consume()} in a handler tells the control to skip its
 * built-in {@code remove}/{@code add} so a controlled / immutable data source can
 * apply the change itself. When not consumed, the control performs
 * {@code fromColumn.getCards().remove(fromIndex)} followed by
 * {@code toColumn.getCards().add(toIndex, card)} — always by index, never
 * {@code remove(Object)}. {@code toIndex} is in the target column's coordinate
 * system <em>after</em> the source removal, so no {@code ±1} adjustment is needed.
 *
 * @param <T> the card type of the owning kanban view
 */
public class CardMovedEvent<T> extends Event {

    /**
     * The single event type for a card move. Use {@link #cardMovedType()} to obtain
     * it with the card type bound.
     */
    public static final EventType<CardMovedEvent<?>> CARD_MOVED =
            new EventType<>(Event.ANY, "RX_KANBAN_CARD_MOVED");

    private final transient RXKanbanView<T> kanbanView;
    private final transient RXKanbanColumn<T> fromColumn;
    private final int fromIndex;
    private final transient RXKanbanColumn<T> toColumn;
    private final int toIndex;
    private final transient T card;

    /**
     * Creates a card move event whose source and target are the kanban view.
     *
     * @param source     the kanban view firing the event
     * @param fromColumn the column the card is moving from
     * @param fromIndex  the card's index in the source column
     * @param toColumn   the column the card is moving to
     * @param toIndex    the card's target index (source-removed coordinate system)
     * @param card       the card being moved, possibly {@code null}
     */
    public CardMovedEvent(RXKanbanView<T> source,
                          RXKanbanColumn<T> fromColumn, int fromIndex,
                          RXKanbanColumn<T> toColumn, int toIndex,
                          T card) {
        super(source, source, cardMovedType());
        this.kanbanView = source;
        this.fromColumn = fromColumn;
        this.fromIndex = fromIndex;
        this.toColumn = toColumn;
        this.toIndex = toIndex;
        this.card = card;
    }

    /**
     * Returns the card move event type with the given card type bound.
     *
     * @param <T> the card type
     * @return the {@link #CARD_MOVED} type viewed as {@code EventType<CardMovedEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<CardMovedEvent<T>> cardMovedType() {
        return (EventType<CardMovedEvent<T>>) (EventType<?>) CARD_MOVED;
    }

    /**
     * Returns the kanban view that fired this event.
     *
     * @return the source kanban view
     */
    public RXKanbanView<T> getKanbanView() {
        return kanbanView;
    }

    /**
     * Returns the column the card is moving from.
     *
     * @return the source column
     */
    public RXKanbanColumn<T> getFromColumn() {
        return fromColumn;
    }

    /**
     * Returns the card's index in the source column.
     *
     * @return the source index
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * Returns the column the card is moving to.
     *
     * @return the target column
     */
    public RXKanbanColumn<T> getToColumn() {
        return toColumn;
    }

    /**
     * Returns the card's target index in the target column, in the coordinate
     * system after the source removal.
     *
     * @return the target index
     */
    public int getToIndex() {
        return toIndex;
    }

    /**
     * Returns the card being moved.
     *
     * @return the moved card, possibly {@code null}
     */
    public T getCard() {
        return card;
    }

    /**
     * Whether this move is a same-column reorder ({@code fromColumn == toColumn}).
     *
     * @return {@code true} if the card stayed in its column
     */
    public boolean isReorder() {
        return fromColumn == toColumn;
    }
}

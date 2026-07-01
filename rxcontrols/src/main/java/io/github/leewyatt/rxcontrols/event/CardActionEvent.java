package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a card is activated in an {@link RXKanbanView} — by pressing
 * {@code Enter} on the focused card or double-clicking it. Install a handler via
 * {@link RXKanbanView#setOnCardAction(javafx.event.EventHandler)} or
 * {@code addEventHandler(CardActionEvent.cardActionType(), ...)}.
 *
 * @param <T> the card type of the owning kanban view
 */
public class CardActionEvent<T> extends Event {

    /**
     * The single event type for card activation. Use {@link #cardActionType()} to
     * obtain it with the card type bound.
     */
    public static final EventType<CardActionEvent<?>> CARD_ACTION =
            new EventType<>(Event.ANY, "RX_KANBAN_CARD_ACTION");

    private final transient RXKanbanView<T> kanbanView;
    private final transient RXKanbanColumn<T> column;
    private final transient T card;
    private final int index;

    /**
     * Creates a card activation event whose source and target are the kanban view.
     *
     * @param source the kanban view firing the event
     * @param column the column the activated card belongs to
     * @param card   the activated card, possibly {@code null}
     * @param index  the activated card's index within its column
     */
    public CardActionEvent(RXKanbanView<T> source, RXKanbanColumn<T> column, T card, int index) {
        super(source, source, cardActionType());
        this.kanbanView = source;
        this.column = column;
        this.card = card;
        this.index = index;
    }

    /**
     * Returns the card activation event type with the given card type bound.
     *
     * @param <T> the card type
     * @return the {@link #CARD_ACTION} type viewed as {@code EventType<CardActionEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<CardActionEvent<T>> cardActionType() {
        return (EventType<CardActionEvent<T>>) (EventType<?>) CARD_ACTION;
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
     * Returns the column the activated card belongs to.
     *
     * @return the owning column
     */
    public RXKanbanColumn<T> getColumn() {
        return column;
    }

    /**
     * Returns the activated card.
     *
     * @return the activated card, possibly {@code null}
     */
    public T getCard() {
        return card;
    }

    /**
     * Returns the activated card's index within its column.
     *
     * @return the card index
     */
    public int getIndex() {
        return index;
    }
}

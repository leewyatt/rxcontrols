package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXKanbanColumn;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a column is reordered in an {@link RXKanbanView} by dragging
 * its header. Fired <em>before</em> the built-in reorder and vetoable via
 * {@link #consume()}; when not consumed the control moves the column within
 * {@code getColumns()} by index.
 *
 * @param <T> the card type of the owning kanban view
 */
public class ColumnMovedEvent<T> extends Event {

    /**
     * The single event type for a column move. Use {@link #columnMovedType()} to
     * obtain it with the card type bound.
     */
    public static final EventType<ColumnMovedEvent<?>> COLUMN_MOVED =
            new EventType<>(Event.ANY, "RX_KANBAN_COLUMN_MOVED");

    private final transient RXKanbanView<T> kanbanView;
    private final int fromIndex;
    private final int toIndex;
    private final transient RXKanbanColumn<T> column;

    /**
     * Creates a column move event whose source and target are the kanban view.
     *
     * @param source    the kanban view firing the event
     * @param fromIndex the column's current index
     * @param toIndex   the column's target index
     * @param column    the column being moved
     */
    public ColumnMovedEvent(RXKanbanView<T> source, int fromIndex, int toIndex, RXKanbanColumn<T> column) {
        super(source, source, columnMovedType());
        this.kanbanView = source;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
        this.column = column;
    }

    /**
     * Returns the column move event type with the given card type bound.
     *
     * @param <T> the card type
     * @return the {@link #COLUMN_MOVED} type viewed as {@code EventType<ColumnMovedEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<ColumnMovedEvent<T>> columnMovedType() {
        return (EventType<ColumnMovedEvent<T>>) (EventType<?>) COLUMN_MOVED;
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
     * Returns the column's current index.
     *
     * @return the source index
     */
    public int getFromIndex() {
        return fromIndex;
    }

    /**
     * Returns the column's target index.
     *
     * @return the target index
     */
    public int getToIndex() {
        return toIndex;
    }

    /**
     * Returns the column being moved.
     *
     * @return the moved column
     */
    public RXKanbanColumn<T> getColumn() {
        return column;
    }
}

package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXListView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when an item is activated in an {@link RXListView} — by pressing
 * {@code Enter} on the focused row or double-clicking it. Install a handler via
 * {@link RXListView#setOnAction(javafx.event.EventHandler)} or
 * {@code addEventHandler(RXListViewActionEvent.actionType(), ...)}.
 *
 * @param <T> the item type of the owning list view
 */
public class RXListViewActionEvent<T> extends Event {

    /**
     * The single event type for item activation. Use {@link #actionType()} to
     * obtain it with the item type bound.
     */
    public static final EventType<RXListViewActionEvent<?>> ACTION =
            new EventType<>(Event.ANY, "RX_LIST_VIEW_ACTION");

    private final transient RXListView<T> listView;
    private final transient T item;
    private final int index;

    /**
     * Creates a list activation event whose source and target are the list view.
     *
     * @param source the list view firing the event
     * @param item   the activated item, possibly {@code null}
     * @param index  the activated item's index in the items list
     */
    public RXListViewActionEvent(RXListView<T> source, T item, int index) {
        super(source, source, actionType());
        this.listView = source;
        this.item = item;
        this.index = index;
    }

    /**
     * Returns the activation event type with the given item type bound.
     *
     * @param <T> the item type
     * @return the {@link #ACTION} type viewed as {@code EventType<RXListViewActionEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<RXListViewActionEvent<T>> actionType() {
        return (EventType<RXListViewActionEvent<T>>) (EventType<?>) ACTION;
    }

    /**
     * Returns the list view that fired this event.
     *
     * @return the source list view
     */
    public RXListView<T> getListView() {
        return listView;
    }

    /**
     * Returns the activated item.
     *
     * @return the activated item, possibly {@code null}
     */
    public T getItem() {
        return item;
    }

    /**
     * Returns the activated item's index in the items list.
     *
     * @return the item index
     */
    public int getIndex() {
        return index;
    }
}

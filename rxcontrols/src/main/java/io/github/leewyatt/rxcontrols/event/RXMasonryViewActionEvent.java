package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXMasonryView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a cell is activated in an {@link RXMasonryView} — by pressing
 * {@code Enter} on the focused cell or double-clicking it. Install a handler via
 * {@link RXMasonryView#setOnAction(javafx.event.EventHandler)} or
 * {@code addEventHandler(RXMasonryViewActionEvent.actionType(), ...)}.
 *
 * @param <T> the item type of the owning masonry view
 */
public class RXMasonryViewActionEvent<T> extends Event {

    /**
     * The single event type for cell activation. Use {@link #actionType()} to obtain
     * it with the item type bound.
     */
    public static final EventType<RXMasonryViewActionEvent<?>> ACTION =
            new EventType<>(Event.ANY, "RX_MASONRY_VIEW_ACTION");

    private final transient RXMasonryView<T> masonryView;
    private final transient T item;
    private final int index;

    /**
     * Creates a cell activation event whose source and target are the masonry view.
     *
     * @param source the masonry view firing the event
     * @param item   the activated item, possibly {@code null}
     * @param index  the activated item's index in the items list
     */
    public RXMasonryViewActionEvent(RXMasonryView<T> source, T item, int index) {
        super(source, source, actionType());
        this.masonryView = source;
        this.item = item;
        this.index = index;
    }

    /**
     * Returns the activation event type with the given item type bound.
     *
     * @param <T> the item type
     * @return the {@link #ACTION} type viewed as {@code EventType<RXMasonryViewActionEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<RXMasonryViewActionEvent<T>> actionType() {
        return (EventType<RXMasonryViewActionEvent<T>>) (EventType<?>) ACTION;
    }

    /**
     * Returns the masonry view that fired this event.
     *
     * @return the source masonry view
     */
    public RXMasonryView<T> getMasonryView() {
        return masonryView;
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

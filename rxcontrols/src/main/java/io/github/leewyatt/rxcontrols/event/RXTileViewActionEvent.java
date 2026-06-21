package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXTileView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a tile is activated in an {@link RXTileView} — by pressing
 * {@code Enter} on the focused tile or double-clicking it. Install a handler via
 * {@link RXTileView#setOnAction(javafx.event.EventHandler)} or
 * {@code addEventHandler(RXTileViewActionEvent.actionType(), ...)}.
 *
 * @param <T> the item type of the owning tile view
 */
public class RXTileViewActionEvent<T> extends Event {

    /**
     * The single event type for tile activation. Use {@link #actionType()} to
     * obtain it with the item type bound.
     */
    public static final EventType<RXTileViewActionEvent<?>> ACTION =
            new EventType<>(Event.ANY, "RX_TILE_VIEW_ACTION");

    private final transient RXTileView<T> tileView;
    private final transient T item;
    private final int index;

    /**
     * Creates a tile activation event whose source and target are the tile view.
     *
     * @param source the tile view firing the event
     * @param item   the activated item, possibly {@code null}
     * @param index  the activated item's index in the items list
     */
    public RXTileViewActionEvent(RXTileView<T> source, T item, int index) {
        super(source, source, actionType());
        this.tileView = source;
        this.item = item;
        this.index = index;
    }

    /**
     * Returns the activation event type with the given item type bound.
     *
     * @param <T> the item type
     * @return the {@link #ACTION} type viewed as {@code EventType<RXTileViewActionEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<RXTileViewActionEvent<T>> actionType() {
        return (EventType<RXTileViewActionEvent<T>>) (EventType<?>) ACTION;
    }

    /**
     * Returns the tile view that fired this event.
     *
     * @return the source tile view
     */
    public RXTileView<T> getTileView() {
        return tileView;
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

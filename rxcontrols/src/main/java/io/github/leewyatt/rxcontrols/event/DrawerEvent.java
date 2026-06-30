package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXDrawerPane;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Events fired across the open/close lifecycle of an {@link RXDrawerPane}.
 *
 * <p>The lifecycle is:</p>
 * <pre>
 * OPENING → OPENED        (open)
 * CLOSE_REQUEST → CLOSING → CLOSED   (close; CLOSE_REQUEST may veto)
 * </pre>
 *
 * <p>{@link #CLOSE_REQUEST} is fired before any close proceeds and can be
 * {@link Event#consume() consumed} to abort it — the drawer-equivalent of
 * {@code Stage.setOnCloseRequest}. The other four are pure notifications:
 * {@link #OPENING} / {@link #CLOSING} when the slide starts, {@link #OPENED} /
 * {@link #CLOSED} when it finishes.</p>
 */
public class DrawerEvent extends Event {

    /**
     * Base type for all drawer events.
     */
    public static final EventType<DrawerEvent> ANY = new EventType<>(Event.ANY, "DRAWER");

    /**
     * Fired when an open slide starts.
     */
    public static final EventType<DrawerEvent> OPENING = new EventType<>(ANY, "OPENING");

    /**
     * Fired when an open slide has fully completed.
     */
    public static final EventType<DrawerEvent> OPENED = new EventType<>(ANY, "OPENED");

    /**
     * Fired before any close proceeds; {@link Event#consume() consuming} it aborts
     * the close.
     */
    public static final EventType<DrawerEvent> CLOSE_REQUEST = new EventType<>(ANY, "CLOSE_REQUEST");

    /**
     * Fired when a close slide starts (the close was not vetoed).
     */
    public static final EventType<DrawerEvent> CLOSING = new EventType<>(ANY, "CLOSING");

    /**
     * Fired when a close slide has fully completed.
     */
    public static final EventType<DrawerEvent> CLOSED = new EventType<>(ANY, "CLOSED");

    private final transient RXDrawerPane drawer;

    /**
     * Creates a drawer event whose source and target are the drawer itself.
     *
     * @param eventType the specific event type
     * @param drawer    the drawer firing the event
     */
    public DrawerEvent(EventType<DrawerEvent> eventType, RXDrawerPane drawer) {
        super(drawer, drawer, eventType);
        this.drawer = drawer;
    }

    /**
     * Returns the drawer that fired this event.
     *
     * @return the source drawer
     */
    public RXDrawerPane getDrawer() {
        return drawer;
    }
}

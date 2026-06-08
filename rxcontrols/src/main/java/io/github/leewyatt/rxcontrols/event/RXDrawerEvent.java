package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.enums.CloseReason;

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
 * {@link #CLOSED} when it finishes. The {@link #getReason() reason} is meaningful
 * only on the close events ({@code CLOSE_REQUEST} / {@code CLOSING} /
 * {@code CLOSED}); it is {@code null} on {@code OPENING} / {@code OPENED}.</p>
 */
public class RXDrawerEvent extends Event {

    /**
     * Base type for all drawer events.
     */
    public static final EventType<RXDrawerEvent> ANY = new EventType<>(Event.ANY, "RX_DRAWER");

    /**
     * Fired when an open slide starts.
     */
    public static final EventType<RXDrawerEvent> OPENING = new EventType<>(ANY, "OPENING");

    /**
     * Fired when an open slide has fully completed.
     */
    public static final EventType<RXDrawerEvent> OPENED = new EventType<>(ANY, "OPENED");

    /**
     * Fired before any close proceeds; {@link Event#consume() consuming} it aborts
     * the close.
     */
    public static final EventType<RXDrawerEvent> CLOSE_REQUEST = new EventType<>(ANY, "CLOSE_REQUEST");

    /**
     * Fired when a close slide starts (the close was not vetoed).
     */
    public static final EventType<RXDrawerEvent> CLOSING = new EventType<>(ANY, "CLOSING");

    /**
     * Fired when a close slide has fully completed.
     */
    public static final EventType<RXDrawerEvent> CLOSED = new EventType<>(ANY, "CLOSED");

    private final transient RXDrawerPane drawer;
    private final CloseReason reason;

    /**
     * Creates a drawer event whose source and target are the drawer itself.
     *
     * @param eventType the specific event type
     * @param drawer    the drawer firing the event
     * @param reason    the close reason, meaningful only for the close events; {@code null} otherwise
     */
    public RXDrawerEvent(EventType<RXDrawerEvent> eventType, RXDrawerPane drawer, CloseReason reason) {
        super(drawer, drawer, eventType);
        this.drawer = drawer;
        this.reason = reason;
    }

    /**
     * Returns the drawer that fired this event.
     *
     * @return the source drawer
     */
    public RXDrawerPane getDrawer() {
        return drawer;
    }

    /**
     * Returns why the drawer was asked to close, or {@code null} for the open
     * events.
     *
     * @return the close reason, or {@code null}
     */
    public CloseReason getReason() {
        return reason;
    }
}

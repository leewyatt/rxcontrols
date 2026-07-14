package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXSpeedDial;
import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;

/**
 * Event fired by {@link RXSpeedDial} across open and close lifecycle
 * transitions.
 */
public class RXSpeedDialEvent extends Event {

    /**
     * Base type for all speed-dial events.
     */
    public static final EventType<RXSpeedDialEvent> ANY =
            new EventType<>(Event.ANY, "RX_SPEED_DIAL");

    /**
     * Fired before the dial expands.
     */
    public static final EventType<RXSpeedDialEvent> SHOWING =
            new EventType<>(ANY, "RX_SPEED_DIAL_SHOWING");

    /**
     * Fired after the dial has expanded.
     */
    public static final EventType<RXSpeedDialEvent> SHOWN =
            new EventType<>(ANY, "RX_SPEED_DIAL_SHOWN");

    /**
     * Fired before any close proceeds; consuming it aborts the close.
     */
    public static final EventType<RXSpeedDialEvent> CLOSE_REQUEST =
            new EventType<>(ANY, "RX_SPEED_DIAL_CLOSE_REQUEST");

    /**
     * Fired before the dial collapses after close was not vetoed.
     */
    public static final EventType<RXSpeedDialEvent> HIDING =
            new EventType<>(ANY, "RX_SPEED_DIAL_HIDING");

    /**
     * Fired after the dial has collapsed.
     */
    public static final EventType<RXSpeedDialEvent> HIDDEN =
            new EventType<>(ANY, "RX_SPEED_DIAL_HIDDEN");

    private final RXSpeedDial.CloseReason closeReason;

    /**
     * Creates a speed-dial event.
     *
     * @param source      the event source
     * @param target      the event target
     * @param eventType   the specific event type
     * @param closeReason the close reason for close-path events, or {@code null}
     */
    public RXSpeedDialEvent(Object source, EventTarget target,
                            EventType<RXSpeedDialEvent> eventType,
                            RXSpeedDial.CloseReason closeReason) {
        super(source, target, eventType);
        this.closeReason = closeReason;
    }

    /**
     * Returns the close reason for close-path events.
     *
     * @return the close reason, or {@code null} for open-path events
     */
    public RXSpeedDial.CloseReason getCloseReason() {
        return closeReason;
    }
}

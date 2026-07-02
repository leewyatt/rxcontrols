package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.DismissReason;
import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSnackbarRequest;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Events fired by an {@link RXSnackbarHost} across a snackbar's display
 * lifecycle.
 *
 * <p>Every displayed request fires {@link #SHOWING} when it becomes current and
 * {@link #SHOWN} when its enter transition completes; every removed request fires
 * {@link #DISMISSED} exactly once, carrying the {@link #getReason() reason}. None
 * of these events is vetoable — a snackbar is a non-modal, transient notice.</p>
 */
public class RXSnackbarEvent extends Event {

    /**
     * Base type for all snackbar events.
     */
    public static final EventType<RXSnackbarEvent> ANY = new EventType<>(Event.ANY, "RX_SNACKBAR");

    /**
     * Fired when a request becomes the displayed snackbar (its enter transition
     * starts).
     */
    public static final EventType<RXSnackbarEvent> SHOWING = new EventType<>(ANY, "RX_SNACKBAR_SHOWING");

    /**
     * Fired when the enter transition has fully completed.
     */
    public static final EventType<RXSnackbarEvent> SHOWN = new EventType<>(ANY, "RX_SNACKBAR_SHOWN");

    /**
     * Fired exactly once when a request is removed — displayed or not — just after
     * the request's own {@code onDismissed} callback.
     */
    public static final EventType<RXSnackbarEvent> DISMISSED = new EventType<>(ANY, "RX_SNACKBAR_DISMISSED");

    private final transient RXSnackbarRequest request;
    private final transient DismissReason reason;

    /**
     * Creates a snackbar lifecycle event. Source and target are the host itself.
     *
     * @param eventType the specific event type
     * @param host      the host firing the event
     * @param request   the request this event is about
     * @param reason    why the request was dismissed, or {@code null} for
     *                  {@link #SHOWING} / {@link #SHOWN}
     */
    public RXSnackbarEvent(EventType<RXSnackbarEvent> eventType, RXSnackbarHost host,
                           RXSnackbarRequest request, DismissReason reason) {
        super(host, host, eventType);
        this.request = request;
        this.reason = reason;
    }

    /**
     * Returns the request this event is about.
     *
     * @return the request
     */
    public RXSnackbarRequest getRequest() {
        return request;
    }

    /**
     * Returns why the request was dismissed. Only meaningful on
     * {@link #DISMISSED}; {@code null} on {@link #SHOWING} / {@link #SHOWN}.
     *
     * @return the dismiss reason, or {@code null}
     */
    public DismissReason getReason() {
        return reason;
    }
}

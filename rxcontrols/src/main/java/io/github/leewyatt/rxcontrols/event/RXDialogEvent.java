package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.CloseReason;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.control.ButtonType;

/**
 *  Named RXDialogEvent to avoid conflicts with JavaFX DialogEvent.
 * Events fired across the show / hide lifecycle of an {@link RXDialog}.
 *
 * <p>The lifecycle is:</p>
 * <pre>
 * SHOWING → SHOWN                      (show)
 * CLOSE_REQUEST → HIDING → HIDDEN      (close; CLOSE_REQUEST may veto)
 * </pre>
 *
 * <p>The names mirror {@code javafx.stage.WindowEvent} /
 * {@code javafx.scene.control.DialogEvent} verbatim. {@link #CLOSE_REQUEST} is
 * fired before any close proceeds and can be {@link Event#consume() consumed} to
 * abort it. The {@code CLOSE_REQUEST} / {@link #HIDING} / {@link #HIDDEN} events
 * additionally carry the candidate {@link #getButtonType() button type} (possibly
 * {@code null}) and the {@link #getCloseReason() close reason}, so a handler can
 * tell why the dialog is closing and with which result candidate. {@link #SHOWING}
 * / {@link #SHOWN} carry neither (both {@code null}).</p>
 */
public class RXDialogEvent extends Event {

    /**
     * Base type for all dialog events.
     */
    public static final EventType<RXDialogEvent> ANY = new EventType<>(Event.ANY, "RX_DIALOG");

    /**
     * Fired when a show transition starts.
     */
    public static final EventType<RXDialogEvent> SHOWING = new EventType<>(ANY, "RX_DIALOG_SHOWING");

    /**
     * Fired when a show transition has fully completed.
     */
    public static final EventType<RXDialogEvent> SHOWN = new EventType<>(ANY, "RX_DIALOG_SHOWN");

    /**
     * Fired before any close proceeds; {@link Event#consume() consuming} it aborts
     * the close (the dialog stays open and no result is delivered).
     */
    public static final EventType<RXDialogEvent> CLOSE_REQUEST = new EventType<>(ANY, "RX_DIALOG_CLOSE_REQUEST");

    /**
     * Fired when a close transition starts (the close was not vetoed).
     */
    public static final EventType<RXDialogEvent> HIDING = new EventType<>(ANY, "RX_DIALOG_HIDING");

    /**
     * Fired when a close transition has fully completed, just before the result is
     * delivered to {@code onResult}.
     */
    public static final EventType<RXDialogEvent> HIDDEN = new EventType<>(ANY, "RX_DIALOG_HIDDEN");

    private final transient RXDialog<?> dialog;
    private final transient ButtonType buttonType;
    private final transient CloseReason closeReason;

    /**
     * Creates a lifecycle event with no close payload (for {@link #SHOWING} /
     * {@link #SHOWN}). Source and target are the dialog itself.
     *
     * @param eventType the specific event type
     * @param dialog    the dialog firing the event
     */
    public RXDialogEvent(EventType<RXDialogEvent> eventType, RXDialog<?> dialog) {
        this(eventType, dialog, null, null);
    }

    /**
     * Creates a lifecycle event carrying the close payload (for
     * {@link #CLOSE_REQUEST} / {@link #HIDING} / {@link #HIDDEN}). Source and
     * target are the dialog itself.
     *
     * @param eventType   the specific event type
     * @param dialog      the dialog firing the event
     * @param buttonType  the candidate button type, or {@code null}
     * @param closeReason the reason the close was requested, or {@code null}
     */
    public RXDialogEvent(EventType<RXDialogEvent> eventType, RXDialog<?> dialog,
                         ButtonType buttonType, CloseReason closeReason) {
        super(dialog, dialog, eventType);
        this.dialog = dialog;
        this.buttonType = buttonType;
        this.closeReason = closeReason;
    }

    /**
     * Returns the dialog that fired this event.
     *
     * @return the source dialog
     */
    public RXDialog<?> getDialog() {
        return dialog;
    }

    /**
     * Returns the candidate button type that drove this close, if any. The action
     * button's own type for {@link CloseReason#ACTION_BUTTON}; the cancel-type
     * button (or {@code null}) for ESC / scrim / close-button dismissals; whatever
     * was passed for a programmatic close. Always {@code null} on {@link #SHOWING}
     * / {@link #SHOWN}.
     *
     * @return the candidate button type, or {@code null}
     */
    public ButtonType getButtonType() {
        return buttonType;
    }

    /**
     * Returns why the close was requested, or {@code null} on {@link #SHOWING} /
     * {@link #SHOWN}.
     *
     * @return the close reason, or {@code null}
     */
    public CloseReason getCloseReason() {
        return closeReason;
    }
}

package io.github.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event for the autocomplete lifecycle: a suggestion was committed and the
 * completion handler has written it back.
 *
 * <p>{@link #COMPLETED} is a pure post-notification for observing completions;
 * customizing what a commit writes into the field is the job of the control's
 * {@code completionHandler} strategy, not of this event.</p>
 */
public class RXAutoCompleteEvent extends Event {

    /**
     * Base type for all autocomplete events.
     */
    public static final EventType<RXAutoCompleteEvent> ANY =
            new EventType<>(Event.ANY, "RX_AUTO_COMPLETE");

    /**
     * Fired after a suggestion was committed and the completion handler ran.
     */
    public static final EventType<RXAutoCompleteEvent> COMPLETED =
            new EventType<>(ANY, "RX_AUTO_COMPLETE_COMPLETED");

    private final transient String completion;

    /**
     * Creates an autocomplete event carrying the committed suggestion. The event's
     * source and target are assigned when it is fired.
     *
     * @param eventType  the specific event type
     * @param completion the committed suggestion, or {@code null}
     */
    public RXAutoCompleteEvent(EventType<RXAutoCompleteEvent> eventType, String completion) {
        super(eventType);
        this.completion = completion;
    }

    /**
     * Returns the committed suggestion this event concerns.
     *
     * @return the committed suggestion, or {@code null}
     */
    public String getCompletion() {
        return completion;
    }
}

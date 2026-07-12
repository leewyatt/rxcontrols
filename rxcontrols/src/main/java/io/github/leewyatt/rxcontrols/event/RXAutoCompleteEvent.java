package io.github.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event for the autocomplete lifecycle: a suggestion was committed and the
 * completion pipeline has run.
 *
 * <p>{@link #COMPLETED} is a pure post-notification for observing completions;
 * customizing what a commit writes into the field (possibly nothing at all) is
 * the job of the {@code completionHandler} strategy, not of this event.</p>
 *
 * <p>The event is not generic: {@link #getItem() item} is typed {@link Object}
 * so the runtime {@link EventType} constants stay simple (a generic
 * {@code EventType} plays badly with the JavaFX event system).</p>
 */
public class RXAutoCompleteEvent extends Event {

    /**
     * Base type for all autocomplete events.
     */
    public static final EventType<RXAutoCompleteEvent> ANY =
            new EventType<>(Event.ANY, "RX_AUTO_COMPLETE");

    /**
     * Fired after a suggestion was committed and the completion pipeline ran.
     */
    public static final EventType<RXAutoCompleteEvent> COMPLETED =
            new EventType<>(ANY, "RX_AUTO_COMPLETE_COMPLETED");

    private final transient Object item;
    private final transient String completion;

    /**
     * Creates an autocomplete event carrying the committed suggestion. The event's
     * source and target are assigned when it is fired.
     *
     * @param eventType  the specific event type
     * @param item       the committed suggestion item, or {@code null}
     * @param completion the item's default write-back text, or {@code null}
     */
    public RXAutoCompleteEvent(EventType<RXAutoCompleteEvent> eventType, Object item, String completion) {
        super(eventType);
        this.item = item;
        this.completion = completion;
    }

    /**
     * Returns the original suggestion item this commit concerns.
     *
     * @return the committed item, or {@code null}
     */
    public Object getItem() {
        return item;
    }

    /**
     * Returns the default write-back text of the committed suggestion. This is not
     * necessarily what ended up in the field — a custom completion handler may
     * write something else (or nothing); read the target field's text for the
     * final value.
     *
     * @return the default write-back text, or {@code null}
     */
    public String getCompletion() {
        return completion;
    }
}

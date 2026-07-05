package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXChip;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event for the chip lifecycle: a chip is about to be removed, was added to a chip
 * input, was removed from a chip input or chip set, or a chip-set selection changed.
 *
 * <p>Removal is vetoable — a handler may {@link Event#consume() consume} the
 * {@link #REMOVE} event to cancel the removal (the chip-input equivalent of
 * {@code Stage.setOnCloseRequest}). {@link #ADDED} / {@link #REMOVED} /
 * {@link #SELECTION_CHANGED} are pure post-notifications.</p>
 *
 * <p>The event is not generic: {@link #getItem() item} is typed {@link Object}
 * so the runtime {@link EventType} constants stay simple (a generic
 * {@code EventType} plays badly with the JavaFX event system).</p>
 */
public class RXChipEvent extends Event {

    /**
     * Base type for all chip events.
     */
    public static final EventType<RXChipEvent> ANY = new EventType<>(Event.ANY, "RX_CHIP");

    /**
     * Fired before a chip is removed; {@link Event#consume() consuming} it vetoes
     * the removal.
     */
    public static final EventType<RXChipEvent> REMOVE = new EventType<>(ANY, "RX_CHIP_REMOVE");

    /**
     * Fired after a chip (item) was added to a chip input.
     */
    public static final EventType<RXChipEvent> ADDED = new EventType<>(ANY, "RX_CHIP_ADDED");

    /**
     * Fired after a chip was removed from a chip input (its item) or a chip set.
     */
    public static final EventType<RXChipEvent> REMOVED = new EventType<>(ANY, "RX_CHIP_REMOVED");

    /**
     * Fired when a chip-set selection changed.
     */
    public static final EventType<RXChipEvent> SELECTION_CHANGED =
            new EventType<>(ANY, "RX_CHIP_SELECTION_CHANGED");

    private final transient Object item;
    private final transient RXChip chip;

    /**
     * Creates a chip event carrying the affected chip node and item. The event's
     * source and target are assigned when it is fired.
     *
     * @param eventType the specific event type
     * @param chip      the chip node involved, or {@code null}
     * @param item      the item (the {@code T} of a chip input) involved, or
     *                  {@code null}
     */
    public RXChipEvent(EventType<RXChipEvent> eventType, RXChip chip, Object item) {
        super(eventType);
        this.chip = chip;
        this.item = item;
    }

    /**
     * Returns the item (the {@code T} of a chip input) this event concerns.
     *
     * @return the item, or {@code null} for a standalone chip with no backing item
     */
    public Object getItem() {
        return item;
    }

    /**
     * Returns the chip node this event concerns.
     *
     * @return the chip node, or {@code null}
     */
    public RXChip getChip() {
        return chip;
    }
}

package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a timeline item is clicked in an {@link RXTimelineView}.
 */
public class RXTimelineItemEvent extends Event {

    /**
     * Base type for all timeline item events.
     */
    public static final EventType<RXTimelineItemEvent> ANY =
            new EventType<>(Event.ANY, "RX_TIMELINE_ITEM");

    /**
     * Fired when a timeline item is clicked.
     */
    public static final EventType<RXTimelineItemEvent> ITEM_CLICKED =
            new EventType<>(ANY, "ITEM_CLICKED");

    private final transient RXTimelineView timelineView;
    private final transient RXTimelineItem item;
    private final int index;

    /**
     * Creates a timeline item event whose source and target are the timeline view.
     *
     * @param source    the timeline view firing the event
     * @param eventType the specific event type
     * @param item      the clicked item
     * @param index     the clicked item's model index (stable regardless of
     *                  {@code reverse})
     */
    public RXTimelineItemEvent(RXTimelineView source, EventType<RXTimelineItemEvent> eventType,
                               RXTimelineItem item, int index) {
        super(source, source, eventType);
        this.timelineView = source;
        this.item = item;
        this.index = index;
    }

    /**
     * Returns the timeline view that fired this event.
     *
     * @return the source timeline view
     */
    public RXTimelineView getTimelineView() {
        return timelineView;
    }

    /**
     * Returns the clicked item.
     *
     * @return the clicked item
     */
    public RXTimelineItem getItem() {
        return item;
    }

    /**
     * Returns the clicked item's model index.
     *
     * @return the model index, unaffected by {@code reverse}
     */
    public int getIndex() {
        return index;
    }
}

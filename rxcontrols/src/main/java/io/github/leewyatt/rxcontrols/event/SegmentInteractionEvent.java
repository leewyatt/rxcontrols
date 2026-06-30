package io.github.leewyatt.rxcontrols.event;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a segmented control reports interaction with one segment.
 */
public class SegmentInteractionEvent extends Event {

    /**
     * Base event type for all segment interaction events.
     */
    public static final EventType<SegmentInteractionEvent> ANY = new EventType<>(Event.ANY, "SEGMENT_INTERACTION");

    /**
     * Fired when a segment is clicked.
     */
    public static final EventType<SegmentInteractionEvent> CLICKED = new EventType<>(ANY, "SEGMENT_CLICKED");

    /**
     * Fired when the pointer enters a segment, including transitions from one
     * segment to another while moving across the control.
     */
    public static final EventType<SegmentInteractionEvent> ENTERED = new EventType<>(ANY, "SEGMENT_ENTERED");

    private final int segmentIndex;

    /**
     * Creates a segment interaction event.
     *
     * @param eventType    the event type
     * @param segmentIndex the interacted segment index
     */
    public SegmentInteractionEvent(EventType<SegmentInteractionEvent> eventType, int segmentIndex) {
        super(eventType);
        this.segmentIndex = segmentIndex;
    }

    /**
     * Returns the interacted segment index.
     *
     * @return the segment index
     */
    public int getSegmentIndex() {
        return segmentIndex;
    }
}

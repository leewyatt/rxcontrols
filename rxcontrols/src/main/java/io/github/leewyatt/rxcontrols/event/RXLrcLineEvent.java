package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.lrc.RXLrcLine;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.util.Duration;

/**
 * Event fired when a lyric line is clicked in an {@link RXLrcView}.
 */
public class RXLrcLineEvent extends Event {

    /**
     * Base type for all LRC line events.
     */
    public static final EventType<RXLrcLineEvent> ANY =
            new EventType<>(Event.ANY, "RX_LRC_LINE");

    /**
     * Fired when a lyric line is clicked.
     */
    public static final EventType<RXLrcLineEvent> LINE_CLICKED =
            new EventType<>(ANY, "LINE_CLICKED");

    private final transient RXLrcView lrcView;
    private final transient RXLrcLine line;
    private final int index;
    private final transient Duration time;

    /**
     * Creates a lyric line event whose source and target are the LRC view.
     *
     * @param source    the LRC view firing the event
     * @param eventType the specific event type
     * @param line      the clicked lyric line
     * @param index     the clicked line index
     * @param time      the clicked line start time
     */
    public RXLrcLineEvent(RXLrcView source, EventType<RXLrcLineEvent> eventType,
                          RXLrcLine line, int index, Duration time) {
        super(source, source, eventType);
        this.lrcView = source;
        this.line = line;
        this.index = index;
        this.time = time;
    }

    /**
     * Returns the LRC view that fired this event.
     *
     * @return the source LRC view
     */
    public RXLrcView getLrcView() {
        return lrcView;
    }

    /**
     * Returns the clicked lyric line.
     *
     * @return the clicked lyric line
     */
    public RXLrcLine getLine() {
        return line;
    }

    /**
     * Returns the clicked line index.
     *
     * @return the clicked line index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns the clicked line start time.
     *
     * @return the clicked line start time
     */
    public Duration getTime() {
        return time;
    }
}

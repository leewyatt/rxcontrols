package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;

/**
 * Content lifecycle events of an {@link RXTransitionPane}: a node becoming,
 * and ceasing to be, the pane's current content.
 *
 * <p>Every content node goes through {@code OPENING → OPENED} when it becomes
 * the current content and {@code CLOSING → CLOSED} when it is replaced. The
 * events are fired at the content node, bubble to the owning pane and stop
 * there: handlers on the content and on the pane receive them, handlers on
 * the pane's ancestors (including an outer {@code RXTransitionPane}) do not.
 * Event filters on ancestors still see them during the capturing phase.</p>
 *
 * <p>The events are notifications only; consuming one does not cancel the
 * content change. See {@link RXTransitionPane} for the exact order and the
 * pane state at each event.</p>
 */
public class RXTransitionPaneEvent extends Event {

    /**
     * Base type for all transition pane content events.
     */
    public static final EventType<RXTransitionPaneEvent> ANY =
            new EventType<>(Event.ANY, "RX_TRANSITION_PANE_CONTENT");

    /**
     * Fired when a node is about to become the current content. The node is
     * already in the pane but not yet visible.
     */
    public static final EventType<RXTransitionPaneEvent> OPENING =
            new EventType<>(ANY, "RX_TRANSITION_PANE_CONTENT_OPENING");

    /**
     * Fired when a node has become the current content and is fully shown.
     */
    public static final EventType<RXTransitionPaneEvent> OPENED =
            new EventType<>(ANY, "RX_TRANSITION_PANE_CONTENT_OPENED");

    /**
     * Fired when the current content is about to be replaced. The node is
     * still in the pane and visible.
     */
    public static final EventType<RXTransitionPaneEvent> CLOSING =
            new EventType<>(ANY, "RX_TRANSITION_PANE_CONTENT_CLOSING");

    /**
     * Fired when a replaced content is fully hidden. The node is still in the
     * pane and is removed right after this event.
     */
    public static final EventType<RXTransitionPaneEvent> CLOSED =
            new EventType<>(ANY, "RX_TRANSITION_PANE_CONTENT_CLOSED");

    private final transient RXTransitionPane pane;
    private final transient Node content;

    /**
     * Creates a content event whose target is the content node.
     *
     * @param eventType the specific event type
     * @param pane      the pane whose content changes
     * @param content   the affected content node
     */
    public RXTransitionPaneEvent(EventType<RXTransitionPaneEvent> eventType,
                                 RXTransitionPane pane, Node content) {
        super(pane, content, eventType);
        this.pane = pane;
        this.content = content;
    }

    /**
     * Returns the pane whose content changes. Unlike the event source, this
     * stays the same while the event travels from the content to the pane.
     *
     * @return the owning pane
     */
    public RXTransitionPane getPane() {
        return pane;
    }

    /**
     * Returns the content node this event is about.
     *
     * @return the affected content
     */
    public Node getContent() {
        return content;
    }
}

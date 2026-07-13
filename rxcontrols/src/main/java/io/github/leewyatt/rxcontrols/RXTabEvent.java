package io.github.leewyatt.rxcontrols;

import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired by an {@link RXTabPane} around the lifecycle of closing an
 * {@link RXTab}.
 *
 * <p>Because {@code RXTab} is not a {@code Node} and does not participate in an
 * event-dispatch chain (the project stays free of the {@code com.sun} private
 * {@code EventHandlerManager}), these events are fired on the owning
 * {@code RXTabPane} — a real {@code Node} with its own dispatch chain. The
 * event {@linkplain #getSource() source} is the {@code RXTab} being closed and
 * the {@linkplain #getTarget() target} is the {@code RXTabPane}.</p>
 *
 * <p><b>Consumption across dispatch copies.</b> A pane sits mid-chain (below the
 * scene and window), so dispatch fixes the event source per hop by
 * {@link #copyFor cloning}, and {@code copyFor} resets {@code consumed} to
 * {@code false} on every clone. A handler therefore consumes a per-hop copy, and
 * that veto would be invisible on the original event. To make a
 * {@link #consume() veto} reliable regardless of how many copies the dispatch
 * makes, consumption is recorded in a one-element flag that all clones share (the
 * shallow {@code clone()} copies the array <i>reference</i>): {@link #consume()}
 * sets it and {@link #isConsumed()} reads it, so a consume on any copy is visible
 * on all of them.</p>
 */
public class RXTabEvent extends Event {

    private static final long serialVersionUID = 20260713L;

    /**
     * One-element consumed flag shared by every {@code copyFor} clone (the
     * shallow clone copies the array reference). Survives {@code copyFor}
     * resetting the inherited {@code consumed} field, so a veto on any dispatch
     * copy is observable on the original event.
     */
    private final transient boolean[] consumedFlag;

    /**
     * Common supertype for all {@code RXTabEvent} types. The runtime name is
     * prefixed {@code RX_} to keep the global {@code EventType} registry
     * collision-free with other libraries.
     */
    public static final EventType<RXTabEvent> ANY = new EventType<>(Event.ANY, "RX_TAB");

    /**
     * Fired before a tab is closed. Consuming this event vetoes the close.
     */
    public static final EventType<RXTabEvent> TAB_CLOSE_REQUEST =
            new EventType<>(ANY, "RX_TAB_CLOSE_REQUEST");

    /**
     * Fired after a tab has been removed from its pane. Consuming has no effect
     * (the close already happened).
     */
    public static final EventType<RXTabEvent> TAB_CLOSED =
            new EventType<>(ANY, "RX_TAB_CLOSED");

    private final transient RXTab tab;

    /**
     * Creates an event for the given tab and pane.
     *
     * @param tab  the tab this event concerns; becomes the event source
     * @param pane the pane firing the event; becomes the event target (must be
     *             the node the event is fired on so a veto is not lost to a
     *             {@code copyFor} copy)
     * @param type the event type
     */
    public RXTabEvent(RXTab tab, RXTabPane pane, EventType<RXTabEvent> type) {
        super(tab, pane, type);
        this.tab = tab;
        this.consumedFlag = new boolean[1];
    }

    /**
     * Returns the tab this event concerns.
     *
     * @return the tab
     */
    public RXTab getTab() {
        return tab;
    }

    /**
     * Marks this event as consumed, vetoing the close. The veto is recorded in a
     * flag shared with every dispatch copy so it is observable on the original
     * event even after {@code copyFor} resets the inherited consumed state.
     */
    @Override
    public void consume() {
        super.consume();
        if (consumedFlag != null) {
            consumedFlag[0] = true;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reports {@code true} if this event or any of its dispatch copies has
     * been consumed.</p>
     */
    @Override
    public boolean isConsumed() {
        return super.isConsumed() || (consumedFlag != null && consumedFlag[0]);
    }
}

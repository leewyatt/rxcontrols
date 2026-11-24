package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Fired by an {@link RXDashboardPane} after the user commits an edit: a drag or a resize released with a layout
 * different from the one the gesture started on. Changes made by code ({@code moveCard}, {@code resizeCard},
 * {@code compact}, {@code applyLayout}, adding or removing cards, changing the column count) do not fire it.
 *
 * <p>The event cannot be vetoed: it is fired once the new layout is committed, the cards' placement properties and
 * the pane's {@link RXDashboardPane#layoutProperty() layout} property updated. A handler may change the layout again,
 * for example compact it after every edit, or reject the edit with
 * {@code dashboard.applyLayout(event.getPreviousLayout())}; such a change takes effect at once. The layouts the event
 * carries are those of the moment the user committed, so a later handler may find the pane's current layout already
 * changed by an earlier one. To save the layout, listen to the {@code layout} property instead: it follows every
 * change, from the user and from code.</p>
 */
public class RXDashboardEvent extends Event {

    /**
     * Base type for all dashboard events.
     */
    public static final EventType<RXDashboardEvent> ANY = new EventType<>(Event.ANY, "RX_DASHBOARD");

    /**
     * Fired after the user commits a drag or a resize that changed the layout.
     */
    public static final EventType<RXDashboardEvent> LAYOUT_CHANGED =
            new EventType<>(ANY, "RX_DASHBOARD_LAYOUT_CHANGED");

    private final transient RXDashboardPane dashboard;
    private final transient RXDashboardLayout layout;
    private final transient RXDashboardLayout previousLayout;
    private final transient RXDashboardCard card;

    /**
     * Creates a dashboard event whose source and target are the dashboard itself.
     *
     * @param eventType      the event type
     * @param dashboard      the dashboard firing the event
     * @param layout         the layout the user committed
     * @param previousLayout the layout before the edit
     * @param card           the card the user dragged or resized
     */
    public RXDashboardEvent(EventType<RXDashboardEvent> eventType, RXDashboardPane dashboard,
                            RXDashboardLayout layout, RXDashboardLayout previousLayout, RXDashboardCard card) {
        super(dashboard, dashboard, eventType);
        this.dashboard = dashboard;
        this.layout = layout;
        this.previousLayout = previousLayout;
        this.card = card;
    }

    /**
     * Returns the dashboard that fired this event.
     *
     * @return the source dashboard
     */
    public RXDashboardPane getDashboard() {
        return dashboard;
    }

    /**
     * Returns the layout the user committed, every card with an ID included, those pushed out of the way as well;
     * a card without an ID is in no snapshot.
     *
     * @return the committed layout
     */
    public RXDashboardLayout getLayout() {
        return layout;
    }

    /**
     * Returns the layout before the edit. Applying it puts the cards with an ID back where they were; a card without
     * an ID, which the snapshot does not name, may stay where the edit pushed it.
     *
     * @return the layout the gesture started on
     */
    public RXDashboardLayout getPreviousLayout() {
        return previousLayout;
    }

    /**
     * Returns the card the user dragged or resized.
     *
     * @return the edited card
     */
    public RXDashboardCard getCard() {
        return card;
    }
}

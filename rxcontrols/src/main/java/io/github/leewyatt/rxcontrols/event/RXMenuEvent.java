package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXPopupMenu;
import io.github.leewyatt.rxcontrols.RXPopupMenu.CloseReason;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Lifecycle payload delivered to an {@link RXPopupMenu}'s
 * {@code onShowing}/{@code onShown}/{@code onHiding}/{@code onHidden} handlers.
 *
 * <p>{@code RXPopupMenu} is a plain composition object (not a {@code Node} /
 * {@code EventTarget}), so these events are <b>not</b> dispatched through a JavaFX
 * event chain — the popup calls {@code handler.handle(event)} directly. Consuming
 * a {@link #MENU_HIDING} event ({@link #consume()}) vetoes the close.</p>
 *
 * <p>The {@code RX_} prefix on every {@link EventType} name is mandatory: these
 * types hang off {@link Event#ANY}, and a bare name (e.g. {@code "MENU_SHOWING"})
 * would risk a global name collision at class-load time.</p>
 */
public class RXMenuEvent extends Event {

    /**
     * Base type for all menu lifecycle events.
     */
    public static final EventType<RXMenuEvent> ANY = new EventType<>(Event.ANY, "RX_MENU");

    /**
     * Fired just before the menu is shown (populate items here). Fires even if the
     * subsequent show fails, matching native {@code PopupControl.WINDOW_SHOWING}.
     */
    public static final EventType<RXMenuEvent> MENU_SHOWING = new EventType<>(ANY, "RX_MENU_SHOWING");

    /**
     * Fired once the menu is fully shown.
     */
    public static final EventType<RXMenuEvent> MENU_SHOWN = new EventType<>(ANY, "RX_MENU_SHOWN");

    /**
     * Fired before the menu hides. {@link #consume()} vetoes the close (only on
     * the explicit {@code hide} path; auto-hide / owner-detach cannot be vetoed).
     */
    public static final EventType<RXMenuEvent> MENU_HIDING = new EventType<>(ANY, "RX_MENU_HIDING");

    /**
     * Fired once the menu has hidden, carrying the {@link #getReason() reason}.
     */
    public static final EventType<RXMenuEvent> MENU_HIDDEN = new EventType<>(ANY, "RX_MENU_HIDDEN");

    private final transient CloseReason reason;

    /**
     * Creates a menu lifecycle event.
     *
     * @param eventType the specific event type
     * @param reason    the close reason for {@link #MENU_HIDING} / {@link #MENU_HIDDEN};
     *                  {@code null} for {@link #MENU_SHOWING} / {@link #MENU_SHOWN}
     */
    public RXMenuEvent(EventType<RXMenuEvent> eventType, CloseReason reason) {
        super(eventType);
        this.reason = reason;
    }

    /**
     * Returns why the menu is closing. Meaningful only on {@link #MENU_HIDING} /
     * {@link #MENU_HIDDEN}; {@code null} on the show events.
     *
     * @return the close reason, or {@code null}
     */
    public CloseReason getReason() {
        return reason;
    }
}

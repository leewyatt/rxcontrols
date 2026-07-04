package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXBBCodeView;
import io.github.leewyatt.rxcontrols.bbcode.RXLinkKind;
import javafx.event.Event;
import javafx.event.EventType;

/**
 * Event fired when a link is activated in an {@link RXBBCodeView}.
 *
 * <p>The {@link #getHref() href} has already passed the view's scheme allow-list at
 * parse time, so it is safe to hand to a browser / mail client; the view itself does
 * not open it.
 */
public class RXBBCodeLinkEvent extends Event {

    /**
     * Base type for all BBCode link events.
     */
    public static final EventType<RXBBCodeLinkEvent> ANY = new EventType<>(Event.ANY, "RX_BBCODE_LINK");

    /**
     * Fired when a link is activated (clicked).
     */
    public static final EventType<RXBBCodeLinkEvent> LINK_ACTIVATED =
            new EventType<>(ANY, "RX_BBCODE_LINK_ACTIVATED");

    private final transient RXBBCodeView view;
    private final transient String href;
    private final transient RXLinkKind linkKind;

    /**
     * Creates a link event whose source and target are the BBCode view.
     *
     * @param source    the BBCode view firing the event
     * @param eventType the specific event type
     * @param href      the activated link target (already scheme-validated)
     * @param linkKind  the kind of link (URL or email)
     */
    public RXBBCodeLinkEvent(RXBBCodeView source, EventType<RXBBCodeLinkEvent> eventType,
                             String href, RXLinkKind linkKind) {
        super(source, source, eventType);
        this.view = source;
        this.href = href;
        this.linkKind = linkKind;
    }

    /**
     * Returns the BBCode view that fired this event.
     *
     * @return the source BBCode view
     */
    public RXBBCodeView getView() {
        return view;
    }

    /**
     * Returns the activated link target.
     *
     * @return the link href, already validated against the scheme allow-list
     */
    public String getHref() {
        return href;
    }

    /**
     * Returns the kind of link that was activated.
     *
     * @return the link kind (URL or email)
     */
    public RXLinkKind getLinkKind() {
        return linkKind;
    }
}

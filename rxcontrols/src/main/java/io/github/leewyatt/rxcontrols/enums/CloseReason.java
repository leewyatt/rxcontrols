package io.github.leewyatt.rxcontrols.enums;

/**
 * Why an {@link io.github.leewyatt.rxcontrols.RXDrawerPane RXDrawerPane} was asked
 * to close. Carried by the {@code CLOSE_REQUEST} / {@code CLOSING} / {@code CLOSED}
 * {@link io.github.leewyatt.rxcontrols.event.RXDrawerEvent RXDrawerEvent} so a veto
 * handler can decide based on the trigger.
 *
 */
public enum CloseReason {

    /**
     * The user pressed the ESC key while the drawer was open.
     */
    ESC,

    /**
     * The user clicked the overlay pane (the dimmed backdrop) outside the drawer.
     */
    OVERLAY_PANE_CLICK,

    /**
     * The user activated the drawer's built-in close button.
     */
    CLOSE_BUTTON,

    /**
     * Code requested the close — {@code close()}, {@code toggle()}, or setting
     * {@code showing} to {@code false}.
     */
    PROGRAMMATIC
}

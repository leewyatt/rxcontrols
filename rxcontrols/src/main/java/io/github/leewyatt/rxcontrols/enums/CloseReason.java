package io.github.leewyatt.rxcontrols.enums;

/**
 * Why an {@link io.github.leewyatt.rxcontrols.RXDrawerPane} was asked to close.
 * Carried by the {@code CLOSE_REQUEST} / {@code CLOSING} / {@code CLOSED}
 * {@link io.github.leewyatt.rxcontrols.event.RXDrawerEvent} so a veto handler can
 * decide based on the trigger.
 *
 * <p>The drawer's overlay-sliding increment produces only {@link #PROGRAMMATIC}.
 * {@link #ESC}, {@link #SCRIM_CLICK} and {@link #CLOSE_BUTTON} are produced once
 * their respective triggers (ESC key handling, scrim, header close button) are
 * added in later increments.</p>
 */
public enum CloseReason {

    /**
     * The user pressed the ESC key while the drawer was open.
     */
    ESC,

    /**
     * The user clicked the scrim (the dimmed backdrop) outside the drawer.
     */
    SCRIM_CLICK,

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

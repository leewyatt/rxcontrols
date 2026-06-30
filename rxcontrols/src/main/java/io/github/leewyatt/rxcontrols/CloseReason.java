package io.github.leewyatt.rxcontrols;

/**
 * Why an {@link io.github.leewyatt.rxcontrols.RXDialog RXDialog} close was
 * requested. Carried on the {@code CLOSE_REQUEST} / {@code HIDING} /
 * {@code HIDDEN}
 * {@link io.github.leewyatt.rxcontrols.event.RXDialogEvent RXDialogEvent} so a
 * handler can tell an explicit button choice apart from a dismissal.
 */
public enum CloseReason {

    /**
     * The user clicked one of the dialog's action buttons (built from a
     * {@code ButtonType}). The candidate button type is that button's type.
     */
    ACTION_BUTTON,

    /**
     * The user pressed ESC. The candidate button type is the cancel-type button
     * in {@code buttonTypes}, if any, else {@code null}.
     */
    ESC,

    /**
     * The user clicked the dimmed scrim outside the card. The candidate button
     * type is the cancel-type button in {@code buttonTypes}, if any, else
     * {@code null}.
     */
    SCRIM,

    /**
     * The user clicked the card's close (X) button. The candidate button type is
     * the cancel-type button in {@code buttonTypes}, if any, else {@code null}.
     */
    CLOSE_BUTTON,

    /**
     * The application called {@code close()} or {@code close(ButtonType)}. The
     * candidate button type is whatever was passed (or {@code null}).
     */
    PROGRAMMATIC
}

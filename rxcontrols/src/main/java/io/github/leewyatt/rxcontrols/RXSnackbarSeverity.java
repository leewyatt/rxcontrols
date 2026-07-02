package io.github.leewyatt.rxcontrols;

/**
 * Visual severity of a snackbar — a pure style hook. A non-{@code NONE} severity
 * adds a {@code rx-snackbar-<severity>} style class and matching pseudo-class to
 * the bar so the stylesheet can tint it; structure and behavior never change.
 * {@link #NONE} (the default) adds nothing.
 */
public enum RXSnackbarSeverity {

    /** Neutral bar; no severity style class or pseudo-class is applied. */
    NONE,

    /** Informational tint. */
    INFO,

    /** Success tint. */
    SUCCESS,

    /** Warning tint. */
    WARNING,

    /** Error tint. */
    ERROR
}

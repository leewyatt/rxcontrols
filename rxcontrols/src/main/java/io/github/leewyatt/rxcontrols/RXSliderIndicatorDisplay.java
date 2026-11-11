package io.github.leewyatt.rxcontrols;

/**
 * Display policy for a slider's value indicator bubble.
 */
public enum RXSliderIndicatorDisplay {

    /**
     * The indicator is never shown.
     */
    NEVER,

    /**
     * The indicator is shown while the value is being changed by dragging, and
     * while the slider holds focus reached or used with the keyboard; it hides
     * shortly after the interaction ends. This is the default ("labeled on select" in Material).
     */
    DRAGGING,

    /**
     * The indicator is always shown.
     */
    ALWAYS
}

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
     * Like {@link #DRAGGING}, and also shown while the pointer is over a thumb; it
     * hides shortly after the pointer leaves.
     */
    HOVER,

    /**
     * The indicator is always shown.
     */
    ALWAYS
}

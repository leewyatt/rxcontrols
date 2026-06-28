package io.github.leewyatt.rxcontrols;

/**
 * Display policy for a slider's value indicator bubble, aligned with the three
 * states of the MUI {@code valueLabelDisplay} option.
 */
public enum RXSliderIndicatorDisplay {

    /**
     * The indicator is never shown.
     */
    NEVER,

    /**
     * The indicator is shown while the value is being changed by dragging, the
     * keyboard, or while the slider is focused, and hides shortly after the
     * interaction ends. This is the default ("labeled on select" in Material).
     */
    DRAGGING,

    /**
     * The indicator is always shown.
     */
    ALWAYS
}

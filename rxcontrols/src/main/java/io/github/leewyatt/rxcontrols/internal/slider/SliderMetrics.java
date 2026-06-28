package io.github.leewyatt.rxcontrols.internal.slider;

/**
 * Shared visual layout metrics for the single- and range-slider skins, kept in
 * one place so the two controls render identically (a future tweak stays in
 * lockstep).
 */
public final class SliderMetrics {

    /** Default main-axis length of a slider with no explicit preferred size. */
    public static final double DEFAULT_PREF_LENGTH = 150.0;

    /** Gap between the thumb and its value-indicator bubble. */
    public static final double INDICATOR_GAP = 8.0;

    /** Gap between the bar and the tick-label band. */
    public static final double TICK_LABEL_GAP = 4.0;

    private SliderMetrics() {
    }
}

package io.github.leewyatt.rxcontrols;

/**
 * Side of the track, across it, on which a slider's value indicator bubble is
 * placed. The sides are named along the axis across the track, not along the
 * track itself: {@code START} and {@code END} never mean the minimum or the
 * maximum end of the track.
 */
public enum RXSliderIndicatorPosition {

    /**
     * The start side across the track: above a horizontal track; beside a
     * vertical track on the left, or on the right in a right-to-left orientation.
     */
    START,

    /**
     * The end side across the track: below a horizontal track; beside a vertical
     * track on the right, or on the left in a right-to-left orientation.
     */
    END
}

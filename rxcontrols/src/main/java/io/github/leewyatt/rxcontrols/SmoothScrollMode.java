package io.github.leewyatt.rxcontrols;

/**
 * Defines how enabled smooth wheel input is animated.
 */
public enum SmoothScrollMode {

    /**
     * Animates the current offset toward a delta-derived target offset over a fixed
     * duration, without adding extra scroll distance.
     */
    TARGET,

    /**
     * Converts wheel input into velocity and lets it decay over time: the motion
     * starts fast and eases out in a kinetic tail after the wheel input stops. A
     * wheel step travels the same distance as in {@link #TARGET} mode, and quick
     * steps add up.
     */
    MOMENTUM
}

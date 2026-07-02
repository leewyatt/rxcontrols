package io.github.leewyatt.rxcontrols;

/**
 * Defines how enabled smooth wheel input is animated.
 */
public enum SmoothScrollMode {

    /**
     * Animates the current offset toward a delta-derived target offset without
     * adding extra scroll distance.
     */
    TARGET,

    /**
     * Converts wheel input into velocity and lets it decay over time, producing a
     * kinetic tail after the wheel input stops.
     */
    MOMENTUM
}

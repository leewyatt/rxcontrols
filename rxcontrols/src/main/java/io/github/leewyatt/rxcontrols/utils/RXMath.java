package io.github.leewyatt.rxcontrols.utils;

/**
 * Numeric helpers used by RXControls and available to application code.
 */
public final class RXMath {

    private RXMath() {
    }

    /**
     * Converts {@code NaN} and negative values to {@code 0}; all other values
     * are returned unchanged.
     *
     * @param value the value to sanitize
     * @return {@code 0} for {@code NaN} or negative input; otherwise
     *         {@code value}
     */
    public static double sanitizeNonNegative(double value) {
        if (Double.isNaN(value) || value < 0.0) {
            return 0.0;
        }
        return value;
    }

    /**
     * Clamps a value to the unit interval {@code [0, 1]}. {@code NaN} is
     * treated as {@code 0}.
     *
     * @param value the value to clamp
     * @return the clamped value
     */
    public static double clamp0To1(double value) {
        if (Double.isNaN(value) || value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    /**
     * Clamps a value to the inclusive range {@code [min, max]}. {@code NaN}
     * values are returned unchanged.
     *
     * @param value the value to clamp
     * @param min   the inclusive lower bound
     * @param max   the inclusive upper bound
     * @return the clamped value
     * @throws IllegalArgumentException if {@code min > max}
     */
    public static double clamp(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * Clamps a value to the inclusive range {@code [min, max]}.
     *
     * @param value the value to clamp
     * @param min   the inclusive lower bound
     * @param max   the inclusive upper bound
     * @return the clamped value
     * @throws IllegalArgumentException if {@code min > max}
     */
    public static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min cannot be greater than max");
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}

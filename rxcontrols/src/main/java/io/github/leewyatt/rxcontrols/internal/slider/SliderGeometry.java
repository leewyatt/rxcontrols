package io.github.leewyatt.rxcontrols.internal.slider;

import io.github.leewyatt.rxcontrols.utils.RXMath;

/**
 * Static geometry helpers shared by the slider skins: value-to-pixel mapping
 * and (added later) tick value sequences and snapping. Pure functions with no
 * JavaFX dependency beyond {@link RXMath}.
 */
public final class SliderGeometry {

    private static final double[] EMPTY = new double[0];
    private static final int MAX_TICK_COUNT = 1000;
    private static final double TICK_TOLERANCE_RATIO = 1.0e-6;

    private SliderGeometry() {
    }

    /**
     * Maps a value to its fraction of the {@code [min, max]} span, clamped to
     * {@code [0, 1]}. A non-positive or non-finite span (including
     * {@code min == max}) yields {@code 0} rather than {@code NaN}.
     *
     * @param value the value to map
     * @param min   the range minimum
     * @param max   the range maximum
     * @return the clamped fraction in {@code [0, 1]}
     */
    public static double valueToFraction(double value, double min, double max) {
        double span = max - min;
        if (!(span > 0.0)) {
            return 0.0;
        }
        return RXMath.clamp0To1((value - min) / span);
    }

    /**
     * Maps a fraction of the {@code [min, max]} span back to a value. The
     * fraction is first clamped to {@code [0, 1]}.
     *
     * @param fraction the fraction to map
     * @param min      the range minimum
     * @param max      the range maximum
     * @return the value at the clamped fraction
     */
    public static double fractionToValue(double fraction, double min, double max) {
        return min + RXMath.clamp0To1(fraction) * (max - min);
    }

    /**
     * Returns the value sequence of all tick positions (major and minor),
     * starting at {@code min} and stepping by the minor spacing
     * {@code majorTickUnit / (minorTickCount + 1)} up to {@code max}. The minor
     * count is floored at {@code 0}. Returns an empty array for a non-positive
     * tick unit or a non-positive span. The count is capped to avoid a
     * pathological node explosion for a tiny tick unit.
     *
     * @param min            the range minimum
     * @param max            the range maximum
     * @param majorTickUnit  the spacing between major ticks
     * @param minorTickCount the number of minor ticks between two major ticks
     * @return the tick value sequence, ascending
     */
    public static double[] tickValues(double min, double max, double majorTickUnit, int minorTickCount) {
        if (!(majorTickUnit > 0.0) || !(max > min)) {
            return EMPTY;
        }
        return positions(min, max, minorSpacing(majorTickUnit, minorTickCount));
    }

    /**
     * Returns the spacing between adjacent ticks (the minor spacing) for the
     * given major tick unit and minor count: {@code majorTickUnit /
     * (max(minorTickCount, 0) + 1)}. This is the single source of the tick
     * spacing shared by tick generation and the snap-aware keyboard step.
     *
     * @param majorTickUnit  the spacing between major ticks
     * @param minorTickCount the number of minor ticks between two major ticks
     * @return the minor tick spacing
     */
    public static double minorSpacing(double majorTickUnit, int minorTickCount) {
        return majorTickUnit / (Math.max(minorTickCount, 0) + 1);
    }

    /**
     * Returns the value sequence of the major tick positions, starting at
     * {@code min} and stepping by {@code majorTickUnit} up to {@code max}.
     * Returns an empty array for a non-positive tick unit or span.
     *
     * @param min           the range minimum
     * @param max           the range maximum
     * @param majorTickUnit the spacing between major ticks
     * @return the major tick value sequence, ascending
     */
    public static double[] majorTickValues(double min, double max, double majorTickUnit) {
        if (!(majorTickUnit > 0.0) || !(max > min)) {
            return EMPTY;
        }
        return positions(min, max, majorTickUnit);
    }

    /**
     * Returns the low value clamped to {@code [min, max]} and, so the two values
     * keep at least {@code minGap} apart, to {@code [min, high - minGap]}. The
     * cross-clamp is applied only when the high value is itself within
     * {@code [min, max]} — the death-loop guard that prevents an infinite
     * re-clamp while the other value is not yet settled (e.g. during
     * construction). A {@code minGap} of {@code 0} leaves the values free to meet.
     *
     * @param low    the low value to clamp
     * @param min    the range minimum
     * @param max    the range maximum
     * @param high   the high value
     * @param minGap the minimum gap between the values (value units, {@code >= 0})
     * @return the clamped low value
     */
    public static double clampLow(double low, double min, double max, double high, double minGap) {
        if (!(min <= max)) {
            // An inverted (or NaN) range has no valid interval; leave the value
            // untouched (lenient, like the JavaFX model) instead of throwing.
            return low;
        }
        if (low < min || low > max) {
            return RXMath.clamp(low, min, max);
        }
        if (high >= min && high <= max) {
            double upper = high - minGap;
            if (low > upper) {
                return RXMath.clamp(low, min, Math.max(min, upper));
            }
        }
        return low;
    }

    /**
     * Returns the high value clamped to {@code [min, max]} and, so the two values
     * keep at least {@code minGap} apart, to {@code [low + minGap, max]}. The
     * cross-clamp is applied only when the low value is itself within
     * {@code [min, max]} (the death-loop guard).
     *
     * @param high   the high value to clamp
     * @param min    the range minimum
     * @param max    the range maximum
     * @param low    the low value
     * @param minGap the minimum gap between the values (value units, {@code >= 0})
     * @return the clamped high value
     */
    public static double clampHigh(double high, double min, double max, double low, double minGap) {
        if (!(min <= max)) {
            // An inverted (or NaN) range has no valid interval; leave the value
            // untouched (lenient, like the JavaFX model) instead of throwing.
            return high;
        }
        if (high < min || high > max) {
            return RXMath.clamp(high, min, max);
        }
        if (low >= min && low <= max) {
            double lower = low + minGap;
            if (high < lower) {
                return RXMath.clamp(high, Math.min(max, lower), max);
            }
        }
        return high;
    }

    /**
     * Snaps a value to the nearest tick when {@code snapToTicks} is set,
     * otherwise clamps it to {@code [min, max]}. Mirrors the native
     * {@code Slider.snapValueToTicks} for the self-built range control.
     *
     * @param value          the value to snap
     * @param min            the range minimum
     * @param max            the range maximum
     * @param majorTickUnit  the spacing between major ticks
     * @param minorTickCount the number of minor ticks between two major ticks
     * @param snapToTicks    whether snapping is enabled
     * @return the snapped (or clamped) value
     */
    public static double snap(double value, double min, double max,
                              double majorTickUnit, int minorTickCount, boolean snapToTicks) {
        if (!snapToTicks || max <= min) {
            return RXMath.clamp(value, min, max);
        }
        double spacing = minorSpacing(majorTickUnit, minorTickCount);
        if (!(spacing > 0.0)) {
            return RXMath.clamp(value, min, max);
        }
        int prev = (int) ((value - min) / spacing);
        double prevTick = prev * spacing + min;
        double nextTick = (prev + 1) * spacing + min;
        double nearest = Math.abs(value - prevTick) <= Math.abs(value - nextTick) ? prevTick : nextTick;
        return RXMath.clamp(nearest, min, max);
    }

    private static double[] positions(double min, double max, double spacing) {
        // A non-positive (e.g. underflowed or overflow-negated) spacing is
        // degenerate; yield no ticks rather than the cap's worth of coincident
        // ones.
        if (!(spacing > 0.0)) {
            return EMPTY;
        }
        // Tolerate floating-point drift at the endpoint so a tick that should
        // land exactly on max is not dropped.
        double tolerance = spacing * TICK_TOLERANCE_RATIO;
        int count = 0;
        while (count < MAX_TICK_COUNT && min + count * spacing <= max + tolerance) {
            count++;
        }
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            values[i] = min + i * spacing;
        }
        return values;
    }
}

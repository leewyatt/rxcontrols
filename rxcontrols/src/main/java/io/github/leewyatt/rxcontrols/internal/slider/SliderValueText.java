package io.github.leewyatt.rxcontrols.internal.slider;

import java.math.RoundingMode;
import java.text.NumberFormat;

/**
 * Default value text for the slider skins when no {@code labelFormatter} is set.
 * The number of fraction digits follows the range and the major tick unit, so a
 * {@code 0..100} slider shows whole numbers and a {@code 0..1} slider shows
 * {@code 0.37}; trailing zeros are dropped.
 */
public final class SliderValueText {

    // The text resolves about one hundredth of the range.
    private static final double RESOLUTION_STEPS = 100.0;
    private static final int MAX_FRACTION_DIGITS = 4;
    // Relative tolerance for deciding that a tick unit has no further decimals.
    private static final double DECIMAL_TOLERANCE = 1.0e-9;

    private SliderValueText() {
    }

    /**
     * Formats a value with the fraction digits the range and tick unit call for,
     * in the default locale and without grouping.
     *
     * @param value         the value to format
     * @param min           the range minimum
     * @param max           the range maximum
     * @param majorTickUnit the spacing between major ticks
     * @return the value text
     */
    public static String format(double value, double min, double max, double majorTickUnit) {
        if (!Double.isFinite(value)) {
            return Long.toString(Math.round(value));
        }
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setGroupingUsed(false);
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(fractionDigits(min, max, majorTickUnit));
        return format.format(value);
    }

    // Enough digits to resolve a hundredth of the range, and to show a major tick
    // unit smaller than the range exactly, at most four.
    private static int fractionDigits(double min, double max, double majorTickUnit) {
        double span = max - min;
        if (!(span > 0.0) || !Double.isFinite(span)) {
            return 0;
        }
        int digits = digitsToResolve(span / RESOLUTION_STEPS);
        if (majorTickUnit > 0.0 && majorTickUnit < span) {
            digits = Math.max(digits, decimalsOf(majorTickUnit));
        }
        return Math.min(digits, MAX_FRACTION_DIGITS);
    }

    // 1 -> 0, 0.1 -> 1, 0.05 -> 2, 0.01 -> 2.
    private static int digitsToResolve(double step) {
        if (step >= 1.0) {
            return 0;
        }
        return (int) Math.ceil(-Math.log10(step) - DECIMAL_TOLERANCE);
    }

    // 25 -> 0, 0.5 -> 1, 0.25 -> 2; a repeating fraction such as 1/3 takes the cap.
    private static int decimalsOf(double unit) {
        double scaled = unit;
        for (int digits = 0; digits < MAX_FRACTION_DIGITS; digits++) {
            if (Math.abs(scaled - Math.rint(scaled)) <= DECIMAL_TOLERANCE * Math.max(1.0, scaled)) {
                return digits;
            }
            scaled *= 10.0;
        }
        return MAX_FRACTION_DIGITS;
    }
}

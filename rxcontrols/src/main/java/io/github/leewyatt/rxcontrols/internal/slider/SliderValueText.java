package io.github.leewyatt.rxcontrols.internal.slider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

/**
 * Default value text for the slider skins when no {@code labelFormatter} is set.
 * The number of fraction digits follows the range, its start and the major tick
 * unit, so a {@code 0..100} slider shows whole numbers, a {@code 0..1} slider shows
 * {@code 0.37} and a {@code 0.5..100.5} slider shows its ticks as {@code 25.5};
 * trailing zeros are dropped.
 */
public final class SliderValueText {

    // The text resolves about one hundredth of the range.
    private static final double RESOLUTION_STEPS = 100.0;
    private static final int MAX_FRACTION_DIGITS = 4;
    // Absorbs the rounding in log10 for exact powers of ten.
    private static final double LOG_TOLERANCE = 1.0e-9;

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
        int digits = fractionDigits(min, max, majorTickUnit);
        // A value that rounds to zero shows as 0, not -0.
        double shown = Math.abs(value) < 0.5 * Math.pow(10.0, -digits) ? 0.0 : value;
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setGroupingUsed(false);
        format.setRoundingMode(RoundingMode.HALF_UP);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(digits);
        return format.format(shown);
    }

    // Enough digits to resolve a hundredth of the range and to show the ticks exactly:
    // they step from min by the major tick unit. At most four.
    private static int fractionDigits(double min, double max, double majorTickUnit) {
        double span = max - min;
        if (!(span > 0.0) || !Double.isFinite(span)) {
            return 0;
        }
        int digits = Math.max(digitsToResolve(span / RESOLUTION_STEPS), decimalsOf(min));
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
        return (int) Math.ceil(-Math.log10(step) - LOG_TOLERANCE);
    }

    // The decimals left after rounding to the shown precision: 25 -> 0, 0.5 -> 1,
    // -0.25 -> 2, 1/3 -> 4, and 0.1 * 3 (0.30000000000000004) -> 1.
    private static int decimalsOf(double number) {
        BigDecimal shown = BigDecimal.valueOf(number)
                .setScale(MAX_FRACTION_DIGITS, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return Math.max(0, shown.scale());
    }
}

package io.github.leewyatt.rxcontrols.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * UI value-to-string formatters shared across RXControls.
 */
public final class RXFormat {

    private RXFormat() {
    }

    /**
     * Formats a ratio as an integer percentage. The input is not clamped:
     * values outside {@code [0, 1]} pass through unchanged.
     * The argument must be finite; results for {@code NaN} or infinity are
     * outside the contract and may differ between overloads.
     * Examples: {@code percent(0.52312)} → {@code "52%"};
     * {@code percent(1.5)} → {@code "150%"};
     * {@code percent(-0.05)} → {@code "-5%"}.
     *
     * @param value the ratio to scale by 100
     * @return the formatted percentage
     */
    public static String percent(double value) {
        return percent(value, 0);
    }

    /**
     * Formats a ratio as a percentage with up to {@code decimals} fractional
     * digits; trailing zeros are stripped. The input is not clamped.
     * The argument must be finite; results for {@code NaN} or infinity are
     * outside the contract and may differ between overloads.
     * Examples: {@code percent(0.52312, 2)} → {@code "52.31%"};
     * {@code percent(0.523, 2)} → {@code "52.3%"};
     * {@code percent(0.5, 2)} → {@code "50%"};
     * {@code percent(2.0, 1)} → {@code "200%"}.
     *
     * @param value    the ratio to scale by 100
     * @param decimals maximum fractional digits; {@code <= 0} produces an integer percentage
     * @return the formatted percentage
     */
    public static String percent(double value, int decimals) {
        double scaled = value * 100.0;
        if (decimals <= 0) {
            return Math.round(scaled) + "%";
        }
        return BigDecimal.valueOf(scaled)
                .setScale(decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString() + "%";
    }
}

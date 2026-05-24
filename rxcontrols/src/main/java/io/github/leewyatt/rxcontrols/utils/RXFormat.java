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
     * Formats a {@code [0, 1]} progress as an integer percentage.
     * Example: {@code percent(0.52312)} → {@code "52%"}.
     *
     * @param progress the progress; {@code null} or negative returns {@code ""}
     * @return the formatted percentage, or {@code ""} for indeterminate input
     */
    public static String percent(Double progress) {
        return percent(progress, 0);
    }

    /**
     * Formats a {@code [0, 1]} progress as a percentage with {@code decimals}
     * fractional digits. Trailing zeros are preserved.
     * Examples: {@code percent(0.52312, 0)} → {@code "52%"};
     * {@code percent(0.52312, 2)} → {@code "52.31%"};
     * {@code percent(0.5, 2)} → {@code "50.00%"}.
     *
     * @param progress the progress; {@code null} or negative returns {@code ""}
     * @param decimals fractional digits; {@code <= 0} produces an integer percentage
     * @return the formatted percentage, or {@code ""} for indeterminate input
     */
    public static String percent(Double progress, int decimals) {
        if (progress == null || progress < 0.0) {
            return "";
        }
        double scaled = RXMath.clamp0To1(progress) * 100.0;
        if (decimals <= 0) {
            return Math.round(scaled) + "%";
        }
        return BigDecimal.valueOf(scaled)
                .setScale(decimals, RoundingMode.HALF_UP)
                .toPlainString() + "%";
    }
}

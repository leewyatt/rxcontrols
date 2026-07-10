package io.github.leewyatt.rxcontrols.internal.number;

import javafx.util.StringConverter;

import java.math.BigDecimal;

/**
 * Plain {@link Double} converter for double fields.
 * <p>
 * Finite values render through
 * {@code BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()}: plain
 * decimal with no scientific notation (whose 'E' the edit filter would lock
 * out of editing) and no {@code .0} tail, while staying round-trip safe
 * ({@code valueOf} uses the canonical {@code Double.toString} digits).
 * {@link Double#toString(double)} is the defensive fallback for a non-finite
 * value, reachable only through a bound value property — an unbound write is
 * rejected by the field before rendering. Parsing shares the stub- and
 * scientific-notation-rejecting {@link NumberParsing} path and additionally
 * rejects a magnitude that overflows to infinity, rolling the text back.
 */
public final class DoubleFieldConverter extends StringConverter<Double> {

    /**
     * Creates a plain double converter.
     */
    public DoubleFieldConverter() {
    }

    @Override
    public String toString(Double value) {
        if (value == null) {
            return "";
        }
        double d = value;
        if (!Double.isFinite(d)) {
            return Double.toString(d);
        }
        return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    }

    @Override
    public Double fromString(String text) {
        if (text == null) {
            return null;
        }
        String raw = text.trim();
        if (raw.isEmpty()) {
            return null;
        }
        double parsed = NumberParsing.parsePlainDecimal(raw).doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new NumberFormatException("Number out of double range: " + raw);
        }
        return parsed;
    }
}

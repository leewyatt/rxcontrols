package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXNumberField;
import javafx.util.StringConverter;

import java.math.BigDecimal;

/**
 * Default {@link StringConverter} for {@link RXNumberField}. Stateless, no
 * formatting opinion — {@code toString} delegates to
 * {@link BigDecimal#toPlainString()}, {@code fromString} delegates to
 * {@code new BigDecimal(text)}.
 * <p>
 * Empty input, lone sign ({@code "-"} / {@code "+"}), lone decimal point, and
 * sign-plus-decimal ({@code "-."} / {@code "+."}) all parse back to
 * {@code null}. Any other unparseable input throws
 * {@link NumberFormatException} so the JavaFX
 * {@code TextFormatter.updateValue} catch block rolls back the displayed text
 * to the last valid value's canonical form.
 * <p>
 * Internal class — declared {@code public} only so {@link RXNumberField} (a
 * different package) can instantiate it. Not part of the stable public API.
 */
public final class NumberFieldStringConverter extends StringConverter<BigDecimal> {

    @Override
    public String toString(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    @Override
    public BigDecimal fromString(String text) {
        if (text == null) {
            return null;
        }
        String raw = text.trim();
        if (raw.isEmpty() || isIncompleteNumeric(raw)) {
            return null;
        }
        return new BigDecimal(raw);
    }

    private static boolean isIncompleteNumeric(String s) {
        return "-".equals(s) || "+".equals(s) || ".".equals(s)
                || "-.".equals(s) || "+.".equals(s);
    }
}

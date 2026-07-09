package io.github.leewyatt.rxcontrols.internal.number;

import javafx.util.StringConverter;

import java.math.BigDecimal;

/**
 * Plain {@link BigDecimal} converter for number fields.
 */
public final class NumberFieldStringConverter extends StringConverter<BigDecimal> {

    /**
     * Creates a plain number-field converter.
     */
    public NumberFieldStringConverter() {
    }

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
        if (raw.isEmpty()) {
            return null;
        }
        return NumberParsing.parsePlainDecimal(raw);
    }
}

package io.github.leewyatt.rxcontrols.internal.number;

import javafx.util.StringConverter;

/**
 * Plain {@link Integer} converter for integer fields. A sign-only stub the
 * edit filter admits mid-edit is rejected so the commit path reverts instead
 * of clearing, and an out-of-range magnitude (beyond 32-bit) is rejected by
 * {@link Integer#valueOf(String)} itself, which rolls the text back.
 */
public final class IntegerFieldConverter extends StringConverter<Integer> {

    /**
     * Creates a plain integer converter.
     */
    public IntegerFieldConverter() {
    }

    @Override
    public String toString(Integer value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public Integer fromString(String text) {
        if (text == null) {
            return null;
        }
        String raw = text.trim();
        if (raw.isEmpty()) {
            return null;
        }
        if ("-".equals(raw) || "+".equals(raw)) {
            throw new NumberFormatException("Incomplete number: " + raw);
        }
        return Integer.valueOf(raw);
    }
}

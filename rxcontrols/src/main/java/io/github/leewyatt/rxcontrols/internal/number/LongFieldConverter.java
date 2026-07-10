package io.github.leewyatt.rxcontrols.internal.number;

import javafx.util.StringConverter;

/**
 * Plain {@link Long} converter for long fields. A sign-only stub the edit
 * filter admits mid-edit is rejected so the commit path reverts instead of
 * clearing, and an out-of-range magnitude (beyond 64-bit) is rejected by
 * {@link Long#valueOf(String)} itself, which rolls the text back.
 */
public final class LongFieldConverter extends StringConverter<Long> {

    /**
     * Creates a plain long converter.
     */
    public LongFieldConverter() {
    }

    @Override
    public String toString(Long value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public Long fromString(String text) {
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
        return Long.valueOf(raw);
    }
}

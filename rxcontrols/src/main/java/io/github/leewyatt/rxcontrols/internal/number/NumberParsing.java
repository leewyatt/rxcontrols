package io.github.leewyatt.rxcontrols.internal.number;

import java.math.BigDecimal;

/**
 * Shared plain-decimal parsing used by the number-field converters.
 */
final class NumberParsing {

    private NumberParsing() {
    }

    /**
     * Parses a plain (ASCII, unformatted) decimal string. A sign- or
     * dot-only stub the edit filter admits mid-edit is rejected so the commit
     * path reverts to the last valid value rather than dropping it.
     *
     * @param raw the non-empty, trimmed text
     * @return the parsed value
     * @throws NumberFormatException if {@code raw} is an incomplete stub or not a number
     */
    static BigDecimal parsePlainDecimal(String raw) {
        if ("-".equals(raw) || "+".equals(raw) || ".".equals(raw)
                || "-.".equals(raw) || "+.".equals(raw)) {
            throw new NumberFormatException("Incomplete number: " + raw);
        }
        return new BigDecimal(raw);
    }
}

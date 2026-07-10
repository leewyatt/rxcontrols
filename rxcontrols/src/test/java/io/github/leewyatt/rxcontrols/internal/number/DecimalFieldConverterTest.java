package io.github.leewyatt.rxcontrols.internal.number;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parsing tests for {@link DecimalFieldConverter}: under a currency / percent
 * format the edit filter accepts a bare (affix-less) numeric body, so the
 * converter must accept it too instead of throwing and silently dropping the
 * committed value. Also pins the plain (null-format) path and the terminal
 * parser's scientific-notation rejection across the typed converters — the
 * edit filters never admit an 'e', but a bound text property bypasses the
 * filter and hands raw strings straight to the converter.
 */
public class DecimalFieldConverterTest {

    private static DecimalFieldConverter converterFor(NumberFormat format) {
        return new DecimalFieldConverter(() -> format);
    }

    /** Asserts numeric (scale-agnostic) equality. */
    private static void assertValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    public void percentAcceptsBareBody() {
        DecimalFieldConverter conv = converterFor(NumberFormat.getPercentInstance(Locale.US));

        // Bare digits typed into a percent field must commit, not vanish.
        assertValue("0.75", conv.fromString("75"));
        assertValue("0.07", conv.fromString("7"));
        assertValue("0.0075", conv.fromString("0.75"));

        // Explicit affix and signs still work (and agree with the bare form).
        assertValue("0.75", conv.fromString("75%"));
        assertValue("-0.75", conv.fromString("-75"));
        assertValue("-0.75", conv.fromString("-75%"));

        // Explicit "+" must stay positive even though percent's positive and
        // negative suffix are both "%" (regression guard for the affix order).
        assertValue("0.75", conv.fromString("+75"));
        assertValue("0.75", conv.fromString("+75%"));

        // Round trip through the format is stable.
        assertValue("0.75", conv.fromString(conv.toString(new BigDecimal("0.75"))));
    }

    @Test
    public void currencyAcceptsBareBody() {
        DecimalFieldConverter conv = converterFor(NumberFormat.getCurrencyInstance(Locale.US));

        assertValue("100", conv.fromString("100"));
        assertValue("100", conv.fromString("$100"));
        assertValue("100.5", conv.fromString("100.50"));
        assertValue("100.5", conv.fromString("$100.50"));
        assertValue("-100", conv.fromString("-100"));

        assertValue("100", conv.fromString(conv.toString(new BigDecimal("100"))));
    }

    @Test
    public void emptyOrNullIsNull() {
        DecimalFieldConverter cur = converterFor(NumberFormat.getCurrencyInstance(Locale.US));
        assertNull(cur.fromString(null));
        assertNull(cur.fromString(""));
        assertNull(cur.fromString("   "));
    }

    @Test
    public void incompleteInputThrows() {
        DecimalFieldConverter pct = converterFor(NumberFormat.getPercentInstance(Locale.US));
        DecimalFieldConverter cur = converterFor(NumberFormat.getCurrencyInstance(Locale.US));
        // Affix / sign / separator only is incomplete (not empty text): must throw
        // so the commit path reverts to the last valid value rather than clearing.
        assertThrows(NumberFormatException.class, () -> pct.fromString("%"));
        assertThrows(NumberFormatException.class, () -> pct.fromString("-"));
        assertThrows(NumberFormatException.class, () -> pct.fromString("."));
        assertThrows(NumberFormatException.class, () -> cur.fromString("$"));
    }

    @Test
    public void groupingSeparatorsInBareBody() {
        DecimalFieldConverter cur = converterFor(NumberFormat.getCurrencyInstance(Locale.US));
        DecimalFieldConverter num = converterFor(NumberFormat.getNumberInstance(Locale.US));
        // Bare / trailing grouping separators (the filter allows them mid-edit)
        // must commit the digits, not get dropped on the full-consume check.
        assertValue("5", num.fromString("5,"));
        assertValue("1234", num.fromString("1,234"));
        assertValue("1000", cur.fromString("1,000"));
    }

    @Test
    public void germanSuffixCurrencySigns() {
        DecimalFieldConverter de = converterFor(NumberFormat.getCurrencyInstance(Locale.GERMANY));
        // Currency symbol is a suffix and decimal separator is ',': a bare body
        // plus an explicit sign must round-trip with the correct sign.
        assertValue("100", de.fromString("100,00"));
        assertValue("-100", de.fromString("-100,00"));
        assertValue("100", de.fromString(de.toString(new BigDecimal("100"))));
    }

    @Test
    public void garbageStillThrows() {
        DecimalFieldConverter conv = converterFor(NumberFormat.getPercentInstance(Locale.US));
        assertThrows(NumberFormatException.class, () -> conv.fromString("abc"));
    }

    @Test
    public void nullFormatParsesPlainAndRendersPlain() {
        DecimalFieldConverter conv = converterFor(null);

        assertValue("75", conv.fromString("75"));
        assertValue("-1.5", conv.fromString("-1.5"));
        assertEquals("1234.50", conv.toString(new BigDecimal("1234.50")));
        assertThrows(NumberFormatException.class, () -> conv.fromString("-"));
        assertThrows(NumberFormatException.class, () -> conv.fromString("1,234"));
    }

    @Test
    public void defaultNumberFormatUnchanged() {
        DecimalFieldConverter conv = converterFor(NumberFormat.getNumberInstance(Locale.US));

        assertValue("75", conv.fromString("75"));
        assertValue("1234", conv.fromString("1,234"));
        assertValue("-75", conv.fromString("-75"));
        assertValue("1.5", conv.fromString("1.5"));
    }

    /**
     * DecimalFormat returns NaN / infinity as Double even with
     * setParseBigDecimal(true); the converter must treat them as a parse
     * failure (NumberFormatException), not blow up on the BigDecimal cast.
     */
    @Test
    public void infinitySymbolIsAParseFailureNotAClassCastException() {
        DecimalFieldConverter conv = converterFor(NumberFormat.getNumberInstance(Locale.US));
        assertThrows(NumberFormatException.class, () -> conv.fromString("∞"));
        assertThrows(NumberFormatException.class, () -> conv.fromString("-∞"));
    }

    /** The terminal parsers reject scientific notation across the typed converters. */
    @Test
    public void scientificNotationIsRejectedByAllTypedConverters() {
        DecimalFieldConverter plain = converterFor(null);
        assertThrows(NumberFormatException.class, () -> plain.fromString("1e5"));
        assertThrows(NumberFormatException.class, () -> plain.fromString("1E5"));

        IntegerFieldConverter integer = new IntegerFieldConverter();
        assertThrows(NumberFormatException.class, () -> integer.fromString("1e5"));

        LongFieldConverter longConverter = new LongFieldConverter();
        assertThrows(NumberFormatException.class, () -> longConverter.fromString("1e5"));

        DoubleFieldConverter dbl = new DoubleFieldConverter();
        assertThrows(NumberFormatException.class, () -> dbl.fromString("1e5"));
        assertThrows(NumberFormatException.class, () -> dbl.fromString("1E5"));
    }
}

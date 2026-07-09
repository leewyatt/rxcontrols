package io.github.leewyatt.rxcontrols.internal.number;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Parsing tests for {@link FormattedNumberFieldConverter}, focused on review
 * finding F1: under a currency / percent format the edit filter accepts a bare
 * (affix-less) numeric body, so the converter must accept it too instead of
 * throwing and silently dropping the committed value. Also pins the default
 * number-format path against regression.
 */
public class FormattedNumberFieldConverterTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    private static FormattedNumberFieldConverter converterFor(NumberFormat format) {
        RXFormattedNumberField field = new RXFormattedNumberField();
        field.setNumberFormat(format);
        return new FormattedNumberFieldConverter(field);
    }

    /** Asserts numeric (scale-agnostic) equality. */
    private static void assertValue(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    public void percentAcceptsBareBody() {
        FormattedNumberFieldConverter conv = converterFor(NumberFormat.getPercentInstance(Locale.US));

        // F1 core: bare digits typed into a percent field must commit, not vanish.
        assertValue("0.75", conv.fromString("75"));
        assertValue("0.07", conv.fromString("7"));
        assertValue("0.0075", conv.fromString("0.75"));

        // Explicit affix and signs still work (and now agree with the bare form).
        assertValue("0.75", conv.fromString("75%"));
        assertValue("-0.75", conv.fromString("-75"));
        assertValue("-0.75", conv.fromString("-75%"));

        // Round trip through the format is stable.
        assertValue("0.75", conv.fromString(conv.toString(new BigDecimal("0.75"))));
    }

    @Test
    public void currencyAcceptsBareBody() {
        FormattedNumberFieldConverter conv = converterFor(NumberFormat.getCurrencyInstance(Locale.US));

        assertValue("100", conv.fromString("100"));
        assertValue("100", conv.fromString("$100"));
        assertValue("100.5", conv.fromString("100.50"));
        assertValue("100.5", conv.fromString("$100.50"));
        assertValue("-100", conv.fromString("-100"));

        assertValue("100", conv.fromString(conv.toString(new BigDecimal("100"))));
    }

    @Test
    public void affixOrSignOnlyIsNoValue() {
        FormattedNumberFieldConverter pct = converterFor(NumberFormat.getPercentInstance(Locale.US));
        FormattedNumberFieldConverter cur = converterFor(NumberFormat.getCurrencyInstance(Locale.US));

        assertNull(pct.fromString("%"));
        assertNull(pct.fromString("-"));
        assertNull(cur.fromString("$"));
        assertNull(cur.fromString(""));
        assertNull(cur.fromString(null));
    }

    @Test
    public void garbageStillThrows() {
        FormattedNumberFieldConverter conv = converterFor(NumberFormat.getPercentInstance(Locale.US));
        assertThrows(NumberFormatException.class, () -> conv.fromString("abc"));
    }

    @Test
    public void defaultNumberFormatUnchanged() {
        FormattedNumberFieldConverter conv = converterFor(NumberFormat.getNumberInstance(Locale.US));

        assertValue("75", conv.fromString("75"));
        assertValue("1234", conv.fromString("1,234"));
        assertValue("-75", conv.fromString("-75"));
        assertValue("1.5", conv.fromString("1.5"));
    }
}

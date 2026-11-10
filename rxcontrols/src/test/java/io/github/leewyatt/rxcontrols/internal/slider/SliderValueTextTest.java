package io.github.leewyatt.rxcontrols.internal.slider;

import org.junit.jupiter.api.Test;

import java.text.DecimalFormatSymbols;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests {@link SliderValueText}.
 */
public class SliderValueTextTest {

    private static final String SEP = String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator());

    /** Verifies a 0..100 slider keeps whole numbers, rounding half up, without grouping. */
    @Test
    public void wholeNumberRangeShowsWholeNumbers() {
        assertEquals("42", SliderValueText.format(42.4, 0.0, 100.0, 25.0));
        assertEquals("43", SliderValueText.format(42.5, 0.0, 100.0, 25.0));
        assertEquals("12345", SliderValueText.format(12345.0, 0.0, 100000.0, 25000.0));
    }

    /** Verifies a 0..1 slider resolves hundredths and drops trailing zeros. */
    @Test
    public void unitRangeShowsHundredths() {
        assertEquals("0" + SEP + "37", SliderValueText.format(0.37, 0.0, 1.0, 25.0));
        assertEquals("0" + SEP + "5", SliderValueText.format(0.5, 0.0, 1.0, 25.0));
        assertEquals("1", SliderValueText.format(1.0, 0.0, 1.0, 25.0));
    }

    /** Verifies a finer tick unit than the range resolution shows its own decimals, capped at four. */
    @Test
    public void fineTickUnitShowsItsDecimals() {
        assertEquals("0" + SEP + "125", SliderValueText.format(0.125, 0.0, 10.0, 0.125));
        assertEquals("3" + SEP + "8", SliderValueText.format(3.75, 0.0, 10.0, 1.0));
        assertEquals("0" + SEP + "3333", SliderValueText.format(1.0 / 3.0, 0.0, 10.0, 1.0 / 3.0));
    }

    /** Verifies an empty or invalid range falls back to whole numbers. */
    @Test
    public void degenerateRangeShowsWholeNumbers() {
        assertEquals("5", SliderValueText.format(5.37, 5.0, 5.0, 1.0));
        assertEquals("5", SliderValueText.format(5.37, 5.0, 1.0, 1.0));
        assertEquals("5", SliderValueText.format(5.37, 0.0, Double.NaN, 1.0));
    }
}

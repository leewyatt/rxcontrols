package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts.ParseResult;
import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Default formatting and strict parsing of {@link DateTimeTexts}.
 */
public class DateTimeTextsTest {

    /**
     * Verifies the localized short date pattern is widened to a four-digit year.
     */
    @Test
    public void datePatternUsesFourDigitYear() {
        StringConverter<LocalDate> us = DateTimeTexts.dateConverter(Locale.US);
        assertEquals("11/14/2026", us.toString(LocalDate.of(2026, 11, 14)));
        assertEquals(LocalDate.of(2026, 3, 5), us.fromString("3/5/2026"));
        StringConverter<LocalDate> german = DateTimeTexts.dateConverter(Locale.GERMANY);
        assertEquals("05.03.2026", german.toString(LocalDate.of(2026, 3, 5)));
        assertEquals(LocalDate.of(2026, 3, 5), german.fromString("5.3.2026"));
    }

    /**
     * Verifies parsing is strict, blank text is empty, and garbage is invalid.
     */
    @Test
    public void parseClassifiesText() {
        StringConverter<LocalDate> converter = DateTimeTexts.dateConverter(Locale.US);
        assertEquals(ParseResult.Status.EMPTY, DateTimeTexts.parse(converter, "  ").status());
        assertEquals(ParseResult.Status.INVALID, DateTimeTexts.parse(converter, "2/30/2026").status());
        assertEquals(ParseResult.Status.INVALID, DateTimeTexts.parse(converter, "11/14/").status());
        assertEquals(LocalDate.of(2026, 2, 28), DateTimeTexts.parse(converter, "2/28/2026").value());
    }

    /**
     * Verifies time patterns follow the seconds and 12-hour options.
     */
    @Test
    public void timeConverterFollowsOptions() {
        LocalTime time = LocalTime.of(14, 5, 9);
        assertEquals("14:05", DateTimeTexts.timeConverter(Locale.US, false, false).toString(time));
        assertEquals("02:05:09 PM", DateTimeTexts.timeConverter(Locale.US, true, true).toString(time));
        assertEquals(LocalTime.of(9, 30),
                DateTimeTexts.timeConverter(Locale.US, false, false).fromString("9:30"));
        assertEquals(LocalTime.of(21, 30),
                DateTimeTexts.timeConverter(Locale.US, false, true).fromString("9:30 pm"));
    }

    /**
     * Verifies the period converters round-trip and null maps to empty text.
     */
    @Test
    public void periodConvertersRoundTrip() {
        StringConverter<YearMonth> months = DateTimeTexts.yearMonthConverter(Locale.US);
        assertEquals("2026-03", months.toString(YearMonth.of(2026, 3)));
        assertEquals(YearMonth.of(2026, 3), months.fromString("2026-3"));
        StringConverter<RXYearQuarter> quarters = DateTimeTexts.yearQuarterConverter();
        assertEquals(RXYearQuarter.of(2026, 2), quarters.fromString("2026-Q2"));
        assertEquals("", quarters.toString(null));
        assertNull(quarters.fromString(""));
    }
}

package io.github.leewyatt.rxcontrols;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value semantics of {@link RXMonthRange}, {@link RXQuarterRange} and {@link RXYearRange}.
 */
public class RXPeriodRangeTest {

    /**
     * Verifies a month range validates, orders and measures its endpoints.
     */
    @Test
    public void monthRangeSemantics() {
        YearMonth start = YearMonth.of(2026, 1);
        YearMonth end = YearMonth.of(2027, 8);
        assertThrows(NullPointerException.class, () -> RXMonthRange.of(null, end));
        assertThrows(NullPointerException.class, () -> RXMonthRange.of(start, null));
        assertThrows(IllegalArgumentException.class, () -> RXMonthRange.of(end, start));
        RXMonthRange range = RXMonthRange.ofOrdered(end, start);
        assertEquals(RXMonthRange.of(start, end), range);
        assertTrue(range.contains(start));
        assertTrue(range.contains(end));
        assertFalse(range.contains(YearMonth.of(2027, 9)));
        assertFalse(range.contains(null));
        assertEquals(20, range.lengthInMonths());
        assertEquals(1, RXMonthRange.of(start, start).lengthInMonths());
        assertEquals(RXDateRange.of(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 8, 31)), range.toDateRange());
        assertEquals("2026-01/2027-08", range.toString());
    }

    /**
     * Verifies a quarter range validates, orders and measures its endpoints.
     */
    @Test
    public void quarterRangeSemantics() {
        RXYearQuarter start = RXYearQuarter.of(2024, 1);
        RXYearQuarter end = RXYearQuarter.of(2025, 3);
        assertThrows(NullPointerException.class, () -> RXQuarterRange.of(null, end));
        assertThrows(NullPointerException.class, () -> RXQuarterRange.of(start, null));
        assertThrows(IllegalArgumentException.class, () -> RXQuarterRange.of(end, start));
        RXQuarterRange range = RXQuarterRange.ofOrdered(end, start);
        assertEquals(RXQuarterRange.of(start, end), range);
        assertTrue(range.contains(start));
        assertTrue(range.contains(end));
        assertFalse(range.contains(RXYearQuarter.of(2025, 4)));
        assertFalse(range.contains(null));
        assertEquals(7, range.lengthInQuarters());
        assertEquals(1, RXQuarterRange.of(start, start).lengthInQuarters());
        assertEquals(RXDateRange.of(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 9, 30)), range.toDateRange());
        assertEquals("2024-Q1/2025-Q3", range.toString());
    }

    /**
     * Verifies a year range validates, orders and measures its endpoints.
     */
    @Test
    public void yearRangeSemantics() {
        Year start = Year.of(2020);
        Year end = Year.of(2033);
        assertThrows(NullPointerException.class, () -> RXYearRange.of(null, end));
        assertThrows(NullPointerException.class, () -> RXYearRange.of(start, null));
        assertThrows(IllegalArgumentException.class, () -> RXYearRange.of(end, start));
        RXYearRange range = RXYearRange.ofOrdered(end, start);
        assertEquals(RXYearRange.of(start, end), range);
        assertTrue(range.contains(start));
        assertTrue(range.contains(end));
        assertFalse(range.contains(Year.of(2034)));
        assertFalse(range.contains(null));
        assertEquals(14, range.lengthInYears());
        assertEquals(1, RXYearRange.of(start, start).lengthInYears());
        assertEquals(RXDateRange.of(LocalDate.of(2020, 1, 1), LocalDate.of(2033, 12, 31)), range.toDateRange());
        assertEquals("2020/2033", range.toString());
    }
}

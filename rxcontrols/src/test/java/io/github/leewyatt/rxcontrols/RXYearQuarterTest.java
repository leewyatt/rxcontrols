package io.github.leewyatt.rxcontrols;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value semantics of {@link RXYearQuarter}.
 */
public class RXYearQuarterTest {

    /**
     * Verifies out-of-range quarters are rejected.
     */
    @Test
    public void rejectsInvalidQuarter() {
        assertThrows(IllegalArgumentException.class, () -> RXYearQuarter.of(2026, 0));
        assertThrows(IllegalArgumentException.class, () -> RXYearQuarter.of(2026, 5));
    }

    /**
     * Verifies quarter boundaries and membership.
     */
    @Test
    public void boundariesAndContains() {
        RXYearQuarter q4 = RXYearQuarter.of(2026, 4);
        assertEquals(LocalDate.of(2026, 10, 1), q4.firstDay());
        assertEquals(LocalDate.of(2026, 12, 31), q4.lastDay());
        assertEquals(LocalDate.of(2024, 3, 31), RXYearQuarter.of(2024, 1).lastDay());
        assertTrue(q4.contains(LocalDate.of(2026, 11, 14)));
        assertFalse(q4.contains(LocalDate.of(2026, 9, 30)));
        assertEquals(q4, RXYearQuarter.from(LocalDate.of(2026, 12, 31)));
    }

    /**
     * Verifies quarter arithmetic crosses year boundaries in both directions.
     */
    @Test
    public void arithmeticCrossesYears() {
        assertEquals(RXYearQuarter.of(2027, 1), RXYearQuarter.of(2026, 4).plusQuarters(1));
        assertEquals(RXYearQuarter.of(2025, 4), RXYearQuarter.of(2026, 1).minusQuarters(1));
        assertEquals(RXYearQuarter.of(2024, 3), RXYearQuarter.of(2026, 3).plusQuarters(-8));
        assertTrue(RXYearQuarter.of(2026, 1).compareTo(RXYearQuarter.of(2025, 4)) > 0);
    }

    /**
     * Verifies text round-trips and malformed text is rejected.
     */
    @Test
    public void textRoundTrip() {
        RXYearQuarter value = RXYearQuarter.of(2026, 3);
        assertEquals("2026-Q3", value.toString());
        assertEquals(value, RXYearQuarter.parse("2026-Q3"));
        assertThrows(DateTimeParseException.class, () -> RXYearQuarter.parse("2026-Q5"));
        assertThrows(DateTimeParseException.class, () -> RXYearQuarter.parse("2026/3"));
    }

    /**
     * Verifies now() reads the given clock.
     */
    @Test
    public void nowReadsClock() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 5, 20).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        assertEquals(RXYearQuarter.of(2026, 2), RXYearQuarter.now(clock));
    }
}

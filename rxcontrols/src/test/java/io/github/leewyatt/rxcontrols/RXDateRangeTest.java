package io.github.leewyatt.rxcontrols;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value semantics of {@link RXDateRange}, {@link RXDateTimeRange} and {@link RXTimeRange}.
 */
public class RXDateRangeTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);
    private static final LocalDate NOV_10 = LocalDate.of(2026, 11, 10);

    /**
     * Verifies endpoints are validated for null and order.
     */
    @Test
    public void rejectsNullAndReversedEndpoints() {
        assertThrows(NullPointerException.class, () -> RXDateRange.of(null, NOV_1));
        assertThrows(NullPointerException.class, () -> RXDateRange.of(NOV_1, null));
        assertThrows(IllegalArgumentException.class, () -> RXDateRange.of(NOV_10, NOV_1));
    }

    /**
     * Verifies ofOrdered swaps reversed endpoints.
     */
    @Test
    public void ofOrderedSwapsReversedEndpoints() {
        assertEquals(RXDateRange.of(NOV_1, NOV_10), RXDateRange.ofOrdered(NOV_10, NOV_1));
        assertEquals(RXDateRange.of(NOV_1, NOV_1), RXDateRange.ofOrdered(NOV_1, NOV_1));
    }

    /**
     * Verifies containment is inclusive and the length counts both endpoints.
     */
    @Test
    public void containsAndLengthAreInclusive() {
        RXDateRange range = RXDateRange.of(NOV_1, NOV_10);
        assertTrue(range.contains(NOV_1));
        assertTrue(range.contains(NOV_10));
        assertFalse(range.contains(NOV_10.plusDays(1)));
        assertFalse(range.contains(null));
        assertEquals(10, range.lengthInDays());
        assertEquals(1, RXDateRange.of(NOV_1, NOV_1).lengthInDays());
        assertEquals("2026-11-01/2026-11-10", range.toString());
    }

    /**
     * Verifies the date-time range validates, orders and measures its endpoints.
     */
    @Test
    public void dateTimeRangeSemantics() {
        LocalDateTime nine = NOV_1.atTime(9, 0);
        LocalDateTime five = NOV_1.atTime(17, 30);
        assertThrows(IllegalArgumentException.class, () -> RXDateTimeRange.of(five, nine));
        RXDateTimeRange range = RXDateTimeRange.ofOrdered(five, nine);
        assertEquals(nine, range.start());
        assertTrue(range.contains(five));
        assertFalse(range.contains(five.plusSeconds(1)));
        assertEquals(Duration.ofMinutes(510), range.duration());
    }

    /**
     * Verifies a time range with its start after its end runs past midnight.
     */
    @Test
    public void timeRangePastMidnight() {
        RXTimeRange night = RXTimeRange.of(LocalTime.of(22, 0), LocalTime.of(6, 0));
        assertEquals(LocalTime.of(22, 0), night.start());
        assertTrue(night.contains(LocalTime.of(23, 30)));
        assertTrue(night.contains(LocalTime.MIDNIGHT));
        assertTrue(night.contains(LocalTime.of(6, 0)));
        assertFalse(night.contains(LocalTime.of(12, 0)));
        assertEquals(Duration.ofHours(8), night.duration());
        assertEquals("22:00/06:00", night.toString());
    }

    /**
     * Verifies the time range validates, orders and measures its endpoints.
     */
    @Test
    public void timeRangeSemantics() {
        LocalTime nine = LocalTime.of(9, 0);
        LocalTime five = LocalTime.of(17, 30);
        assertThrows(NullPointerException.class, () -> RXTimeRange.of(nine, null));
        RXTimeRange range = RXTimeRange.ofOrdered(five, nine);
        assertEquals(RXTimeRange.of(nine, five), range);
        assertTrue(range.contains(nine));
        assertTrue(range.contains(five));
        assertFalse(range.contains(five.plusSeconds(1)));
        assertFalse(range.contains(null));
        assertEquals(Duration.ofMinutes(510), range.duration());
        assertEquals(Duration.ZERO, RXTimeRange.of(nine, nine).duration());
        assertEquals("09:00/17:30", range.toString());
    }
}

package io.github.leewyatt.rxcontrols;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value semantics of {@link RXDateRange} and {@link RXDateTimeRange}.
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
}

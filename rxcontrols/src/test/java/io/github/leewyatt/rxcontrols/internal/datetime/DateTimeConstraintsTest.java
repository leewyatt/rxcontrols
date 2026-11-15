package io.github.leewyatt.rxcontrols.internal.datetime;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bound-day and acceptance rules of {@link DateTimeConstraints}.
 */
public class DateTimeConstraintsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    /**
     * Verifies a bound day without a time on the step grid stays selectable and takes the bound's time.
     */
    @Test
    public void boundDayWithoutGridTimeTakesBound() {
        DateTimeConstraints rules = new DateTimeConstraints(DAY.atTime(23, 50), null, null, null, 1, 15, 1, false);
        assertTrue(rules.isDateSelectable(DAY));
        assertEquals(DAY.atTime(23, 50), rules.combine(DAY, LocalTime.of(9, 0)));
        assertTrue(rules.isAcceptable(DAY.atTime(23, 55)));
        assertFalse(rules.isAcceptable(DAY.atTime(23, 45)));
    }

    /**
     * Verifies the time taken on such a day has the precision of the columns and passes the time filter.
     */
    @Test
    public void boundDayTimeFollowsPrecisionAndFilter() {
        LocalDateTime min = DAY.atTime(23, 50, 30);
        DateTimeConstraints minutes = new DateTimeConstraints(min, null, null, null, 1, 15, 1, false);
        assertEquals(DAY.atTime(23, 51), minutes.combine(DAY, LocalTime.NOON));
        DateTimeConstraints seconds = new DateTimeConstraints(min, null, null, null, 1, 15, 1, true);
        assertEquals(min, seconds.combine(DAY, LocalTime.NOON));
        DateTimeConstraints filtered = new DateTimeConstraints(min, null, null,
                time -> time.getMinute() >= 55, 1, 15, 1, false);
        assertEquals(DAY.atTime(23, 55), filtered.combine(DAY, LocalTime.NOON));
    }

    /**
     * Verifies the upper bound's own day takes the acceptable time nearest below the bound.
     */
    @Test
    public void upperBoundDayTakesTimeBelowBound() {
        DateTimeConstraints rules = new DateTimeConstraints(null, DAY.atTime(5, 30), null,
                time -> time.getHour() != 0 && time.getMinute() != 30, 6, 1, 1, false);
        assertTrue(rules.isDateSelectable(DAY));
        assertEquals(DAY.atTime(5, 29), rules.combine(DAY, LocalTime.NOON));
    }

    /**
     * Verifies a bound day without any acceptable time is not selectable.
     */
    @Test
    public void boundDayWithoutAcceptableTimeIsNotSelectable() {
        DateTimeConstraints filtered = new DateTimeConstraints(DAY.atTime(23, 50), null, null,
                time -> time.getHour() != 23, 1, 15, 1, false);
        assertFalse(filtered.isDateSelectable(DAY));
        assertTrue(filtered.isDateSelectable(DAY.plusDays(1)));
        DateTimeConstraints pastLastMinute = new DateTimeConstraints(DAY.atTime(23, 59, 30), null, null, null,
                1, 15, 1, false);
        assertFalse(pastLastMinute.isDateSelectable(DAY));
    }

    /**
     * Verifies a bound day's selectability is computed once per snapshot.
     */
    @Test
    public void boundDaySelectabilityIsComputedOnce() {
        AtomicInteger calls = new AtomicInteger();
        DateTimeConstraints rules = new DateTimeConstraints(DAY.atTime(9, 30), DAY.plusDays(3).atTime(17, 0), null,
                time -> {
                    calls.incrementAndGet();
                    return time.getHour() >= 12;
                }, 1, 1, 1, false);
        assertTrue(rules.isDateSelectable(DAY));
        assertTrue(rules.isDateSelectable(DAY.plusDays(3)));
        int first = calls.get();
        assertTrue(rules.isDateSelectable(DAY));
        assertTrue(rules.isDateSelectable(DAY.plusDays(3)));
        assertEquals(first, calls.get());
    }

    /**
     * Verifies both filters and the bounds decide acceptance.
     */
    @Test
    public void filtersAndBoundsDecideAcceptance() {
        DateTimeConstraints rules = new DateTimeConstraints(null, DAY.atTime(18, 0),
                date -> date.getDayOfWeek() != DayOfWeek.SUNDAY, time -> time.getHour() >= 9, 1, 1, 1, false);
        assertTrue(rules.isAcceptable(DAY.atTime(9, 7)));
        assertFalse(rules.isAcceptable(DAY.atTime(8, 59)));
        assertFalse(rules.isAcceptable(DAY.atTime(18, 1)));
        assertFalse(rules.isAcceptable(LocalDateTime.of(2026, 11, 8, 12, 0)));
        assertFalse(rules.isAcceptable(null));
    }
}

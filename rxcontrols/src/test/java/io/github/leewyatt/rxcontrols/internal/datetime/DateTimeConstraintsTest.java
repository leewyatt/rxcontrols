package io.github.leewyatt.rxcontrols.internal.datetime;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bound-day and acceptance rules of {@link DateTimeConstraints}.
 */
public class DateTimeConstraintsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 14);

    /**
     * Verifies the steps make a bound day unpickable without rejecting values on it.
     */
    @Test
    public void stepsDisableBoundDayButNotItsValues() {
        DateTimeConstraints rules = new DateTimeConstraints(DAY.atTime(23, 50), null, null, null, 1, 15, 1, false);
        assertFalse(rules.isDateSelectable(DAY));
        assertTrue(rules.isAcceptable(DAY.atTime(23, 55)));
        assertFalse(rules.isAcceptable(DAY.atTime(23, 45)));
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

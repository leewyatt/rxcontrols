package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints.Unit;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Step grid, availability and snapping rules of {@link TimeConstraints}.
 */
public class TimeConstraintsTest {

    /**
     * Verifies the step grid and hidden seconds restrict selectable times but not acceptable ones.
     */
    @Test
    public void stepsShapeSelectableButNotAcceptable() {
        TimeConstraints rules = new TimeConstraints(null, null, null, 1, 15, 1, false);
        assertTrue(rules.isAllowed(LocalTime.of(9, 45)));
        assertFalse(rules.isAllowed(LocalTime.of(9, 40)));
        assertFalse(rules.isAllowed(LocalTime.of(9, 45, 10)));
        assertTrue(rules.isAcceptable(LocalTime.of(9, 40, 10)));
    }

    /**
     * Verifies an hour stays available while any of its minutes is selectable.
     */
    @Test
    public void hourAvailableWhileAnyMinuteIs() {
        TimeConstraints rules = new TimeConstraints(LocalTime.of(9, 50), LocalTime.of(17, 10), null, 1, 1, 1, false);
        assertFalse(rules.isHourAvailable(8));
        assertTrue(rules.isHourAvailable(9));
        assertFalse(rules.isMinuteAvailable(9, 49));
        assertTrue(rules.isMinuteAvailable(17, 10));
        assertFalse(rules.isHourAvailable(18));
    }

    /**
     * Verifies snapping keeps the picked unit and moves the others to the nearest selectable time.
     */
    @Test
    public void snapKeepsPickedUnit() {
        TimeConstraints rules = new TimeConstraints(LocalTime.of(9, 30), null, null, 1, 15, 1, false);
        assertEquals(LocalTime.of(9, 30), rules.snap(LocalTime.of(9, 0), Unit.HOUR));
        assertEquals(LocalTime.of(10, 15), rules.snap(LocalTime.of(10, 15), Unit.MINUTE));
        assertEquals(LocalTime.of(9, 30), rules.snap(LocalTime.of(3, 0), null));
        TimeConstraints none = new TimeConstraints(null, null, time -> false, 1, 1, 1, false);
        assertNull(none.snap(LocalTime.NOON, null));
    }

    /**
     * Verifies truncation onto the step grid.
     */
    @Test
    public void truncateOntoGrid() {
        TimeConstraints rules = new TimeConstraints(null, null, null, 2, 15, 10, true);
        assertEquals(LocalTime.of(14, 30, 50), rules.truncate(LocalTime.of(15, 44, 59, 999)));
    }
}

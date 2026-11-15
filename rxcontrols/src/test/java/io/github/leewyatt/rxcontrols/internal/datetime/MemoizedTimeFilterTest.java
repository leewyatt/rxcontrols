package io.github.leewyatt.rxcontrols.internal.datetime;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Remembered answers and reuse rules of {@link MemoizedTimeFilter}.
 */
public class MemoizedTimeFilterTest {

    /**
     * Verifies an accepted and a rejected time are each asked once.
     */
    @Test
    public void asksOncePerTime() {
        AtomicInteger calls = new AtomicInteger();
        Predicate<LocalTime> filter = MemoizedTimeFilter.of(time -> {
            calls.incrementAndGet();
            return time.getHour() != 12;
        }, null);
        assertTrue(filter.test(LocalTime.of(9, 30)));
        assertTrue(filter.test(LocalTime.of(9, 30)));
        assertFalse(filter.test(LocalTime.of(12, 0)));
        assertFalse(filter.test(LocalTime.of(12, 0)));
        assertEquals(2, calls.get());
    }

    /**
     * Verifies a time with a fraction of a second is asked every time.
     */
    @Test
    public void fractionalTimesAreNotRemembered() {
        AtomicInteger calls = new AtomicInteger();
        Predicate<LocalTime> filter = MemoizedTimeFilter.of(time -> {
            calls.incrementAndGet();
            return time.getNano() == 0;
        }, null);
        assertFalse(filter.test(LocalTime.of(9, 30, 0, 500)));
        assertFalse(filter.test(LocalTime.of(9, 30, 0, 500)));
        assertTrue(filter.test(LocalTime.of(9, 30)));
        assertEquals(3, calls.get());
    }

    /**
     * Verifies the previous instance is reused for the same filter only.
     */
    @Test
    public void reusedWhileFilterIsTheSame() {
        Predicate<LocalTime> source = time -> true;
        Predicate<LocalTime> first = MemoizedTimeFilter.of(source, null);
        assertSame(first, MemoizedTimeFilter.of(source, first));
        assertNotSame(first, MemoizedTimeFilter.of(time -> true, first));
        assertNull(MemoizedTimeFilter.of(null, first));
    }
}

package io.github.leewyatt.rxcontrols;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Immutable, closed range of times of day, used as the value of
 * {@link RXTimeRangePicker}.
 *
 * <p>Both endpoints are always present and inclusive. A {@code start} after the
 * {@code end} is a range that runs past midnight into the next day, such as
 * 22:00 to 06:00; equal endpoints are a single time. Like {@link RXDateRange}, a
 * half-picked range is an editing state, never a value.
 *
 * @param start the first time of the range, inclusive
 * @param end   the last time of the range, inclusive
 */
public record RXTimeRange(LocalTime start, LocalTime end) {

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first time of the range, inclusive
     * @param end   the last time of the range, inclusive
     * @throws NullPointerException if either endpoint is {@code null}
     */
    public RXTimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
    }

    /**
     * Creates a range from the given endpoints, kept as given.
     *
     * @param start the first time, inclusive
     * @param end   the last time, inclusive; before {@code start} for a range that runs past midnight
     * @return the range
     * @throws NullPointerException if either endpoint is {@code null}
     */
    public static RXTimeRange of(LocalTime start, LocalTime end) {
        return new RXTimeRange(start, end);
    }

    /**
     * Creates a range within one day from two times given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range from the earlier to the later time
     * @throws NullPointerException if either time is {@code null}
     */
    public static RXTimeRange ofOrdered(LocalTime first, LocalTime second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.isBefore(first) ? new RXTimeRange(second, first) : new RXTimeRange(first, second);
    }

    /**
     * Returns whether the time lies within this range, endpoints included.
     *
     * @param time the time to test
     * @return {@code true} if the time is reached going from {@code start} to
     *         {@code end}, past midnight if the range runs past it; {@code false} for {@code null}
     */
    public boolean contains(LocalTime time) {
        if (time == null) {
            return false;
        }
        if (end.isBefore(start)) {
            return !time.isBefore(start) || !time.isAfter(end);
        }
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * Returns the length of this range.
     *
     * @return the duration from {@code start} to {@code end}, past midnight if the
     *         range runs past it; never negative
     */
    public Duration duration() {
        Duration length = Duration.between(start, end);
        return length.isNegative() ? length.plusDays(1) : length;
    }

    /**
     * Returns the ISO-8601 interval form, for example {@code 09:00/17:30}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

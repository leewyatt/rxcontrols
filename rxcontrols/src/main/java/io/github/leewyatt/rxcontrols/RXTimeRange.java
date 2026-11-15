package io.github.leewyatt.rxcontrols;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Immutable, closed range of times of day, used as the value of
 * {@link RXTimeRangePicker}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never
 * comes after {@code end}: the range lies within one day and does not run past
 * midnight. Like {@link RXDateRange}, a half-picked range is an editing state,
 * never a value.
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
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXTimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first time, inclusive
     * @param end   the last time, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXTimeRange of(LocalTime start, LocalTime end) {
        return new RXTimeRange(start, end);
    }

    /**
     * Creates a range from two times given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both times
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
     * @return {@code true} if {@code start <= time <= end}; {@code false} for {@code null}
     */
    public boolean contains(LocalTime time) {
        return time != null && !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * Returns the length of this range.
     *
     * @return the duration from {@code start} to {@code end}, never negative
     */
    public Duration duration() {
        return Duration.between(start, end);
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

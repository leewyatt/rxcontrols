package io.github.leewyatt.rxcontrols;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable, closed range of local date-times, used as the value of
 * {@link RXDateTimeRangePicker} and {@link RXDateTimeRangeView}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never
 * comes after {@code end}. Like {@link RXDateRange}, a half-picked range is an
 * editing state, never a value. The endpoints carry no time zone: the range
 * describes wall-clock times, not instants, so it does not account for daylight
 * saving transitions.
 *
 * @param start the first moment of the range, inclusive
 * @param end   the last moment of the range, inclusive
 */
public record RXDateTimeRange(LocalDateTime start, LocalDateTime end) {

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first moment of the range, inclusive
     * @param end   the last moment of the range, inclusive
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXDateTimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first moment, inclusive
     * @param end   the last moment, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXDateTimeRange of(LocalDateTime start, LocalDateTime end) {
        return new RXDateTimeRange(start, end);
    }

    /**
     * Creates a range from two date-times given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both date-times
     * @throws NullPointerException if either date-time is {@code null}
     */
    public static RXDateTimeRange ofOrdered(LocalDateTime first, LocalDateTime second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.isBefore(first) ? new RXDateTimeRange(second, first) : new RXDateTimeRange(first, second);
    }

    /**
     * Returns whether the date-time lies within this range, endpoints included.
     *
     * @param dateTime the date-time to test
     * @return {@code true} if {@code start <= dateTime <= end}; {@code false} for {@code null}
     */
    public boolean contains(LocalDateTime dateTime) {
        return dateTime != null && !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    /**
     * Returns the wall-clock length of this range.
     *
     * @return the duration from {@code start} to {@code end}, never negative
     */
    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Returns the ISO-8601 interval form, for example
     * {@code 2026-11-01T09:00/2026-11-01T17:30}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

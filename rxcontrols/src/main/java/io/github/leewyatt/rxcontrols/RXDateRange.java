package io.github.leewyatt.rxcontrols;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable, closed range of local dates, used as the value of
 * {@link RXDateRangePicker} and {@link RXDateRangeView}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never
 * comes after {@code end}; a single-day range has {@code start.equals(end)}. A
 * range that is still being picked (only one endpoint chosen) is an editing state
 * of the control, never a value: a range-valued property is either {@code null}
 * or a complete instance of this type, so listeners never observe a half-updated
 * range.
 *
 * @param start the first day of the range, inclusive
 * @param end   the last day of the range, inclusive
 */
public record RXDateRange(LocalDate start, LocalDate end) {

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first day of the range, inclusive
     * @param end   the last day of the range, inclusive
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXDateRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first day, inclusive
     * @param end   the last day, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXDateRange of(LocalDate start, LocalDate end) {
        return new RXDateRange(start, end);
    }

    /**
     * Creates a range from two dates given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both dates
     * @throws NullPointerException if either date is {@code null}
     */
    public static RXDateRange ofOrdered(LocalDate first, LocalDate second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.isBefore(first) ? new RXDateRange(second, first) : new RXDateRange(first, second);
    }

    /**
     * Returns whether the date lies within this range, endpoints included.
     *
     * @param date the date to test
     * @return {@code true} if {@code start <= date <= end}; {@code false} for {@code null}
     */
    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * Returns the number of days in this range, endpoints included.
     *
     * @return the day count, at least {@code 1}
     */
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * Returns the ISO-8601 interval form, for example {@code 2026-11-01/2026-11-10}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

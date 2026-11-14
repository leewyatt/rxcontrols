package io.github.leewyatt.rxcontrols;

import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable, closed range of years, used as the value of {@link RXYearRangePicker}
 * and {@link RXYearRangeView}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never comes
 * after {@code end}; a single-year range has {@code start.equals(end)}. A range that
 * is still being picked is an editing state of the control, never a value.
 *
 * @param start the first year of the range, inclusive
 * @param end   the last year of the range, inclusive
 */
public record RXYearRange(Year start, Year end) {

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first year of the range, inclusive
     * @param end   the last year of the range, inclusive
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXYearRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first year, inclusive
     * @param end   the last year, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXYearRange of(Year start, Year end) {
        return new RXYearRange(start, end);
    }

    /**
     * Creates a range from two years given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both years
     * @throws NullPointerException if either year is {@code null}
     */
    public static RXYearRange ofOrdered(Year first, Year second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.isBefore(first) ? new RXYearRange(second, first) : new RXYearRange(first, second);
    }

    /**
     * Returns whether the year lies within this range, endpoints included.
     *
     * @param year the year to test
     * @return {@code true} if {@code start <= year <= end}; {@code false} for {@code null}
     */
    public boolean contains(Year year) {
        return year != null && !year.isBefore(start) && !year.isAfter(end);
    }

    /**
     * Returns the number of years in this range, endpoints included.
     *
     * @return the year count, at least {@code 1}
     */
    public long lengthInYears() {
        return ChronoUnit.YEARS.between(start, end) + 1;
    }

    /**
     * Returns the days this range covers: January 1 of {@code start} to December 31
     * of {@code end}.
     *
     * @return the date range
     */
    public RXDateRange toDateRange() {
        return new RXDateRange(start.atDay(1), end.atDay(end.length()));
    }

    /**
     * Returns the ISO-8601 interval form, for example {@code 2020/2033}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

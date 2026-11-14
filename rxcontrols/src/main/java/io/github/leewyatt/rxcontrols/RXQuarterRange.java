package io.github.leewyatt.rxcontrols;

import java.util.Objects;

/**
 * Immutable, closed range of calendar quarters, used as the value of
 * {@link RXQuarterRangePicker} and {@link RXQuarterRangeView}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never comes
 * after {@code end}; a single-quarter range has {@code start.equals(end)}. A range
 * that is still being picked is an editing state of the control, never a value.
 *
 * @param start the first quarter of the range, inclusive
 * @param end   the last quarter of the range, inclusive
 */
public record RXQuarterRange(RXYearQuarter start, RXYearQuarter end) {

    private static final long QUARTERS_PER_YEAR = 4;

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first quarter of the range, inclusive
     * @param end   the last quarter of the range, inclusive
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXQuarterRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.compareTo(start) < 0) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first quarter, inclusive
     * @param end   the last quarter, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXQuarterRange of(RXYearQuarter start, RXYearQuarter end) {
        return new RXQuarterRange(start, end);
    }

    /**
     * Creates a range from two quarters given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both quarters
     * @throws NullPointerException if either quarter is {@code null}
     */
    public static RXQuarterRange ofOrdered(RXYearQuarter first, RXYearQuarter second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.compareTo(first) < 0 ? new RXQuarterRange(second, first) : new RXQuarterRange(first, second);
    }

    /**
     * Returns whether the quarter lies within this range, endpoints included.
     *
     * @param quarter the quarter to test
     * @return {@code true} if {@code start <= quarter <= end}; {@code false} for {@code null}
     */
    public boolean contains(RXYearQuarter quarter) {
        return quarter != null && quarter.compareTo(start) >= 0 && quarter.compareTo(end) <= 0;
    }

    /**
     * Returns the number of quarters in this range, endpoints included.
     *
     * @return the quarter count, at least {@code 1}
     */
    public long lengthInQuarters() {
        return ((long) end.year() - start.year()) * QUARTERS_PER_YEAR + end.quarter() - start.quarter() + 1;
    }

    /**
     * Returns the days this range covers: the first day of {@code start} to the last
     * day of {@code end}.
     *
     * @return the date range
     */
    public RXDateRange toDateRange() {
        return new RXDateRange(start.firstDay(), end.lastDay());
    }

    /**
     * Returns the interval form, for example {@code 2024-Q1/2025-Q3}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

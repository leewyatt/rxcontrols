package io.github.leewyatt.rxcontrols;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Immutable, closed range of months, used as the value of {@link RXMonthRangePicker}
 * and {@link RXMonthRangeView}.
 *
 * <p>Both endpoints are always present and inclusive, and {@code start} never comes
 * after {@code end}; a single-month range has {@code start.equals(end)}. A range that
 * is still being picked is an editing state of the control, never a value.
 *
 * @param start the first month of the range, inclusive
 * @param end   the last month of the range, inclusive
 */
public record RXMonthRange(YearMonth start, YearMonth end) {

    /**
     * Creates a range, validating its endpoints.
     *
     * @param start the first month of the range, inclusive
     * @param end   the last month of the range, inclusive
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public RXMonthRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end " + end + " is before start " + start);
        }
    }

    /**
     * Creates a range from the given endpoints.
     *
     * @param start the first month, inclusive
     * @param end   the last month, inclusive
     * @return the range
     * @throws NullPointerException     if either endpoint is {@code null}
     * @throws IllegalArgumentException if {@code end} is before {@code start}
     */
    public static RXMonthRange of(YearMonth start, YearMonth end) {
        return new RXMonthRange(start, end);
    }

    /**
     * Creates a range from two months given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range spanning both months
     * @throws NullPointerException if either month is {@code null}
     */
    public static RXMonthRange ofOrdered(YearMonth first, YearMonth second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        return second.isBefore(first) ? new RXMonthRange(second, first) : new RXMonthRange(first, second);
    }

    /**
     * Returns whether the month lies within this range, endpoints included.
     *
     * @param month the month to test
     * @return {@code true} if {@code start <= month <= end}; {@code false} for {@code null}
     */
    public boolean contains(YearMonth month) {
        return month != null && !month.isBefore(start) && !month.isAfter(end);
    }

    /**
     * Returns the number of months in this range, endpoints included.
     *
     * @return the month count, at least {@code 1}
     */
    public long lengthInMonths() {
        return ChronoUnit.MONTHS.between(start, end) + 1;
    }

    /**
     * Returns the days this range covers: the first day of {@code start} to the last
     * day of {@code end}.
     *
     * @return the date range
     */
    public RXDateRange toDateRange() {
        return new RXDateRange(start.atDay(1), end.atEndOfMonth());
    }

    /**
     * Returns the ISO-8601 interval form, for example {@code 2026-01/2027-08}.
     *
     * @return the interval text
     */
    @Override
    public String toString() {
        return start + "/" + end;
    }
}

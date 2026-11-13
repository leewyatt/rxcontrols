package io.github.leewyatt.rxcontrols;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable calendar quarter of an ISO year, such as {@code 2026-Q1}, used as the
 * value of {@link RXQuarterPicker} and {@link RXQuarterView}.
 *
 * <p>Quarters are the natural calendar quarters: Q1 is January to March, Q2 April
 * to June, Q3 July to September and Q4 October to December. Fiscal quarters that
 * start in another month are not modeled by this type.
 *
 * @param year    the ISO year, within {@link Year#MIN_VALUE} and {@link Year#MAX_VALUE}
 * @param quarter the quarter of the year, from {@code 1} to {@code 4}
 */
public record RXYearQuarter(int year, int quarter) implements Comparable<RXYearQuarter> {

    private static final int MONTHS_PER_QUARTER = 3;
    private static final int QUARTERS_PER_YEAR = 4;
    private static final Pattern TEXT_PATTERN = Pattern.compile("([+-]?\\d{1,9})-Q([1-4])");

    /**
     * Creates a year-quarter, validating its fields.
     *
     * @param year    the ISO year
     * @param quarter the quarter, from {@code 1} to {@code 4}
     * @throws IllegalArgumentException if the year or quarter is out of range
     */
    public RXYearQuarter {
        if (year < Year.MIN_VALUE || year > Year.MAX_VALUE) {
            throw new IllegalArgumentException("year out of range: " + year);
        }
        if (quarter < 1 || quarter > QUARTERS_PER_YEAR) {
            throw new IllegalArgumentException("quarter must be 1-4: " + quarter);
        }
    }

    /**
     * Creates a year-quarter.
     *
     * @param year    the ISO year
     * @param quarter the quarter, from {@code 1} to {@code 4}
     * @return the year-quarter
     * @throws IllegalArgumentException if the year or quarter is out of range
     */
    public static RXYearQuarter of(int year, int quarter) {
        return new RXYearQuarter(year, quarter);
    }

    /**
     * Returns the quarter containing the given date.
     *
     * @param date the date
     * @return the quarter containing {@code date}
     * @throws NullPointerException if {@code date} is {@code null}
     */
    public static RXYearQuarter from(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return new RXYearQuarter(date.getYear(), date.get(IsoFields.QUARTER_OF_YEAR));
    }

    /**
     * Returns the current quarter according to the given clock.
     *
     * @param clock the clock supplying today's date
     * @return the current quarter
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    public static RXYearQuarter now(Clock clock) {
        return from(LocalDate.now(Objects.requireNonNull(clock, "clock")));
    }

    /**
     * Parses text in the {@link #toString()} form, such as {@code 2026-Q3}.
     *
     * @param text the text to parse
     * @return the parsed year-quarter
     * @throws NullPointerException   if {@code text} is {@code null}
     * @throws DateTimeParseException if the text is not a valid year-quarter
     */
    public static RXYearQuarter parse(CharSequence text) {
        Objects.requireNonNull(text, "text");
        Matcher matcher = TEXT_PATTERN.matcher(text);
        if (!matcher.matches()) {
            throw new DateTimeParseException("Text cannot be parsed as a year-quarter", text, 0);
        }
        try {
            return new RXYearQuarter(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        } catch (IllegalArgumentException ex) {
            throw new DateTimeParseException(ex.getMessage(), text, 0, ex);
        }
    }

    /**
     * Returns the first day of this quarter.
     *
     * @return the first day
     */
    public LocalDate firstDay() {
        return LocalDate.of(year, (quarter - 1) * MONTHS_PER_QUARTER + 1, 1);
    }

    /**
     * Returns the last day of this quarter.
     *
     * @return the last day
     */
    public LocalDate lastDay() {
        LocalDate lastMonthStart = LocalDate.of(year, quarter * MONTHS_PER_QUARTER, 1);
        return lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth());
    }

    /**
     * Returns whether the date falls within this quarter.
     *
     * @param date the date to test
     * @return {@code true} if the date belongs to this quarter; {@code false} for {@code null}
     */
    public boolean contains(LocalDate date) {
        return date != null && date.getYear() == year && date.get(IsoFields.QUARTER_OF_YEAR) == quarter;
    }

    /**
     * Returns this quarter shifted by the given number of quarters.
     *
     * @param quarters the quarters to add, may be negative
     * @return the shifted quarter
     * @throws IllegalArgumentException if the result is out of the supported year range
     */
    public RXYearQuarter plusQuarters(long quarters) {
        long index = (long) year * QUARTERS_PER_YEAR + (quarter - 1) + quarters;
        long newYear = Math.floorDiv(index, QUARTERS_PER_YEAR);
        if (newYear < Year.MIN_VALUE || newYear > Year.MAX_VALUE) {
            throw new IllegalArgumentException("year out of range: " + newYear);
        }
        return new RXYearQuarter((int) newYear, (int) Math.floorMod(index, QUARTERS_PER_YEAR) + 1);
    }

    /**
     * Returns this quarter shifted back by the given number of quarters.
     *
     * @param quarters the quarters to subtract, may be negative
     * @return the shifted quarter
     * @throws IllegalArgumentException if the result is out of the supported year range
     */
    public RXYearQuarter minusQuarters(long quarters) {
        return plusQuarters(-quarters);
    }

    /**
     * Compares quarters chronologically.
     *
     * @param other the quarter to compare to
     * @return negative, zero or positive as this quarter is before, equal to or after {@code other}
     */
    @Override
    public int compareTo(RXYearQuarter other) {
        int byYear = Integer.compare(year, other.year);
        return byYear != 0 ? byYear : Integer.compare(quarter, other.quarter);
    }

    /**
     * Returns the text form {@code yyyy-Qq}, for example {@code 2026-Q1}.
     *
     * @return the text form
     */
    @Override
    public String toString() {
        return year + "-Q" + quarter;
    }
}

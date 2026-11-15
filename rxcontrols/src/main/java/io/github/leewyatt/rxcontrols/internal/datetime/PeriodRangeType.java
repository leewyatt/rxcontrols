package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXMonthRange;
import io.github.leewyatt.rxcontrols.RXQuarterRange;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.RXYearRange;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Describes one kind of period range (weeks, months, quarters or years) to the skins
 * that share their logic: how a period maps to the days of the calendar panel and how
 * a range is taken apart and put together.
 *
 * @param <P> the period type
 * @param <R> the range type
 */
public final class PeriodRangeType<P extends Comparable<? super P>, R> {

    /** Ranges of months. */
    public static final PeriodRangeType<YearMonth, RXMonthRange> MONTH = new PeriodRangeType<>(
            CalendarPanel.Level.MONTH, month -> month.atDay(1), YearMonth::atEndOfMonth, YearMonth::from,
            RXMonthRange::start, RXMonthRange::end, RXMonthRange::ofOrdered);

    /** Ranges of quarters. */
    public static final PeriodRangeType<RXYearQuarter, RXQuarterRange> QUARTER = new PeriodRangeType<>(
            CalendarPanel.Level.QUARTER, RXYearQuarter::firstDay, RXYearQuarter::lastDay, RXYearQuarter::from,
            RXQuarterRange::start, RXQuarterRange::end, RXQuarterRange::ofOrdered);

    /** Ranges of years. */
    public static final PeriodRangeType<Year, RXYearRange> YEAR = new PeriodRangeType<>(
            CalendarPanel.Level.YEAR, year -> year.atDay(1), year -> year.atDay(year.length()), Year::from,
            RXYearRange::start, RXYearRange::end, RXYearRange::ofOrdered);

    /**
     * Creates the type for ranges of weeks. A week is the date of its first day, and
     * any other day stands for the week containing it; a range is an
     * {@link RXDateRange} from the first day of its first week to the last day of its
     * last week.
     *
     * @param rules supplies the week rules in effect; asked on every use
     * @return the type
     */
    public static PeriodRangeType<LocalDate, RXDateRange> weeks(Supplier<WeekFields> rules) {
        Function<LocalDate, LocalDate> weekStart = date -> TemporalRules.weekStart(date, rules.get());
        return new PeriodRangeType<>(CalendarPanel.Level.DAY, weekStart,
                week -> TemporalRules.weekEnd(week, rules.get()), weekStart,
                range -> weekStart.apply(range.start()), range -> weekStart.apply(range.end()),
                (first, second) -> {
                    RXDateRange ordered = RXDateRange.ofOrdered(first, second);
                    return RXDateRange.of(weekStart.apply(ordered.start()),
                            TemporalRules.weekEnd(ordered.end(), rules.get()));
                });
    }

    private final CalendarPanel.Level level;
    private final Function<P, LocalDate> firstDay;
    private final Function<P, LocalDate> lastDay;
    private final Function<LocalDate, P> periodOf;
    private final Function<R, P> start;
    private final Function<R, P> end;
    private final BiFunction<P, P, R> rangeOf;

    private PeriodRangeType(CalendarPanel.Level level, Function<P, LocalDate> firstDay,
                            Function<P, LocalDate> lastDay, Function<LocalDate, P> periodOf,
                            Function<R, P> start, Function<R, P> end, BiFunction<P, P, R> rangeOf) {
        this.level = level;
        this.firstDay = firstDay;
        this.lastDay = lastDay;
        this.periodOf = periodOf;
        this.start = start;
        this.end = end;
        this.rangeOf = rangeOf;
    }

    /**
     * Returns the panel level at which the periods are picked.
     *
     * @return the pick level
     */
    public CalendarPanel.Level level() {
        return level;
    }

    /**
     * Returns the first day of a period.
     *
     * @param period the period
     * @return its first day
     */
    public LocalDate firstDay(P period) {
        return firstDay.apply(period);
    }

    /**
     * Returns the last day of a period.
     *
     * @param period the period
     * @return its last day
     */
    public LocalDate lastDay(P period) {
        return lastDay.apply(period);
    }

    /**
     * Returns the period containing a date.
     *
     * @param date the date
     * @return the period
     */
    public P periodOf(LocalDate date) {
        return periodOf.apply(date);
    }

    /**
     * Returns the first period of a range.
     *
     * @param range the range
     * @return its start
     */
    public P start(R range) {
        return start.apply(range);
    }

    /**
     * Returns the last period of a range.
     *
     * @param range the range
     * @return its end
     */
    public P end(R range) {
        return end.apply(range);
    }

    /**
     * Creates the range spanning two periods given in either order.
     *
     * @param first  one endpoint
     * @param second the other endpoint
     * @return the range
     */
    public R rangeOf(P first, P second) {
        return rangeOf.apply(first, second);
    }
}

package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Combined date and time rules of the date-time views and pickers: optional inclusive
 * date-time bounds, a date filter, a time-of-day filter and the time steps. On the
 * first and last allowed day the time columns are further limited by the bounds' time,
 * so a bound of {@code 2026-11-14T15:30} keeps that day selectable while disabling
 * its earlier times. Such a day stays selectable while any time on it is acceptable:
 * where the step grid offers none, picking the day takes the acceptable time nearest
 * to the bound.
 *
 * <p>Instances are snapshots of the rules; build a new one when a property changes.
 * They remember whether each bound's own day offers a time, so calendar refreshes do
 * not rescan that day.
 */
public final class DateTimeConstraints {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_DAY = 24 * 60 * SECONDS_PER_MINUTE;

    private final LocalDateTime min;
    private final LocalDateTime max;
    private final Predicate<LocalDate> dateFilter;
    private final Predicate<LocalTime> timeFilter;
    private final int hourStep;
    private final int minuteStep;
    private final int secondStep;
    private final boolean showSeconds;
    /** Whether the lower / upper bound's own day has an acceptable time; {@code null} until asked. */
    private Boolean minDayHasTime;
    private Boolean maxDayHasTime;

    /**
     * Creates a constraint snapshot.
     *
     * @param min         the earliest date-time, or {@code null}
     * @param max         the latest date-time, or {@code null}
     * @param dateFilter  the date filter, or {@code null}
     * @param timeFilter  the time-of-day filter, or {@code null}
     * @param hourStep    the hour step
     * @param minuteStep  the minute step
     * @param secondStep  the second step
     * @param showSeconds whether seconds are selectable
     */
    public DateTimeConstraints(LocalDateTime min, LocalDateTime max, Predicate<LocalDate> dateFilter,
                               Predicate<LocalTime> timeFilter, int hourStep, int minuteStep, int secondStep,
                               boolean showSeconds) {
        this.min = min;
        this.max = max;
        this.dateFilter = dateFilter;
        this.timeFilter = timeFilter;
        this.hourStep = hourStep;
        this.minuteStep = minuteStep;
        this.secondStep = secondStep;
        this.showSeconds = showSeconds;
    }

    /**
     * Returns the earliest allowed date.
     *
     * @return the date of the lower bound, or {@code null}
     */
    public LocalDate minDate() {
        return min == null ? null : min.toLocalDate();
    }

    /**
     * Returns the latest allowed date.
     *
     * @return the date of the upper bound, or {@code null}
     */
    public LocalDate maxDate() {
        return max == null ? null : max.toLocalDate();
    }

    /**
     * Returns whether a day may be picked.
     *
     * @param date the date
     * @return {@code true} if within the bounds' dates, accepted by the date filter and,
     *         on a bound's own day, having at least one acceptable time
     */
    public boolean isDateSelectable(LocalDate date) {
        if (!TemporalRules.isSelectable(date, minDate(), maxDate(), dateFilter)) {
            return false;
        }
        // A bound's own day is selectable while some time on it is acceptable, on the
        // step grid or off it (a minimum of 23:50 with a 15-minute step leaves 23:50).
        if (date.equals(minDate())) {
            if (minDayHasTime == null) {
                minDayHasTime = hasTimeOn(date);
            }
            return minDayHasTime;
        }
        if (date.equals(maxDate())) {
            if (maxDayHasTime == null) {
                maxDayHasTime = hasTimeOn(date);
            }
            return maxDayHasTime;
        }
        return true;
    }

    private boolean hasTimeOn(LocalDate date) {
        return timesOn(date).snap(LocalTime.MIDNIGHT, null) != null || boundTimeOn(date) != null;
    }

    /**
     * Returns the time nearest to a bound on that bound's own day that the bounds and
     * the time filter accept, at the precision the columns show. It stands in for the
     * day where the step grid offers no time.
     *
     * @param date the day
     * @return the time, or {@code null} if the day is no bound's own or has no such time
     */
    private LocalTime boundTimeOn(LocalDate date) {
        boolean minDay = date.equals(minDate());
        boolean maxDay = date.equals(maxDate());
        if (!minDay && !maxDay) {
            return null;
        }
        int unit = showSeconds ? 1 : SECONDS_PER_MINUTE;
        int first = 0;
        if (minDay) {
            LocalTime from = min.toLocalTime();
            int seconds = from.toSecondOfDay() + (from.getNano() == 0 ? 0 : 1);
            first = (seconds + unit - 1) / unit * unit;
        }
        int last = maxDay ? max.toLocalTime().toSecondOfDay() / unit * unit : SECONDS_PER_DAY - unit;
        // Upwards from the lower bound; downwards from the upper bound on a day that
        // is only the upper bound's.
        int step = minDay ? unit : -unit;
        for (int second = minDay ? first : last; second >= first && second <= last; second += step) {
            LocalTime time = LocalTime.ofSecondOfDay(second);
            if (timeFilter == null || timeFilter.test(time)) {
                return time;
            }
        }
        return null;
    }

    /**
     * Returns the time rules for a given day.
     *
     * @param date the day, or {@code null} for a day without bound-specific limits
     * @return the time constraints of that day
     */
    public TimeConstraints timesOn(LocalDate date) {
        return timesOn(date, null, null);
    }

    /**
     * Returns the time rules for a given day, further limited by extra time bounds (used
     * to keep a range's end time from preceding its start time on the same day).
     *
     * @param date     the day, or {@code null} for a day without bound-specific limits
     * @param extraMin an additional earliest time, or {@code null}
     * @param extraMax an additional latest time, or {@code null}
     * @return the time constraints of that day
     */
    public TimeConstraints timesOn(LocalDate date, LocalTime extraMin, LocalTime extraMax) {
        LocalTime from = min != null && min.toLocalDate().equals(date) ? min.toLocalTime() : null;
        LocalTime to = max != null && max.toLocalDate().equals(date) ? max.toLocalTime() : null;
        if (extraMin != null && (from == null || extraMin.isAfter(from))) {
            from = extraMin;
        }
        if (extraMax != null && (to == null || extraMax.isBefore(to))) {
            to = extraMax;
        }
        return new TimeConstraints(from, to, timeFilter, hourStep, minuteStep, secondStep, showSeconds);
    }

    /**
     * Returns whether a date-time satisfies the bounds and both filters. This is the
     * rule for typed and programmatic values; the steps only shape the columns, also
     * on a bound's own day.
     *
     * @param value the date-time
     * @return {@code true} if acceptable; {@code false} for {@code null}
     */
    public boolean isAcceptable(LocalDateTime value) {
        return value != null && TemporalRules.withinBounds(value, min, max)
                && (dateFilter == null || dateFilter.test(value.toLocalDate()))
                && (timeFilter == null || timeFilter.test(value.toLocalTime()));
    }

    /**
     * Combines a day with a wished time, moving the time to the nearest selectable one
     * on that day when the wished time is not acceptable there. On a bound's own day
     * whose step grid offers no time, the acceptable time nearest to the bound is taken.
     *
     * @param date the day
     * @param time the wished time
     * @return the combined date-time; the wished time is kept when no time fits
     */
    public LocalDateTime combine(LocalDate date, LocalTime time) {
        TimeConstraints times = timesOn(date);
        if (times.isAcceptable(time)) {
            return date.atTime(time);
        }
        LocalTime snapped = times.snap(time, null);
        if (snapped == null) {
            snapped = boundTimeOn(date);
        }
        return date.atTime(snapped != null ? snapped : time);
    }

    /**
     * Truncates a time onto the step grid.
     *
     * @param time the time
     * @return the truncated time
     */
    public LocalTime truncate(LocalTime time) {
        return timesOn(null).truncate(time);
    }
}

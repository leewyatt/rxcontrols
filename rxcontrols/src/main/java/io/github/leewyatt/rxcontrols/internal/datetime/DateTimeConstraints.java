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
 * its earlier times.
 *
 * <p>Instances are immutable snapshots; build a new one when a property changes.
 */
public final class DateTimeConstraints {

    private final LocalDateTime min;
    private final LocalDateTime max;
    private final Predicate<LocalDate> dateFilter;
    private final Predicate<LocalTime> timeFilter;
    private final int hourStep;
    private final int minuteStep;
    private final int secondStep;
    private final boolean showSeconds;

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
     * @return {@code true} if within the bounds' dates and accepted by the date filter
     */
    public boolean isDateSelectable(LocalDate date) {
        return TemporalRules.isSelectable(date, minDate(), maxDate(), dateFilter);
    }

    /**
     * Returns the time rules for a given day.
     *
     * @param date the day, or {@code null} for a day without bound-specific limits
     * @return the time constraints of that day
     */
    public TimeConstraints timesOn(LocalDate date) {
        LocalTime from = min != null && min.toLocalDate().equals(date) ? min.toLocalTime() : null;
        LocalTime to = max != null && max.toLocalDate().equals(date) ? max.toLocalTime() : null;
        return new TimeConstraints(from, to, timeFilter, hourStep, minuteStep, secondStep, showSeconds);
    }

    /**
     * Returns whether a date-time satisfies the bounds and both filters (the steps
     * only shape the columns).
     *
     * @param value the date-time
     * @return {@code true} if acceptable; {@code false} for {@code null}
     */
    public boolean isAcceptable(LocalDateTime value) {
        return value != null && isDateSelectable(value.toLocalDate())
                && TemporalRules.withinBounds(value, min, max)
                && (timeFilter == null || timeFilter.test(value.toLocalTime()));
    }

    /**
     * Combines a day with a wished time, moving the time to the nearest selectable one
     * on that day when the wished time is not acceptable there.
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

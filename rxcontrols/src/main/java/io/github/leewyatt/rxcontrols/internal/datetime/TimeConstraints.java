package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * Selectability rules for times of day: an optional inclusive range, an optional
 * filter, and per-unit steps. A time is selectable when every unit lies on its step
 * grid (counted from zero), the seconds are zero unless seconds are shown, it lies in
 * the range and the filter accepts it. A column item is available when at least one
 * selectable time uses it, so an hour stays enabled while any of its minutes is.
 *
 * <p>Instances are immutable snapshots; build a new one when a property changes.
 */
public final class TimeConstraints {

    /** The time units of the columns. */
    public enum Unit {
        /** Hour of day. */
        HOUR,
        /** Minute of hour. */
        MINUTE,
        /** Second of minute. */
        SECOND
    }

    private static final int HOURS = 24;
    private static final int MINUTES = 60;
    private static final int SECONDS = 60;

    private final LocalTime min;
    private final LocalTime max;
    private final Predicate<LocalTime> filter;
    private final int hourStep;
    private final int minuteStep;
    private final int secondStep;
    private final boolean showSeconds;

    /**
     * Creates a constraint snapshot. Steps below 1 are treated as 1.
     *
     * @param min         the earliest time, or {@code null}
     * @param max         the latest time, or {@code null}
     * @param filter      the filter, or {@code null}
     * @param hourStep    the hour step
     * @param minuteStep  the minute step
     * @param secondStep  the second step
     * @param showSeconds whether seconds are selectable at all
     */
    public TimeConstraints(LocalTime min, LocalTime max, Predicate<LocalTime> filter,
                           int hourStep, int minuteStep, int secondStep, boolean showSeconds) {
        this.min = min;
        this.max = max;
        this.filter = filter;
        this.hourStep = Math.max(1, hourStep);
        this.minuteStep = Math.max(1, minuteStep);
        this.secondStep = Math.max(1, secondStep);
        this.showSeconds = showSeconds;
    }

    /**
     * Returns the step of a unit.
     *
     * @param unit the unit
     * @return the step, at least 1
     */
    public int step(Unit unit) {
        switch (unit) {
            case HOUR:
                return hourStep;
            case MINUTE:
                return minuteStep;
            case SECOND:
            default:
                return secondStep;
        }
    }

    /**
     * Returns whether seconds are selectable.
     *
     * @return {@code true} if seconds are shown
     */
    public boolean isShowSeconds() {
        return showSeconds;
    }

    /**
     * Returns whether a time may be selected.
     *
     * @param time the time
     * @return {@code true} if selectable; {@code false} for {@code null}
     */
    public boolean isAllowed(LocalTime time) {
        if (time == null || time.getNano() != 0) {
            return false;
        }
        if (time.getHour() % hourStep != 0 || time.getMinute() % minuteStep != 0) {
            return false;
        }
        if (showSeconds ? time.getSecond() % secondStep != 0 : time.getSecond() != 0) {
            return false;
        }
        return TemporalRules.isSelectable(time, min, max, filter);
    }

    /**
     * Returns whether a time satisfies the range and the filter, ignoring the step grid
     * and the precision. This is the rule for typed and programmatic values: steps only
     * shape what the columns offer.
     *
     * @param time the time
     * @return {@code true} if acceptable; {@code false} for {@code null}
     */
    public boolean isAcceptable(LocalTime time) {
        return TemporalRules.isSelectable(time, min, max, filter);
    }

    /**
     * Returns whether an hour has at least one selectable time.
     *
     * @param hour the hour of day
     * @return {@code true} if the hour is available
     */
    public boolean isHourAvailable(int hour) {
        if (hour % hourStep != 0) {
            return false;
        }
        for (int minute = 0; minute < MINUTES; minute += minuteStep) {
            if (isMinuteAvailable(hour, minute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a minute of the given hour has at least one selectable time.
     *
     * @param hour   the hour of day
     * @param minute the minute
     * @return {@code true} if the minute is available
     */
    public boolean isMinuteAvailable(int hour, int minute) {
        if (minute % minuteStep != 0) {
            return false;
        }
        if (!showSeconds) {
            return isAllowed(LocalTime.of(hour, minute));
        }
        for (int second = 0; second < SECONDS; second += secondStep) {
            if (isAllowed(LocalTime.of(hour, minute, second))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a second of the given hour and minute is selectable.
     *
     * @param hour   the hour of day
     * @param minute the minute
     * @param second the second
     * @return {@code true} if the second is available
     */
    public boolean isSecondAvailable(int hour, int minute, int second) {
        return isAllowed(LocalTime.of(hour, minute, second));
    }

    /**
     * Returns the selectable time nearest to {@code time}, preferring times that keep
     * {@code fixed} unchanged (the unit the user just picked).
     *
     * @param time  the wished time
     * @param fixed the unit to keep if possible, or {@code null}
     * @return the nearest selectable time, or {@code null} if nothing is selectable
     */
    public LocalTime snap(LocalTime time, Unit fixed) {
        if (isAllowed(time)) {
            return time;
        }
        LocalTime best = null;
        long bestDistance = Long.MAX_VALUE;
        LocalTime fallback = null;
        long fallbackDistance = Long.MAX_VALUE;
        int target = time.toSecondOfDay();
        for (int hour = 0; hour < HOURS; hour += hourStep) {
            for (int minute = 0; minute < MINUTES; minute += minuteStep) {
                int secondLimit = showSeconds ? SECONDS : 1;
                for (int second = 0; second < secondLimit; second += secondStep) {
                    LocalTime candidate = LocalTime.of(hour, minute, second);
                    if (!TemporalRules.isSelectable(candidate, min, max, filter)) {
                        continue;
                    }
                    long distance = Math.abs(candidate.toSecondOfDay() - target);
                    if (keepsUnit(candidate, time, fixed) && distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                    if (distance < fallbackDistance) {
                        fallback = candidate;
                        fallbackDistance = distance;
                    }
                }
            }
        }
        return best != null ? best : fallback;
    }

    /**
     * Truncates a time onto the step grid (and drops seconds when they are hidden).
     *
     * @param time the time
     * @return the truncated time
     */
    public LocalTime truncate(LocalTime time) {
        int second = showSeconds ? time.getSecond() - time.getSecond() % secondStep : 0;
        return LocalTime.of(time.getHour() - time.getHour() % hourStep,
                time.getMinute() - time.getMinute() % minuteStep, second);
    }

    private static boolean keepsUnit(LocalTime candidate, LocalTime time, Unit fixed) {
        if (fixed == null) {
            return true;
        }
        switch (fixed) {
            case HOUR:
                return candidate.getHour() == time.getHour();
            case MINUTE:
                return candidate.getMinute() == time.getMinute();
            case SECOND:
            default:
                return candidate.getSecond() == time.getSecond();
        }
    }
}

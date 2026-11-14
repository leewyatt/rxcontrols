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
 * <p>Instances are snapshots of the rules; build a new one when a property changes. They
 * memoize availability internally, so a filter runs at most once per time of day.
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
    /** Lazily computed availability: 0 unknown, 1 available, 2 unavailable. */
    private final byte[] hourMemo = new byte[HOURS];
    private final byte[] minuteMemo = new byte[HOURS * MINUTES];
    /** Lazily computed range-and-filter result per second of day, so a filter runs once per time. */
    private byte[] acceptMemo;

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
        if (acceptMemo == null) {
            acceptMemo = new byte[HOURS * MINUTES * SECONDS];
        }
        int key = time.toSecondOfDay();
        if (acceptMemo[key] == 0) {
            acceptMemo[key] = TemporalRules.isSelectable(time, min, max, filter) ? (byte) 1 : (byte) 2;
        }
        return acceptMemo[key] == 1;
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
        if (hourMemo[hour] == 0) {
            boolean available = false;
            for (int minute = 0; minute < MINUTES && !available; minute += minuteStep) {
                available = isMinuteAvailable(hour, minute);
            }
            hourMemo[hour] = available ? (byte) 1 : (byte) 2;
        }
        return hourMemo[hour] == 1;
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
        int key = hour * MINUTES + minute;
        if (minuteMemo[key] == 0) {
            boolean available = false;
            int secondLimit = showSeconds ? SECONDS : 1;
            for (int second = 0; second < secondLimit && !available; second += secondStep) {
                available = isAllowed(LocalTime.of(hour, minute, second));
            }
            minuteMemo[key] = available ? (byte) 1 : (byte) 2;
        }
        return minuteMemo[key] == 1;
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
     * Returns the selectable time nearest to {@code time}. With a {@code fixed} unit (the
     * unit the user just picked) it prefers times that keep that unit, then times that
     * keep more of the other units, then the smallest distance.
     *
     * @param time  the wished time
     * @param fixed the unit to keep if possible, or {@code null}
     * @return the nearest selectable time, or {@code null} if nothing is selectable
     */
    public LocalTime snap(LocalTime time, Unit fixed) {
        return snap(time, fixed, false);
    }

    /**
     * Like {@link #snap(LocalTime, Unit)}, optionally keeping the result in the same
     * half of the day (AM / PM) as {@code time}.
     *
     * @param time     the wished time
     * @param fixed    the unit to keep if possible, or {@code null}
     * @param sameHalf whether the result must stay in {@code time}'s half of the day
     * @return the nearest selectable time, or {@code null} if nothing qualifies
     */
    public LocalTime snap(LocalTime time, Unit fixed, boolean sameHalf) {
        LocalTime wished = time.withNano(0);
        if (!showSeconds) {
            wished = wished.withSecond(0);
        }
        if (isAllowed(wished)) {
            return wished;
        }
        int halfStart = wished.getHour() < HOURS / 2 ? 0 : HOURS / 2;
        LocalTime best = null;
        long bestRank = Long.MAX_VALUE;
        int target = wished.toSecondOfDay();
        int secondLimit = showSeconds ? SECONDS : 1;
        for (int hour = 0; hour < HOURS; hour += hourStep) {
            if ((sameHalf && (hour < halfStart || hour >= halfStart + HOURS / 2)) || !isHourAvailable(hour)) {
                continue;
            }
            for (int minute = 0; minute < MINUTES; minute += minuteStep) {
                if (!isMinuteAvailable(hour, minute)) {
                    continue;
                }
                for (int second = 0; second < secondLimit; second += secondStep) {
                    LocalTime candidate = LocalTime.of(hour, minute, second);
                    if (!isAllowed(candidate)) {
                        continue;
                    }
                    // Rank: a lost fixed unit costs most, then each other lost unit,
                    // then the distance in seconds.
                    // Unit keeping only matters for a pick; a free snap is purely nearest.
                    long lost = fixed == null ? 0 : (keepsUnit(candidate, wished, fixed) ? 0 : 4)
                            + (candidate.getHour() == wished.getHour() ? 0 : 1)
                            + (candidate.getMinute() == wished.getMinute() ? 0 : 1)
                            + (candidate.getSecond() == wished.getSecond() ? 0 : 1);
                    long rank = lost * (HOURS * MINUTES * SECONDS) + Math.abs(candidate.toSecondOfDay() - target);
                    if (rank < bestRank) {
                        best = candidate;
                        bestRank = rank;
                    }
                }
            }
        }
        return best;
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

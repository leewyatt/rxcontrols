package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * Selectability rules shared by the date-based views and pickers: a value is
 * selectable when it lies within the optional bounds and passes the optional
 * filter. Bounds with {@code min > max} leave nothing selectable.
 */
public final class TemporalRules {

    private TemporalRules() {
    }

    /**
     * Returns whether a value lies within optional inclusive bounds.
     *
     * @param value the value
     * @param min   the lower bound, or {@code null}
     * @param max   the upper bound, or {@code null}
     * @param <T>   the value type
     * @return {@code true} if {@code min <= value <= max}
     */
    public static <T extends Comparable<? super T>> boolean withinBounds(T value, T min, T max) {
        return value != null && (min == null || value.compareTo(min) >= 0) && (max == null || value.compareTo(max) <= 0);
    }

    /**
     * Returns whether a value is within the bounds and accepted by the filter.
     *
     * @param value  the value
     * @param min    the lower bound, or {@code null}
     * @param max    the upper bound, or {@code null}
     * @param filter the filter, or {@code null} to accept all
     * @param <T>    the value type
     * @return {@code true} if the value may be selected
     */
    public static <T extends Comparable<? super T>> boolean isSelectable(T value, T min, T max,
                                                                      Predicate<? super T> filter) {
        return withinBounds(value, min, max) && (filter == null || filter.test(value));
    }

    /**
     * Clamps a date into optional bounds; with {@code min > max} the lower bound wins.
     *
     * @param date the date, or {@code null}
     * @param min  the lower bound, or {@code null}
     * @param max  the upper bound, or {@code null}
     * @return the clamped date, or {@code null} for a {@code null} date
     */
    public static LocalDate clamp(LocalDate date, LocalDate min, LocalDate max) {
        if (date == null) {
            return null;
        }
        if (max != null && date.isAfter(max)) {
            date = max;
        }
        if (min != null && date.isBefore(min)) {
            date = min;
        }
        return date;
    }
}

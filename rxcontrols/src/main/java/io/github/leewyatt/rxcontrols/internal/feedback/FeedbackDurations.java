package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.util.Duration;

/**
 * Fallback rules for the duration properties of feedback buttons. A value
 * the control cannot use is never rejected; the control reads the property
 * through one of these methods and gets its default instead.
 */
public final class FeedbackDurations {

    private FeedbackDurations() {
    }

    /**
     * Returns a duration that may hold forever: {@code Duration.ZERO} and
     * {@code Duration.INDEFINITE} are kept; {@code null}, unknown and
     * negative values give the fallback.
     *
     * @param value    the property value, may be {@code null}
     * @param fallback the control's default
     * @return the value, or the fallback when the value is unusable
     */
    public static Duration orDefault(Duration value, Duration fallback) {
        if (value == null || value.isUnknown() || value.lessThan(Duration.ZERO)) {
            return fallback;
        }
        return value;
    }

    /**
     * Returns a duration that has to end: {@code Duration.ZERO} is kept;
     * {@code null}, unknown, negative and indefinite values give the
     * fallback.
     *
     * @param value    the property value, may be {@code null}
     * @param fallback the control's default
     * @return the value, or the fallback when the value is unusable
     */
    public static Duration finiteOrDefault(Duration value, Duration fallback) {
        if (value == null || value.isUnknown() || value.isIndefinite()
                || value.lessThan(Duration.ZERO)) {
            return fallback;
        }
        return value;
    }
}

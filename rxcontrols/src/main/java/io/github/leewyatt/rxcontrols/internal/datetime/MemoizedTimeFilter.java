package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalTime;
import java.util.function.Predicate;

/**
 * A time filter that asks the wrapped filter at most once per second of day and
 * remembers the answer. The time columns ask about the same times on every refresh,
 * so a skin keeps one instance for as long as the control's filter stays the same.
 *
 * <p>Times with a fraction of a second are passed straight to the wrapped filter.
 */
public final class MemoizedTimeFilter implements Predicate<LocalTime> {

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;
    private static final byte ACCEPTED = 1;
    private static final byte REJECTED = 2;

    private final Predicate<LocalTime> filter;
    /** The answer per second of day, {@code 0} until asked; allocated on the first question. */
    private byte[] answers;

    private MemoizedTimeFilter(Predicate<LocalTime> filter) {
        this.filter = filter;
    }

    /**
     * Returns the remembering form of a filter, reusing {@code previous} and its
     * answers while it wraps the same filter instance.
     *
     * @param filter   the filter, or {@code null}
     * @param previous the result of the last call, or {@code null}
     * @return the remembering filter, or {@code null} if there is no filter
     */
    public static Predicate<LocalTime> of(Predicate<LocalTime> filter, Predicate<LocalTime> previous) {
        if (filter == null) {
            return null;
        }
        if (previous instanceof MemoizedTimeFilter && ((MemoizedTimeFilter) previous).filter == filter) {
            return previous;
        }
        return new MemoizedTimeFilter(filter);
    }

    /** {@inheritDoc} */
    @Override
    public boolean test(LocalTime time) {
        if (time.getNano() != 0) {
            return filter.test(time);
        }
        if (answers == null) {
            answers = new byte[SECONDS_PER_DAY];
        }
        int key = time.toSecondOfDay();
        if (answers[key] == 0) {
            answers[key] = filter.test(time) ? ACCEPTED : REJECTED;
        }
        return answers[key] == ACCEPTED;
    }
}

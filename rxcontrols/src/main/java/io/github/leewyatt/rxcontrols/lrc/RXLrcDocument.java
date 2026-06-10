package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable parsed LRC document.
 *
 * @param metadata the parsed metadata
 * @param lines    the normalized lyric lines, sorted by time
 * @throws NullPointerException if {@code metadata}, {@code lines}, or one of the lines is {@code null}
 */
public record RXLrcDocument(RXLrcMetadata metadata, List<RXLrcLine> lines) {

    private static final int NO_LINE_INDEX = -1;
    private static final RXLrcDocument EMPTY =
            new RXLrcDocument(new RXLrcMetadata(Map.of()), List.of());

    /**
     * Creates an immutable LRC document.
     */
    public RXLrcDocument {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(lines, "lines");
        lines = List.copyOf(lines);
    }

    /**
     * Returns the truly empty document.
     *
     * @return an immutable document with empty metadata and no timed lines
     */
    public static RXLrcDocument empty() {
        return EMPTY;
    }

    /**
     * Returns whether this document contains no timed lyric lines.
     *
     * @return {@code true} if no timed lines are present
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * Finds the current line index for the given lookup time.
     *
     * <p>The lookup is a floor search over line start times. Exact timestamp
     * matches are included, and duplicate timestamp groups resolve to their
     * first line.</p>
     *
     * @param time the finite lookup time
     * @return the current line index, or {@code -1} if the lookup time is before
     *         the first line or the document is empty
     * @throws NullPointerException if {@code time} is {@code null}
     */
    public int lineIndexAt(Duration time) {
        Objects.requireNonNull(time, "time");
        if (lines.isEmpty() || !isFinite(time)) {
            return NO_LINE_INDEX;
        }

        double lookupMillis = time.toMillis();
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).time().toMillis() <= lookupMillis) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }

        int index = low - 1;
        if (index < 0) {
            return NO_LINE_INDEX;
        }
        while (index > 0 && lines.get(index - 1).time().equals(lines.get(index).time())) {
            index--;
        }
        return index;
    }

    private static boolean isFinite(Duration duration) {
        return !duration.isUnknown()
                && !duration.isIndefinite()
                && Double.isFinite(duration.toMillis());
    }
}

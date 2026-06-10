package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;

import java.util.Objects;

/**
 * Immutable lyric line in a parsed LRC document.
 *
 * @param index            the stable index after parser sorting
 * @param time             the normalized display time after LRC metadata offset baking
 * @param duration         the duration until the next distinct timestamp, or {@link Duration#UNKNOWN}
 * @param text             the lyric text; may be empty for instrumental gaps
 * @param translation      reserved for a future line-level translation track; may be {@code null}
 * @param romanization     reserved for a future romanization track; may be {@code null}
 * @param rawLine          the original source line that produced this lyric line
 * @param sourceLineNumber the 1-based source line number
 * @throws NullPointerException if {@code time}, {@code duration}, {@code text}, or {@code rawLine}
 *                              is {@code null}
 */
public record RXLrcLine(int index, Duration time, Duration duration, String text,
                        String translation, String romanization,
                        String rawLine, int sourceLineNumber) {

    /**
     * Creates an immutable lyric line.
     */
    public RXLrcLine {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(rawLine, "rawLine");
    }
}

package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the fallback rules shared by the duration properties of feedback
 * buttons.
 */
public class FeedbackDurationsTest {

    private static final Duration FALLBACK = Duration.millis(123.0);

    @Test
    public void orDefaultKeepsZeroAndIndefinite() {
        assertEquals(Duration.ZERO, FeedbackDurations.orDefault(Duration.ZERO, FALLBACK));
        assertEquals(Duration.INDEFINITE, FeedbackDurations.orDefault(Duration.INDEFINITE, FALLBACK));
        assertEquals(Duration.millis(40.0), FeedbackDurations.orDefault(Duration.millis(40.0), FALLBACK));

        assertEquals(FALLBACK, FeedbackDurations.orDefault(null, FALLBACK));
        assertEquals(FALLBACK, FeedbackDurations.orDefault(Duration.UNKNOWN, FALLBACK));
        assertEquals(FALLBACK, FeedbackDurations.orDefault(Duration.millis(-1.0), FALLBACK));
    }

    @Test
    public void finiteOrDefaultAlsoReplacesIndefinite() {
        assertEquals(Duration.ZERO, FeedbackDurations.finiteOrDefault(Duration.ZERO, FALLBACK));
        assertEquals(Duration.millis(40.0), FeedbackDurations.finiteOrDefault(Duration.millis(40.0), FALLBACK));

        assertEquals(FALLBACK, FeedbackDurations.finiteOrDefault(Duration.INDEFINITE, FALLBACK));
        assertEquals(FALLBACK, FeedbackDurations.finiteOrDefault(null, FALLBACK));
        assertEquals(FALLBACK, FeedbackDurations.finiteOrDefault(Duration.UNKNOWN, FALLBACK));
        assertEquals(FALLBACK, FeedbackDurations.finiteOrDefault(Duration.millis(-1.0), FALLBACK));
    }
}

package io.github.leewyatt.rxcontrols.internal;

/**
 * Keys of the one-shot signals a control posts into its own
 * {@code getProperties()} map for its skin to consume. The skin removes the
 * key as soon as it reads it, so the same value can be posted again.
 */
public final class FeedbackSignals {

    /**
     * Result of the latest copy activation of a copy button:
     * {@code Boolean.TRUE} when the clipboard was written, {@code Boolean.FALSE}
     * when nothing was copied or the feedback is being reset.
     */
    public static final String COPY_RESULT = "rx-copy-result";

    private FeedbackSignals() {
    }
}

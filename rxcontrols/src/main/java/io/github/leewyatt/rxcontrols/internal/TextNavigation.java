package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.control.IndexRange;

import java.text.BreakIterator;

/**
 * Pure text-boundary helpers for selection gestures: the word segment containing an index
 * (double-click) and the paragraph containing an index (triple-click). Stateless and
 * Unicode-aware via {@link BreakIterator}; paragraphs are runs delimited by {@code '\n'}.
 */
public final class TextNavigation {

    private TextNavigation() {
    }

    /**
     * Returns the range of the word (or boundary segment) containing {@code index}, using
     * Unicode word boundaries. Empty text yields an empty range.
     *
     * @param text  the text; {@code null} is treated as empty
     * @param index the character index to locate (clamped into the text)
     * @return the word range covering {@code index}
     */
    public static IndexRange wordRangeAt(String text, int index) {
        String source = text == null ? "" : text;
        int len = source.length();
        if (len == 0) {
            return new IndexRange(0, 0);
        }
        int i = RXMath.clamp(index, 0, len);
        BreakIterator boundaries = BreakIterator.getWordInstance();
        boundaries.setText(source);
        int start;
        int end;
        if (i == len) {
            end = len;
            start = boundaries.preceding(len);
        } else {
            start = boundaries.isBoundary(i) ? i : boundaries.preceding(i);
            end = boundaries.following(i);
        }
        if (start == BreakIterator.DONE) {
            start = 0;
        }
        if (end == BreakIterator.DONE) {
            end = len;
        }
        return new IndexRange(start, end);
    }

    /**
     * Returns the range of the paragraph (run between {@code '\n'} delimiters) containing
     * {@code index}.
     *
     * @param text  the text; {@code null} is treated as empty
     * @param index the character index to locate (clamped into the text)
     * @return the paragraph range covering {@code index}
     */
    public static IndexRange paragraphRangeAt(String text, int index) {
        String source = text == null ? "" : text;
        int len = source.length();
        int i = RXMath.clamp(index, 0, len);
        int start = i;
        while (start > 0 && source.charAt(start - 1) != '\n') {
            start--;
        }
        int end = i;
        while (end < len && source.charAt(end) != '\n') {
            end++;
        }
        return new IndexRange(start, end);
    }
}

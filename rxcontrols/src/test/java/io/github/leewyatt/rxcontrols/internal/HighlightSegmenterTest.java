package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.internal.HighlightSegmenter.Segment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HighlightSegmenter}, locking the segmentation contract the
 * RXHighlightText control and its skin rely on: literal vs regex matching,
 * case sensitivity, overlapping-keyword merge, zero-width and invalid-regex
 * handling, blank-keyword skipping, and the {@code matches} short-circuit.
 */
public class HighlightSegmenterTest {

    /** Renders segments as plain text with {@code [..]} around highlighted runs. */
    private static String render(List<Segment> segments) {
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            if (segment.highlight()) {
                sb.append('[').append(segment.text()).append(']');
            } else {
                sb.append(segment.text());
            }
        }
        return sb.toString();
    }

    private static String literal(String text, String... keywords) {
        return render(HighlightSegmenter.segment(text, List.of(keywords), false, false));
    }

    // ==================== Literal matching ====================

    @Test
    public void singleLiteralKeyword() {
        assertEquals("a[bc]d", literal("abcd", "bc"));
    }

    @Test
    public void literalIsCaseSensitiveByDefault() {
        assertEquals("Hello", literal("Hello", "hello"));
        assertEquals("[Hello]", render(HighlightSegmenter.segment("Hello", List.of("hello"), false, true)));
    }

    /**
     * Overlapping keywords must merge into one contiguous run. For "abcdefg" the
     * keywords abc[0,3), bcd[1,4) and bc[1,3) merge to [0,4) -> "abcd", not just "abc".
     */
    @Test
    public void overlappingKeywordsMergeIntoOneRun() {
        assertEquals("[abcd]efg", literal("abcdefg", "abc", "bcd", "bc"));
    }

    @Test
    public void touchingKeywordsMerge() {
        assertEquals("[abcd]", literal("abcd", "ab", "cd"));
    }

    @Test
    public void multipleSeparateOccurrences() {
        assertEquals("[a]b[a]b[a]", literal("ababa", "a"));
    }

    // ==================== Regex matching ====================

    @Test
    public void regexMultipleOccurrences() {
        assertEquals("[123]ABC[456]",
                render(HighlightSegmenter.segment("123ABC456", List.of("[0-9]+"), true, false)));
    }

    @Test
    public void invalidRegexIsSkippedButOthersApply() {
        // "[bad(" is an invalid pattern -> skipped; "bar" still highlights.
        assertEquals("foo [bar]",
                render(HighlightSegmenter.segment("foo bar", List.of("[bad(", "bar"), true, false)));
    }

    @Test
    public void zeroWidthRegexProducesNoHighlightAndTerminates() {
        // "x*" matches empty at every position; zero-width matches are skipped
        // (and Matcher.find advances), so no highlight and no infinite loop.
        assertEquals("aaa", render(HighlightSegmenter.segment("aaa", List.of("x*"), true, false)));
    }

    // ==================== Blank / empty / null handling ====================

    @Test
    public void blankKeywordIsIgnored() {
        // Regression: a whitespace-only keyword must not highlight spaces.
        assertEquals("a b c", literal("a b c", " "));
        assertEquals("a [b] c", literal("a b c", " ", "b"));
    }

    @Test
    public void noKeywordsYieldsSinglePlainRun() {
        assertEquals("abc", literal("abc"));
    }

    @Test
    public void nullTextYieldsSingleEmptyPlainRun() {
        List<Segment> segments = HighlightSegmenter.segment(null, List.of("a"), false, false);
        assertEquals(1, segments.size());
        assertFalse(segments.get(0).highlight());
        assertEquals("", segments.get(0).text());
    }

    @Test
    public void emptyTextYieldsSingleEmptyPlainRun() {
        assertEquals("", literal("", "a"));
    }

    // ==================== matches() short-circuit ====================

    @Test
    public void matchesReflectsAnyHit() {
        assertTrue(HighlightSegmenter.matches("abcdefg", List.of("abc"), false, false));
        assertFalse(HighlightSegmenter.matches("abcdefg", List.of("xyz"), false, false));
    }

    @Test
    public void matchesHonorsCaseAndBlankAndInvalidRegex() {
        assertTrue(HighlightSegmenter.matches("Hello", List.of("hello"), false, true));
        assertFalse(HighlightSegmenter.matches("Hello", List.of("hello"), false, false));
        assertFalse(HighlightSegmenter.matches("a b c", List.of(" "), false, false));
        assertFalse(HighlightSegmenter.matches("foo", List.of("[bad("), true, false));
        assertFalse(HighlightSegmenter.matches(null, List.of("a"), false, false));
    }
}

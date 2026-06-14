package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.control.IndexRange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link HighlightSegmenter#highlightRanges}, locking the matching contract the
 * RXHighlightText control and its skin rely on: literal vs regex matching, case
 * sensitivity, overlapping / touching keyword merge, zero-width and invalid-regex
 * handling, blank-keyword skipping, null / empty text, and the ordered, non-overlapping,
 * unmodifiable shape of the returned range list.
 */
public class HighlightSegmenterTest {

    /** Renders the ranges back over the text with {@code [..]} around each highlighted run. */
    private static String render(String text, List<IndexRange> ranges) {
        String source = text == null ? "" : text;
        StringBuilder sb = new StringBuilder();
        int cursor = 0;
        for (IndexRange range : ranges) {
            sb.append(source, cursor, range.getStart());
            sb.append('[').append(source, range.getStart(), range.getEnd()).append(']');
            cursor = range.getEnd();
        }
        sb.append(source, cursor, source.length());
        return sb.toString();
    }

    private static String literal(String text, String... keywords) {
        return render(text, HighlightSegmenter.highlightRanges(text, List.of(keywords), false, false));
    }

    private static boolean matches(String text, List<String> keywords, boolean regex, boolean ignoreCase) {
        return !HighlightSegmenter.highlightRanges(text, keywords, regex, ignoreCase).isEmpty();
    }

    // ==================== Literal matching ====================

    @Test
    public void singleLiteralKeyword() {
        assertEquals("a[bc]d", literal("abcd", "bc"));
    }

    @Test
    public void literalIsCaseSensitiveByDefault() {
        assertEquals("Hello", literal("Hello", "hello"));
        assertEquals("[Hello]", render("Hello",
                HighlightSegmenter.highlightRanges("Hello", List.of("hello"), false, true)));
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
                render("123ABC456", HighlightSegmenter.highlightRanges("123ABC456", List.of("[0-9]+"), true, false)));
    }

    @Test
    public void invalidRegexIsSkippedButOthersApply() {
        // "[bad(" is an invalid pattern -> skipped; "bar" still highlights.
        assertEquals("foo [bar]",
                render("foo bar", HighlightSegmenter.highlightRanges("foo bar", List.of("[bad(", "bar"), true, false)));
    }

    @Test
    public void zeroWidthRegexProducesNoHighlightAndTerminates() {
        // "x*" matches empty at every position; zero-width matches are skipped
        // (and Matcher.find advances), so no highlight and no infinite loop.
        assertEquals("aaa", render("aaa", HighlightSegmenter.highlightRanges("aaa", List.of("x*"), true, false)));
    }

    // ==================== Blank / empty / null handling ====================

    @Test
    public void blankKeywordIsIgnored() {
        // Regression: a whitespace-only keyword must not highlight spaces.
        assertEquals("a b c", literal("a b c", " "));
        assertEquals("a [b] c", literal("a b c", " ", "b"));
    }

    @Test
    public void noKeywordsYieldsNoRanges() {
        assertEquals("abc", literal("abc"));
        assertTrue(HighlightSegmenter.highlightRanges("abc", List.of(), false, false).isEmpty());
    }

    @Test
    public void nullTextYieldsNoRanges() {
        assertTrue(HighlightSegmenter.highlightRanges(null, List.of("a"), false, false).isEmpty());
    }

    @Test
    public void emptyTextYieldsNoRanges() {
        assertTrue(HighlightSegmenter.highlightRanges("", List.of("a"), false, false).isEmpty());
    }

    // ==================== Range list shape ====================

    @Test
    public void rangesAreOrderedNonOverlappingAndMerged() {
        List<IndexRange> ranges =
                HighlightSegmenter.highlightRanges("abcdefg", List.of("abc", "bcd", "bc"), false, false);
        assertEquals(1, ranges.size());
        assertEquals(0, ranges.get(0).getStart());
        assertEquals(4, ranges.get(0).getEnd());
    }

    @Test
    public void separateOccurrencesStayOrdered() {
        List<IndexRange> ranges = HighlightSegmenter.highlightRanges("ababa", List.of("a"), false, false);
        assertEquals(3, ranges.size());
        assertEquals(0, ranges.get(0).getStart());
        assertEquals(2, ranges.get(1).getStart());
        assertEquals(4, ranges.get(2).getStart());
    }

    @Test
    public void resultIsUnmodifiable() {
        List<IndexRange> ranges = HighlightSegmenter.highlightRanges("abc", List.of("a"), false, false);
        assertThrows(UnsupportedOperationException.class, () -> ranges.add(new IndexRange(0, 1)));
    }

    // ==================== matched short-circuit (non-empty ranges) ====================

    @Test
    public void rangesNonEmptyReflectsAnyHit() {
        assertTrue(matches("abcdefg", List.of("abc"), false, false));
        assertFalse(matches("abcdefg", List.of("xyz"), false, false));
    }

    @Test
    public void matchingHonorsCaseAndBlankAndInvalidRegex() {
        assertTrue(matches("Hello", List.of("hello"), false, true));
        assertFalse(matches("Hello", List.of("hello"), false, false));
        assertFalse(matches("a b c", List.of(" "), false, false));
        assertFalse(matches("foo", List.of("[bad("), true, false));
        assertFalse(matches(null, List.of("a"), false, false));
    }
}

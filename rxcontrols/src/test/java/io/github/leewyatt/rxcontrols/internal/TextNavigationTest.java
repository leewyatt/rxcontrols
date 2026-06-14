package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.control.IndexRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link TextNavigation}: the word segment (double-click) and paragraph
 * (triple-click) boundary resolution, including index clamping and newline-delimited
 * paragraph scanning.
 */
public class TextNavigationTest {

    private static String word(String text, int index) {
        IndexRange range = TextNavigation.wordRangeAt(text, index);
        return text.substring(range.getStart(), range.getEnd());
    }

    private static String paragraph(String text, int index) {
        IndexRange range = TextNavigation.paragraphRangeAt(text, index);
        return text.substring(range.getStart(), range.getEnd());
    }

    // ==================== Word boundaries ====================

    @Test
    public void wordInsideToken() {
        assertEquals("quick", word("the quick brown fox", 5));
        assertEquals("quick", word("the quick brown fox", 4));
        assertEquals("the", word("the quick brown fox", 0));
    }

    @Test
    public void wordAtEndSelectsLastWord() {
        assertEquals("fox", word("the quick brown fox", 19));
        assertEquals("fox", word("the quick brown fox", 16));
    }

    @Test
    public void wordOnEmptyText() {
        IndexRange range = TextNavigation.wordRangeAt("", 0);
        assertEquals(0, range.getStart());
        assertEquals(0, range.getEnd());
    }

    @Test
    public void wordClampsOutOfRangeIndex() {
        assertEquals("the", word("the fox", -5));
        assertEquals("fox", word("the fox", 100));
    }

    // ==================== Paragraph boundaries ====================

    @Test
    public void paragraphBetweenNewlines() {
        assertEquals("line2", paragraph("line1\nline2\nline3", 8));
        assertEquals("line1", paragraph("line1\nline2\nline3", 0));
        assertEquals("line3", paragraph("line1\nline2\nline3", 16));
    }

    @Test
    public void paragraphSingleLine() {
        assertEquals("hello world", paragraph("hello world", 5));
    }

    @Test
    public void paragraphAtNewlineBoundaryPicksFollowingLine() {
        // index right after a newline belongs to the following paragraph
        assertEquals("line2", paragraph("line1\nline2", 6));
    }

    @Test
    public void paragraphOnEmptyLine() {
        // caret on an empty paragraph between two newlines -> empty range
        IndexRange range = TextNavigation.paragraphRangeAt("a\n\nb", 2);
        assertEquals(2, range.getStart());
        assertEquals(2, range.getEnd());
    }
}

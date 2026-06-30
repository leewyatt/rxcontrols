package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the lenient LRC parser, immutable parse result, and parser warning boundaries.
 */
public class LrcParserTest {

    private static final double EPSILON = 0.0001;

    @Test
    public void parseRejectsNullAndTreatsBlankAsEmpty() {
        assertThrows(NullPointerException.class, () -> LrcParser.parse(null));

        LrcParseResult result = LrcParser.parse("\uFEFF \n \t");

        assertTrue(result.document().isEmpty());
        assertTrue(result.document().metadata().tags().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void parseConvertsFractionalSecondsByDigitCount() {
        assertSingleLineMillis("[00:01.5]x", 1500.0);
        assertSingleLineMillis("[00:01.05]x", 1050.0);
        assertSingleLineMillis("[00:01.50]x", 1500.0);
        assertSingleLineMillis("[00:01.005]x", 1005.0);
        assertSingleLineMillis("[00:01]x", 1000.0);
        assertSingleLineMillis("[00:01:5]x", 1500.0);
    }

    @Test
    public void parseExpandsMultiTimestampLinesAndSortsByTime() {
        LrcParseResult result = LrcParser.parse("""
                [02:50.34][03:57.94]chorus
                [00:10.00]first
                """);

        List<LrcLine> lines = result.document().lines();
        assertEquals(3, lines.size());
        assertEquals("first", lines.get(0).text());
        assertEquals("chorus", lines.get(1).text());
        assertEquals("chorus", lines.get(2).text());
        assertMillis(10_000.0, lines.get(0).time());
        assertMillis(170_340.0, lines.get(1).time());
        assertMillis(237_940.0, lines.get(2).time());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void parsePreservesKnownAndUnknownMetadataTags() {
        LrcParseResult result = LrcParser.parse("""
                [ti:T][ar:A] [al:Al]
                [au:Au]
                [lr:Lr]
                [by:B]
                [length:3:21]
                [re:R]
                [tool:X]
                [ve:1.0]
                [xyz:foo]
                [00:01.00]line
                """);

        LrcMetadata metadata = result.document().metadata();
        assertEquals("T", metadata.getTitle());
        assertEquals("A", metadata.getArtist());
        assertEquals("Al", metadata.getAlbum());
        assertEquals("Au", metadata.getCreator());
        assertEquals("3:21", metadata.getLength());
        assertEquals("Lr", metadata.tags().get("lr"));
        assertEquals("B", metadata.tags().get("by"));
        assertEquals("R", metadata.tags().get("re"));
        assertEquals("X", metadata.tags().get("tool"));
        assertEquals("1.0", metadata.tags().get("ve"));
        assertEquals("foo", metadata.tags().get("xyz"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void parseBakesPositiveAndNegativeMetadataOffsetIntoLineTime() {
        LrcParseResult positive = LrcParser.parse("""
                [offset:+500]
                [00:01.00]line
                """);
        LrcParseResult negative = LrcParser.parse("""
                [offset:-300]
                [00:01.00]line
                """);
        LrcParseResult clamped = LrcParser.parse("""
                [offset:5000]
                [00:01.00]line
                """);

        assertEquals(Duration.millis(500.0), positive.document().metadata().getOffset());
        assertMillis(500.0, positive.document().lines().get(0).time());
        assertEquals(Duration.millis(-300.0), negative.document().metadata().getOffset());
        assertMillis(1300.0, negative.document().lines().get(0).time());
        assertMillis(0.0, clamped.document().lines().get(0).time());
    }

    @Test
    public void parseWarnsForInvalidOffsetAndKeepsLineTimesUnshifted() {
        LrcParseResult result = LrcParser.parse("""
                [offset:abc]
                [00:01.00]line
                """);

        assertEquals(Duration.ZERO, result.document().metadata().getOffset());
        assertMillis(1000.0, result.document().lines().get(0).time());
        assertEquals(List.of(LrcWarningCode.INVALID_OFFSET), warningCodes(result));
    }

    @Test
    public void parseSkipsInvalidTimestampTagsWithoutDroppingValidSiblings() {
        LrcParseResult result = LrcParser.parse("""
                [00:75.00][00:01.00]line
                [99:99]junk
                """);

        assertEquals(1, result.document().lines().size());
        assertEquals("line", result.document().lines().get(0).text());
        assertMillis(1000.0, result.document().lines().get(0).time());
        assertEquals(2, warningCodes(result).stream()
                .filter(LrcWarningCode.INVALID_TIMESTAMP::equals)
                .count());
    }

    @Test
    public void parseWarnsForMalformedMetadataTags() {
        LrcParseResult result = LrcParser.parse("""
                [kana:abc
                [foo:bar] trailing
                [ti:Title] trailing
                [00:01.00]line
                """);

        assertEquals(1, result.document().lines().size());
        assertEquals(3, warningCodes(result).stream()
                .filter(LrcWarningCode.INVALID_METADATA::equals)
                .count());
    }

    @Test
    public void parseKeepsEmptyTimedLines() {
        LrcParseResult result = LrcParser.parse("[00:09.60]");

        assertEquals(1, result.document().lines().size());
        assertEquals("", result.document().lines().get(0).text());
        assertMillis(9600.0, result.document().lines().get(0).time());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    public void parseWarnsForUntimedTextAndPreservesMetadataWhenNoTimedLinesExist() {
        LrcParseResult result = LrcParser.parse("""
                [ti:T]
                plain text
                """);

        assertTrue(result.document().lines().isEmpty());
        assertEquals("T", result.document().metadata().getTitle());
        assertEquals(List.of(LrcWarningCode.UNTIMED_TEXT, LrcWarningCode.NO_TIMED_LINES),
                warningCodes(result));
    }

    @Test
    public void parseKeepsDuplicateTimestampsStableAndComputesDurationsToNextDistinctTime() {
        LrcParseResult result = LrcParser.parse("""
                [00:01.00]A
                [00:01.00]B
                [00:03.00]C
                """);

        List<LrcLine> lines = result.document().lines();
        assertEquals("A", lines.get(0).text());
        assertEquals("B", lines.get(1).text());
        assertEquals("C", lines.get(2).text());
        assertMillis(2000.0, lines.get(0).duration());
        assertMillis(2000.0, lines.get(1).duration());
        assertEquals(Duration.UNKNOWN, lines.get(2).duration());
        assertTrue(warningCodes(result).contains(LrcWarningCode.DUPLICATE_TIMESTAMP));
    }

    @Test
    public void parseHandlesBomMixedLineSeparatorsAndBracketedLyricText() {
        LrcParseResult result = LrcParser.parse(
                "\uFEFF[00:01.00]see [you]\r\n[00:02.00]b\r[00:03.00]c\n[00:04.00]d");

        List<LrcLine> lines = result.document().lines();
        assertEquals(4, lines.size());
        assertEquals("see [you]", lines.get(0).text());
        assertEquals("b", lines.get(1).text());
        assertEquals("c", lines.get(2).text());
        assertEquals("d", lines.get(3).text());
        assertTrue(result.warnings().isEmpty());
    }

    private static void assertSingleLineMillis(String text, double millis) {
        LrcParseResult result = LrcParser.parse(text);
        assertEquals(1, result.document().lines().size());
        assertMillis(millis, result.document().lines().get(0).time());
        assertTrue(result.warnings().isEmpty());
    }

    private static void assertMillis(double expected, Duration actual) {
        assertEquals(expected, actual.toMillis(), EPSILON);
    }

    private static List<LrcWarningCode> warningCodes(LrcParseResult result) {
        return result.warnings().stream()
                .map(LrcParseWarning::code)
                .toList();
    }
}

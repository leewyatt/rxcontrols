package io.github.leewyatt.rxcontrols.lrc;

import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the immutable LRC document model and current-line binary search.
 */
public class LrcDocumentTest {

    @Test
    public void lineIndexAtReturnsNoLineForEmptyDocumentAndBeforeFirstLine() {
        assertEquals(-1, LrcDocument.empty().lineIndexAt(Duration.millis(1000.0)));

        LrcDocument document = document(1000L, 2000L, 3000L);

        assertEquals(-1, document.lineIndexAt(Duration.millis(500.0)));
    }

    @Test
    public void lineIndexAtFindsExactBetweenAndAfterLastTimes() {
        LrcDocument document = document(1000L, 2000L, 3000L);

        assertEquals(0, document.lineIndexAt(Duration.millis(1000.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(1500.0)));
        assertEquals(1, document.lineIndexAt(Duration.millis(2000.0)));
        assertEquals(2, document.lineIndexAt(Duration.millis(9999.0)));
    }

    @Test
    public void lineIndexAtHandlesSingleLineDocuments() {
        LrcDocument document = document(1000L);

        assertEquals(-1, document.lineIndexAt(Duration.millis(500.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(1000.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(2000.0)));
    }

    @Test
    public void lineIndexAtReturnsDuplicateTimestampGroupStart() {
        LrcDocument middleGroup = document(1000L, 2000L, 2000L, 3000L);
        LrcDocument firstGroup = document(0L, 0L, 1000L);
        LrcDocument lastGroup = document(1000L, 2000L, 2000L);

        assertEquals(1, middleGroup.lineIndexAt(Duration.millis(2000.0)));
        assertEquals(1, middleGroup.lineIndexAt(Duration.millis(2500.0)));
        assertEquals(0, firstGroup.lineIndexAt(Duration.ZERO));
        assertEquals(1, lastGroup.lineIndexAt(Duration.millis(5000.0)));
    }

    @Test
    public void lineIndexAtRejectsNullAndReturnsNoLineForNonFiniteTime() {
        LrcDocument document = document(1000L);

        assertThrows(NullPointerException.class, () -> document.lineIndexAt(null));
        assertEquals(-1, document.lineIndexAt(Duration.UNKNOWN));
        assertEquals(-1, document.lineIndexAt(Duration.INDEFINITE));
    }

    @Test
    public void constructorDefensivelyCopiesLines() {
        List<LrcLine> source = new ArrayList<>();
        source.add(line(0, 1000L));

        LrcDocument document = new LrcDocument(new LrcMetadata(Map.of()), source);
        source.clear();

        assertEquals(1, document.lines().size());
        assertThrows(UnsupportedOperationException.class,
                () -> document.lines().add(line(1, 2000L)));
    }

    private static LrcDocument document(long... millis) {
        List<LrcLine> lines = new ArrayList<>();
        for (int i = 0; i < millis.length; i++) {
            lines.add(line(i, millis[i]));
        }
        return new LrcDocument(new LrcMetadata(Map.of()), lines);
    }

    private static LrcLine line(int index, long millis) {
        return new LrcLine(index, Duration.millis(millis), Duration.UNKNOWN,
                "line " + index, null, null, "[00:00.00]line " + index, index + 1);
    }
}

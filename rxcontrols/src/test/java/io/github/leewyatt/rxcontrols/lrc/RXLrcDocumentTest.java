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
public class RXLrcDocumentTest {

    @Test
    public void lineIndexAtReturnsNoLineForEmptyDocumentAndBeforeFirstLine() {
        assertEquals(-1, RXLrcDocument.empty().lineIndexAt(Duration.millis(1000.0)));

        RXLrcDocument document = document(1000L, 2000L, 3000L);

        assertEquals(-1, document.lineIndexAt(Duration.millis(500.0)));
    }

    @Test
    public void lineIndexAtFindsExactBetweenAndAfterLastTimes() {
        RXLrcDocument document = document(1000L, 2000L, 3000L);

        assertEquals(0, document.lineIndexAt(Duration.millis(1000.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(1500.0)));
        assertEquals(1, document.lineIndexAt(Duration.millis(2000.0)));
        assertEquals(2, document.lineIndexAt(Duration.millis(9999.0)));
    }

    @Test
    public void lineIndexAtHandlesSingleLineDocuments() {
        RXLrcDocument document = document(1000L);

        assertEquals(-1, document.lineIndexAt(Duration.millis(500.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(1000.0)));
        assertEquals(0, document.lineIndexAt(Duration.millis(2000.0)));
    }

    @Test
    public void lineIndexAtReturnsDuplicateTimestampGroupStart() {
        RXLrcDocument middleGroup = document(1000L, 2000L, 2000L, 3000L);
        RXLrcDocument firstGroup = document(0L, 0L, 1000L);
        RXLrcDocument lastGroup = document(1000L, 2000L, 2000L);

        assertEquals(1, middleGroup.lineIndexAt(Duration.millis(2000.0)));
        assertEquals(1, middleGroup.lineIndexAt(Duration.millis(2500.0)));
        assertEquals(0, firstGroup.lineIndexAt(Duration.ZERO));
        assertEquals(1, lastGroup.lineIndexAt(Duration.millis(5000.0)));
    }

    @Test
    public void lineIndexAtRejectsNullAndReturnsNoLineForNonFiniteTime() {
        RXLrcDocument document = document(1000L);

        assertThrows(NullPointerException.class, () -> document.lineIndexAt(null));
        assertEquals(-1, document.lineIndexAt(Duration.UNKNOWN));
        assertEquals(-1, document.lineIndexAt(Duration.INDEFINITE));
    }

    @Test
    public void constructorDefensivelyCopiesLines() {
        List<RXLrcLine> source = new ArrayList<>();
        source.add(line(0, 1000L));

        RXLrcDocument document = new RXLrcDocument(new RXLrcMetadata(Map.of()), source);
        source.clear();

        assertEquals(1, document.lines().size());
        assertThrows(UnsupportedOperationException.class,
                () -> document.lines().add(line(1, 2000L)));
    }

    private static RXLrcDocument document(long... millis) {
        List<RXLrcLine> lines = new ArrayList<>();
        for (int i = 0; i < millis.length; i++) {
            lines.add(line(i, millis[i]));
        }
        return new RXLrcDocument(new RXLrcMetadata(Map.of()), lines);
    }

    private static RXLrcLine line(int index, long millis) {
        return new RXLrcLine(index, Duration.millis(millis), Duration.UNKNOWN,
                "line " + index, null, null, "[00:00.00]line " + index, index + 1);
    }
}

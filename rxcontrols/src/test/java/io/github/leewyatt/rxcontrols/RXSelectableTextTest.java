package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.control.IndexRange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link RXSelectableText}'s selection state machine: index clamping,
 * anchor/caret direction preservation, selection normalization, selectedText derivation,
 * empty-selection copy no-op, and the re-clamp invariant when the text changes (shrinks
 * or becomes {@code null}). All logic is on the control and headless-testable.
 */
public class RXSelectableTextTest {

    /**
     * Starts the JavaFX toolkit so control instances can be created off a live runtime.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== selectRange / clamp ====================

    @Test
    public void selectRangeClampsNegativeAndOverlong() {
        RXSelectableText control = new RXSelectableText("hello"); // length 5
        control.selectRange(-3, 100);
        assertEquals(0, control.getAnchor());
        assertEquals(5, control.getCaretPosition());
        assertEquals(0, control.getSelection().getStart());
        assertEquals(5, control.getSelection().getEnd());
        assertEquals("hello", control.getSelectedText());
    }

    @Test
    public void backwardSelectionPreservesAnchorDirection() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectRange(4, 1);
        assertEquals(4, control.getAnchor());
        assertEquals(1, control.getCaretPosition());
        assertEquals(1, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
        assertEquals("ell", control.getSelectedText());
    }

    @Test
    public void selectedTextIsSubstringOfSelection() {
        RXSelectableText control = new RXSelectableText("abcdef");
        control.selectRange(2, 5);
        assertEquals("cde", control.getSelectedText());
    }

    // ==================== selectAll / deselect / positionCaret ====================

    @Test
    public void selectAllSelectsWholeText() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectAll();
        assertEquals(0, control.getAnchor());
        assertEquals(5, control.getCaretPosition());
        assertEquals("hello", control.getSelectedText());
    }

    @Test
    public void deselectCollapsesToCaret() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectRange(1, 4);
        control.deselect();
        assertEquals(4, control.getAnchor());
        assertEquals(4, control.getCaretPosition());
        assertEquals(0, control.getSelection().getLength());
        assertEquals("", control.getSelectedText());
    }

    @Test
    public void positionCaretClearsSelection() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectAll();
        control.positionCaret(2);
        assertEquals(2, control.getAnchor());
        assertEquals(2, control.getCaretPosition());
        assertEquals("", control.getSelectedText());
    }

    // ==================== extendSelection ====================

    @Test
    public void extendSelectionKeepsAnchor() {
        RXSelectableText control = new RXSelectableText("hello");
        control.positionCaret(1);
        control.extendSelection(4);
        assertEquals(1, control.getAnchor());
        assertEquals(4, control.getCaretPosition());
        assertEquals(1, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
    }

    @Test
    public void extendSelectionReversesWhenCrossingAnchor() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectRange(2, 4); // anchor 2, caret 4
        control.extendSelection(0); // crosses below the anchor
        assertEquals(4, control.getAnchor());
        assertEquals(0, control.getCaretPosition());
        assertEquals(0, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
    }

    // ==================== copy ====================

    @Test
    public void emptySelectionCopyIsNoOp() {
        RXSelectableText control = new RXSelectableText("hello");
        control.positionCaret(2); // empty selection — copy must not touch the clipboard
        assertDoesNotThrow(control::copy);
    }

    // ==================== text change re-clamp invariant ====================

    @Test
    public void textShrinkReclampsSelection() {
        RXSelectableText control = new RXSelectableText("hello world"); // length 11
        control.selectRange(6, 11); // "world"
        control.setText("hi"); // length 2
        assertEquals(2, control.getAnchor());
        assertEquals(2, control.getCaretPosition());
        assertEquals("", control.getSelectedText());
        assertEquals(2, control.getLength());
    }

    @Test
    public void textToNullReclampsToEmptyAndStaysSafe() {
        RXSelectableText control = new RXSelectableText("hello");
        control.selectAll();
        control.setText(null);
        assertEquals(0, control.getAnchor());
        assertEquals(0, control.getCaretPosition());
        assertEquals("", control.getSelectedText());
        assertEquals(0, control.getLength());
        assertNull(control.getText());
    }

    @Test
    public void getLengthTreatsNullAsEmpty() {
        RXSelectableText control = new RXSelectableText();
        control.setText(null);
        assertEquals(0, control.getLength());
    }
}

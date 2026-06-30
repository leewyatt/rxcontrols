package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXTextView}'s selection state machine: index clamping,
 * anchor/caret direction preservation, selection normalization, selectedText derivation,
 * empty-selection copy no-op, and the re-clamp invariant when the text changes (shrinks
 * or becomes {@code null}). All logic is on the control and headless-testable.
 */
public class RXTextViewTest {

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
        RXTextView control = new RXTextView("hello"); // length 5
        control.selectRange(-3, 100);
        assertEquals(0, control.getAnchor());
        assertEquals(5, control.getCaretPosition());
        assertEquals(0, control.getSelection().getStart());
        assertEquals(5, control.getSelection().getEnd());
        assertEquals("hello", control.getSelectedText());
    }

    @Test
    public void backwardSelectionPreservesAnchorDirection() {
        RXTextView control = new RXTextView("hello");
        control.selectRange(4, 1);
        assertEquals(4, control.getAnchor());
        assertEquals(1, control.getCaretPosition());
        assertEquals(1, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
        assertEquals("ell", control.getSelectedText());
    }

    @Test
    public void selectedTextIsSubstringOfSelection() {
        RXTextView control = new RXTextView("abcdef");
        control.selectRange(2, 5);
        assertEquals("cde", control.getSelectedText());
    }

    // ==================== selectAll / deselect / positionCaret ====================

    @Test
    public void selectAllSelectsWholeText() {
        RXTextView control = new RXTextView("hello");
        control.selectAll();
        assertEquals(0, control.getAnchor());
        assertEquals(5, control.getCaretPosition());
        assertEquals("hello", control.getSelectedText());
    }

    @Test
    public void deselectCollapsesToCaret() {
        RXTextView control = new RXTextView("hello");
        control.selectRange(1, 4);
        control.deselect();
        assertEquals(4, control.getAnchor());
        assertEquals(4, control.getCaretPosition());
        assertEquals(0, control.getSelection().getLength());
        assertEquals("", control.getSelectedText());
    }

    @Test
    public void positionCaretClearsSelection() {
        RXTextView control = new RXTextView("hello");
        control.selectAll();
        control.positionCaret(2);
        assertEquals(2, control.getAnchor());
        assertEquals(2, control.getCaretPosition());
        assertEquals("", control.getSelectedText());
    }

    // ==================== extendSelection ====================

    @Test
    public void extendSelectionKeepsAnchor() {
        RXTextView control = new RXTextView("hello");
        control.positionCaret(1);
        control.extendSelection(4);
        assertEquals(1, control.getAnchor());
        assertEquals(4, control.getCaretPosition());
        assertEquals(1, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
    }

    @Test
    public void extendSelectionReversesWhenCrossingAnchor() {
        RXTextView control = new RXTextView("hello");
        control.selectRange(2, 4); // anchor 2, caret 4
        control.extendSelection(0); // crosses below the anchor
        assertEquals(4, control.getAnchor());
        assertEquals(0, control.getCaretPosition());
        assertEquals(0, control.getSelection().getStart());
        assertEquals(4, control.getSelection().getEnd());
    }

    // ==================== copy ====================

    // Keep this as a manual test to avoid overwriting the user's system clipboard during automated test runs.
    // @Test
    // public void emptySelectionCopyIsNoOp() {
    //     RXTextView control = new RXTextView("hello");
    //     control.positionCaret(2); // empty selection — copy must not touch the clipboard
    //     assertDoesNotThrow(control::copy);
    // }

    // ==================== text change re-clamp invariant ====================

    @Test
    public void textShrinkReclampsSelection() {
        RXTextView control = new RXTextView("hello world"); // length 11
        control.selectRange(6, 11); // "world"
        control.setText("hi"); // length 2
        assertEquals(2, control.getAnchor());
        assertEquals(2, control.getCaretPosition());
        assertEquals("", control.getSelectedText());
        assertEquals(2, control.getLength());
    }

    @Test
    public void textToNullReclampsToEmptyAndStaysSafe() {
        RXTextView control = new RXTextView("hello");
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
        RXTextView control = new RXTextView();
        control.setText(null);
        assertEquals(0, control.getLength());
    }

    // ==================== Color properties ====================

    @Test
    public void controlCssMetaDataExposesColorProperties() {
        Set<String> names = cssPropertyNames(new RXTextView());
        assertTrue(names.contains("-rx-text-fill"));
        assertTrue(names.contains("-rx-selection-fill"));
        assertTrue(names.contains("-rx-selected-text-fill"));
    }

    @Test
    public void controlCssMetaDataHasNoCaretFill() {
        assertFalse(cssPropertyNames(new RXTextView()).contains("-rx-caret-fill"));
    }

    @Test
    public void colorPropertiesAcceptNullWithoutThrowing() {
        RXTextView control = new RXTextView("hello");
        assertDoesNotThrow(() -> {
            control.setTextFill(null);
            control.setSelectionFill(null);
            control.setSelectedTextFill(null);
        });
        assertNull(control.getTextFill());
        assertNull(control.getSelectionFill());
        assertNull(control.getSelectedTextFill());
    }

    private static Set<String> cssPropertyNames(RXTextView control) {
        Set<String> names = new HashSet<>();
        for (CssMetaData<? extends Styleable, ?> metaData : control.getControlCssMetaData()) {
            names.add(metaData.getProperty());
        }
        return names;
    }
}

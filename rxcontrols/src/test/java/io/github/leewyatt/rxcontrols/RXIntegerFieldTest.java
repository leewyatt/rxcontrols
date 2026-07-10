package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typed-value, range, and commit behaviour for {@link RXIntegerField}:
 * Slider-aligned convergence and clamping on primitive int bounds, the
 * {@code null}-is-empty value contract, and 32-bit overflow rolling the text
 * back instead of corrupting the value.
 */
public class RXIntegerFieldTest {

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

    private static <T> T onFx(Supplier<T> body) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(body.get());
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not finish");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (err.get() != null) {
            throw new RuntimeException(err.get());
        }
        return out.get();
    }

    /** getValue returns a real Integer; setValue takes autoboxed ints and null clears. */
    @Test
    public void typedValueRoundTrip() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(42);
            Integer typed = f.getValue();
            String text = f.getText();
            f.setValue(null);
            return new Object[]{typed, text, f.getValue(), f.getText()};
        });
        assertEquals(42, r[0]);
        assertEquals("42", r[1], "initial value renders");
        assertNull(r[2], "null clears the value");
        assertEquals("", r[3], "cleared value renders empty text");
    }

    /** Both style classes are present: the family class and the control's own. */
    @Test
    public void familyAndOwnStyleClassesArePresent() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            return new Object[]{
                    f.getStyleClass().contains("rx-number-field"),
                    f.getStyleClass().contains("rx-integer-field")};
        });
        assertTrue((Boolean) r[0], "family style class rx-number-field");
        assertTrue((Boolean) r[1], "own style class rx-integer-field");
    }

    /** An out-of-range unbound value clamps to the active bound; in-range values pass through. */
    @Test
    public void outOfRangeValueClamps() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(50);
            f.setMin(10);
            f.setMax(100);
            f.setValue(5);
            Integer clampedUp = f.getValue();
            f.setValue(500);
            Integer clampedDown = f.getValue();
            f.setValue(77);
            Integer inRange = f.getValue();
            return new Object[]{clampedUp, clampedDown, inRange, f.getText()};
        });
        assertEquals(10, r[0], "below min clamps up");
        assertEquals(100, r[1], "above max clamps down");
        assertEquals(77, r[2], "in-range value passes through");
        assertEquals("77", r[3], "text follows the committed value");
    }

    /** An empty field (null value) is never clamped into a value by a bound change. */
    @Test
    public void emptyValueIsNotClampedByBounds() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            f.setMin(10);
            return new Object[]{f.getValue(), f.getText()};
        });
        assertNull(r[0], "empty field stays empty after setMin");
        assertEquals("", r[1], "text stays empty");
    }

    /** Slider convergence on primitive bounds: setMin above max pulls max up; value follows. */
    @Test
    public void setMinAboveMaxRaisesMax() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(5);
            f.setMax(10);
            f.setMin(20);
            return new Object[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertEquals(20, r[0], "min");
        assertEquals(20, r[1], "max converged up to min");
        assertEquals(20, r[2], "value clamped into the converged range");
    }

    /** Converging into a bound opposite bound throws "A bound value cannot be set". */
    @Test
    public void convergingIntoBoundOppositeThrows() {
        onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            SimpleIntegerProperty maxSrc = new SimpleIntegerProperty(10);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            String message = null;
            try {
                f.setMin(20);
            } catch (RuntimeException ex) {
                threw = true;
                message = ex.getMessage();
            }
            assertTrue(threw, "converging into a bound max must throw");
            assertTrue(message != null && message.contains("bound value cannot be set"),
                    "Slider-style message (was: " + message + ")");
            return null;
        });
    }

    /** An inverted setRange converges deterministically to [max, max] from any prior state. */
    @Test
    public void setRangeInvertedConvergesFromAnyPriorState() {
        int[] r = onFx(() -> {
            RXIntegerField wide = new RXIntegerField();
            wide.setRange(0, 100);
            wide.setRange(20, 10);

            RXIntegerField narrow = new RXIntegerField();
            narrow.setRange(0, 5);
            narrow.setRange(20, 10);

            return new int[]{wide.getMin(), wide.getMax(), narrow.getMin(), narrow.getMax()};
        });
        assertEquals(10, r[0], "min after inverted setRange from [0,100]");
        assertEquals(10, r[1], "max after inverted setRange from [0,100]");
        assertEquals(10, r[2], "min after inverted setRange from [0,5]");
        assertEquals(10, r[3], "max after inverted setRange from [0,5]");
    }

    /** setRange with a bound max rejects up front without mutating either bound. */
    @Test
    public void setRangeWithBoundMaxThrowsWithoutMutating() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            f.setMin(0);
            SimpleIntegerProperty maxSrc = new SimpleIntegerProperty(50);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            try {
                f.setRange(10, 20);
            } catch (IllegalStateException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getMin(), f.getMax()};
        });
        assertTrue((Boolean) r[0], "setRange with a bound max throws IllegalStateException");
        assertEquals(0, r[1], "min left unchanged (no half-apply)");
        assertEquals(50, r[2], "max left unchanged");
    }

    /** setValue is a no-op while the value is bound; an out-of-range bound value displays as-is. */
    @Test
    public void boundValueIsNoOpAndDisplaysAsIs() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            f.setMax(10);
            SimpleObjectProperty<Integer> src = new SimpleObjectProperty<>(5);
            f.valueProperty().bind(src);
            f.setValue(3);                      // no-op, value is bound
            Integer afterSet = f.getValue();
            src.set(20);                        // out of range, but bound: displayed as-is
            return new Object[]{afterSet, f.getValue(), f.getText()};
        });
        assertEquals(5, r[0], "setValue on a bound value is a no-op");
        assertEquals(20, r[1], "bound value stays 20 (not clamped)");
        assertEquals("20", r[2], "text follows the bound value");
    }

    /** 2^31 overflows the 32-bit range: the commit rolls the text back and keeps the value. */
    @Test
    public void overflowRollsTextBackAndKeepsValue() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(7);
            f.setText("2147483648");            // Integer.MAX_VALUE + 1
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(7, r[0], "overflowed input must not reach the value");
        assertEquals("7", r[1], "text rolled back to the last valid rendering");
    }

    /** Integer.MAX_VALUE is exactly representable and commits. */
    @Test
    public void maxIntCommits() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField();
            f.setText("2147483647");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(Integer.MAX_VALUE, r[0]);
        assertEquals("2147483647", r[1]);
    }

    /** A sign-only stub commit reverts to the previous value instead of clearing it. */
    @Test
    public void stubCommitRevertsInsteadOfClearing() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(100);
            f.setText("-");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(100, r[0], "stub input keeps the previous value");
        assertEquals("100", r[1], "text reverts to the previous rendering");
    }

    /** Clearing the text commits null (empty field). */
    @Test
    public void clearedTextCommitsNull() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(7);
            f.setText("");
            f.commitValue();
            return new Object[]{f.getValue()};
        });
        assertNull(r[0], "cleared text commits null");
    }

    /**
     * An equal-value commit ("+5" parses to the current 5) keeps the value and
     * leaves the text as typed — the JavaFX-native TextFormatter semantics
     * (the commit path never canonicalizes the displayed text; the previous
     * BigDecimal-based field behaved identically).
     */
    @Test
    public void equalValueCommitKeepsValueAndTypedText() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(5);
            f.setText("+5");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(5, r[0], "equal-value commit keeps the value");
        assertEquals("+5", r[1], "text stays as typed (JavaFX TextFormatter convention)");
    }

    /** The edit filter rejects letters and the decimal point. */
    @Test
    public void filterRejectsLettersAndDecimalPoint() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(5);
            f.setText("5a");
            String afterLetter = f.getText();
            f.setText("5.5");
            String afterDecimal = f.getText();
            return new Object[]{afterLetter, afterDecimal};
        });
        assertEquals("5", r[0], "letter rejected by the filter");
        assertEquals("5", r[1], "decimal point rejected by the filter");
    }
}

package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
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
 * Typed-value, range, and commit behaviour for {@link RXLongField}:
 * Slider-aligned convergence and clamping on primitive long bounds, the
 * {@code null}-is-empty value contract, exact precision beyond 2^53, and
 * 64-bit overflow rolling the text back instead of corrupting the value.
 */
public class RXLongFieldTest {

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

    /** getValue returns a real Long; setValue takes autoboxed longs and null clears. */
    @Test
    public void typedValueRoundTrip() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(42L);
            Long typed = f.getValue();
            String text = f.getText();
            f.setValue(null);
            return new Object[]{typed, text, f.getValue(), f.getText()};
        });
        assertEquals(42L, r[0]);
        assertEquals("42", r[1], "initial value renders");
        assertNull(r[2], "null clears the value");
        assertEquals("", r[3], "cleared value renders empty text");
    }

    /** Both style classes are present: the family class and the control's own. */
    @Test
    public void familyAndOwnStyleClassesArePresent() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            return new Object[]{
                    f.getStyleClass().contains("rx-number-field"),
                    f.getStyleClass().contains("rx-long-field")};
        });
        assertTrue((Boolean) r[0], "family style class rx-number-field");
        assertTrue((Boolean) r[1], "own style class rx-long-field");
    }

    /** Values beyond 2^53 (the double-safe range) are carried exactly. */
    @Test
    public void beyondDoubleSafeRangeIsExact() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            f.setText("9007199254740993");            // 2^53 + 1, not representable as double
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(9007199254740993L, r[0], "2^53 + 1 commits exactly");
        assertEquals("9007199254740993", r[1], "text renders the exact value");
    }

    /** An out-of-range unbound value clamps to the active bound; in-range values pass through. */
    @Test
    public void outOfRangeValueClamps() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(50L);
            f.setMin(10L);
            f.setMax(100L);
            f.setValue(5L);
            Long clampedUp = f.getValue();
            f.setValue(500L);
            Long clampedDown = f.getValue();
            f.setValue(77L);
            Long inRange = f.getValue();
            return new Object[]{clampedUp, clampedDown, inRange, f.getText()};
        });
        assertEquals(10L, r[0], "below min clamps up");
        assertEquals(100L, r[1], "above max clamps down");
        assertEquals(77L, r[2], "in-range value passes through");
        assertEquals("77", r[3], "text follows the committed value");
    }

    /** An empty field (null value) is never clamped into a value by a bound change. */
    @Test
    public void emptyValueIsNotClampedByBounds() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            f.setMin(10L);
            return new Object[]{f.getValue(), f.getText()};
        });
        assertNull(r[0], "empty field stays empty after setMin");
        assertEquals("", r[1], "text stays empty");
    }

    /** Slider convergence on primitive bounds: setMin above max pulls max up; value follows. */
    @Test
    public void setMinAboveMaxRaisesMax() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(5L);
            f.setMax(10L);
            f.setMin(20L);
            return new Object[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertEquals(20L, r[0], "min");
        assertEquals(20L, r[1], "max converged up to min");
        assertEquals(20L, r[2], "value clamped into the converged range");
    }

    /** Converging into a bound opposite bound throws "A bound value cannot be set". */
    @Test
    public void convergingIntoBoundOppositeThrows() {
        onFx(() -> {
            RXLongField f = new RXLongField();
            SimpleLongProperty maxSrc = new SimpleLongProperty(10L);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            String message = null;
            try {
                f.setMin(20L);
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
        long[] r = onFx(() -> {
            RXLongField wide = new RXLongField();
            wide.setRange(0L, 100L);
            wide.setRange(20L, 10L);

            RXLongField narrow = new RXLongField();
            narrow.setRange(0L, 5L);
            narrow.setRange(20L, 10L);

            return new long[]{wide.getMin(), wide.getMax(), narrow.getMin(), narrow.getMax()};
        });
        assertEquals(10L, r[0], "min after inverted setRange from [0,100]");
        assertEquals(10L, r[1], "max after inverted setRange from [0,100]");
        assertEquals(10L, r[2], "min after inverted setRange from [0,5]");
        assertEquals(10L, r[3], "max after inverted setRange from [0,5]");
    }

    /** setRange with a bound max rejects up front without mutating either bound. */
    @Test
    public void setRangeWithBoundMaxThrowsWithoutMutating() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            f.setMin(0L);
            SimpleLongProperty maxSrc = new SimpleLongProperty(50L);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            try {
                f.setRange(10L, 20L);
            } catch (IllegalStateException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getMin(), f.getMax()};
        });
        assertTrue((Boolean) r[0], "setRange with a bound max throws IllegalStateException");
        assertEquals(0L, r[1], "min left unchanged (no half-apply)");
        assertEquals(50L, r[2], "max left unchanged");
    }

    /** setValue is a no-op while the value is bound; an out-of-range bound value displays as-is. */
    @Test
    public void boundValueIsNoOpAndDisplaysAsIs() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            f.setMax(10L);
            SimpleObjectProperty<Long> src = new SimpleObjectProperty<>(5L);
            f.valueProperty().bind(src);
            f.setValue(3L);                     // no-op, value is bound
            Long afterSet = f.getValue();
            src.set(20L);                       // out of range, but bound: displayed as-is
            return new Object[]{afterSet, f.getValue(), f.getText()};
        });
        assertEquals(5L, r[0], "setValue on a bound value is a no-op");
        assertEquals(20L, r[1], "bound value stays 20 (not clamped)");
        assertEquals("20", r[2], "text follows the bound value");
    }

    /** 2^63 overflows the 64-bit range: the commit rolls the text back and keeps the value. */
    @Test
    public void overflowRollsTextBackAndKeepsValue() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(7L);
            f.setText("9223372036854775808");   // Long.MAX_VALUE + 1
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(7L, r[0], "overflowed input must not reach the value");
        assertEquals("7", r[1], "text rolled back to the last valid rendering");
    }

    /** Long.MAX_VALUE is exactly representable and commits. */
    @Test
    public void maxLongCommits() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField();
            f.setText("9223372036854775807");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(Long.MAX_VALUE, r[0]);
        assertEquals("9223372036854775807", r[1]);
    }

    /** A sign-only stub commit reverts to the previous value instead of clearing it. */
    @Test
    public void stubCommitRevertsInsteadOfClearing() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(100L);
            f.setText("-");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(100L, r[0], "stub input keeps the previous value");
        assertEquals("100", r[1], "text reverts to the previous rendering");
    }

    /** Clearing the text commits null (empty field). */
    @Test
    public void clearedTextCommitsNull() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(7L);
            f.setText("");
            f.commitValue();
            return new Object[]{f.getValue()};
        });
        assertNull(r[0], "cleared text commits null");
    }

    /** The edit filter rejects letters and the decimal point. */
    @Test
    public void filterRejectsLettersAndDecimalPoint() {
        Object[] r = onFx(() -> {
            RXLongField f = new RXLongField(5L);
            f.setText("5a");
            String afterLetter = f.getText();
            f.setText("5.5");
            String afterDecimal = f.getText();
            return new Object[]{afterLetter, afterDecimal};
        });
        assertEquals("5", r[0], "letter rejected by the filter");
        assertEquals("5", r[1], "decimal point rejected by the filter");
    }

    /**
     * A commit always normalizes the displayed text, whether or not the parsed
     * Long hits the valueOf cache: "+5" (cached, reference-equal to the current
     * value) and "+500" (new instance) must both render canonically.
     */
    @Test
    public void commitNormalizesTextRegardlessOfLongCache() {
        Object[] r = onFx(() -> {
            RXLongField small = new RXLongField(5L);
            small.setText("+5");
            small.commitValue();
            RXLongField large = new RXLongField(500L);
            large.setText("+500");
            large.commitValue();
            return new Object[]{small.getValue(), small.getText(),
                    large.getValue(), large.getText()};
        });
        assertEquals(5L, r[0], "equal-value commit keeps the value (cached path)");
        assertEquals("5", r[1], "text normalized to the canonical rendering (cached path)");
        assertEquals(500L, r[2], "equal-value commit keeps the value (uncached path)");
        assertEquals("500", r[3], "text normalized to the canonical rendering (uncached path)");
    }
}

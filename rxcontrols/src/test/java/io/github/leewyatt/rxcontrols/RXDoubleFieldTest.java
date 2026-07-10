package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Typed-value, finiteness, rendering, and range behaviour for
 * {@link RXDoubleField}: non-finite programmatic values are rejected (coerced
 * to null + IllegalArgumentException) while a bound non-finite value is only
 * rendered defensively; finite values render as plain decimal with no
 * scientific notation and no trailing {@code .0}; NaN bounds are "no
 * constraint"; overflowing input rolls the text back.
 */
public class RXDoubleFieldTest {

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

    /** Both style classes are present: the family class and the control's own. */
    @Test
    public void familyAndOwnStyleClassesArePresent() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField();
            return new Object[]{
                    f.getStyleClass().contains("rx-number-field"),
                    f.getStyleClass().contains("rx-double-field")};
        });
        assertTrue((Boolean) r[0], "family style class rx-number-field");
        assertTrue((Boolean) r[1], "own style class rx-double-field");
    }

    /** Finite values render plain: no trailing .0, no scientific notation. */
    @Test
    public void finiteValuesRenderPlainDecimal() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(2.0);
            String whole = f.getText();
            f.setValue(1.5);
            String fractional = f.getText();
            f.setValue(0.1 + 0.2);
            String representation = f.getText();
            return new Object[]{whole, fractional, representation};
        });
        assertEquals("2", r[0], "whole double renders without the .0 tail");
        assertEquals("1.5", r[1], "fractional double renders as typed");
        assertEquals("0.30000000000000004", r[2],
                "binary representation error is rendered honestly (see RXDecimalField for exact decimal)");
    }

    /** An extreme magnitude renders as its full plain form, never scientific notation. */
    @Test
    public void extremeMagnitudeRendersPlainAndCommitsRoundTrip() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(1e308);
            String text = f.getText();
            f.commitValue();                        // re-parse of our own render must round-trip
            return new Object[]{text, f.getValue()};
        });
        String text = (String) r[0];
        assertTrue(text.indexOf('E') < 0 && text.indexOf('e') < 0,
                "no scientific notation in the rendering (was: " + text + ")");
        assertEquals(309, text.length(), "1e308 renders its full plain form");
        assertEquals(1e308, r[1], "round trip commit preserves the value");
    }

    /** setValue(NaN / ±Infinity) coerces to null (empty field) and throws IAE. */
    @Test
    public void nonFiniteSetValueCoercesToNullAndThrows() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(5.0);
            boolean nanThrew = false;
            try {
                f.setValue(Double.NaN);
            } catch (IllegalArgumentException expected) {
                nanThrew = true;
            }
            Double afterNaN = f.getValue();
            String textAfterNaN = f.getText();

            f.setValue(5.0);
            boolean infThrew = false;
            try {
                f.setValue(Double.POSITIVE_INFINITY);
            } catch (IllegalArgumentException expected) {
                infThrew = true;
            }
            return new Object[]{nanThrew, afterNaN, textAfterNaN, infThrew, f.getValue()};
        });
        assertTrue((Boolean) r[0], "setValue(NaN) throws IllegalArgumentException");
        assertNull(r[1], "value coerced to null (empty field), not left non-finite");
        assertEquals("", r[2], "text refreshed to empty");
        assertTrue((Boolean) r[3], "setValue(+Infinity) throws IllegalArgumentException");
        assertNull(r[4], "value coerced to null after the infinity rejection");
    }

    /** A non-finite constructor value fails fast. */
    @Test
    public void nonFiniteConstructorValueThrows() {
        onFx(() -> {
            assertThrows(IllegalArgumentException.class, () -> new RXDoubleField(Double.NaN));
            return null;
        });
    }

    /**
     * A bound non-finite value is owned by its binding: it is not coerced, the
     * binding stays intact, the text falls back to Double.toString, and nothing
     * leaks onto the FX thread's uncaught handler.
     */
    @Test
    public void boundNonFiniteValueIsNotCoerced() {
        Object[] r = onFx(() -> {
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler prev = fx.getUncaughtExceptionHandler();
            AtomicReference<Throwable> uncaught = new AtomicReference<>();
            fx.setUncaughtExceptionHandler((t, e) -> uncaught.set(e));
            try {
                RXDoubleField f = new RXDoubleField();
                SimpleObjectProperty<Double> src = new SimpleObjectProperty<>(1.0);
                f.valueProperty().bind(src);
                src.set(Double.POSITIVE_INFINITY);
                return new Object[]{f.getValue(), f.getText(),
                        f.valueProperty().isBound(), uncaught.get()};
            } finally {
                fx.setUncaughtExceptionHandler(prev);
            }
        });
        assertEquals(Double.POSITIVE_INFINITY, r[0], "bound value stays non-finite (binding owns it)");
        assertEquals("Infinity", r[1], "defensive Double.toString rendering");
        assertTrue((Boolean) r[2], "binding stays intact");
        assertNull(r[3], "no uncaught exception routed to the FX thread's handler");
    }

    /** A NaN bound compares with nothing: no convergence, no clamping — "no constraint". */
    @Test
    public void nanBoundsAreNoConstraint() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(5.0);
            f.setMax(10.0);
            f.setMin(Double.NaN);               // must not converge max or clamp the value
            Double afterNaNMin = f.getValue();
            double maxAfter = f.getMax();
            f.setValue(-1000.0);                // NaN min clamps nothing
            return new Object[]{afterNaNMin, maxAfter, f.getValue()};
        });
        assertEquals(5.0, r[0], "value untouched by a NaN min");
        assertEquals(10.0, r[1], "max untouched by a NaN min (no convergence)");
        assertEquals(-1000.0, r[2], "NaN min clamps nothing");
    }

    /** Default ±Infinity bounds clamp nothing; finite bounds clamp normally and converge. */
    @Test
    public void boundsClampAndConverge() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(1e300);
            Double unclamped = f.getValue();    // default ±Infinity: no clamp
            f.setMin(0.0);
            f.setMax(10.0);
            Double clamped = f.getValue();
            f.setMin(20.0);                     // converge: pulls max up
            return new Object[]{unclamped, clamped, f.getMax(), f.getValue()};
        });
        assertEquals(1e300, r[0], "default infinite bounds clamp nothing");
        assertEquals(10.0, r[1], "value clamps into [0,10]");
        assertEquals(20.0, r[2], "max converged up to min");
        assertEquals(20.0, r[3], "value re-clamped into the converged range");
    }

    /** Input whose magnitude overflows the double range rolls the text back. */
    @Test
    public void overflowingInputRollsTextBack() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(7.0);
            f.setText("1" + "0".repeat(309));   // beyond double range -> parses to Infinity
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertEquals(7.0, r[0], "overflowed input must not reach the value");
        assertEquals("7", r[1], "text rolled back to the last valid rendering");
    }

    /** setValue is a no-op while the value is bound. */
    @Test
    public void setValueIsNoOpWhileBound() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField();
            SimpleObjectProperty<Double> src = new SimpleObjectProperty<>(5.0);
            f.valueProperty().bind(src);
            f.setValue(3.0);
            return new Object[]{f.getValue()};
        });
        assertEquals(5.0, r[0], "setValue on a bound value is a no-op");
    }

    /** An empty field (null value) is never clamped into a value by a bound change. */
    @Test
    public void emptyValueIsNotClampedByBounds() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField();
            f.setMin(10.0);
            return new Object[]{f.getValue(), f.getText()};
        });
        assertNull(r[0], "empty field stays empty after setMin");
        assertEquals("", r[1], "text stays empty");
    }

    /** The edit filter rejects letters, a second decimal point, and a mid-text sign. */
    @Test
    public void filterRejectsLettersSecondPointAndMidSign() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(1.5);
            f.setText("1.5a");
            String afterLetter = f.getText();
            f.setText("1.5.5");
            String afterSecondPoint = f.getText();
            f.setText("1-5");
            String afterMidSign = f.getText();
            return new Object[]{afterLetter, afterSecondPoint, afterMidSign};
        });
        assertEquals("1.5", r[0], "letter rejected by the filter");
        assertEquals("1.5", r[1], "second decimal point rejected by the filter");
        assertEquals("1.5", r[2], "mid-text sign rejected by the filter");
    }

    /** A sign- or dot-only stub commit reverts to the previous value instead of clearing it. */
    @Test
    public void stubCommitRevertsInsteadOfClearing() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(2.5);
            f.setText("-");
            f.commitValue();
            Double afterSign = f.getValue();
            f.setText(".");
            f.commitValue();
            return new Object[]{afterSign, f.getValue(), f.getText()};
        });
        assertEquals(2.5, r[0], "sign stub keeps the previous value");
        assertEquals(2.5, r[1], "dot stub keeps the previous value");
        assertEquals("2.5", r[2], "text reverts to the previous rendering");
    }

    /** Clearing the text commits null (empty field). */
    @Test
    public void clearedTextCommitsNull() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField(7.0);
            f.setText("");
            f.commitValue();
            return new Object[]{f.getValue(), f.getText()};
        });
        assertNull(r[0], "cleared text commits null");
        assertEquals("", r[1], "text stays empty");
    }

    /** Converging into a bound opposite bound throws "A bound value cannot be set". */
    @Test
    public void convergingIntoBoundOppositeThrows() {
        onFx(() -> {
            RXDoubleField f = new RXDoubleField();
            SimpleDoubleProperty maxSrc = new SimpleDoubleProperty(10.0);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            String message = null;
            try {
                f.setMin(20.0);
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
        double[] r = onFx(() -> {
            RXDoubleField wide = new RXDoubleField();
            wide.setRange(0.0, 100.0);
            wide.setRange(20.0, 10.0);

            RXDoubleField narrow = new RXDoubleField();
            narrow.setRange(0.0, 5.0);
            narrow.setRange(20.0, 10.0);

            return new double[]{wide.getMin(), wide.getMax(), narrow.getMin(), narrow.getMax()};
        });
        assertEquals(10.0, r[0], "min after inverted setRange from [0,100]");
        assertEquals(10.0, r[1], "max after inverted setRange from [0,100]");
        assertEquals(10.0, r[2], "min after inverted setRange from [0,5]");
        assertEquals(10.0, r[3], "max after inverted setRange from [0,5]");
    }

    /** setRange with a bound max rejects up front without mutating either bound. */
    @Test
    public void setRangeWithBoundMaxThrowsWithoutMutating() {
        Object[] r = onFx(() -> {
            RXDoubleField f = new RXDoubleField();
            f.setMin(0.0);
            SimpleDoubleProperty maxSrc = new SimpleDoubleProperty(50.0);
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            try {
                f.setRange(10.0, 20.0);
            } catch (IllegalStateException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getMin(), f.getMax()};
        });
        assertTrue((Boolean) r[0], "setRange with a bound max throws IllegalStateException");
        assertEquals(0.0, r[1], "min left unchanged (no half-apply)");
        assertEquals(50.0, r[2], "max left unchanged");
    }
}

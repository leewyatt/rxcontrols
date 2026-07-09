package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Range-constraint behaviour for {@link RXNumberField}, aligned with
 * {@link javafx.scene.control.Slider}: unbound setters converge the opposite
 * bound to keep {@code min <= max}; converging into a {@code bound} opposite
 * bound throws "A bound value cannot be set" (not swallowed); {@code setValue}
 * on a bound value is a no-op; {@link RXNumberField#setRange} sets both bounds
 * leniently (converging, not rejecting, an inverted pair) but rejects up front
 * when a bound is {@code bound}; and {@code validateValue} gates the value only,
 * not the bounds.
 */
public class RXNumberFieldBoundConstraintTest {

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

    private static void assertBig(String expected, BigDecimal actual, String msg) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> msg + " (was " + actual + ")");
    }

    /** Unbound setMin above the current max raises max to keep min <= max; value follows. */
    @Test
    public void unboundSetMinAboveMaxRaisesMax() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMax(new BigDecimal("10"));
            f.setValue(new BigDecimal("5"));
            f.setMin(new BigDecimal("20"));
            return new BigDecimal[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertBig("20", r[0], "min");
        assertBig("20", r[1], "max converged up to min");
        assertBig("20", r[2], "value clamped into the converged range");
    }

    /** Unbound setMax below the current min lowers min to keep min <= max; value follows. */
    @Test
    public void unboundSetMaxBelowMinLowersMin() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("10"));
            f.setValue(new BigDecimal("50"));
            f.setMax(new BigDecimal("5"));
            return new BigDecimal[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertBig("5", r[0], "min converged down to max");
        assertBig("5", r[1], "max");
        assertBig("5", r[2], "value clamped into the converged range");
    }

    /**
     * A bound value that ends up out of range must NOT abort the convergence of an
     * unbound opposite bound: the boundary still moves (Plan A), the bound value is
     * left to its binding, only the text is refreshed.
     */
    @Test
    public void boundValueDoesNotAbortConvergence() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMax(new BigDecimal("10"));
            SimpleObjectProperty<BigDecimal> valueSrc = new SimpleObjectProperty<>(new BigDecimal("5"));
            f.valueProperty().bind(valueSrc);
            SimpleObjectProperty<BigDecimal> minSrc = new SimpleObjectProperty<>(new BigDecimal("0"));
            f.minProperty().bind(minSrc);
            minSrc.set(new BigDecimal("20"));           // min -> 20; unbound max must converge up
            return new BigDecimal[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertBig("20", r[0], "min followed its binding");
        assertBig("20", r[1], "unbound max converged up to 20 (not left inverted at 10)");
        assertBig("5", r[2], "bound value stays (its binding owns it), does not abort the converge");
    }

    /**
     * Slider parity: converging into a {@code bound} opposite bound cannot move it,
     * so the convergence {@code set()} throws "A bound value cannot be set" — the
     * exception is surfaced, not swallowed with a WARNING.
     */
    @Test
    public void convergingIntoBoundOppositeThrows() {
        onFx(() -> {
            RXNumberField f = new RXNumberField();
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("10"));
            f.maxProperty().bind(maxSrc);
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> f.setMin(new BigDecimal("20")));   // must raise a bound max -> throws
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("bound value cannot be set"),
                    "Slider-style: a bound value cannot be set (was: " + ex.getMessage() + ")");
            return null;
        });
    }

    /** setRange no longer rejects an inverted pair; like the setters it converges. */
    @Test
    public void setRangeInvertedConverges() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setRange(new BigDecimal("20"), new BigDecimal("10"));   // inverted input, lenient
            return new BigDecimal[]{f.getMin(), f.getMax()};
        });
        // fresh field: min written first, then max=10 converges min down to 10.
        assertBig("10", r[0], "min converged down to max");
        assertBig("10", r[1], "max");
        assertTrue(r[0].compareTo(r[1]) <= 0, "range ends up ordered (min <= max)");
    }

    /** setRange sets both bounds and orders the writes so no spurious convergence intermediate is observed. */
    @Test
    public void setRangeSetsBothWithoutSpuriousConvergence() {
        Object[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("0"));
            f.setMax(new BigDecimal("10"));
            List<String> maxSeen = new ArrayList<>();
            f.maxProperty().addListener((o, ov, nv) -> maxSeen.add(nv == null ? "null" : nv.toPlainString()));
            f.setRange(new BigDecimal("20"), new BigDecimal("30"));   // [0,10] -> [20,30]
            return new Object[]{f.getMin(), f.getMax(), maxSeen};
        });
        assertBig("20", (BigDecimal) r[0], "min");
        assertBig("30", (BigDecimal) r[1], "max");
        @SuppressWarnings("unchecked")
        List<String> maxSeen = (List<String>) r[2];
        assertEquals(List.of("30"), maxSeen, "max should change 10 -> 30 directly, not via a converged 20");
    }

    /** setRange with a bound max rejects up front (IllegalStateException) without mutating either bound. */
    @Test
    public void setRangeWithBoundMaxThrowsWithoutMutating() {
        Object[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("0"));
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("50"));
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            try {
                f.setRange(new BigDecimal("10"), new BigDecimal("20"));
            } catch (IllegalStateException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getMin(), f.getMax()};
        });
        assertTrue((Boolean) r[0], "setRange with a bound max throws IllegalStateException");
        assertBig("0", (BigDecimal) r[1], "min left unchanged (no half-apply)");
        assertBig("50", (BigDecimal) r[2], "max left unchanged");
    }

    /**
     * setRange narrows around a bound value the same way the individual setters do:
     * both bounds are set and the now-out-of-range bound value is left to its
     * binding (caller's responsibility), not rejected.
     */
    @Test
    public void setRangeLeavesAnExcludedBoundValueToItsBinding() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("100"));
            f.setMax(new BigDecimal("1000"));
            SimpleObjectProperty<BigDecimal> src = new SimpleObjectProperty<>(new BigDecimal("500"));
            f.valueProperty().bind(src);
            f.setRange(new BigDecimal("0"), new BigDecimal("100"));   // 500 no longer fits, but is bound
            return new BigDecimal[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertBig("0", r[0], "min set");
        assertBig("100", r[1], "max set");
        assertBig("500", r[2], "bound value left as-is (its binding owns it)");
    }

    /**
     * A bound min follows its source without validation (bounds are lenient) and
     * without leaking anything onto the FX thread's uncaught handler — even for a
     * value a subclass validateValue would reject on the value axis.
     */
    @Test
    public void boundMinFollowsSourceWithoutEscaping() {
        Object[] r = onFx(() -> {
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler prev = fx.getUncaughtExceptionHandler();
            AtomicReference<Throwable> uncaught = new AtomicReference<>();
            fx.setUncaughtExceptionHandler((t, e) -> uncaught.set(e));
            try {
                RXNumberField f = new RXNumberField() {
                    @Override
                    protected void validateValue(BigDecimal candidate) {
                        if (candidate != null && candidate.signum() < 0) {
                            throw new IllegalArgumentException("negative not allowed");
                        }
                    }
                };
                SimpleObjectProperty<BigDecimal> minSrc = new SimpleObjectProperty<>(new BigDecimal("0"));
                f.minProperty().bind(minSrc);
                minSrc.set(new BigDecimal("-5"));       // bounds are not validated
                return new Object[]{f.getMin(), uncaught.get()};
            } finally {
                fx.setUncaughtExceptionHandler(prev);
            }
        });
        assertBig("-5", (BigDecimal) r[0], "bound min follows its source (bounds are lenient)");
        assertNull(r[1], "no uncaught exception escaped onto the FX thread");
    }

    /**
     * validateValue gates the {@code value} only; {@code min} / {@code max} are
     * lenient (Slider-style), so a negative bound is accepted while a negative
     * value is rejected and reverted.
     */
    @Test
    public void validateValueGatesValueNotBounds() {
        Object[] r = onFx(() -> {
            RXNumberField f = new RXNumberField() {
                @Override
                protected void validateValue(BigDecimal candidate) {
                    if (candidate != null && candidate.signum() < 0) {
                        throw new IllegalArgumentException("negative not allowed");
                    }
                }
            };
            f.setMin(new BigDecimal("-10"));   // bounds not validated: accepted
            f.setMax(new BigDecimal("-1"));
            boolean valueRejected = false;
            try {
                f.setValue(new BigDecimal("-5"));
            } catch (IllegalArgumentException expected) {
                valueRejected = true;
            }
            return new Object[]{f.getMin(), f.getMax(), valueRejected, f.getValue()};
        });
        assertBig("-10", (BigDecimal) r[0], "min accepted (bounds are lenient)");
        assertBig("-1", (BigDecimal) r[1], "max accepted (bounds are lenient)");
        assertTrue((Boolean) r[2], "setValue(-5) rejected by validateValue");
        assertNull(r[3], "value reverted to null after the rejection");
    }

    /**
     * A bound value pushed out of range cannot be clamped, but the text must not
     * stay stale — and it must not leak an uncaught exception onto the FX thread.
     */
    @Test
    public void boundValueOutOfRangeRefreshesTextWithoutUncaughtException() {
        Object[] r = onFx(() -> {
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler prev = fx.getUncaughtExceptionHandler();
            AtomicReference<Throwable> uncaught = new AtomicReference<>();
            fx.setUncaughtExceptionHandler((t, e) -> uncaught.set(e));
            try {
                RXNumberField field = new RXNumberField();
                field.setMax(new BigDecimal("10"));
                SimpleObjectProperty<BigDecimal> src = new SimpleObjectProperty<>(new BigDecimal("5"));
                field.valueProperty().bind(src);
                String textBefore = field.getText();
                src.set(new BigDecimal("20"));          // bound value pushed out of range
                RXNumberField ref = new RXNumberField();
                ref.setValue(new BigDecimal("20"));
                return new Object[]{field.getValue(), field.getText(), textBefore, ref.getText(), uncaught.get()};
            } finally {
                fx.setUncaughtExceptionHandler(prev);
            }
        });
        assertBig("20", (BigDecimal) r[0], "bound value stays 20");
        assertEquals("5", r[2], "text was '5' before the out-of-range push");
        assertEquals(r[3], r[1], "text refreshed to the actual bound value (not stale '5')");
        assertNull(r[4], "no uncaught exception routed to the FX thread's handler");
    }

    /**
     * A bound-driven clamp must keep the domain-rejection revert target in range:
     * after min/max clamps the value, a later rejected edit reverts to the clamped
     * (in-range) value, not a stale out-of-range one. (Regression: the clamp path
     * writes value under the reentrancy guard and must refresh lastValidValue.)
     */
    @Test
    public void boundsClampThenRejectedEditRevertsInRange() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(new BigDecimal("5"));   // value = 5
            f.setMax(new BigDecimal("3"));                                // clamps value 5 -> 3
            boolean threw = false;
            try {
                f.setValue(new BigDecimal("2.5"));                        // rejected by the integer domain
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getValue(), f.getMax()};
        });
        assertTrue((Boolean) r[0], "fractional value rejected by the integer domain");
        assertBig("3", (BigDecimal) r[1], "value reverts to the clamped 3, not the stale 5");
        assertTrue(((BigDecimal) r[1]).compareTo((BigDecimal) r[2]) <= 0, "reverted value stays within max (in range)");
    }

    /** With integer bounds an integer field's value always stays integral through a bound clamp. */
    @Test
    public void integerFieldWithIntegerBoundsStaysIntegral() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(new BigDecimal("50"));
            f.setMin(new BigDecimal("10"));
            f.setMax(new BigDecimal("100"));
            f.setValue(new BigDecimal("5"));            // below min -> clamps up to 10
            BigDecimal v = f.getValue();
            return new Object[]{v.toPlainString(), v.scale()};
        });
        assertEquals("10", r[0], "value clamped to the integer min");
        assertEquals(0, ((Integer) r[1]).intValue(), "value stays integral (scale 0)");
    }

    /**
     * A fractional bound must never make the value fractional: the clamp target is
     * snapped into the integer domain — lower bound up (ceil), upper bound down
     * (floor) — so the value stays integral and still honours the raw bound.
     */
    @Test
    public void integerFieldFractionalBoundsSnapClampTargetToInteger() {
        Object[] r = onFx(() -> {
            RXIntegerField lower = new RXIntegerField(new BigDecimal("1"));
            lower.setMin(new BigDecimal("1.5"));            // effective lower limit 2
            RXIntegerField upper = new RXIntegerField(new BigDecimal("9"));
            upper.setMax(new BigDecimal("8.5"));            // effective upper limit 8
            return new Object[]{lower.getValue(), upper.getValue()};
        });
        assertBig("2", (BigDecimal) r[0], "value clamped up to ceil(1.5) = 2, not left at fractional 1.5");
        assertEquals(0, ((BigDecimal) r[0]).scale(), "lower-clamped value is integral");
        assertBig("8", (BigDecimal) r[1], "value clamped down to floor(8.5) = 8");
        assertEquals(0, ((BigDecimal) r[1]).scale(), "upper-clamped value is integral");
    }

    /** Negative fractional bounds snap toward the interior: ceil(-1.2) = -1, floor(-2.6) = -3. */
    @Test
    public void integerFieldNegativeFractionalBoundsSnapTowardInterior() {
        Object[] r = onFx(() -> {
            RXIntegerField lower = new RXIntegerField(new BigDecimal("-5"));
            lower.setMin(new BigDecimal("-1.2"));           // effective lower limit -1
            RXIntegerField upper = new RXIntegerField(new BigDecimal("0"));
            upper.setMax(new BigDecimal("-2.6"));           // effective upper limit -3
            return new Object[]{lower.getValue(), upper.getValue()};
        });
        assertBig("-1", (BigDecimal) r[0], "value clamped up to ceil(-1.2) = -1");
        assertBig("-3", (BigDecimal) r[1], "value clamped down to floor(-2.6) = -3");
    }

    /**
     * A range with no integer member (min = 1.5, max = 1.8 -> effective [2, 1]) has
     * no solution: value-domain priority keeps the current integer value rather than
     * making it fractional or pinning it to a wrong bound.
     */
    @Test
    public void integerFieldEmptyEffectiveIntervalKeepsIntegerValue() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(new BigDecimal("5"));
            f.setMin(new BigDecimal("1.5"));                // effective lower 2
            f.setMax(new BigDecimal("1.8"));                // effective [2, 1] -> empty
            BigDecimal v = f.getValue();
            return new Object[]{v.toPlainString(), v.scale()};
        });
        assertEquals("5", r[0], "value kept at its current integer, not forced onto a fractional bound");
        assertEquals(0, ((Integer) r[1]).intValue(), "value stays integral (scale 0)");
    }
}

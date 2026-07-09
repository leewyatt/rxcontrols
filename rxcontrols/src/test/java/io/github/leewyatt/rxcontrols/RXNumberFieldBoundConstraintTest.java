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
 * Range-constraint behaviour for {@link RXNumberField}: unbound setters converge
 * the opposite bound to keep {@code min <= max}; when the opposite bound is bound
 * and cannot move, the inverted range suspends clamping instead of pushing the
 * value to a wrong bound; {@link RXNumberField#setRange} rejects an inverted pair
 * and sets both without a spurious convergence step; and the {@code validateValue}
 * hook gates {@code min} / {@code max}.
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

    /** When the opposite bound is bound (cannot converge), the range stays inverted and clamping is suspended. */
    @Test
    public void oppositeBoundInversionSuspendsClamp() {
        BigDecimal[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("10"));
            f.maxProperty().bind(maxSrc);
            f.setMin(new BigDecimal("20"));           // cannot converge a bound max
            f.setValue(new BigDecimal("15"));
            return new BigDecimal[]{f.getMin(), f.getMax(), f.getValue()};
        });
        assertBig("20", r[0], "min set");
        assertBig("10", r[1], "bound max unchanged");
        assertBig("15", r[2], "value left unclamped, not pushed to a wrong bound");
    }

    /** setRange rejects an inverted pair instead of converging. */
    @Test
    public void setRangeRejectsInverted() {
        onFx(() -> {
            RXNumberField f = new RXNumberField();
            assertThrows(IllegalArgumentException.class,
                    () -> f.setRange(new BigDecimal("20"), new BigDecimal("10")));
            return null;
        });
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

    /** setRange rejects a narrowing that would exclude a bound value, without mutating either bound. */
    @Test
    public void setRangeWithBoundValueOutsideThrowsWithoutMutating() {
        Object[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();
            f.setMin(new BigDecimal("100"));
            f.setMax(new BigDecimal("1000"));
            SimpleObjectProperty<BigDecimal> src = new SimpleObjectProperty<>(new BigDecimal("500"));
            f.valueProperty().bind(src);
            boolean threw = false;
            try {
                f.setRange(new BigDecimal("0"), new BigDecimal("100"));   // bound value 500 does not fit
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            return new Object[]{threw, f.getMin(), f.getMax(), f.getValue()};
        });
        assertTrue((Boolean) r[0], "setRange throws when the bound value does not fit the requested range");
        assertBig("100", (BigDecimal) r[1], "min left unchanged (no half-apply)");
        assertBig("1000", (BigDecimal) r[2], "max left unchanged");
        assertBig("500", (BigDecimal) r[3], "bound value unchanged");
    }

    /**
     * A bound min whose source produces a value a subclass validateValue rejects
     * cannot be reverted; it must log and be left as-is, not escape onto the FX
     * thread's uncaught handler (symmetric with the bound-value handling).
     */
    @Test
    public void boundMinFailingValidateDoesNotEscape() {
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
                minSrc.set(new BigDecimal("-5"));       // bound min now fails validate
                return new Object[]{f.getMin(), uncaught.get()};
            } finally {
                fx.setUncaughtExceptionHandler(prev);
            }
        });
        assertBig("-5", (BigDecimal) r[0], "bound min follows its source even when it fails validate");
        assertNull(r[1], "no uncaught exception escaped onto the FX thread");
    }

    /** validateValue gates min / max the same way it gates value. */
    @Test
    public void validateValueGatesMinAndMax() {
        Boolean[] r = onFx(() -> {
            RXNumberField f = new RXNumberField() {
                @Override
                protected void validateValue(BigDecimal candidate) {
                    if (candidate != null && candidate.signum() < 0) {
                        throw new IllegalArgumentException("negative not allowed");
                    }
                }
            };
            boolean minRejected = false;
            try {
                f.setMin(new BigDecimal("-10"));
            } catch (IllegalArgumentException expected) {
                minRejected = true;
            }
            boolean maxRejected = false;
            try {
                f.setMax(new BigDecimal("-1"));
            } catch (IllegalArgumentException expected) {
                maxRejected = true;
            }
            return new Boolean[]{minRejected, f.getMin() == null, maxRejected, f.getMax() == null};
        });
        assertTrue(r[0], "setMin(-10) rejected by validateValue");
        assertTrue(r[1], "min reverted to null");
        assertTrue(r[2], "setMax(-1) rejected by validateValue");
        assertTrue(r[3], "max reverted to null");
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
}

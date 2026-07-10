package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
 * Range-constraint behaviour for {@link RXDecimalField}, aligned with
 * {@link javafx.scene.control.Slider}: unbound setters converge the opposite
 * bound to keep {@code min <= max}; converging into a {@code bound} opposite
 * bound throws "A bound value cannot be set" (not swallowed); {@code setValue}
 * on a bound value is a no-op; {@link RXDecimalField#setRange} sets both bounds
 * leniently (converging, not rejecting, an inverted pair) but rejects up front
 * when a bound is {@code bound}. Bounds themselves are stored leniently and
 * never validated.
 */
public class RXDecimalFieldBoundConstraintTest {

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
            RXDecimalField f = new RXDecimalField();
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
            RXDecimalField f = new RXDecimalField();
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
     * unbound opposite bound: the boundary still moves, the bound value is
     * left to its binding, only the text is refreshed.
     */
    @Test
    public void boundValueDoesNotAbortConvergence() {
        BigDecimal[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField();
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
            RXDecimalField f = new RXDecimalField();
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("10"));
            f.maxProperty().bind(maxSrc);
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> f.setMin(new BigDecimal("20")));   // must raise a bound max -> throws
            assertTrue(ex.getMessage() != null && ex.getMessage().contains("bound value cannot be set"),
                    "Slider-style: a bound value cannot be set (was: " + ex.getMessage() + ")");
            return null;
        });
    }

    /** setRange does not reject an inverted pair; like the setters it converges. */
    @Test
    public void setRangeInvertedConverges() {
        BigDecimal[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField();
            f.setRange(new BigDecimal("20"), new BigDecimal("10"));   // inverted input, lenient
            return new BigDecimal[]{f.getMin(), f.getMax()};
        });
        // fresh field: min written first, then max=10 converges min down to 10.
        assertBig("10", r[0], "min converged down to max");
        assertBig("10", r[1], "max");
        assertTrue(r[0].compareTo(r[1]) <= 0, "range ends up ordered (min <= max)");
    }

    /**
     * An inverted pair converges to [max, max] deterministically, whatever the
     * previous bounds were: the fixed min-first write order means the later max
     * write always pulls min down onto it.
     */
    @Test
    public void setRangeInvertedConvergesFromAnyPriorState() {
        BigDecimal[][] r = onFx(() -> {
            RXDecimalField wide = new RXDecimalField();
            wide.setRange(new BigDecimal("0"), new BigDecimal("100"));
            wide.setRange(new BigDecimal("20"), new BigDecimal("10"));

            RXDecimalField narrow = new RXDecimalField();
            narrow.setRange(new BigDecimal("0"), new BigDecimal("5"));
            narrow.setRange(new BigDecimal("20"), new BigDecimal("10"));

            return new BigDecimal[][]{
                    {wide.getMin(), wide.getMax()},
                    {narrow.getMin(), narrow.getMax()}};
        });
        assertBig("10", r[0][0], "min after inverted setRange from [0,100]");
        assertBig("10", r[0][1], "max after inverted setRange from [0,100]");
        assertBig("10", r[1][0], "min after inverted setRange from [0,5]");
        assertBig("10", r[1][1], "max after inverted setRange from [0,5]");
    }

    /** setRange sets both bounds and orders the writes so no spurious convergence intermediate is observed. */
    @Test
    public void setRangeSetsBothWithoutSpuriousConvergence() {
        Object[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField();
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
            RXDecimalField f = new RXDecimalField();
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
            RXDecimalField f = new RXDecimalField();
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
     * without leaking anything onto the FX thread's uncaught handler.
     */
    @Test
    public void boundMinFollowsSourceWithoutEscaping() {
        Object[] r = onFx(() -> {
            Thread fx = Thread.currentThread();
            Thread.UncaughtExceptionHandler prev = fx.getUncaughtExceptionHandler();
            AtomicReference<Throwable> uncaught = new AtomicReference<>();
            fx.setUncaughtExceptionHandler((t, e) -> uncaught.set(e));
            try {
                RXDecimalField f = new RXDecimalField();
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
                RXDecimalField field = new RXDecimalField();
                field.setMax(new BigDecimal("10"));
                SimpleObjectProperty<BigDecimal> src = new SimpleObjectProperty<>(new BigDecimal("5"));
                field.valueProperty().bind(src);
                String textBefore = field.getText();
                src.set(new BigDecimal("20"));          // bound value pushed out of range
                RXDecimalField ref = new RXDecimalField();
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
     * In the transiently inverted range left by a convergence that threw on a
     * bound opposite bound, the clamp keeps Slider's Utils.clamp order —
     * uniform across all four typed fields. Here the below-min candidate is
     * pulled up to min (a candidate above max would pin to max instead).
     */
    @Test
    public void invertedTransientRangeClampsToMinLikeSlider() {
        Object[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField();
            SimpleObjectProperty<BigDecimal> maxSrc = new SimpleObjectProperty<>(new BigDecimal("10"));
            f.maxProperty().bind(maxSrc);
            boolean threw = false;
            try {
                f.setMin(new BigDecimal("20"));    // convergence into the bound max throws
            } catch (RuntimeException expected) {
                threw = true;
            }
            f.setValue(new BigDecimal("5"));       // inverted [20,10]: pins to min
            return new Object[]{threw, f.getMin(), f.getMax(), f.getValue()};
        });
        assertTrue((Boolean) r[0], "convergence into the bound max threw");
        assertBig("20", (BigDecimal) r[1], "min stayed at 20 (inverted transient)");
        assertBig("10", (BigDecimal) r[2], "bound max stayed at 10");
        assertBig("20", (BigDecimal) r[3], "below-min candidate pulled up to min (Slider's Utils.clamp order)");
    }

    /**
     * A bound text property bypasses the edit filter (TextInputControl only
     * filters unbound text), handing raw strings straight to the converter: a
     * scientific-notation or garbage string must fail the parse inside
     * TextFormatter.updateValue (which catches and re-renders) and must never
     * pollute the value.
     */
    @Test
    public void boundTextBypassingFilterCannotPolluteValue() {
        Object[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("7"));
            SimpleStringProperty textSrc = new SimpleStringProperty("7");
            f.textProperty().bind(textSrc);
            textSrc.set("1e5");                  // bypasses the filter, reaches the converter
            f.commitValue();
            BigDecimal afterScientific = f.getValue();
            textSrc.set("abc");
            f.commitValue();
            BigDecimal afterGarbage = f.getValue();
            return new Object[]{afterScientific, afterGarbage};
        });
        assertBig("7", (BigDecimal) r[0], "scientific notation must not reach the value");
        assertBig("7", (BigDecimal) r[1], "garbage text must not reach the value");
    }
}

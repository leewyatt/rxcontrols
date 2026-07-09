package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Commit behaviour for {@link RXFormattedNumberField} under a lossy display
 * format (percent / currency with fixed fraction digits): an edit whose parsed
 * value re-renders to the currently-displayed text must still be committed to the
 * value property, not silently dropped.
 */
public class RXFormattedNumberFieldCommitTest {

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

    /** Editing 33% -> 33.4% in a 0-fraction percent field must commit 0.334, not stay 0.33. */
    @Test
    public void committingSubDisplayPrecisionPercentKeepsTheEdit() {
        BigDecimal v = onFx(() -> {
            RXFormattedNumberField f = new RXFormattedNumberField();
            f.setNumberFormat(NumberFormat.getPercentInstance(Locale.US));
            f.setValue(new BigDecimal("0.33"));               // displays "33%"
            f.replaceText(0, f.getText().length(), "33.4%");  // uncommitted user edit (like typing)
            f.commitValue();                                  // ENTER / focus-loss commit
            return f.getValue();
        });
        assertBig("0.334", v, "committed 33.4% must set value 0.334, not drop back to 0.33");
    }

    /** Editing $1.23 -> $1.231 in a 2-fraction currency field must commit 1.231, not stay 1.234. */
    @Test
    public void committingSubDisplayPrecisionCurrencyKeepsTheEdit() {
        BigDecimal v = onFx(() -> {
            RXFormattedNumberField f = new RXFormattedNumberField();
            f.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
            f.setValue(new BigDecimal("1.234"));               // displays "$1.23"
            f.replaceText(0, f.getText().length(), "$1.231");  // uncommitted user edit within the same bucket
            f.commitValue();
            return f.getValue();
        });
        assertBig("1.231", v, "committed $1.231 must set value 1.231, not stay 1.234");
    }

    /** A deliberate scale-only edit in a plain field (scale is observable via toPlainString) must commit. */
    @Test
    public void committingScaleOnlyEditInPlainFieldIsPreserved() {
        Object[] r = onFx(() -> {
            RXNumberField f = new RXNumberField();               // plain, toPlainString converter
            f.setValue(new BigDecimal("100"));                   // scale 0, displays "100"
            f.replaceText(0, f.getText().length(), "100.00");    // user edits to scale 2
            f.commitValue();
            BigDecimal v = f.getValue();
            return new Object[]{v.toPlainString(), v.scale()};
        });
        assertEquals("100.00", r[0], "committed 100.00 must preserve the scale-2 value, not collapse to 100");
        assertEquals(2, ((Integer) r[1]).intValue(), "value scale must be 2");
    }

    /** A no-op commit (no edit) of a scale-drifting render must not spuriously change the value. */
    @Test
    public void committingWithoutEditKeepsTheValueUnchanged() {
        BigDecimal v = onFx(() -> {
            RXFormattedNumberField f = new RXFormattedNumberField();
            f.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
            f.setValue(new BigDecimal("100"));   // scale 0, displays "$100.00" (parses back scale 2)
            f.commitValue();                     // focus-loss / ENTER with no edit
            return f.getValue();
        });
        assertBig("100", v, "value stays 100 on a no-op commit (no spurious scale drift)");
    }

    /**
     * An equal-value edit (deleting trailing zeros that do not change the number)
     * must not strand the edit-origin flag and let a later no-op commit drift the
     * scale (100 -> 100.00 with no user edit).
     */
    @Test
    public void equalValueEditThenNoOpCommitDoesNotDriftScale() {
        Object[] r = onFx(() -> {
            RXFormattedNumberField f = new RXFormattedNumberField();
            f.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
            f.setValue(new BigDecimal("100"));                 // scale 0, displays "$100.00"
            f.replaceText(0, f.getText().length(), "$100");    // equal-value edit (parses back to 100)
            f.commitValue();                                    // commit #1: value unchanged
            f.commitValue();                                    // commit #2: no edit
            BigDecimal v = f.getValue();
            return new Object[]{v.toPlainString(), v.scale()};
        });
        assertEquals("100", r[0], "value stays 100 after an equal-value edit followed by a no-op commit");
        assertEquals(0, ((Integer) r[1]).intValue(), "no scale drift (stays scale 0)");
    }
}

package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Behavioral parity between each plain number field and its Material sibling.
 * The value models are deliberate line-for-line copies (the engine owns the
 * shared live logic); the copyable surface that can still drift is the clamp,
 * the min/max convergence {@code invalidated()}, {@code setRange}, the Double
 * sanitize wiring, and the Decimal numberFormat wiring — so every scenario
 * here drives the twins through the same script
 * and asserts identical end states. The inverted-transient-range scenario is
 * the one script that catches a drifted clamp order (a generic out-of-range
 * clamp cannot).
 */
public class MaterialNumberFieldParityTest {

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

    // ==================== Integer ====================

    @Test
    public void integerTwinsStayInLockstep() {
        runOnFx(() -> {
            RXIntegerField plain = new RXIntegerField();
            RXMaterialIntegerField material = new RXMaterialIntegerField();

            // Clamp: below min, above max, null never clamped.
            plain.setRange(0, 100);
            material.setRange(0, 100);
            plain.setValue(-5);
            material.setValue(-5);
            assertEquals(plain.getValue(), material.getValue(), "below-min clamp drifted");
            plain.setValue(200);
            material.setValue(200);
            assertEquals(plain.getValue(), material.getValue(), "above-max clamp drifted");
            plain.setValue(null);
            material.setValue(null);
            assertNull(plain.getValue());
            assertEquals(plain.getText(), material.getText(), "null render drifted");

            // Convergence: raising min past max pulls max up.
            plain.setMin(150);
            material.setMin(150);
            assertEquals(plain.getMax(), material.getMax(), "min-past-max convergence drifted");

            // Commit: same typed edit commits the same value and text.
            plain.replaceText(0, plain.getText().length(), "42");
            material.replaceText(0, material.getText().length(), "42");
            plain.commitValue();
            material.commitValue();
            assertEquals(plain.getValue(), material.getValue(), "commit drifted");
            assertEquals(plain.getText(), material.getText(), "post-commit render drifted");

            // clear() commits null on both sides.
            plain.clear();
            material.clear();
            assertNull(plain.getValue());
            assertNull(material.getValue());
        });
    }

    @Test
    public void integerInvertedTransientRangeClampsInTheSameOrder() {
        runOnFx(() -> {
            RXIntegerField plain = new RXIntegerField();
            RXMaterialIntegerField material = new RXMaterialIntegerField();

            // Bind max so the min-past-max convergence throws, leaving a transiently
            // inverted range [20, 10]. The lo-first clamp order (Slider Utils.clamp)
            // then pins a low candidate to min and a high candidate to max — the one
            // observable difference a drifted clamp order would produce.
            plain.maxProperty().bind(new SimpleIntegerProperty(10));
            material.maxProperty().bind(new SimpleIntegerProperty(10));
            assertThrows(RuntimeException.class, () -> plain.setMin(20),
                    "convergence into a bound max must throw");
            assertThrows(RuntimeException.class, () -> material.setMin(20),
                    "convergence into a bound max must throw");
            plain.setValue(5);
            material.setValue(5);
            assertEquals(plain.getValue(), material.getValue(),
                    "low candidate in the inverted range must pin identically (lo-first)");
            plain.setValue(25);
            material.setValue(25);
            assertEquals(plain.getValue(), material.getValue(),
                    "high candidate in the inverted range must pin identically");
            // The in-band candidate (between max=10 and min=20) is the only input
            // that distinguishes the clamp order: lo-first pins it to min=20, a
            // drifted hi-first copy would pin it to max=10. Assert the absolute
            // lo-first pin on both twins — out-of-band candidates cannot tell.
            plain.setValue(15);
            material.setValue(15);
            assertEquals(20, plain.getValue(), "in-band candidate must pin lo-first to min");
            assertEquals(20, material.getValue(), "in-band candidate must pin lo-first to min");
        });
    }

    @Test
    public void longInvertedTransientRangeClampsInTheSameOrder() {
        runOnFx(() -> {
            RXLongField plain = new RXLongField();
            RXMaterialLongField material = new RXMaterialLongField();

            plain.maxProperty().bind(new SimpleLongProperty(10L));
            material.maxProperty().bind(new SimpleLongProperty(10L));
            assertThrows(RuntimeException.class, () -> plain.setMin(20L),
                    "convergence into a bound max must throw");
            assertThrows(RuntimeException.class, () -> material.setMin(20L),
                    "convergence into a bound max must throw");
            plain.setValue(15L);
            material.setValue(15L);
            assertEquals(20L, plain.getValue(), "in-band candidate must pin lo-first to min");
            assertEquals(20L, material.getValue(), "in-band candidate must pin lo-first to min");
        });
    }

    @Test
    public void doubleInvertedTransientRangeClampsInTheSameOrder() {
        runOnFx(() -> {
            RXDoubleField plain = new RXDoubleField();
            RXMaterialDoubleField material = new RXMaterialDoubleField();

            plain.maxProperty().bind(new SimpleDoubleProperty(10.0));
            material.maxProperty().bind(new SimpleDoubleProperty(10.0));
            assertThrows(RuntimeException.class, () -> plain.setMin(20.0),
                    "convergence into a bound max must throw");
            assertThrows(RuntimeException.class, () -> material.setMin(20.0),
                    "convergence into a bound max must throw");
            plain.setValue(15.0);
            material.setValue(15.0);
            assertEquals(20.0, plain.getValue(), "in-band candidate must pin lo-first to min");
            assertEquals(20.0, material.getValue(), "in-band candidate must pin lo-first to min");
        });
    }

    @Test
    public void integerSetRangeParity() {
        runOnFx(() -> {
            RXIntegerField plain = new RXIntegerField();
            RXMaterialIntegerField material = new RXMaterialIntegerField();

            // Inverted pair converges deterministically to [max, max].
            plain.setRange(20, 10);
            material.setRange(20, 10);
            assertEquals(plain.getMin(), material.getMin(), "inverted setRange min drifted");
            assertEquals(plain.getMax(), material.getMax(), "inverted setRange max drifted");

            // Bound side rejects up front, leaving both bounds unchanged.
            plain.minProperty().bind(new SimpleIntegerProperty(0));
            material.minProperty().bind(new SimpleIntegerProperty(0));
            assertThrows(IllegalStateException.class, () -> plain.setRange(1, 2));
            assertThrows(IllegalStateException.class, () -> material.setRange(1, 2));
            assertEquals(plain.getMax(), material.getMax(),
                    "failure-atomic setRange must leave the twins identical");
        });
    }

    // ==================== Long ====================

    @Test
    public void longTwinsStayInLockstep() {
        runOnFx(() -> {
            RXLongField plain = new RXLongField();
            RXMaterialLongField material = new RXMaterialLongField();

            plain.setRange(0L, 100L);
            material.setRange(0L, 100L);
            plain.setValue(-5L);
            material.setValue(-5L);
            assertEquals(plain.getValue(), material.getValue(), "below-min clamp drifted");
            plain.setValue(200L);
            material.setValue(200L);
            assertEquals(plain.getValue(), material.getValue(), "above-max clamp drifted");

            plain.setMin(150L);
            material.setMin(150L);
            assertEquals(plain.getMax(), material.getMax(), "min-past-max convergence drifted");

            // 2^53 + 1: the value that separates a long-exact model from a
            // double-backed one — both twins must carry it exactly.
            plain.setRange(Long.MIN_VALUE, Long.MAX_VALUE);
            material.setRange(Long.MIN_VALUE, Long.MAX_VALUE);
            plain.replaceText(0, plain.getText().length(), "9007199254740993");
            material.replaceText(0, material.getText().length(), "9007199254740993");
            plain.commitValue();
            material.commitValue();
            assertEquals(9007199254740993L, plain.getValue());
            assertEquals(plain.getValue(), material.getValue(), "long-exact commit drifted");

            plain.clear();
            material.clear();
            assertNull(plain.getValue());
            assertNull(material.getValue());
        });
    }

    // ==================== Double ====================

    @Test
    public void doubleTwinsStayInLockstep() {
        runOnFx(() -> {
            RXDoubleField plain = new RXDoubleField();
            RXMaterialDoubleField material = new RXMaterialDoubleField();

            plain.setRange(0.0, 10.0);
            material.setRange(0.0, 10.0);
            plain.setValue(-1.5);
            material.setValue(-1.5);
            assertEquals(plain.getValue(), material.getValue(), "below-min clamp drifted");
            plain.setValue(0.1 + 0.2);
            material.setValue(0.1 + 0.2);
            assertEquals(plain.getText(), material.getText(),
                    "binary-float rendering drifted (both must show the exact representation)");

            // Non-finite rejection: both coerce to null and throw.
            assertThrows(IllegalArgumentException.class, () -> plain.setValue(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> material.setValue(Double.NaN));
            assertNull(plain.getValue());
            assertNull(material.getValue());
            assertEquals(plain.getText(), material.getText(), "post-rejection state drifted");

            // NaN bound = no constraint on both sides.
            plain.setMin(Double.NaN);
            material.setMin(Double.NaN);
            plain.setValue(-100.0);
            material.setValue(-100.0);
            assertEquals(plain.getValue(), material.getValue(), "NaN-bound semantics drifted");

            // An infinite bound is never a clamp target: the isFinite guard is
            // the load-bearing line — a copy that lost it would clamp 5.0 to
            // +Infinity here and break the finite-value contract. (NaN bounds
            // cannot catch that drift: NaN comparisons are self-protecting.)
            plain.setMax(Double.POSITIVE_INFINITY);
            material.setMax(Double.POSITIVE_INFINITY);
            plain.setValue(5.0);
            material.setValue(5.0);
            plain.setMin(Double.POSITIVE_INFINITY);
            material.setMin(Double.POSITIVE_INFINITY);
            assertEquals(5.0, plain.getValue(), "an infinite bound must never clamp");
            assertEquals(5.0, material.getValue(), "an infinite bound must never clamp");
        });
    }

    // ==================== Decimal ====================

    @Test
    public void decimalTwinsStayInLockstep() {
        runOnFx(() -> {
            RXDecimalField plain = new RXDecimalField();
            RXMaterialDecimalField material = new RXMaterialDecimalField();

            plain.setRange(BigDecimal.ZERO, new BigDecimal("100"));
            material.setRange(BigDecimal.ZERO, new BigDecimal("100"));
            plain.setValue(new BigDecimal("-5"));
            material.setValue(new BigDecimal("-5"));
            assertEquals(plain.getValue(), material.getValue(), "below-min clamp drifted");

            // Scale-preserving commit through the plain (null-format) converter.
            plain.setValue(new BigDecimal("100"));
            material.setValue(new BigDecimal("100"));
            plain.replaceText(0, plain.getText().length(), "100.00");
            material.replaceText(0, material.getText().length(), "100.00");
            plain.commitValue();
            material.commitValue();
            assertEquals(plain.getValue(), material.getValue(),
                    "scale-only edit must commit identically");
            assertEquals(2, material.getValue().scale(), "committed scale must be preserved");

            // numberFormat wiring: assigning a format re-renders on both sides.
            NumberFormat plainFormat = NumberFormat.getNumberInstance(Locale.US);
            NumberFormat materialFormat = NumberFormat.getNumberInstance(Locale.US);
            plain.setValue(new BigDecimal("1234.5"));
            material.setValue(new BigDecimal("1234.5"));
            plain.setNumberFormat(plainFormat);
            material.setNumberFormat(materialFormat);
            assertEquals(plain.getText(), material.getText(), "numberFormat re-render drifted");

            // Convergence with a bound opposite bound throws on both sides,
            // leaving the same transiently inverted range and the same lo-first pin.
            plain.setNumberFormat(null);
            material.setNumberFormat(null);
            plain.maxProperty().bind(new SimpleObjectProperty<>(BigDecimal.TEN));
            material.maxProperty().bind(new SimpleObjectProperty<>(BigDecimal.TEN));
            assertThrows(RuntimeException.class, () -> plain.setMin(new BigDecimal("20")));
            assertThrows(RuntimeException.class, () -> material.setMin(new BigDecimal("20")));
            plain.setValue(new BigDecimal("5"));
            material.setValue(new BigDecimal("5"));
            assertEquals(plain.getValue(), material.getValue(),
                    "inverted-transient clamp order drifted");
            // In-band candidate (between max=10 and min=20): the only input that
            // distinguishes lo-first from a drifted hi-first copy (see the
            // integer scenario). Assert the absolute lo-first pin to min=20.
            plain.setValue(new BigDecimal("15"));
            material.setValue(new BigDecimal("15"));
            assertEquals(0, new BigDecimal("20").compareTo(plain.getValue()),
                    "in-band candidate must pin lo-first to min");
            assertEquals(0, new BigDecimal("20").compareTo(material.getValue()),
                    "in-band candidate must pin lo-first to min");

            plain.clear();
            material.clear();
            assertNull(plain.getValue());
            assertNull(material.getValue());
        });
    }

    @Test
    public void decimalSetRangeParity() {
        runOnFx(() -> {
            // The Decimal setRange copy is the structurally special one: its
            // ordering logic is null-aware. Pin all three states on both twins.
            RXDecimalField plain = new RXDecimalField();
            RXMaterialDecimalField material = new RXMaterialDecimalField();

            // Inverted pair converges deterministically to [max, max].
            plain.setRange(new BigDecimal("20"), BigDecimal.TEN);
            material.setRange(new BigDecimal("20"), BigDecimal.TEN);
            assertEquals(plain.getMin(), material.getMin(), "inverted setRange min drifted");
            assertEquals(plain.getMax(), material.getMax(), "inverted setRange max drifted");
            assertEquals(0, BigDecimal.TEN.compareTo(plain.getMin()),
                    "inverted pair must converge to [max, max]");

            // A null bound is unbounded and flows through the null-aware ordering.
            plain.setRange(null, BigDecimal.ONE);
            material.setRange(null, BigDecimal.ONE);
            assertEquals(plain.getMin(), material.getMin(), "null-bound setRange drifted");
            assertEquals(plain.getMax(), material.getMax(), "null-bound setRange drifted");

            // A bound side rejects up front, leaving both bounds unchanged.
            plain.minProperty().bind(new SimpleObjectProperty<>(BigDecimal.ZERO));
            material.minProperty().bind(new SimpleObjectProperty<>(BigDecimal.ZERO));
            assertThrows(IllegalStateException.class,
                    () -> plain.setRange(BigDecimal.ONE, BigDecimal.TEN));
            assertThrows(IllegalStateException.class,
                    () -> material.setRange(BigDecimal.ONE, BigDecimal.TEN));
            assertEquals(plain.getMax(), material.getMax(),
                    "failure-atomic setRange must leave the twins identical");
        });
    }

    // ==================== helpers ====================

    private static void runOnFx(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not finish");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        if (failure.get() instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure.get() instanceof Error error) {
            throw error;
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
    }
}

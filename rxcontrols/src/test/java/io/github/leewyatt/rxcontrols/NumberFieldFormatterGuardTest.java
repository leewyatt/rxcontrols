package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.NumberFieldEngine;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The internal-TextFormatter guard shared by the typed number fields: an
 * external {@code setTextFormatter} is reverted with a WARNING, and binding
 * the inherited {@code textFormatterProperty} is repaired with failure
 * atomicity — unbind, restore the internal formatter, WARNING. Both paths
 * assert the after-state only: the guard is an external listener whose
 * exceptions would never reach the caller, so there is nothing to
 * {@code assertThrows}.
 */
public class NumberFieldFormatterGuardTest {

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

    private static TextFormatter<String> foreignFormatter() {
        return new TextFormatter<>(new StringConverter<>() {
            @Override
            public String toString(String s) {
                return s == null ? "" : s;
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
    }

    /** Runs the body while capturing WARNING records from the engine's logger. */
    private static LogRecord captureEngineWarning(Runnable body) {
        Logger logger = Logger.getLogger(NumberFieldEngine.class.getName());
        AtomicReference<LogRecord> captured = new AtomicReference<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel() == Level.WARNING) {
                    captured.set(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);
        try {
            body.run();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(useParentHandlers);
        }
        return captured.get();
    }

    /** An external setTextFormatter is reverted, a WARNING logged, commits keep working. */
    @Test
    public void externalSetTextFormatterIsRevertedWithWarning() {
        Object[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("1"));
            TextFormatter<?> internal = f.getTextFormatter();
            LogRecord warning = captureEngineWarning(() -> f.setTextFormatter(foreignFormatter()));
            f.setText("2");
            f.commitValue();
            return new Object[]{internal, f.getTextFormatter(), warning, f.getValue()};
        });
        assertSame(r[0], r[1], "internal formatter restored");
        assertTrue(r[2] != null, "a WARNING was logged");
        assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) r[3]),
                "commit still works after the guard repaired the field");
    }

    /** setTextFormatter(null) is likewise reverted. */
    @Test
    public void nullTextFormatterIsReverted() {
        Object[] r = onFx(() -> {
            RXIntegerField f = new RXIntegerField(1);
            TextFormatter<?> internal = f.getTextFormatter();
            LogRecord warning = captureEngineWarning(() -> f.setTextFormatter(null));
            return new Object[]{internal, f.getTextFormatter(), warning};
        });
        assertSame(r[0], r[1], "internal formatter restored");
        assertTrue(r[2] != null, "a WARNING was logged");
    }

    /**
     * Binding the inherited textFormatterProperty is repaired with failure
     * atomicity: the binding is removed, the internal formatter restored, a
     * WARNING logged, and the field keeps committing. The guard cannot throw to
     * the bind() caller (ExpressionHelper swallows external listener
     * exceptions), so only the after-state is asserted.
     */
    @Test
    public void bindingTextFormatterIsUnboundAndRestored() {
        Object[] r = onFx(() -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("1"));
            TextFormatter<?> internal = f.getTextFormatter();
            SimpleObjectProperty<TextFormatter<?>> src =
                    new SimpleObjectProperty<>(foreignFormatter());
            LogRecord warning = captureEngineWarning(() -> f.textFormatterProperty().bind(src));
            boolean stillBound = f.textFormatterProperty().isBound();
            f.setText("3");
            f.commitValue();
            return new Object[]{stillBound, internal, f.getTextFormatter(), warning, f.getValue()};
        });
        assertFalse((Boolean) r[0], "the unsupported binding was removed");
        assertSame(r[1], r[2], "internal formatter restored");
        assertTrue(r[3] != null, "a WARNING was logged");
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) r[4]),
                "commit still works after the guard repaired the field");
    }
}

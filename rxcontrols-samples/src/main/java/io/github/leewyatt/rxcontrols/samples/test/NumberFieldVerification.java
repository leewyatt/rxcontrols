package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXDecimalField;
import io.github.leewyatt.rxcontrols.RXDoubleField;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Verification runner for the typed number-field family (RXIntegerField /
 * RXDoubleField / RXDecimalField).
 * <p>
 * Each check exercises one headline contract and prints a single PASS / FAIL
 * line. The runner deliberately stays off {@code Application}, {@code Scene},
 * and {@code Stage}: it only calls {@link Platform#startup(Runnable)} so
 * {@code Control.<clinit>} can resolve the default stylesheet, then runs every
 * check on the FX thread. No scene graph is built, so no node is ever laid out
 * or rendered.
 */
public final class NumberFieldVerification {

    private NumberFieldVerification() {
    }

    public static void main(String[] args) throws Throwable {
        List<Result> results = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                runAll(results);
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();

        if (failure.get() != null) {
            throw failure.get();
        }
        printSummary(results);
        System.exit(anyFailed(results) ? 1 : 0);
    }

    // ==================== Test cases ====================

    private static void runAll(List<Result> results) {
        // -------------------- A: typed values --------------------

        check(results, "A.1 RXIntegerField.getValue() is a real Integer", () -> {
            RXIntegerField f = new RXIntegerField(42);
            Integer v = f.getValue();
            return v != null && v == 42 ? null : "value=" + v + " (expected 42)";
        });

        check(results, "A.2 RXDoubleField renders without trailing .0 or scientific notation", () -> {
            RXDoubleField f = new RXDoubleField(2.0);
            if (!"2".equals(f.getText())) {
                return "text='" + f.getText() + "' (expected '2')";
            }
            f.setValue(1e308);
            String t = f.getText();
            return t.indexOf('E') < 0 && t.indexOf('e') < 0
                    ? null
                    : "text contains scientific notation: " + t;
        });

        check(results, "A.3 RXDecimalField(1.23456) preserves value across a format switch + commit", () -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("1.23456"));
            f.setNumberFormat(new DecimalFormat("0.##"));   // lossy display "1.23"
            f.commitValue();                                // no-edit commit must not corrupt
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("1.23456")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 1.23456)";
        });

        // -------------------- B: domain policies --------------------

        check(results, "B.1 RXIntegerField overflow (2^31) rolls the text back", () -> {
            RXIntegerField f = new RXIntegerField(7);
            f.setText("2147483648");
            f.commitValue();
            Integer v = f.getValue();
            if (v == null || v != 7) {
                return "value=" + v + " (expected 7)";
            }
            return "7".equals(f.getText()) ? null : "text='" + f.getText() + "' (expected '7')";
        });

        check(results, "B.2 RXDoubleField.setValue(NaN) coerces to null and throws IAE", () -> {
            RXDoubleField f = new RXDoubleField(5.0);
            try {
                f.setValue(Double.NaN);
                return "no exception thrown — finiteness policy missing";
            } catch (IllegalArgumentException e) {
                if (f.getValue() != null) {
                    return "value=" + f.getValue() + " (expected null after coerce)";
                }
                return "".equals(f.getText()) ? null : "text='" + f.getText() + "' (expected empty)";
            }
        });

        check(results, "B.3 new RXDoubleField(Double.NaN) fails fast", () -> {
            try {
                new RXDoubleField(Double.NaN);
                return "no exception thrown — finiteness policy missing";
            } catch (IllegalArgumentException e) {
                return null;
            }
        });

        // -------------------- C: setTextFormatter guard (restore + WARNING log) --------------------

        check(results, "C.1 setTextFormatter(other) → WARNING log + restore", () -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("1"));
            TextFormatter<?> original = f.getTextFormatter();
            TextFormatter<String> other = new TextFormatter<>(new StringConverter<>() {
                @Override
                public String toString(String s) { return s == null ? "" : s; }
                @Override
                public String fromString(String s) { return s; }
            });
            return assertGuardRejects(f, original, () -> f.setTextFormatter(other));
        });

        check(results, "C.2 textFormatterProperty().bind(...) → unbind + restore + WARNING", () -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("1"));
            TextFormatter<?> original = f.getTextFormatter();
            SimpleObjectProperty<TextFormatter<?>> src = new SimpleObjectProperty<>(
                    new TextFormatter<>(new StringConverter<String>() {
                        @Override
                        public String toString(String s) { return s == null ? "" : s; }
                        @Override
                        public String fromString(String s) { return s; }
                    }));
            String guardResult = assertGuardRejects(f, original,
                    () -> f.textFormatterProperty().bind(src));
            if (guardResult != null) {
                return guardResult;
            }
            return f.textFormatterProperty().isBound()
                    ? "property still bound — failure atomicity broken"
                    : null;
        });

        // -------------------- D: regression invariants --------------------

        check(results, "D.1 setText('-') then commitValue() keeps the previous value", () -> {
            RXDecimalField f = new RXDecimalField(new BigDecimal("100"));
            f.setText("-");
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("100")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 100)";
        });

        check(results, "D.2 setText('') then commitValue() clears value to null", () -> {
            RXIntegerField f = new RXIntegerField(7);
            f.setText("");
            f.commitValue();
            return f.getValue() == null ? null : "value=" + f.getValue();
        });

        check(results, "D.3 setValue(150) with max=100 clamps to 100 and text follows", () -> {
            RXIntegerField f = new RXIntegerField();
            f.setMax(100);
            f.setValue(150);
            Integer v = f.getValue();
            if (v == null || v != 100) {
                return "value=" + v + " (expected 100)";
            }
            String t = f.getText();
            return "100".equals(t) ? null : "text='" + t + "' (expected '100')";
        });

        check(results, "D.4 NumberFormat in-place mutation visible to parse ('$50' after setPositivePrefix('$'))", () -> {
            DecimalFormat df = new DecimalFormat("0.##");
            RXDecimalField f = new RXDecimalField(new BigDecimal("100"));
            f.setNumberFormat(df);
            df.setPositivePrefix("$");
            f.setText("$50");
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("50")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 50)";
        });

        check(results, "D.5 bound value is never clamped; setMin still converges bounds", () -> {
            RXDoubleField f = new RXDoubleField();
            SimpleObjectProperty<Double> source = new SimpleObjectProperty<>(5.0);
            f.valueProperty().bind(source);
            f.setMin(10.0);
            Double v = f.getValue();
            return v != null && v == 5.0
                    ? null
                    : "value=" + v + " (expected the bound 5.0, untouched)";
        });
    }

    /**
     * Runs {@code action} while a temporary handler is attached to the
     * number-field engine logger, then asserts that a {@code WARNING} record
     * was emitted and that the field's text formatter is still {@code expected}.
     */
    private static String assertGuardRejects(RXDecimalField f, TextFormatter<?> expected, Runnable action) {
        Logger logger = Logger.getLogger("io.github.leewyatt.rxcontrols.internal.number.NumberFieldEngine");
        AtomicReference<LogRecord> captured = new AtomicReference<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                captured.set(record);
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
            action.run();
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(useParentHandlers);
        }
        LogRecord record = captured.get();
        if (record == null || record.getLevel() != Level.WARNING) {
            return "expected a WARNING log record, got "
                    + (record == null ? "none" : record.getLevel());
        }
        return f.getTextFormatter() == expected
                ? null
                : "formatter not restored — guard left control in broken state";
    }

    // ==================== Runner plumbing ====================

    @FunctionalInterface
    private interface Check {
        String run() throws Throwable;
    }

    private static void check(List<Result> results, String name, Check c) {
        try {
            String fail = c.run();
            results.add(new Result(name, fail == null, fail));
        } catch (Throwable t) {
            results.add(new Result(name, false, "EXCEPTION " + t.getClass().getSimpleName()
                    + ": " + t.getMessage()));
        }
    }

    private record Result(String name, boolean pass, String detail) { }

    private static boolean anyFailed(List<Result> results) {
        for (Result r : results) {
            if (!r.pass) {
                return true;
            }
        }
        return false;
    }

    private static void printSummary(List<Result> results) {
        StringBuilder out = new StringBuilder();
        String line = "=".repeat(78);
        out.append('\n').append(line).append('\n');
        out.append("Typed number-field family — verification report\n");
        out.append(line).append('\n');

        int pass = 0;
        for (Result r : results) {
            out.append(r.pass ? "  [PASS]  " : "  [FAIL]  ");
            out.append(r.name).append('\n');
            if (!r.pass) {
                out.append("           └─ ").append(r.detail).append('\n');
            }
            if (r.pass) {
                pass++;
            }
        }
        int fail = results.size() - pass;
        out.append("-".repeat(78)).append('\n');
        out.append(String.format("Total: %d   Pass: %d   Fail: %d%n",
                results.size(), pass, fail));
        out.append(line).append('\n');

        System.out.println(out);
    }
}

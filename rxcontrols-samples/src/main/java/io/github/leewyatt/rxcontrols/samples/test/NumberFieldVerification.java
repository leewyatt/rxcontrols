package io.github.leewyatt.rxcontrols.samples.test;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXNumberField;
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
 * Verification runner for the RXNumberField series rebuild
 * ({@code devdoc/textinput/PLAN.md}).
 * <p>
 * Each check exercises one of the P0 / P1 cases from §6 of the plan and
 * prints a single PASS / FAIL line. The runner deliberately stays off
 * {@code Application}, {@code Scene}, and {@code Stage}: it only calls
 * {@link Platform#startup(Runnable)} so {@code Control.<clinit>} can resolve
 * the default stylesheet, then runs every check on the FX thread. No scene
 * graph is built, so no node is ever laid out or rendered.
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
        // -------------------- P0-A: high-precision value preservation --------------------

        check(results, "P0-A.1 new RXFormattedNumberField(1.23456) preserves value", () -> {
            RXFormattedNumberField f = new RXFormattedNumberField(new BigDecimal("1.23456"));
            BigDecimal v = f.getValue();
            if (v == null) {
                return "value is null, expected 1.23456";
            }
            return v.compareTo(new BigDecimal("1.23456")) == 0
                    ? null
                    : "value=" + v.toPlainString() + " (expected 1.23456)";
        });

        check(results, "P0-A.2 switching numberFormat does not mutate value", () -> {
            RXFormattedNumberField f = new RXFormattedNumberField(new BigDecimal("1.23456"));
            f.setNumberFormat(new DecimalFormat("0.##"));
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("1.23456")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 1.23456)";
        });

        check(results, "P0-A.3 focus-cycle commit with lossy text does not corrupt value", () -> {
            RXFormattedNumberField f = new RXFormattedNumberField(new BigDecimal("1.23456"));
            // After tightening the format, the displayed text becomes lossy
            // ("1.23"). A focus-out cycle without user editing then calls
            // commitValue(), which would in a buggy implementation push the
            // re-parsed lossy text back into value.
            f.setNumberFormat(new DecimalFormat("0.##"));
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("1.23456")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 1.23456)";
        });

        // -------------------- P0-B: RXIntegerField strict-throw policy --------------------

        check(results, "P0-B.1 RXIntegerField.setValue(3.14) throws IllegalArgumentException", () -> {
            RXIntegerField f = new RXIntegerField();
            try {
                f.setValue(new BigDecimal("3.14"));
                return "no exception thrown — strict policy missing";
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Throwable t) {
                return "threw " + t.getClass().getSimpleName() + ", expected IllegalArgumentException";
            }
        });

        check(results, "P0-B.2 RXIntegerField.setValue(3.0) accepts and normalizes to scale=0", () -> {
            RXIntegerField f = new RXIntegerField();
            f.setValue(new BigDecimal("3.0"));
            BigDecimal v = f.getValue();
            if (v == null) {
                return "value is null, expected 3";
            }
            if (v.compareTo(BigDecimal.valueOf(3)) != 0) {
                return "value=" + v.toPlainString() + " (expected 3)";
            }
            return v.scale() == 0 ? null : "value scale=" + v.scale() + " (expected 0)";
        });

        check(results, "P0-B.3 RXIntegerField.setMin(0.5) throws IllegalArgumentException", () -> {
            RXIntegerField f = new RXIntegerField();
            try {
                f.setMin(new BigDecimal("0.5"));
                return "no exception thrown — strict policy missing";
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Throwable t) {
                return "threw " + t.getClass().getSimpleName() + ", expected IllegalArgumentException";
            }
        });

        check(results, "P0-B.4 RXIntegerField.setMax(9.99) throws IllegalArgumentException", () -> {
            RXIntegerField f = new RXIntegerField();
            try {
                f.setMax(new BigDecimal("9.99"));
                return "no exception thrown — strict policy missing";
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Throwable t) {
                return "threw " + t.getClass().getSimpleName() + ", expected IllegalArgumentException";
            }
        });

        check(results, "P0-B.5 new RXIntegerField(7.77) throws IllegalArgumentException at construction", () -> {
            try {
                new RXIntegerField(new BigDecimal("7.77"));
                return "no exception thrown — strict policy missing";
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Throwable t) {
                return "threw " + t.getClass().getSimpleName() + ", expected IllegalArgumentException";
            }
        });

        // -------------------- P0-C: setTextFormatter guard (restore + WARNING log) --------------------
        //
        // TextInputControl.setTextFormatter is public final, so the only available
        // guard is a property ChangeListener. The guard restores the internal
        // formatter and logs a WARNING; we verify both.

        check(results, "P0-C.1 setTextFormatter(other) → WARNING log + restore", () -> {
            RXNumberField f = new RXNumberField(new BigDecimal("1"));
            TextFormatter<?> original = f.getTextFormatter();
            TextFormatter<String> other = new TextFormatter<>(new StringConverter<>() {
                @Override
                public String toString(String s) { return s == null ? "" : s; }
                @Override
                public String fromString(String s) { return s; }
            });
            return assertGuardRejects(f, original, () -> f.setTextFormatter(other));
        });

        check(results, "P0-C.2 setTextFormatter(null) → WARNING log + restore", () -> {
            RXNumberField f = new RXNumberField(new BigDecimal("1"));
            TextFormatter<?> original = f.getTextFormatter();
            return assertGuardRejects(f, original, () -> f.setTextFormatter(null));
        });

        // -------------------- P1: regression invariants --------------------

        check(results, "P1-1 setText('-') then commitValue() keeps the previous value", () -> {
            RXNumberField f = new RXNumberField(new BigDecimal("100"));
            f.setText("-");
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("100")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 100)";
        });

        check(results, "P1-2 setText('.') then commitValue() keeps the previous value", () -> {
            RXNumberField f = new RXNumberField(new BigDecimal("42"));
            f.setText(".");
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("42")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 42)";
        });

        check(results, "P1-3 setText('') then commitValue() clears value to null", () -> {
            RXNumberField f = new RXNumberField(new BigDecimal("7"));
            f.setText("");
            f.commitValue();
            return f.getValue() == null ? null : "value=" + f.getValue();
        });

        check(results, "P1-4 setValue(150) with max=100 clamps to 100 and text follows", () -> {
            RXNumberField f = new RXNumberField();
            f.setMax(new BigDecimal("100"));
            f.setValue(new BigDecimal("150"));
            BigDecimal v = f.getValue();
            if (v == null || v.compareTo(new BigDecimal("100")) != 0) {
                return "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 100)";
            }
            String t = f.getText();
            return "100".equals(t) ? null : "text='" + t + "' (expected '100')";
        });

        check(results, "P1-5 RXFormattedNumberField(1234567) default format applies grouping", () -> {
            RXFormattedNumberField f = new RXFormattedNumberField(new BigDecimal("1234567"));
            String t = f.getText();
            return t != null && !t.isEmpty() && !t.equals("1234567")
                    ? null
                    : "text='" + t + "' — expected grouping format (locale-dependent)";
        });

        check(results, "P1-6 NumberFormat in-place mutation visible to parse ('$50' after setPositivePrefix('$'))", () -> {
            DecimalFormat df = new DecimalFormat("0.##");
            RXFormattedNumberField f = new RXFormattedNumberField(new BigDecimal("100"));
            f.setNumberFormat(df);
            df.setPositivePrefix("$");
            f.setText("$50");
            f.commitValue();
            BigDecimal v = f.getValue();
            return v != null && v.compareTo(new BigDecimal("50")) == 0
                    ? null
                    : "value=" + (v == null ? "null" : v.toPlainString()) + " (expected 50)";
        });

        check(results, "P1-7 bound value: setMin that would clamp throws IllegalArgumentException", () -> {
            RXNumberField f = new RXNumberField();
            SimpleObjectProperty<BigDecimal> source = new SimpleObjectProperty<>(new BigDecimal("5"));
            f.valueProperty().bind(source);
            try {
                f.setMin(new BigDecimal("10"));
                return "no exception thrown — bound value silently violated";
            } catch (IllegalArgumentException e) {
                return null;
            } catch (Throwable t) {
                return "threw " + t.getClass().getSimpleName() + ", expected IllegalArgumentException";
            }
        });
    }

    /**
     * Runs {@code action} while a temporary handler is attached to the
     * {@code RXNumberField} logger, then asserts that a {@code WARNING} record
     * was emitted and that the field's text formatter is still {@code expected}.
     */
    private static String assertGuardRejects(RXNumberField f, TextFormatter<?> expected, Runnable action) {
        Logger logger = Logger.getLogger(RXNumberField.class.getName());
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
        out.append("RXNumberField series rebuild — verification report\n");
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

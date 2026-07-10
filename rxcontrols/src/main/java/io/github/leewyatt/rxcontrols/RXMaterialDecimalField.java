package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.DecimalFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.DecimalFieldConverter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldEngine;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Slider;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.function.UnaryOperator;

/**
 * Material-style field for exact-decimal {@link BigDecimal} input — money,
 * rates, and other quantities where binary floating point is not acceptable.
 * Extends {@link RXMaterialTextField}, so the floating label, bottom
 * activation line, and supporting (helper / error) row come with it; the typed
 * value model is identical to the plain {@link RXDecimalField}. The value is
 * committed on focus loss, ENTER, and {@link #commitValue()}; {@code null}
 * represents an empty field.
 * <p>
 * With the default {@code null} {@link #numberFormatProperty() numberFormat}
 * the field accepts plain decimal text and renders values with
 * {@link BigDecimal#toPlainString()}, so the scale of a programmatically set
 * value survives a render. A non-null {@link NumberFormat} drives both display
 * and commit parsing: grouping, currency, and percent formats are supported
 * (note the percent multiplier — a percent format renders {@code 0.75} as
 * {@code "75%"} and parses {@code "75"} back to {@code 0.75}). A display
 * format lossier than the stored value (e.g. a 2-fraction currency format
 * showing {@code 1.234} as {@code "$1.23"}) never corrupts the value on a
 * no-edit commit.
 * <p>
 * {@link #minProperty() min} / {@link #maxProperty() max} are inclusive,
 * default {@code null} (unbounded), and behave like {@link Slider}: setting
 * one past the other converges the opposite bound to preserve
 * {@code min <= max}; if that opposite bound is itself {@code bound}, the
 * convergence {@code set()} throws {@code "A bound value cannot be set"}. An
 * out-of-range unbound value is clamped, never rejected; a bound value is
 * owned by its binding — it is displayed as-is and
 * {@link #setValue(BigDecimal)} is a no-op, exactly as Slider. An empty field
 * ({@code null} value) is never clamped into a value.
 * <p>
 * The built-in clear button and the {@link #clear()} method clear the
 * committed value, not just the text: a direct {@code text} write commits
 * immediately, unlike a user edit. The internal
 * {@link TextFormatter} is not replaceable: an external
 * {@link #setTextFormatter(TextFormatter) setTextFormatter} is reverted and a
 * {@code WARNING} is logged. Prefer bidirectional binding for the value
 * property; one-way {@code bind} blocks user edits from being committed.
 */
public class RXMaterialDecimalField extends RXMaterialTextField {

    // ==================== Constants ====================

    private static final String FAMILY_STYLE_CLASS = "rx-material-number-field";
    private static final String DEFAULT_STYLE_CLASS = "rx-material-decimal-field";

    // ==================== Fields ====================

    private final NumberFieldEngine<BigDecimal> engine;

    // ==================== Constructors ====================

    /**
     * Creates an empty Material decimal field.
     */
    public RXMaterialDecimalField() {
        this(null);
    }

    /**
     * Creates a Material decimal field with an initial value.
     *
     * @param initialValue the initial value, or {@code null} for an empty field
     */
    public RXMaterialDecimalField(BigDecimal initialValue) {
        getStyleClass().addAll(FAMILY_STYLE_CLASS, DEFAULT_STYLE_CLASS);
        engine = new NumberFieldEngine<>(this, value,
                new DecimalFieldConverter(this::getNumberFormat),
                new DecimalFieldChangeFilter(this::getNumberFormat),
                UnaryOperator.identity(), this::clamp);
        numberFormat.addListener(obs -> engine.refreshText());
        setValue(initialValue);
        engine.refreshText();
    }

    // ==================== value ====================

    private final ObjectProperty<BigDecimal> value = new SimpleObjectProperty<>(this, "value") {
        @Override
        protected void invalidated() {
            if (engine != null) {
                engine.valueInvalidated();
            }
        }
    };

    /**
     * The committed numeric value. {@code null} represents an empty field.
     *
     * @return the value property
     */
    public final ObjectProperty<BigDecimal> valueProperty() {
        return value;
    }

    /**
     * Returns the committed numeric value.
     *
     * @return the value, or {@code null} for an empty field
     */
    public final BigDecimal getValue() {
        return value.get();
    }

    /**
     * Sets the committed numeric value. Like {@link Slider}, this is a no-op
     * when the value property is {@code bound}. {@code null} clears the field.
     *
     * @param value the value, or {@code null}
     */
    public final void setValue(BigDecimal value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== min ====================

    private final ObjectProperty<BigDecimal> min = new SimpleObjectProperty<>(this, "min") {
        @Override
        protected void invalidated() {
            BigDecimal v = get();
            BigDecimal hi = getMax();
            // Slider-style convergence: raising min past max pulls max up so the
            // range stays ordered. If max is bound and cannot move, its set()
            // throws "A bound value cannot be set" — the same contract as Slider.
            if (v != null && hi != null && v.compareTo(hi) > 0) {
                setMax(v);
            }
            if (engine != null) {
                engine.boundsChanged();
            }
        }
    };

    /**
     * Inclusive lower bound for {@link #valueProperty()}. {@code null} means
     * unbounded.
     *
     * @return the min property
     */
    public final ObjectProperty<BigDecimal> minProperty() {
        return min;
    }

    /**
     * Returns the inclusive lower bound.
     *
     * @return the min value, or {@code null}
     */
    public final BigDecimal getMin() {
        return min.get();
    }

    /**
     * Sets the inclusive lower bound.
     *
     * @param min the min value, or {@code null} for unbounded
     */
    public final void setMin(BigDecimal min) {
        this.min.set(min);
    }

    // ==================== max ====================

    private final ObjectProperty<BigDecimal> max = new SimpleObjectProperty<>(this, "max") {
        @Override
        protected void invalidated() {
            BigDecimal v = get();
            BigDecimal lo = getMin();
            // Slider-style convergence: lowering max below min pulls min down so
            // the range stays ordered. If min is bound and cannot move, its set()
            // throws "A bound value cannot be set" — the same contract as Slider.
            if (v != null && lo != null && v.compareTo(lo) < 0) {
                setMin(v);
            }
            if (engine != null) {
                engine.boundsChanged();
            }
        }
    };

    /**
     * Inclusive upper bound for {@link #valueProperty()}. {@code null} means
     * unbounded.
     *
     * @return the max property
     */
    public final ObjectProperty<BigDecimal> maxProperty() {
        return max;
    }

    /**
     * Returns the inclusive upper bound.
     *
     * @return the max value, or {@code null}
     */
    public final BigDecimal getMax() {
        return max.get();
    }

    /**
     * Sets the inclusive upper bound.
     *
     * @param max the max value, or {@code null} for unbounded
     */
    public final void setMax(BigDecimal max) {
        this.max.set(max);
    }

    // ==================== range ====================

    /**
     * Convenience entry that sets {@link #minProperty()} and
     * {@link #maxProperty()} together. To keep the call failure-atomic it
     * rejects up front, before touching either bound, when {@code min} or
     * {@code max} is {@code bound}. A {@code null} bound means unbounded on
     * that side.
     * <p>
     * This is not an atomic <em>update</em>: JavaFX has no multi-property
     * transaction, so a listener may observe one intermediate state during a
     * successful call. An ordered pair is assigned in whichever order avoids a
     * spurious convergence of the not-yet-updated side. An inverted pair
     * ({@code min > max}) is written min-first, so the later {@code max} write
     * converges deterministically to {@code [max, max]} regardless of the
     * previous bounds.
     *
     * @param min the inclusive lower bound, or {@code null} for unbounded
     * @param max the inclusive upper bound, or {@code null} for unbounded
     * @throws IllegalStateException if {@link #minProperty()} or
     *                               {@link #maxProperty()} is bound
     */
    public final void setRange(BigDecimal min, BigDecimal max) {
        // Reject up front so a bound min/max leaves both bounds unchanged rather
        // than half-applying (the individual setters would throw mid-way).
        if (this.min.isBound() || this.max.isBound()) {
            throw new IllegalStateException(
                    "setRange cannot be used while min or max is bound");
        }
        boolean ordered = min == null || max == null || min.compareTo(max) <= 0;
        BigDecimal currentMax = getMax();
        if (ordered && min != null && currentMax != null && min.compareTo(currentMax) > 0) {
            setMax(max);
            setMin(min);
        } else {
            setMin(min);
            setMax(max);
        }
    }

    // ==================== numberFormat ====================

    private final ObjectProperty<NumberFormat> numberFormat =
            new SimpleObjectProperty<>(this, "numberFormat");

    /**
     * The number format used for both display and commit parsing.
     * {@code null} (the default) falls back to plain
     * {@link BigDecimal#toPlainString()} rendering and plain decimal parsing.
     * <p>
     * {@link NumberFormat} is mutable. Mutating the live instance returned by
     * {@link #getNumberFormat()} affects future parse / format operations, but
     * it does not invalidate the property and therefore does not automatically
     * refresh the displayed text. Assign another {@code NumberFormat} instance
     * to trigger automatic re-rendering.
     *
     * @return the number-format property
     */
    public final ObjectProperty<NumberFormat> numberFormatProperty() {
        return numberFormat;
    }

    /**
     * Returns the number format.
     *
     * @return the number format, or {@code null}
     */
    public final NumberFormat getNumberFormat() {
        return numberFormat.get();
    }

    /**
     * Sets the number format.
     *
     * @param numberFormat the number format, or {@code null} for plain
     *                     decimal rendering and parsing
     */
    public final void setNumberFormat(NumberFormat numberFormat) {
        this.numberFormat.set(numberFormat);
    }

    // ==================== internal ====================

    private BigDecimal clamp(BigDecimal candidate) {
        if (candidate == null) {
            return null;
        }
        BigDecimal lo = getMin();
        BigDecimal hi = getMax();
        // Slider's Utils.clamp order: pull up to the lower bound first, then
        // down to the upper bound. The same order applies in the transiently
        // inverted range left by a convergence set() that threw on a bound
        // opposite bound — uniform across all typed fields.
        if (lo != null && candidate.compareTo(lo) < 0) {
            return lo;
        }
        if (hi != null && candidate.compareTo(hi) > 0) {
            return hi;
        }
        return candidate;
    }
}

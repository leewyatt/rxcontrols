package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.DoubleFieldConverter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldEngine;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Slider;
import javafx.scene.control.TextFormatter;

/**
 * Text field for {@link Double} input. The value is committed on focus loss,
 * ENTER, and {@link #commitValue()}; {@code null} represents an empty field.
 * <p>
 * The field accepts plain decimal text and renders finite values as plain
 * decimal with no scientific notation and no trailing {@code .0} (an extreme
 * magnitude like {@code 1e308} renders as its full 300-plus-digit plain
 * form). Double is binary floating point: a value like {@code 0.1 + 0.2}
 * renders its exact representation ({@code 0.30000000000000004}). For money
 * and other exact decimal quantities use {@link RXDecimalField}.
 * <p>
 * The value must be finite. {@link #setValue(Double)} with {@code NaN} or an
 * infinity coerces the value to {@code null} (empty field) and throws
 * {@link IllegalArgumentException}; user input whose magnitude overflows the
 * double range fails to parse and the text rolls back. A <em>bound</em> value
 * is owned by its binding and cannot be coerced: a non-finite bound value is a
 * caller contract violation — the field renders {@link Double#toString(double)}
 * defensively, but that text contains letters the edit filter rejects, so it
 * can only be replaced wholesale, not edited character by character.
 * <p>
 * {@link #minProperty() min} / {@link #maxProperty() max} are inclusive
 * primitive bounds, defaulting to {@link Double#NEGATIVE_INFINITY} /
 * {@link Double#POSITIVE_INFINITY} (unbounded; the clamp is naturally a
 * no-op), and behave like {@link Slider}: setting one past the other converges
 * the opposite bound to preserve {@code min <= max}; if that opposite bound is
 * itself {@code bound}, the convergence {@code set()} throws
 * {@code "A bound value cannot be set"}. A {@code NaN} bound never compares,
 * so it triggers no convergence and clamps nothing — it behaves as "no
 * constraint", exactly as Slider leaves it. An out-of-range unbound value is
 * clamped, never rejected; a bound value is displayed as-is and
 * {@link #setValue(Double)} is a no-op, exactly as Slider. An empty field
 * ({@code null} value) is never clamped into a value.
 * <p>
 * The internal {@link TextFormatter} is not replaceable: an external
 * {@link #setTextFormatter(TextFormatter) setTextFormatter} is reverted and a
 * {@code WARNING} is logged. Prefer bidirectional binding for the value
 * property; to bridge a {@code DoubleProperty} model use
 * {@code field.valueProperty().bindBidirectional(model.ratioProperty().asObject())}
 * — keep a reference to the {@code asObject()} bridge (JavaFX bidirectional
 * bindings are weak) and note that clearing the field writes {@code null},
 * which a {@code DoubleProperty} stores as {@code 0.0}.
 */
public class RXDoubleField extends RXTextField {

    // ==================== Constants ====================

    private static final String FAMILY_STYLE_CLASS = "rx-number-field";
    private static final String DEFAULT_STYLE_CLASS = "rx-double-field";

    // ==================== Fields ====================

    private final NumberFieldEngine<Double> engine;

    // ==================== Constructors ====================

    /**
     * Creates an empty double field.
     */
    public RXDoubleField() {
        this(null);
    }

    /**
     * Creates a double field with an initial value.
     *
     * @param initialValue the initial value, or {@code null} for an empty field
     * @throws IllegalArgumentException if the value is {@code NaN} or infinite
     */
    public RXDoubleField(Double initialValue) {
        getStyleClass().addAll(FAMILY_STYLE_CLASS, DEFAULT_STYLE_CLASS);
        engine = new NumberFieldEngine<>(this, value,
                new DoubleFieldConverter(), new NumberFieldChangeFilter(),
                this::sanitize, this::clamp);
        setValue(initialValue);
        engine.refreshText();
    }

    // ==================== value ====================

    private final ObjectProperty<Double> value = new SimpleObjectProperty<>(this, "value") {
        @Override
        protected void invalidated() {
            if (engine != null) {
                engine.valueInvalidated();
            }
        }
    };

    /**
     * The committed double value. {@code null} represents an empty field.
     *
     * @return the value property
     */
    public final ObjectProperty<Double> valueProperty() {
        return value;
    }

    /**
     * Returns the committed double value.
     *
     * @return the value, or {@code null} for an empty field
     */
    public final Double getValue() {
        return value.get();
    }

    /**
     * Sets the committed double value. Like {@link Slider}, this is a no-op
     * when the value property is {@code bound}. {@code null} clears the field.
     *
     * @param value the value, or {@code null}
     * @throws IllegalArgumentException if the value is {@code NaN} or infinite;
     *                                  the value is coerced to {@code null}
     *                                  (empty field) before the throw. Like the
     *                                  coerce-and-throw properties in JavaFX
     *                                  itself (e.g. {@code Animation.delay}),
     *                                  the rejection happens mid-invalidation:
     *                                  change listeners observe neither the
     *                                  rejected value nor the coercion, only
     *                                  the next genuine change
     */
    public final void setValue(Double value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== min ====================

    private final DoubleProperty min =
            new SimpleDoubleProperty(this, "min", Double.NEGATIVE_INFINITY) {
        @Override
        protected void invalidated() {
            // Slider-style convergence: raising min past max pulls max up so the
            // range stays ordered. If max is bound and cannot move, its set()
            // throws "A bound value cannot be set" — the same contract as Slider.
            if (get() > getMax()) {
                setMax(get());
            }
            if (engine != null) {
                engine.boundsChanged();
            }
        }
    };

    /**
     * Inclusive lower bound for {@link #valueProperty()}. Defaults to
     * {@link Double#NEGATIVE_INFINITY} (unbounded). {@code NaN} behaves as
     * "no constraint".
     *
     * @return the min property
     */
    public final DoubleProperty minProperty() {
        return min;
    }

    /**
     * Returns the inclusive lower bound.
     *
     * @return the min value
     */
    public final double getMin() {
        return min.get();
    }

    /**
     * Sets the inclusive lower bound.
     *
     * @param min the min value
     */
    public final void setMin(double min) {
        this.min.set(min);
    }

    // ==================== max ====================

    private final DoubleProperty max =
            new SimpleDoubleProperty(this, "max", Double.POSITIVE_INFINITY) {
        @Override
        protected void invalidated() {
            // Slider-style convergence: lowering max below min pulls min down so
            // the range stays ordered. If min is bound and cannot move, its set()
            // throws "A bound value cannot be set" — the same contract as Slider.
            if (get() < getMin()) {
                setMin(get());
            }
            if (engine != null) {
                engine.boundsChanged();
            }
        }
    };

    /**
     * Inclusive upper bound for {@link #valueProperty()}. Defaults to
     * {@link Double#POSITIVE_INFINITY} (unbounded). {@code NaN} behaves as
     * "no constraint".
     *
     * @return the max property
     */
    public final DoubleProperty maxProperty() {
        return max;
    }

    /**
     * Returns the inclusive upper bound.
     *
     * @return the max value
     */
    public final double getMax() {
        return max.get();
    }

    /**
     * Sets the inclusive upper bound.
     *
     * @param max the max value
     */
    public final void setMax(double max) {
        this.max.set(max);
    }

    // ==================== range ====================

    /**
     * Convenience entry that sets {@link #minProperty()} and
     * {@link #maxProperty()} together. To keep the call failure-atomic it
     * rejects up front, before touching either bound, when {@code min} or
     * {@code max} is {@code bound}.
     * <p>
     * This is not an atomic <em>update</em>: JavaFX has no multi-property
     * transaction, so a listener may observe one intermediate state during a
     * successful call. An ordered pair is assigned in whichever order avoids a
     * spurious convergence of the not-yet-updated side. An inverted pair
     * ({@code min > max}) is written min-first, so the later {@code max} write
     * converges deterministically to {@code [max, max]} regardless of the
     * previous bounds.
     *
     * @param min the inclusive lower bound
     * @param max the inclusive upper bound
     * @throws IllegalStateException if {@link #minProperty()} or
     *                               {@link #maxProperty()} is bound
     */
    public final void setRange(double min, double max) {
        // Reject up front so a bound min/max leaves both bounds unchanged rather
        // than half-applying (the individual setters would throw mid-way).
        if (this.min.isBound() || this.max.isBound()) {
            throw new IllegalStateException(
                    "setRange cannot be used while min or max is bound");
        }
        if (min <= max && min > getMax()) {
            setMax(max);
            setMin(min);
        } else {
            setMin(min);
            setMax(max);
        }
    }

    // ==================== internal ====================

    private Double sanitize(Double candidate) {
        // Finiteness is the one domain rule the type system cannot express: a
        // non-finite value renders to text the field's own filter locks out of
        // editing, so there is no safe lenient storage (AGENTS §2.2.3 strategy 3).
        // The clamp cannot catch it either: the default ±Infinity bounds do not
        // clamp +Infinity and NaN never compares.
        if (candidate == null || Double.isFinite(candidate)) {
            return candidate;
        }
        throw new IllegalArgumentException("value must be finite (was " + candidate + ")");
    }

    private Double clamp(Double candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate < getMin()) {
            return getMin();
        }
        if (candidate > getMax()) {
            return getMax();
        }
        return candidate;
    }
}

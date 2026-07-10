package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.IntegerFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.IntegerFieldConverter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldEngine;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Slider;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * Material-style field for {@link Integer} input. Extends
 * {@link RXMaterialTextField}, so the floating label, bottom activation line,
 * and supporting (helper / error) row come with it; the typed value model is
 * identical to the plain {@link RXIntegerField}. The value is committed on
 * focus loss, ENTER, and {@link #commitValue()}; {@code null} represents an
 * empty field.
 * <p>
 * User edits reject anything but digits and a leading sign; a magnitude beyond
 * the 32-bit integer range fails to parse and the text rolls back to the last
 * valid rendering. For whole numbers beyond that range use
 * {@link RXMaterialDecimalField}.
 * <p>
 * {@link #minProperty() min} / {@link #maxProperty() max} are inclusive
 * primitive bounds, defaulting to {@link Integer#MIN_VALUE} /
 * {@link Integer#MAX_VALUE} (the full domain, effectively unbounded), and
 * behave like {@link Slider}: setting one past the other converges the
 * opposite bound to preserve {@code min <= max}; if that opposite bound is
 * itself {@code bound}, the convergence {@code set()} throws
 * {@code "A bound value cannot be set"}. An out-of-range unbound value is
 * clamped, never rejected; a bound value is owned by its binding — it is
 * displayed as-is and {@link #setValue(Integer)} is a no-op, exactly as
 * Slider. An empty field ({@code null} value) is never clamped into a value.
 * <p>
 * The built-in clear button and the {@link #clear()} method clear the
 * committed value, not just the text: a direct {@code text} write commits
 * immediately, unlike a user edit. The internal
 * {@link TextFormatter} is not replaceable: an external
 * {@link #setTextFormatter(TextFormatter) setTextFormatter} is reverted and a
 * {@code WARNING} is logged. Prefer bidirectional binding for the value
 * property; to bridge an {@code IntegerProperty} model use
 * {@code field.valueProperty().bindBidirectional(model.countProperty().asObject())}
 * — keep a reference to the {@code asObject()} bridge (JavaFX bidirectional
 * bindings are weak) and note that clearing the field writes {@code null},
 * which an {@code IntegerProperty} stores as {@code 0}.
 */
public class RXMaterialIntegerField extends RXMaterialTextField {

    // ==================== Constants ====================

    private static final String FAMILY_STYLE_CLASS = "rx-material-number-field";
    private static final String DEFAULT_STYLE_CLASS = "rx-material-integer-field";

    // ==================== Fields ====================

    private final NumberFieldEngine<Integer> engine;

    // ==================== Constructors ====================

    /**
     * Creates an empty Material integer field.
     */
    public RXMaterialIntegerField() {
        this(null);
    }

    /**
     * Creates a Material integer field with an initial value.
     *
     * @param initialValue the initial value, or {@code null} for an empty field
     */
    public RXMaterialIntegerField(Integer initialValue) {
        getStyleClass().addAll(FAMILY_STYLE_CLASS, DEFAULT_STYLE_CLASS);
        engine = new NumberFieldEngine<>(this, value,
                new IntegerFieldConverter(), new IntegerFieldChangeFilter(),
                UnaryOperator.identity(), this::clamp);
        setValue(initialValue);
        engine.refreshText();
    }

    // ==================== value ====================

    private final ObjectProperty<Integer> value = new SimpleObjectProperty<>(this, "value") {
        @Override
        protected void invalidated() {
            if (engine != null) {
                engine.valueInvalidated();
            }
        }
    };

    /**
     * The committed integer value. {@code null} represents an empty field.
     *
     * @return the value property
     */
    public final ObjectProperty<Integer> valueProperty() {
        return value;
    }

    /**
     * Returns the committed integer value.
     *
     * @return the value, or {@code null} for an empty field
     */
    public final Integer getValue() {
        return value.get();
    }

    /**
     * Sets the committed integer value. Like {@link Slider}, this is a no-op
     * when the value property is {@code bound}. {@code null} clears the field.
     *
     * @param value the value, or {@code null}
     */
    public final void setValue(Integer value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== min ====================

    private final IntegerProperty min = new SimpleIntegerProperty(this, "min", Integer.MIN_VALUE) {
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
     * {@link Integer#MIN_VALUE} (effectively unbounded).
     *
     * @return the min property
     */
    public final IntegerProperty minProperty() {
        return min;
    }

    /**
     * Returns the inclusive lower bound.
     *
     * @return the min value
     */
    public final int getMin() {
        return min.get();
    }

    /**
     * Sets the inclusive lower bound.
     *
     * @param min the min value
     */
    public final void setMin(int min) {
        this.min.set(min);
    }

    // ==================== max ====================

    private final IntegerProperty max = new SimpleIntegerProperty(this, "max", Integer.MAX_VALUE) {
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
     * {@link Integer#MAX_VALUE} (effectively unbounded).
     *
     * @return the max property
     */
    public final IntegerProperty maxProperty() {
        return max;
    }

    /**
     * Returns the inclusive upper bound.
     *
     * @return the max value
     */
    public final int getMax() {
        return max.get();
    }

    /**
     * Sets the inclusive upper bound.
     *
     * @param max the max value
     */
    public final void setMax(int max) {
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
    public final void setRange(int min, int max) {
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

    private Integer clamp(Integer candidate) {
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

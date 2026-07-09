package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.NumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldStringConverter;
import io.github.leewyatt.rxcontrols.skins.RXNumberFieldSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Skin;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Text field for {@link BigDecimal} input.
 * <p>
 * With the default {@link NumberFieldStringConverter} this class accepts
 * plain decimal text and renders values with {@link BigDecimal#toPlainString()},
 * so the scale of a programmatically set value survives a render. Subclasses
 * may swap the converter and adopt a different rendering policy.
 * <p>
 * <b>Inheritance contract.</b> Subclasses may override {@link #createConverter()},
 * {@link #createFilter()}, {@link #normalizeValue(BigDecimal)}, and
 * {@link #validateValue(BigDecimal)}; the following invariants are fixed:
 * <ul>
 *   <li>{@link #valueProperty()} holds a {@link BigDecimal} (may be {@code null}).
 *   <li>{@link #minProperty()} / {@link #maxProperty()} are inclusive, default
 *       {@code null} (unbounded). Setting one past the other converges the
 *       opposite <em>unbound</em> bound to preserve {@code min <= max} (e.g.
 *       {@code setMin} above {@code max} raises {@code max}); when the opposite
 *       bound is itself bound and cannot move, the range is left inverted, a
 *       {@code WARNING} is logged, and value clamping is suspended until the
 *       caller restores the ordering.
 *       {@link #setRange(BigDecimal, BigDecimal)} sets both at once and rejects
 *       {@code min > max} instead of converging.
 *   <li>The value is committed on focus loss, ENTER, and {@link #commitValue()}.
 *   <li>The internal {@link TextFormatter} is not replaceable; customize
 *       parsing through {@link #createConverter()} / {@link #createFilter()}.
 * </ul>
 * An external {@link #setTextFormatter(TextFormatter)} is rejected: the
 * internal formatter is reinstalled and a {@code WARNING} is logged. The one
 * case the guard cannot repair is a {@link TextFormatter} already bound to
 * another control, where JavaFX nulls the property and throws
 * {@link IllegalStateException} before any listener runs.
 * <p>
 * Prefer bidirectional binding for the value property; one-way {@code bind}
 * blocks user edits from being committed.
 */
public class RXNumberField extends RXTextField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-number-field";
    private static final Logger LOGGER = Logger.getLogger(RXNumberField.class.getName());

    // ==================== Fields ====================

    private final TextFormatter<BigDecimal> formatter;
    private final ChangeListener<BigDecimal> formatterValueListener =
            (obs, oldValue, newValue) -> handleFormatterValueChanged(newValue);
    private final ChangeListener<TextFormatter<?>> textFormatterGuard =
            (obs, oldFormatter, newFormatter) -> guardTextFormatter(newFormatter);
    private final ChangeListener<String> textChangeListener =
            (obs, oldText, newText) -> handleTextChanged(newText);

    // Sync-direction lock: we are pushing value -> formatter/text;
    // ignore the reverse formatter.value listener while true.
    private boolean updatingFormatter;

    // Reentrancy lock: we are restoring the internal formatter after an
    // external setTextFormatter; prevents the guard from recursing.
    private boolean restoringTextFormatter;

    // Reentrancy lock: coerceValueProperty is rewriting value itself;
    // prevents value.invalidated from re-entering.
    private boolean updatingValue;

    // Reentrancy lock for min — same role as updatingValue.
    private boolean updatingMin;

    // Reentrancy lock for max — same role as updatingValue.
    private boolean updatingMax;

    // Edit-origin marker: text has changed since the last render and the
    // change did not come from us. Decides whether focus-out commit is
    // allowed to push the parsed text back into value.
    private boolean textDirty;

    private String lastDisplayedText = "";
    private BigDecimal lastValidValue;

    // ==================== Constructors ====================

    /**
     * Creates an empty number field.
     */
    public RXNumberField() {
        this(null);
    }

    /**
     * Creates a number field with an initial value.
     *
     * @param initialValue the initial value, or {@code null}
     */
    public RXNumberField(BigDecimal initialValue) {
        super();
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        StringConverter<BigDecimal> converter =
                Objects.requireNonNull(createConverter(), "converter cannot be null");
        UnaryOperator<TextFormatter.Change> filter =
                Objects.requireNonNull(createFilter(), "filter cannot be null");

        formatter = new TextFormatter<>(converter, null, filter);
        setTextFormatter(formatter);
        textFormatterProperty().addListener(textFormatterGuard);
        formatter.valueProperty().addListener(formatterValueListener);
        textProperty().addListener(textChangeListener);

        setValue(initialValue);
        refreshTextFromValue();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXNumberFieldSkin(this);
    }

    // ==================== Subclass hooks ====================

    /**
     * Creates the converter used by this field's internal formatter.
     *
     * @return the converter, never {@code null}
     */
    protected StringConverter<BigDecimal> createConverter() {
        return new NumberFieldStringConverter();
    }

    /**
     * Creates the edit filter used by this field's internal formatter.
     *
     * @return the filter, never {@code null}
     */
    protected UnaryOperator<TextFormatter.Change> createFilter() {
        return new NumberFieldChangeFilter();
    }

    /**
     * Normalizes a value written to {@link #valueProperty()},
     * {@link #minProperty()}, or {@link #maxProperty()} before range clamping.
     * Subclasses override this to enforce a narrower numeric domain.
     *
     * @param value the candidate value, may be {@code null}
     * @return the normalized value
     */
    protected BigDecimal normalizeValue(BigDecimal value) {
        return value;
    }

    /**
     * Validates a normalized value written to {@link #valueProperty()},
     * {@link #minProperty()}, or {@link #maxProperty()} before range clamping.
     * Subclasses throw to reject a value outside their numeric domain.
     *
     * @param value the normalized value, may be {@code null}
     * @throws RuntimeException if the value is invalid
     */
    protected void validateValue(BigDecimal value) {
    }

    /**
     * Re-renders the displayed text from the current value without committing
     * text back into the value property.
     */
    protected final void refreshDisplayedText() {
        refreshTextFromValue();
    }

    // ==================== value ====================

    private final ObjectProperty<BigDecimal> value = new SimpleObjectProperty<>(this, "value") {
        @Override
        protected void invalidated() {
            if (updatingValue) {
                return;
            }
            coerceValueProperty();
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
     * @return the value, or {@code null}
     */
    public final BigDecimal getValue() {
        return value.get();
    }

    /**
     * Sets the committed numeric value.
     *
     * @param value the value, or {@code null}
     */
    public final void setValue(BigDecimal value) {
        this.value.set(value);
    }

    // ==================== min ====================

    private final ObjectProperty<BigDecimal> min = new SimpleObjectProperty<>(this, "min") {
        private BigDecimal lastValid;

        @Override
        protected void invalidated() {
            if (updatingMin) {
                return;
            }
            try {
                BigDecimal normalized = normalizeValue(get());
                if (!Objects.equals(get(), normalized)) {
                    if (isBound()) {
                        throw new IllegalArgumentException("bound min cannot be normalized");
                    }
                    updatingMin = true;
                    try {
                        set(normalized);
                    } finally {
                        updatingMin = false;
                    }
                }
                validateValue(get());
                BigDecimal v = get();
                BigDecimal hi = getMax();
                if (v != null && hi != null && v.compareTo(hi) > 0) {
                    // min pushed above max: converge the opposite bound up to keep
                    // min <= max (Slider-style). If max is bound and cannot move,
                    // leave the range inverted, log it, and let clampValue suspend
                    // clamping until the caller restores the ordering.
                    if (!max.isBound()) {
                        setMax(v);
                    } else {
                        LOGGER.log(Level.WARNING,
                                "min ({0}) exceeds a bound max ({1}); the range is"
                                        + " inverted and value clamping is suspended"
                                        + " until the caller restores min <= max.",
                                new Object[]{v.toPlainString(), hi.toPlainString()});
                    }
                }
                coerceCurrentValueAfterConstraintChange();
                lastValid = v;
            } catch (RuntimeException ex) {
                if (!isBound()) {
                    updatingMin = true;
                    try {
                        set(lastValid);
                    } finally {
                        updatingMin = false;
                    }
                }
                throw ex;
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
     * @param min the min value, or {@code null}
     */
    public final void setMin(BigDecimal min) {
        this.min.set(min);
    }

    // ==================== max ====================

    private final ObjectProperty<BigDecimal> max = new SimpleObjectProperty<>(this, "max") {
        private BigDecimal lastValid;

        @Override
        protected void invalidated() {
            if (updatingMax) {
                return;
            }
            try {
                BigDecimal normalized = normalizeValue(get());
                if (!Objects.equals(get(), normalized)) {
                    if (isBound()) {
                        throw new IllegalArgumentException("bound max cannot be normalized");
                    }
                    updatingMax = true;
                    try {
                        set(normalized);
                    } finally {
                        updatingMax = false;
                    }
                }
                validateValue(get());
                BigDecimal v = get();
                BigDecimal lo = getMin();
                if (v != null && lo != null && v.compareTo(lo) < 0) {
                    // max pushed below min: converge the opposite bound down to
                    // keep min <= max. If min is bound and cannot move, leave the
                    // range inverted, log it, and let clampValue suspend clamping
                    // until the caller restores the ordering.
                    if (!min.isBound()) {
                        setMin(v);
                    } else {
                        LOGGER.log(Level.WARNING,
                                "max ({0}) is below a bound min ({1}); the range is"
                                        + " inverted and value clamping is suspended"
                                        + " until the caller restores min <= max.",
                                new Object[]{v.toPlainString(), lo.toPlainString()});
                    }
                }
                coerceCurrentValueAfterConstraintChange();
                lastValid = v;
            } catch (RuntimeException ex) {
                if (!isBound()) {
                    updatingMax = true;
                    try {
                        set(lastValid);
                    } finally {
                        updatingMax = false;
                    }
                }
                throw ex;
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
     * @param max the max value, or {@code null}
     */
    public final void setMax(BigDecimal max) {
        this.max.set(max);
    }

    // ==================== range ====================

    /**
     * Convenience entry that sets {@link #minProperty()} and
     * {@link #maxProperty()} together, rejecting an inverted pair up front. Both
     * values are normalized and validated (see
     * {@link #normalizeValue(BigDecimal)} / {@link #validateValue(BigDecimal)});
     * unlike the individual setters — which converge the opposite bound — a
     * {@code min > max} pair throws.
     * <p>
     * This is not an atomic update: JavaFX has no multi-property transaction, so
     * a listener may observe one intermediate state. The two bounds are assigned
     * in whichever order avoids a spurious convergence of the not-yet-updated
     * side. A {@code null} bound means unbounded on that side.
     *
     * @param min the inclusive lower bound, or {@code null} for unbounded
     * @param max the inclusive upper bound, or {@code null} for unbounded
     * @throws IllegalArgumentException if both are non-null and {@code min > max}
     *                                  after normalization, or a bound is rejected
     *                                  by {@link #validateValue(BigDecimal)}
     */
    public final void setRange(BigDecimal min, BigDecimal max) {
        BigDecimal nMin = normalizeValue(min);
        BigDecimal nMax = normalizeValue(max);
        validateValue(nMin);
        validateValue(nMax);
        if (nMin != null && nMax != null && nMin.compareTo(nMax) > 0) {
            throw new IllegalArgumentException(
                    "min (" + nMin.toPlainString() + ") must be <= max ("
                            + nMax.toPlainString() + ")");
        }
        BigDecimal currentMax = getMax();
        boolean minFirstSafe = currentMax == null || nMin == null
                || nMin.compareTo(currentMax) <= 0;
        if (minFirstSafe) {
            setMin(nMin);
            setMax(nMax);
        } else {
            setMax(nMax);
            setMin(nMin);
        }
    }

    // ==================== Synchronization ====================

    private void handleFormatterValueChanged(BigDecimal newValue) {
        if (updatingFormatter) {
            return;
        }
        if (!textDirty) {
            refreshTextFromValue();
            return;
        }
        if (value.isBound()) {
            refreshTextFromValue();
            return;
        }
        setValue(newValue);
    }

    private void handleTextChanged(String newText) {
        if (updatingFormatter) {
            return;
        }
        textDirty = !Objects.equals(newText, lastDisplayedText);
    }

    private void guardTextFormatter(TextFormatter<?> newFormatter) {
        if (restoringTextFormatter || newFormatter == formatter) {
            return;
        }
        restoringTextFormatter = true;
        try {
            setTextFormatter(formatter);
        } finally {
            restoringTextFormatter = false;
        }
        LOGGER.log(Level.WARNING,
                "RXNumberField manages its own TextFormatter; replacing it via"
                        + " setTextFormatter is unsupported. The internal formatter"
                        + " has been restored; the replacement was ignored."
                        + " Override createFilter() or createConverter() instead.");
    }

    private void coerceValueProperty() {
        BigDecimal candidate = value.get();
        try {
            BigDecimal coerced = coerceValue(candidate);
            if (!Objects.equals(candidate, coerced)) {
                if (value.isBound()) {
                    throw new IllegalArgumentException("bound value cannot be normalized or clamped");
                }
                updatingValue = true;
                try {
                    value.set(coerced);
                } finally {
                    updatingValue = false;
                }
            }
            lastValidValue = coerced;
            refreshTextFromValue();
        } catch (RuntimeException ex) {
            if (!value.isBound()) {
                updatingValue = true;
                try {
                    value.set(lastValidValue);
                } finally {
                    updatingValue = false;
                }
            }
            // Refresh unconditionally: a bound value cannot be reverted or
            // clamped, but the text must still reflect the actual (bound) value
            // rather than being left stale.
            refreshTextFromValue();
            throw ex;
        }
    }

    private void coerceCurrentValueAfterConstraintChange() {
        BigDecimal current = value.get();
        BigDecimal coerced = coerceValue(current);
        if (Objects.equals(current, coerced)) {
            refreshTextFromValue();
            return;
        }
        if (value.isBound()) {
            throw new IllegalArgumentException("bound value is outside the number field range");
        }
        setValue(coerced);
    }

    private BigDecimal coerceValue(BigDecimal candidate) {
        BigDecimal normalized = normalizeValue(candidate);
        validateValue(normalized);
        return clampValue(normalized);
    }

    private BigDecimal clampValue(BigDecimal candidate) {
        if (candidate == null) {
            return null;
        }
        BigDecimal lo = min.get();
        BigDecimal hi = max.get();
        // A contradictory range (min > max) has no value satisfying both bounds.
        // Unbound setMin/setMax converge the opposite bound to keep the range
        // ordered, so this is only reachable when the bound that would need to
        // move is itself bound. Clamping to either side would push the value
        // further from the other, so leave the candidate untouched instead.
        if (lo != null && hi != null && lo.compareTo(hi) > 0) {
            return candidate;
        }
        BigDecimal result = candidate;
        if (hi != null && result.compareTo(hi) > 0) {
            result = hi;
        }
        if (lo != null && result.compareTo(lo) < 0) {
            result = lo;
        }
        return result;
    }

    private void refreshTextFromValue() {
        updatingFormatter = true;
        try {
            BigDecimal current = value.get();
            if (!Objects.equals(formatter.getValue(), current)) {
                formatter.setValue(current);
            }
            cancelEdit();
            if (getText() == null) {
                setText("");
            }
            lastDisplayedText = getText();
            textDirty = false;
        } finally {
            updatingFormatter = false;
        }
    }
}

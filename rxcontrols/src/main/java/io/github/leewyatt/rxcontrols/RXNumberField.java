package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.NumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.NumberFieldStringConverter;
import io.github.leewyatt.rxcontrols.skins.RXNumberFieldSkin;
import javafx.beans.InvalidationListener;
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
 *       {@code null} (unbounded) and behave like {@link javafx.scene.control.Slider}:
 *       setting one past the other converges the opposite bound to preserve
 *       {@code min <= max} (e.g. {@code setMin} above {@code max} raises
 *       {@code max}). If that opposite bound is itself {@code bound}, the
 *       convergence {@code set()} throws {@code "A bound value cannot be set"},
 *       exactly as Slider does. {@link #setRange(BigDecimal, BigDecimal)} sets
 *       both at once with the same lenient convergence (it rejects only the up-front
 *       case where a bound would have to be written while it is {@code bound}).
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
 * blocks user edits from being committed. The {@code value} follows Slider's
 * lenient rule: {@link #setValue(BigDecimal)} on a {@code bound} value is a
 * no-op and an out-of-range bound value is displayed as-is (never clamped or
 * reverted). {@code min} / {@code max} are stored leniently (not normalized or
 * validated), but a subclass may snap the <em>clamp target</em> into its value
 * domain through {@link #effectiveLowerBound(BigDecimal)} /
 * {@link #effectiveUpperBound(BigDecimal)} so a clamped value stays in that
 * domain — e.g. {@link RXIntegerField} rounds the lower bound up and the upper
 * bound down, so the value stays integral.
 */
public class RXNumberField extends RXTextField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-number-field";
    private static final Logger LOGGER = Logger.getLogger(RXNumberField.class.getName());

    // ==================== Fields ====================

    private final TextFormatter<BigDecimal> formatter;
    private final InvalidationListener formatterValueListener =
            obs -> handleFormatterValueChanged();
    private final ChangeListener<TextFormatter<?>> textFormatterGuard =
            (obs, oldFormatter, newFormatter) -> guardTextFormatter(newFormatter);

    // Sync-direction lock: we are pushing value -> formatter/text;
    // ignore the reverse formatter.value listener while true.
    private boolean updatingFormatter;

    // Reentrancy lock: we are restoring the internal formatter after an
    // external setTextFormatter; prevents the guard from recursing.
    private boolean restoringTextFormatter;

    // Reentrancy lock: coerceValueProperty is rewriting value itself;
    // prevents value.invalidated from re-entering.
    private boolean updatingValue;

    // The delegate converter (from createConverter()); used to render the
    // canonical text of the current value for the edit-origin check.
    private final StringConverter<BigDecimal> converter;

    // The text of the most recent commit, captured when the formatter parses it.
    // Refreshed on every commit (success or failure), so it never goes stale.
    private String committedText;

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

        StringConverter<BigDecimal> delegate =
                Objects.requireNonNull(createConverter(), "converter cannot be null");
        this.converter = delegate;
        // Wrap the converter to capture each committed text, so the edit-origin
        // check compares what the user committed against the canonical rendering.
        StringConverter<BigDecimal> capturingConverter = new StringConverter<>() {
            @Override
            public String toString(BigDecimal v) {
                return delegate.toString(v);
            }

            @Override
            public BigDecimal fromString(String s) {
                committedText = s;
                return delegate.fromString(s);
            }
        };
        UnaryOperator<TextFormatter.Change> filter =
                Objects.requireNonNull(createFilter(), "filter cannot be null");

        formatter = new TextFormatter<>(capturingConverter, null, filter);
        setTextFormatter(formatter);
        textFormatterProperty().addListener(textFormatterGuard);
        formatter.valueProperty().addListener(formatterValueListener);

        setValue(initialValue);
        refreshTextFromValue();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXNumberFieldSkin(this);
    }

    // ==================== Subclass hooks ====================

    /**
     * Creates the converter used by this field's internal formatter. Called once
     * during construction, so an override must return a fresh, self-contained
     * instance and must not read subclass state initialized after {@code super()}.
     *
     * @return the converter, never {@code null}
     */
    protected StringConverter<BigDecimal> createConverter() {
        return new NumberFieldStringConverter();
    }

    /**
     * Creates the edit filter used by this field's internal formatter. Called once
     * during construction, so an override must return a fresh, self-contained
     * instance and must not read subclass state initialized after {@code super()}.
     *
     * @return the filter, never {@code null}
     */
    protected UnaryOperator<TextFormatter.Change> createFilter() {
        return new NumberFieldChangeFilter();
    }

    /**
     * Normalizes a value written to {@link #valueProperty()} before range
     * clamping. Subclasses override this to enforce a narrower numeric domain.
     * It applies to the {@code value} only — {@code min} / {@code max} are kept
     * lenient (Slider-style), so a subclass domain constrains the value, not the
     * bounds. It may run during construction (while the initial value is
     * applied), so an override must not depend on subclass state initialized
     * after {@code super()}.
     *
     * @param value the candidate value, may be {@code null}
     * @return the normalized value
     */
    protected BigDecimal normalizeValue(BigDecimal value) {
        return value;
    }

    /**
     * Validates a normalized value written to {@link #valueProperty()} before
     * range clamping. Subclasses throw to reject a value outside their numeric
     * domain. Like {@link #normalizeValue(BigDecimal)} it gates the {@code value}
     * only, not {@code min} / {@code max}. It may run during construction (while
     * the initial value is applied), so an override must not depend on subclass
     * state initialized after {@code super()}.
     *
     * @param value the normalized value, may be {@code null}
     * @throws RuntimeException if the value is invalid
     */
    protected void validateValue(BigDecimal value) {
    }

    /**
     * Returns the effective lower clamp target for the given raw {@code min}.
     * Bounds are stored leniently (Slider-style), but a subclass can snap the
     * bound into its value domain here so a clamped value stays in that domain —
     * e.g. {@link RXIntegerField} rounds the lower bound up so the value stays an
     * integer at or above {@code min}. The default returns the raw bound. Must be
     * a pure, side-effect-free transform (it runs on every clamp, including during
     * construction), and must not depend on subclass state initialized after
     * {@code super()}.
     *
     * @param min the raw lower bound, or {@code null} for unbounded
     * @return the effective lower clamp target, or {@code null} for unbounded
     */
    protected BigDecimal effectiveLowerBound(BigDecimal min) {
        return min;
    }

    /**
     * Returns the effective upper clamp target for the given raw {@code max}.
     * The upper-bound counterpart of {@link #effectiveLowerBound(BigDecimal)} —
     * {@link RXIntegerField} rounds the upper bound down so the value stays an
     * integer at or below {@code max}. The default returns the raw bound; the same
     * purity/construction-timing rules apply.
     *
     * @param max the raw upper bound, or {@code null} for unbounded
     * @return the effective upper clamp target, or {@code null} for unbounded
     */
    protected BigDecimal effectiveUpperBound(BigDecimal max) {
        return max;
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
     * Sets the committed numeric value. Like {@link javafx.scene.control.Slider},
     * this is a no-op when the value property is {@code bound}.
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
            adjustValues();
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
            adjustValues();
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
     * {@link #maxProperty()} together. The bounds are lenient, exactly like the
     * individual setters: an inverted pair converges (it is not rejected), and a
     * fractional bound on an integer subclass is accepted. To keep the call
     * failure-atomic it rejects up front, before touching either bound, when
     * {@code min} or {@code max} is {@code bound} (a bound value cannot be set).
     * <p>
     * This is not an atomic <em>update</em>: JavaFX has no multi-property
     * transaction, so a listener may observe one intermediate state during a
     * successful call. The two bounds are assigned in whichever order avoids a
     * spurious convergence of the not-yet-updated side. A {@code null} bound means
     * unbounded on that side.
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
        // Order the two writes so the not-yet-updated side is never transiently
        // crossed (which would spuriously converge it).
        BigDecimal currentMax = getMax();
        boolean minFirst = currentMax == null || min == null
                || min.compareTo(currentMax) <= 0;
        if (minFirst) {
            setMin(min);
            setMax(max);
        } else {
            setMax(max);
            setMin(min);
        }
    }

    // ==================== Synchronization ====================

    private void handleFormatterValueChanged() {
        if (updatingFormatter) {
            return;
        }
        // Stateless edit-origin check. committedText is the exact string the
        // formatter last parsed (captured by the capturing converter on every
        // commit, success or failure). If it equals the canonical rendering of the
        // current value, the commit carried no genuine edit — either a no-op commit
        // re-reading our own render (100 rendered "$100.00" and re-parsed as 100.00)
        // or a failed parse whose text reverted — so we re-render and return without
        // touching the value. Otherwise the committed text differs from what the
        // value renders to, which is a real user edit (including a deliberate
        // scale-only edit like 100 -> 100.00, observable through the plain
        // toPlainString converter), and we push it. A bound value cannot be set.
        BigDecimal current = value.get();
        if (value.isBound()
                || committedText == null
                || Objects.equals(committedText, converter.toString(current))) {
            refreshTextFromValue();
            return;
        }
        setValue(formatter.getValue());
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
        if (value.isBound()) {
            // A bound value is owned by its binding: it cannot be clamped or
            // reverted (Slider leaves a bound value as-is). Keep the text in sync
            // and leave the value to its binding.
            refreshTextFromValue();
            return;
        }
        BigDecimal candidate = value.get();
        try {
            BigDecimal coerced = coerceValue(candidate);
            if (!Objects.equals(candidate, coerced)) {
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
            // The value violates a subclass domain rule (e.g. a fractional value in
            // an integer field). Revert to the last value that passed, re-clamped so
            // the revert can never land outside the current range, then rethrow.
            updatingValue = true;
            try {
                value.set(clampValue(lastValidValue));
            } finally {
                updatingValue = false;
            }
            refreshTextFromValue();
            throw ex;
        }
    }

    private void adjustValues() {
        // Re-clamp the value after a bound change (Slider's adjustValues). The value
        // itself did not change, so it is only clamped, not re-normalized/validated.
        if (value.isBound()) {
            // A bound value cannot be clamped; keep the text in sync and leave it.
            refreshTextFromValue();
            return;
        }
        BigDecimal current = value.get();
        BigDecimal clamped = clampValue(current);
        if (!Objects.equals(current, clamped)) {
            updatingValue = true;
            try {
                value.set(clamped);
            } finally {
                updatingValue = false;
            }
        }
        // Keep the revert target in range (this path bypasses coerceValueProperty's
        // update), but only if it also passes the value domain: a bound change can
        // leave an unbound value that never went through the domain check — a binding
        // residue after unbind, e.g. 1.5 in an integer field — and that must not become
        // the revert target, or a later domain-rejected edit would rewind onto it. It
        // is left displayed (clamp-only, like Slider); only the bookkeeping skips it.
        if (isDomainValid(clamped)) {
            lastValidValue = clamped;
        }
        refreshTextFromValue();
    }

    private BigDecimal coerceValue(BigDecimal candidate) {
        BigDecimal normalized = normalizeValue(candidate);
        validateValue(normalized);
        return clampValue(normalized);
    }

    private boolean isDomainValid(BigDecimal candidate) {
        try {
            validateValue(normalizeValue(candidate));
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private BigDecimal clampValue(BigDecimal candidate) {
        if (candidate == null) {
            return null;
        }
        BigDecimal lo = effectiveLowerBound(min.get());
        BigDecimal hi = effectiveUpperBound(max.get());
        // An empty effective interval (lo > hi) has no value satisfying it: either an
        // inverted range (transient, after a bound-convergence throw) or a subclass
        // snap with no member (e.g. an integer field with min=1.5, max=1.8 -> [2, 1]).
        // Keep the candidate rather than pin it to a wrong bound (value-domain
        // priority). For a normal (non-empty) range this matches Slider's Utils.clamp:
        // pull up to the lower bound first, then down to the upper bound.
        if (lo != null && hi != null && lo.compareTo(hi) > 0) {
            return candidate;
        }
        if (lo != null && candidate.compareTo(lo) < 0) {
            return lo;
        }
        if (hi != null && candidate.compareTo(hi) > 0) {
            return hi;
        }
        return candidate;
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
        } finally {
            updatingFormatter = false;
        }
    }
}

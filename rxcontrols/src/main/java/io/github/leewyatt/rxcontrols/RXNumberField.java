package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.NumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.skins.NumberFieldStringConverter;
import io.github.leewyatt.rxcontrols.skins.RXNumberFieldSkin;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Minimal numeric text field built on top of {@link RXTextField}. Inherits the
 * {@code left} / {@code right} slot machinery and {@code textPadding} from the
 * parent <em>unchanged</em>, and adds a single {@link BigDecimal}-typed
 * {@link #valueProperty() value} wired through a {@link TextFormatter} plus a
 * permissive keystroke filter that keeps non-numeric characters out of the
 * displayed text.
 * <p>
 * <b>Deliberately small surface.</b> The base class carries no formatting
 * opinion — no grouping, no unit suffix, no locale awareness, no fixed
 * precision, no range, no step semantics. The displayed text is the result of
 * {@link BigDecimal#toPlainString()}; the accepted vocabulary is digits, a
 * leading {@code +} / {@code -}, and a single {@code '.'} as decimal point.
 * Anything richer — locale-aware grouping, currency symbols, unit suffixes,
 * percent format, fixed decimals — lives in {@link RXFormattedNumberField} or
 * further subclasses, and plugs in through the {@link #createConverter()} /
 * {@link #createFilter()} hooks.
 *
 * <h2>Value scale preserved</h2>
 * The base class does <b>not</b> rewrite the {@link BigDecimal} scale of a
 * committed value. {@code "1.2"} commits to {@code BigDecimal("1.2")} (scale
 * 1) and displays as {@code "1.2"}. {@code "1.20000"} commits to scale 5 and
 * displays as {@code "1.20000"} — trailing zeros the user typed are the
 * user's choice. {@link RXFormattedNumberField} deliberately trades this
 * promise for {@code NumberFormat}-driven display.
 *
 * <h2>Range constraints (min / max)</h2>
 * Optional {@link #minProperty() min} and {@link #maxProperty() max} bounds
 * (default {@code null} = unbounded) clamp the value at commit time. Bounds
 * are <b>inclusive</b> — matching HTML5 {@code <input type="number">},
 * Element {@code <el-input-number>}, Ant {@code <InputNumber>}, JavaFX
 * {@code Slider}, and the conventional behavior across web / desktop
 * numeric inputs. Typing {@code "150"} into a field with {@code max=100}
 * commits a value of {@code 100} and the displayed text reformats to
 * {@code "100"} on focus loss. Tightening a bound while the field already
 * holds a value pulls the value back into range immediately. Inherited
 * unchanged by {@link RXIntegerField} and {@link RXFormattedNumberField}.
 * <p>
 * The filter does <em>not</em> reject out-of-range characters at keystroke
 * time — there is no way to distinguish "the user is mid-typing 100 and
 * just typed 1" from "the user finished typing 1". Range enforcement is
 * commit-only.
 * <p>
 * Exclusive bounds ({@code value > min} or {@code value < max}) are not
 * supported and intentionally so: {@code BigDecimal} is an arbitrary-precision
 * continuous type, so "just above zero" has no representable next value.
 * For "strictly positive" semantics, use {@code setMin(new BigDecimal("0.01"))}
 * or whatever explicit lower threshold the use case requires.
 *
 * <h2>Commit timing</h2>
 * Commits push the parsed text into {@code value} and re-derive a canonical
 * displayed string from {@code value}. Commits fire on:
 * <ul>
 *   <li>focus loss (inherited from {@link javafx.scene.control.TextInputControl}
 *       — no opt-out),</li>
 *   <li>ENTER (registered by the default skin via
 *       {@code addEventHandler(ActionEvent.ACTION, ...)} — coexists with any
 *       user {@code setOnAction} handler).</li>
 * </ul>
 *
 * <h2>{@code setTextFormatter} caveat</h2>
 * The constructor installs an internal {@link TextFormatter} and
 * {@link #valueProperty()} is bound bidirectionally to it. Calling
 * {@link javafx.scene.control.TextInputControl#setTextFormatter
 * setTextFormatter} with a different formatter detaches the value model from
 * the displayed text. Unsupported — subclasses that need to swap parsing /
 * filtering behavior override {@link #createConverter()} or
 * {@link #createFilter()} instead.
 *
 * <h2>Subclass extension hooks</h2>
 * Subclasses that want custom parsing / formatting / keystroke filtering
 * override {@link #createConverter()} and {@link #createFilter()}, both called
 * from this class's constructor. Subclasses with additional format-affecting
 * properties install their own listeners that call
 * {@link #refreshDisplayedText()} so a property change re-renders the field
 * without going through a value round-trip.
 * <p>
 * <b>Constructor ordering caveat.</b> {@link #createConverter()} and
 * {@link #createFilter()} run before any subclass field initializer or
 * constructor body, so subclass overrides cannot rely on subclass state — they
 * typically return an instance that captures {@code this} and reads
 * subclass-owned properties lazily on each conversion / filter call. The
 * initial {@code setTextFormatter} call in this constructor invokes the
 * converter's {@code toString} once, so the converter must tolerate
 * subclass-owned state still being {@code null} at that point; subclasses
 * that need a fresh re-render once their fields are initialized call
 * {@link #refreshDisplayedText()} from their own constructor body.
 *
 * <h2>IME / non-ASCII digits</h2>
 * The default filter accepts only ASCII digits {@code 0..9} plus
 * {@code +} / {@code -} / {@code '.'}. Full-width digits ({@code １２３}) and
 * non-Arabic digit systems are rejected. Callers needing east-Asian IME
 * compatibility normalize input with {@code Normalizer.normalize(s, Form.NFKC)}
 * before pushing it through {@code setText}.
 *
 * @see RXFormattedNumberField
 * @see RXTextField
 */
public class RXNumberField extends RXTextField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-number-field";

    // ==================== Fields ====================

    private final TextFormatter<BigDecimal> formatter;

    // ==================== Constructors ====================

    /**
     * Creates a {@code RXNumberField} with a {@code null} value and empty
     * displayed text.
     */
    public RXNumberField() {
        this(null);
    }

    /**
     * Creates a {@code RXNumberField} with the given initial value.
     *
     * @param initialValue the initial {@link BigDecimal} value, may be
     *                     {@code null}
     */
    public RXNumberField(BigDecimal initialValue) {
        super();
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        formatter = new TextFormatter<>(createConverter(), initialValue, createFilter());
        setTextFormatter(formatter);

        value.bindBidirectional(formatter.valueProperty());

        InvalidationListener clampListener = obs -> enforceClamp();
        value.addListener(clampListener);
        min.addListener(clampListener);
        max.addListener(clampListener);
        enforceClamp();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXNumberFieldSkin(this);
    }

    // ==================== Subclass hooks ====================

    /**
     * Creates the {@link StringConverter} used by the internal
     * {@link TextFormatter} to round-trip between displayed text and the
     * {@link BigDecimal} value. Called once from the constructor before any
     * subclass field initializer runs — an override must not read
     * subclass-owned state directly; return a converter that captures
     * {@code this} and reads live properties lazily on each call.
     * <p>
     * The default returns a stateless converter:
     * {@link BigDecimal#toPlainString()} on the way out, {@code new
     * BigDecimal(text)} on the way in. {@code null} value renders as the
     * empty string, an empty / sign-only / single-dot string parses back to
     * {@code null}, and an unparseable string throws
     * {@link NumberFormatException} so the JavaFX
     * {@code TextFormatter.updateValue} catch block rolls back to the last
     * valid text.
     *
     * @return the converter; must not be {@code null}
     */
    protected StringConverter<BigDecimal> createConverter() {
        return new NumberFieldStringConverter();
    }

    /**
     * Creates the keystroke filter installed on the internal
     * {@link TextFormatter}. Called once from the constructor — see
     * {@link #createConverter()} for the same constructor-ordering caveat.
     * <p>
     * The default admits digits, a leading {@code +} / {@code -}, and a
     * single {@code '.'} as decimal point. Incomplete editing states like
     * {@code "-"}, {@code "1."}, {@code ".5"} pass through. Letters, multiple
     * decimal points, and full-width digits are rejected.
     *
     * @return the filter; must not be {@code null}
     */
    protected UnaryOperator<TextFormatter.Change> createFilter() {
        return new NumberFieldChangeFilter();
    }

    /**
     * Re-renders the displayed text from the current {@link #valueProperty()
     * value} through the active {@link StringConverter}. Used to push a fresh
     * representation when a format-only property changes — without going
     * through a parse → value-change → updateText round trip.
     * <p>
     * Subclasses install listeners on their own format-affecting properties
     * that call this method.
     */
    protected final void refreshDisplayedText() {
        StringConverter<BigDecimal> conv = formatter.getValueConverter();
        if (conv == null) {
            return;
        }
        String fresh = conv.toString(getValue());
        if (fresh == null) {
            fresh = "";
        }
        if (!fresh.equals(getText())) {
            setText(fresh);
        }
    }

    // ==================== value ====================

    private final ObjectProperty<BigDecimal> value = new SimpleObjectProperty<>(this, "value");

    /**
     * The current numeric value. {@code null} means "no value" — the displayed
     * text is empty. The property is bound bidirectionally to the underlying
     * {@link TextFormatter} so setting it programmatically pushes the canonical
     * formatted text into the field; conversely, a focus-lost / ENTER commit
     * pushes the parsed value back here.
     * <p>
     * The {@code BigDecimal} scale of the value is preserved across the
     * round-trip — {@code "1.2"} stays at scale 1; the base class does not
     * apply {@code setScale}.
     *
     * @return the value property
     * @defaultValue {@code null}
     */
    public final ObjectProperty<BigDecimal> valueProperty() {
        return value;
    }

    public final BigDecimal getValue() {
        return value.get();
    }

    public final void setValue(BigDecimal value) {
        this.value.set(value);
    }

    // ==================== min ====================

    private final ObjectProperty<BigDecimal> min = new SimpleObjectProperty<>(this, "min") {
        private BigDecimal lastValid = null;

        @Override
        protected void invalidated() {
            BigDecimal v = get();
            BigDecimal hi = getMax();
            if (v != null && hi != null && v.compareTo(hi) > 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException(
                        "min (" + v.toPlainString() + ") must be <= max ("
                                + hi.toPlainString() + ")");
            }
            lastValid = v;
        }
    };

    /**
     * Inclusive lower bound for {@link #valueProperty() value}. {@code null}
     * (the default) means no lower bound. When set to a non-null value, the
     * field clamps any commit / programmatic write that falls below it.
     * Tightening this bound while the field already holds a value pulls the
     * value up into range immediately.
     * <p>
     * Setting {@code min} to a value greater than the current
     * {@link #maxProperty() max} throws {@link IllegalArgumentException} and
     * restores the previous {@code min}. The combination
     * {@code min == max} is allowed and pins the value to that single point.
     *
     * @return the min property
     * @defaultValue {@code null}
     */
    public final ObjectProperty<BigDecimal> minProperty() {
        return min;
    }

    public final BigDecimal getMin() {
        return min.get();
    }

    public final void setMin(BigDecimal min) {
        this.min.set(min);
    }

    // ==================== max ====================

    private final ObjectProperty<BigDecimal> max = new SimpleObjectProperty<>(this, "max") {
        private BigDecimal lastValid = null;

        @Override
        protected void invalidated() {
            BigDecimal v = get();
            BigDecimal lo = getMin();
            if (v != null && lo != null && v.compareTo(lo) < 0) {
                if (!isBound()) {
                    set(lastValid);
                }
                throw new IllegalArgumentException(
                        "max (" + v.toPlainString() + ") must be >= min ("
                                + lo.toPlainString() + ")");
            }
            lastValid = v;
        }
    };

    /**
     * Inclusive upper bound for {@link #valueProperty() value}. {@code null}
     * (the default) means no upper bound. See {@link #minProperty()} for
     * the symmetric behavior.
     *
     * @return the max property
     * @defaultValue {@code null}
     */
    public final ObjectProperty<BigDecimal> maxProperty() {
        return max;
    }

    public final BigDecimal getMax() {
        return max.get();
    }

    public final void setMax(BigDecimal max) {
        this.max.set(max);
    }

    // ==================== Range enforcement ====================

    private boolean clamping = false;

    private void enforceClamp() {
        if (clamping || value.isBound()) {
            return;
        }
        BigDecimal v = value.get();
        if (v == null) {
            return;
        }
        BigDecimal lo = min.get();
        BigDecimal hi = max.get();
        BigDecimal clamped = v;
        boolean changed = false;
        if (hi != null && clamped.compareTo(hi) > 0) {
            clamped = hi;
            changed = true;
        }
        if (lo != null && clamped.compareTo(lo) < 0) {
            clamped = lo;
            changed = true;
        }
        if (!changed) {
            return;
        }
        clamping = true;
        try {
            value.set(clamped);
        } finally {
            clamping = false;
        }
        // When clamp fires inside the commitValue listener cascade, JFX's
        // internal formatter.value → text sync listener has already finished
        // its pass on the pre-clamp value, leaving the displayed text out of
        // sync (text="5" while value=10). Explicit refresh forces the text
        // to reflect the post-clamp value regardless of JFX's listener state.
        refreshDisplayedText();
    }
}

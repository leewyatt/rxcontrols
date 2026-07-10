package io.github.leewyatt.rxcontrols.internal.number;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the {@link TextFormatter} plumbing shared by the typed number fields
 * ({@code RXIntegerField} / {@code RXDoubleField} / {@code RXDecimalField}):
 * formatter assembly, the replace/bind guard, the value-text synchronization
 * locks, the lossy-format commit check, and the value coercion pipeline.
 * <p>
 * The engine is the only writer of the host control's {@code value} property
 * besides the property's own binding. An unbound value change runs the
 * {@code sanitize -> clamp -> write-back -> refresh} pipeline; a bound value is
 * owned by its binding and only re-rendered. When {@code sanitize} throws, the
 * value is coerced to {@code null} (empty field), the text is refreshed, and
 * the exception is rethrown. The rethrow happens inside the value property's
 * own {@code invalidated()} (which JavaFX invokes before notifying external
 * listeners), so it reaches the {@code setValue} caller synchronously; the
 * formatter guard below is an external listener whose exceptions would be
 * swallowed by {@code ExpressionHelper}, which is why the guard logs a WARNING
 * instead of throwing.
 *
 * @param <T> the value type of the host field
 */
public final class NumberFieldEngine<T> {

    private static final Logger LOGGER = Logger.getLogger(NumberFieldEngine.class.getName());

    private final RXTextField control;
    private final ObjectProperty<T> value;
    private final TextFormatter<T> formatter;

    // The delegate converter (as supplied by the control); used to render the
    // canonical text of the current value for the edit-origin check.
    private final StringConverter<T> converter;

    // Domain check applied to unbound value writes before clamping; throws to
    // reject (RXDoubleField rejects non-finite values), identity otherwise.
    private final UnaryOperator<T> sanitize;

    // Range clamp applied to unbound value writes and on bound changes.
    private final UnaryOperator<T> clamp;

    private final InvalidationListener formatterValueListener =
            obs -> handleFormatterValueChanged();
    // A ChangeListener suffices: any reachable formatter change fires it. The
    // one non-changing write — binding a source that already holds the
    // installed formatter — is rejected synchronously by TextInputControl's
    // own invalidated() (bindToControl throws ISE), whose catch unbinds and
    // nulls the property; that nested null write fires a change event, so this
    // guard restores the internal formatter before the ISE reaches the caller.
    private final ChangeListener<TextFormatter<?>> textFormatterGuard =
            (obs, oldFormatter, newFormatter) -> guardTextFormatter(newFormatter);

    // Sync-direction lock: we are pushing value -> formatter/text;
    // ignore the reverse formatter.value listener while true.
    private boolean updatingFormatter;

    // Reentrancy lock: we are restoring the internal formatter after an
    // external setTextFormatter; prevents the guard from recursing.
    private boolean restoringTextFormatter;

    // Reentrancy lock: the pipeline is rewriting value itself;
    // prevents valueInvalidated from re-entering.
    private boolean updatingValue;

    // The text of the most recent commit, captured when the formatter parses it.
    // Refreshed on every commit (success or failure), so it never goes stale.
    private String committedText;

    /**
     * Creates the engine and installs the internal formatter on the control.
     *
     * @param control   the host field
     * @param value     the host's value property; the engine becomes its only
     *                  non-binding writer
     * @param converter the type-specific converter, never {@code null}
     * @param filter    the type-specific edit filter, never {@code null}
     * @param sanitize  the domain check for unbound value writes; may throw a
     *                  {@link RuntimeException} to reject, or
     *                  {@link UnaryOperator#identity()} when the type has no
     *                  domain rule beyond its range
     * @param clamp     the range clamp for unbound value writes
     */
    public NumberFieldEngine(RXTextField control, ObjectProperty<T> value,
                             StringConverter<T> converter,
                             UnaryOperator<TextFormatter.Change> filter,
                             UnaryOperator<T> sanitize,
                             UnaryOperator<T> clamp) {
        this.control = Objects.requireNonNull(control, "control cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
        this.converter = Objects.requireNonNull(converter, "converter cannot be null");
        Objects.requireNonNull(filter, "filter cannot be null");
        this.sanitize = Objects.requireNonNull(sanitize, "sanitize cannot be null");
        this.clamp = Objects.requireNonNull(clamp, "clamp cannot be null");

        // Wrap the converter to capture each committed text, so the edit-origin
        // check compares what the user committed against the canonical rendering.
        StringConverter<T> capturingConverter = new StringConverter<>() {
            @Override
            public String toString(T v) {
                return converter.toString(v);
            }

            @Override
            public T fromString(String s) {
                committedText = s;
                T parsed = converter.fromString(s);
                // Commits normalize the displayed text: a changed value re-renders
                // through TextFormatter's own value.invalidated -> updateText. But
                // ObjectPropertyBase.set short-circuits on reference equality, so a
                // parsed result that is the very instance the formatter already
                // holds (Integer.valueOf cache, null) would fire nothing and leave
                // non-canonical text as typed — "+5" would stick while "+500"
                // normalizes, leaking the integer cache into visible behavior.
                // Throw instead: updateValue's catch calls updateText, which
                // renders the canonical text of the current (unchanged) value.
                if (parsed == formatter.getValue()
                        && !Objects.equals(s, converter.toString(parsed))) {
                    throw new NumberFormatException(
                            "Non-canonical text for an unchanged value: " + s);
                }
                return parsed;
            }
        };
        formatter = new TextFormatter<>(capturingConverter, null, filter);
        control.setTextFormatter(formatter);
        control.textFormatterProperty().addListener(textFormatterGuard);
        formatter.valueProperty().addListener(formatterValueListener);

        // Commit backstop for a purely programmatic fireEvent(new ActionEvent())
        // that bypasses the TextField behavior. JavaFX already commits on ENTER
        // (the behavior commits before firing the action) and on focus loss, so
        // on those paths this is a harmless idempotent no-op. Registered on the
        // control (not a skin) so it survives skin replacement, and via
        // addEventHandler so it coexists with a user onAction handler.
        control.addEventHandler(ActionEvent.ACTION, e -> control.commitValue());
    }

    /**
     * Entry point for the host value property's {@code invalidated()}:
     * a bound value is only re-rendered (its binding owns it, Slider-style);
     * an unbound value runs {@code sanitize -> clamp -> write-back -> refresh}.
     * When {@code sanitize} throws, the value is coerced to {@code null}, the
     * text refreshed, and the exception rethrown to the caller.
     */
    public void valueInvalidated() {
        if (updatingValue) {
            return;
        }
        if (value.isBound()) {
            // A bound value is owned by its binding: it cannot be sanitized or
            // clamped (Slider leaves a bound value as-is). Keep the text in sync
            // and leave the value to its binding.
            refreshText();
            return;
        }
        T candidate = value.get();
        T sanitized;
        try {
            sanitized = sanitize.apply(candidate);
        } catch (RuntimeException ex) {
            // The value violates the type's domain rule (e.g. a non-finite double).
            // Coerce to the constant null (empty field) rather than track a
            // last-valid value, then rethrow so the caller sees the rejection.
            updatingValue = true;
            try {
                value.set(null);
            } finally {
                updatingValue = false;
            }
            refreshText();
            throw ex;
        }
        T coerced = clamp.apply(sanitized);
        if (!Objects.equals(candidate, coerced)) {
            updatingValue = true;
            try {
                value.set(coerced);
            } finally {
                updatingValue = false;
            }
        }
        refreshText();
    }

    /**
     * Entry point for the host min/max properties' {@code invalidated()}: the
     * port of Slider's {@code adjustValues} — re-clamp an unbound value into the
     * new range; a bound value is only re-rendered.
     */
    public void boundsChanged() {
        if (value.isBound()) {
            refreshText();
            return;
        }
        T current = value.get();
        T clamped = clamp.apply(current);
        if (!Objects.equals(current, clamped)) {
            updatingValue = true;
            try {
                value.set(clamped);
            } finally {
                updatingValue = false;
            }
        }
        refreshText();
    }

    /**
     * Re-renders the displayed text from the current value without committing
     * text back into the value property. Also used by the host when a
     * presentation property (e.g. {@code numberFormat}) changes.
     */
    public void refreshText() {
        updatingFormatter = true;
        try {
            T current = value.get();
            if (!Objects.equals(formatter.getValue(), current)) {
                formatter.setValue(current);
            }
            control.cancelEdit();
            if (control.getText() == null) {
                control.setText("");
            }
        } finally {
            updatingFormatter = false;
        }
    }

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
        T current = value.get();
        if (value.isBound()
                || committedText == null
                || Objects.equals(committedText, converter.toString(current))) {
            refreshText();
            return;
        }
        value.set(formatter.getValue());
    }

    private void guardTextFormatter(TextFormatter<?> newFormatter) {
        if (restoringTextFormatter || newFormatter == formatter) {
            return;
        }
        boolean wasBound = control.textFormatterProperty().isBound();
        // This guard is an external listener on the inherited textFormatter
        // property: any exception it throws is caught by ExpressionHelper and
        // routed to the uncaught-exception handler, never reaching the caller.
        // So both repair paths log a WARNING instead of throwing, and the
        // bind path removes the binding first (failure atomicity: the external
        // formatter must not stay installed with the binding intact).
        restoringTextFormatter = true;
        try {
            if (wasBound) {
                control.textFormatterProperty().unbind();
            }
            control.setTextFormatter(formatter);
        } finally {
            restoringTextFormatter = false;
        }
        if (wasBound) {
            LOGGER.log(Level.WARNING,
                    "Binding textFormatter on a number field is unsupported;"
                            + " the binding was removed and the internal formatter"
                            + " restored.");
        } else {
            LOGGER.log(Level.WARNING,
                    "A number field manages its own TextFormatter; replacing it via"
                            + " setTextFormatter is unsupported. The internal formatter"
                            + " has been restored; the replacement was ignored.");
        }
    }
}

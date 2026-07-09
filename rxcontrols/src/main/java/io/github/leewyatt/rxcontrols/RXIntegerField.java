package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.IntegerFieldChangeFilter;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Integer-only numeric text field.
 * <p>
 * User edits reject the decimal point. Direct programmatic writes go through
 * the same integer domain check: values such as {@code 1.0} normalize to
 * {@code 1}, while values such as {@code 1.5} are rejected. A value supplied
 * through a binding, however, is owned by that binding: the field cannot
 * normalize or reject it, so it is displayed as-is (e.g. a bound {@code 1.5}
 * stays {@code 1.5}) and keeping it integral is the caller's responsibility.
 *
 * @see RXNumberField
 */
public class RXIntegerField extends RXNumberField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-integer-field";

    // ==================== Constructors ====================

    /**
     * Creates a {@code RXIntegerField} with a {@code null} value and empty
     * displayed text.
     */
    public RXIntegerField() {
        this(null);
    }

    /**
     * Creates a {@code RXIntegerField} with the given initial value.
     *
     * @param initialValue the initial {@link BigDecimal} value, may be
     *                     {@code null}
     * @throws IllegalArgumentException if the value has a fractional part
     */
    public RXIntegerField(BigDecimal initialValue) {
        super(initialValue);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    // ==================== Subclass hooks ====================

    @Override
    protected UnaryOperator<TextFormatter.Change> createFilter() {
        return new IntegerFieldChangeFilter();
    }

    @Override
    protected BigDecimal normalizeValue(BigDecimal value) {
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(0);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("integer field value must not have a fractional part", ex);
        }
    }
}

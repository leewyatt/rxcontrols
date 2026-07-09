package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.IntegerFieldChangeFilter;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.UnaryOperator;

/**
 * Integer-only numeric text field.
 * <p>
 * User edits reject the decimal point. Direct programmatic writes to the
 * {@link #valueProperty() value} go through the same integer domain check:
 * values such as {@code 1.0} normalize to {@code 1}, while values such as
 * {@code 1.5} are rejected. A value supplied through a binding, however, is
 * owned by that binding: the field cannot normalize or reject it, so it is
 * displayed as-is (e.g. a bound {@code 1.5} stays {@code 1.5}) and keeping it
 * integral is the caller's responsibility.
 * <p>
 * {@link #minProperty() min} / {@link #maxProperty() max} are stored leniently
 * like {@link RXNumberField} (a fractional bound is accepted, not rejected), but
 * the value stays integral because the <em>clamp target</em> is snapped into the
 * integer domain: the lower bound rounds up and the upper bound rounds down (so
 * the clamped value still honours the raw bound). For example {@code min = 1.5}
 * gives an effective lower limit of {@code 2}, and {@code max = 8.5} an effective
 * upper limit of {@code 8}. A range with no integer member (e.g.
 * {@code min = 1.5, max = 1.8}, snapping to {@code [2, 1]}) is a caller
 * misconfiguration; the value keeps its current integer rather than becoming
 * fractional.
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

    @Override
    protected BigDecimal effectiveLowerBound(BigDecimal min) {
        // Round the lower bound up so a value clamped to it stays >= min and integral.
        return min == null ? null : min.setScale(0, RoundingMode.CEILING);
    }

    @Override
    protected BigDecimal effectiveUpperBound(BigDecimal max) {
        // Round the upper bound down so a value clamped to it stays <= max and integral.
        return max == null ? null : max.setScale(0, RoundingMode.FLOOR);
    }
}

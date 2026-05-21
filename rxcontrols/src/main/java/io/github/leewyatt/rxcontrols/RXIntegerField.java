package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.IntegerFieldChangeFilter;
import javafx.scene.control.TextFormatter;

import java.math.BigDecimal;
import java.util.function.UnaryOperator;

/**
 * Integer-only numeric text field. Same {@link BigDecimal} value model and
 * commit / focus / slot machinery as {@link RXNumberField}, with the
 * keystroke filter narrowed to reject the decimal point — only ASCII digits
 * {@code 0..9} and a leading {@code +} / {@code -} pass through.
 * <p>
 * Typing {@code "1.5"} is impossible because {@code '.'} is rejected at
 * keystroke time; the field never enters a fractional state through user
 * input. The base {@link RXNumberField} converter is reused unchanged —
 * since the filter has already prevented {@code '.'} from reaching it,
 * {@code new BigDecimal(text)} naturally produces a {@code BigDecimal} with
 * {@code scale() == 0}. Callers wanting the primitive go through
 * {@link BigDecimal#intValueExact()} or
 * {@link BigDecimal#longValueExact()}; both throw
 * {@link ArithmeticException} on overflow, matching the {@code BigDecimal}
 * standard contract.
 *
 * <h2>Programmatic setValue is the caller's responsibility</h2>
 * The filter only guards user-typed and pasted input. Calling
 * {@link #setValue(BigDecimal) setValue} with a fractional
 * {@code BigDecimal} (e.g. {@code new BigDecimal("3.14")}) is undefined —
 * the displayed text will render the fractional value as-is, but the field
 * has no way to retype it through its own filter. Treat it as garbage in,
 * garbage out; don't pass fractional values to an integer field.
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
     *                     {@code null}; if non-null the caller is responsible
     *                     for ensuring {@code scale() == 0}
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
}

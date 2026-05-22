package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.number.FormattedNumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.internal.number.FormattedNumberFieldConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.function.UnaryOperator;

/**
 * Number field that renders and parses ordinary localized number formats with
 * a {@link NumberFormat}.
 * <p>
 * This class targets common decimal, grouping, currency, and percent formats.
 * Scientific notation is intentionally not supported by the edit filter in
 * this version.
 * <p>
 * {@link NumberFormat} is mutable. Mutating the live instance returned by
 * {@link #getNumberFormat()} affects future parse / format operations, but it
 * does not invalidate the property and therefore does not automatically
 * refresh the displayed text. Assign another {@code NumberFormat} instance to
 * trigger automatic re-rendering.
 *
 * @see RXNumberField
 */
public class RXFormattedNumberField extends RXNumberField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-formatted-number-field";

    // ==================== Constructors ====================

    /**
     * Creates an empty formatted number field.
     */
    public RXFormattedNumberField() {
        this(null);
    }

    /**
     * Creates a formatted number field with an initial value.
     *
     * @param initialValue the initial value, or {@code null}
     */
    public RXFormattedNumberField(BigDecimal initialValue) {
        super();
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        numberFormat.addListener(obs -> refreshDisplayedText());
        setValue(initialValue);
        refreshDisplayedText();
    }

    // ==================== Subclass hooks ====================

    @Override
    protected StringConverter<BigDecimal> createConverter() {
        return new FormattedNumberFieldConverter(this);
    }

    @Override
    protected UnaryOperator<TextFormatter.Change> createFilter() {
        return new FormattedNumberFieldChangeFilter(this);
    }

    // ==================== numberFormat ====================

    private final ObjectProperty<NumberFormat> numberFormat =
            new SimpleObjectProperty<>(this, "numberFormat", NumberFormat.getNumberInstance());

    /**
     * The number format used for both display and commit parsing.
     * {@code null} falls back to plain {@link BigDecimal#toPlainString()}.
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
     * @param numberFormat the number format, or {@code null}
     */
    public final void setNumberFormat(NumberFormat numberFormat) {
        this.numberFormat.set(numberFormat);
    }
}

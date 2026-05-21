package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.FormattedNumberFieldChangeFilter;
import io.github.leewyatt.rxcontrols.skins.FormattedNumberFieldConverter;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.function.UnaryOperator;

/**
 * Numeric text field that drives its rendering and parsing through a
 * {@link NumberFormat}. Inherits the {@link BigDecimal} value model and
 * commit / focus / slot machinery from {@link RXNumberField}, and adds a
 * single {@link #numberFormatProperty() numberFormat} property that decides
 * everything else: grouping, decimal separator, sign style, currency or
 * percent symbol, scientific notation, locale-specific digit shape — pick
 * whatever {@code NumberFormat} subclass fits the use case.
 * <p>
 * Out of the box, the default is {@link NumberFormat#getNumberInstance()} —
 * the JVM's default {@link java.util.Locale.Category#FORMAT FORMAT} locale.
 * A German JVM renders {@code 1234567.89} as {@code "1.234.567,89"}, a US
 * JVM as {@code "1,234,567.89"}, with zero configuration. Swap to any other
 * format via {@link #setNumberFormat(NumberFormat)}:
 *
 * <pre>{@code
 * RXFormattedNumberField field = new RXFormattedNumberField(new BigDecimal("1234567.89"));
 *
 * // US currency
 * field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
 * // → "$1,234,567.89"
 *
 * // Japanese yen (zero fraction digits)
 * field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.JAPAN));
 * // → "¥1,234,568"
 *
 * // Chinese 万分位 (custom pattern)
 * field.setNumberFormat(new DecimalFormat("#,####.##"));
 * // → "123,4567.89"
 *
 * // Percent
 * field.setNumberFormat(NumberFormat.getPercentInstance());
 * // → "123,456,789%"  (for input 1234567.89)
 * }</pre>
 *
 * For decorative suffixes / currency icons in front of or after the field,
 * compose with {@link #setLeft(javafx.scene.Node) setLeft} /
 * {@link #setRight(javafx.scene.Node) setRight} — a styled
 * {@link javafx.scene.control.Label} or SVG icon in the slot generally looks
 * better than text baked into the field.
 *
 * <h2>Scale follows NumberFormat, not user input</h2>
 * Unlike the base class, {@code RXFormattedNumberField} does <b>not</b>
 * preserve the user's typed {@code BigDecimal} scale across commit. The
 * value's scale follows the {@link NumberFormat}'s
 * {@link NumberFormat#getMaximumFractionDigits() maximumFractionDigits}
 * during the format round-trip — a side effect of letting
 * {@code NumberFormat} own rendering. Typing {@code "1.20000"} into a default
 * {@code getNumberInstance()} field (maxFractionDigits=3) commits a value of
 * scale 5, but the display reformats to {@code "1.2"} (max-frac truncated,
 * trailing zeros stripped). Compare values with
 * {@link BigDecimal#compareTo(BigDecimal)}, not {@code equals}.
 * <p>
 * For end-to-end scale preservation, use the parent {@link RXNumberField}
 * directly (no {@code NumberFormat}, plain
 * {@link BigDecimal#toPlainString()}).
 *
 * <h2>BigDecimal precision on parse</h2>
 * The converter internally clones the supplied {@link NumberFormat} and
 * enables {@link java.text.DecimalFormat#setParseBigDecimal(boolean)
 * parseBigDecimal(true)} on the clone so parse results retain full precision.
 * The user-supplied instance is <b>not</b> mutated. {@code NumberFormat}
 * subclasses that are not {@link java.text.DecimalFormat} fall back to a
 * {@code Number} → {@code BigDecimal.toString()} round-trip, which may lose
 * precision; recommend supplying a {@code DecimalFormat} for precision-
 * sensitive use cases.
 *
 * <h2>Property identity matters</h2>
 * The format property change listener fires on {@link Object#equals(Object)}
 * inequality. If you mutate a {@code NumberFormat} in place (e.g. call
 * {@code format.setMaximumFractionDigits(2)} on the live instance), the
 * field does <b>not</b> notice — assign a different instance via
 * {@link #setNumberFormat(NumberFormat)} to trigger a re-render.
 *
 * <h2>Runtime locale changes</h2>
 * The constructor captures {@link NumberFormat#getNumberInstance()} <em>at
 * construction time</em>. If the application later changes
 * {@code Locale.setDefault(...)} (i18n switch), already-created fields
 * continue using the old format. Re-assign via
 * {@code field.setNumberFormat(NumberFormat.getNumberInstance())} after the
 * locale switch to pick up the new locale.
 *
 * @see RXNumberField
 */
public class RXFormattedNumberField extends RXNumberField {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-formatted-number-field";

    // ==================== Constructors ====================

    /**
     * Creates a {@code RXFormattedNumberField} with a {@code null} value and
     * the JVM's default-locale {@link NumberFormat#getNumberInstance()}.
     */
    public RXFormattedNumberField() {
        this(null);
    }

    /**
     * Creates a {@code RXFormattedNumberField} with the given initial value
     * and the JVM's default-locale {@link NumberFormat#getNumberInstance()}.
     *
     * @param initialValue the initial {@link BigDecimal} value, may be
     *                     {@code null}
     */
    public RXFormattedNumberField(BigDecimal initialValue) {
        super(initialValue);
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        numberFormat.addListener(obs -> refreshDisplayedText());
        // The base constructor's setTextFormatter triggered toString() once
        // with this.numberFormat still null (field initializer hadn't run
        // yet); re-render now that the format is available so the user sees
        // formatted text immediately after construction.
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
     * The {@link NumberFormat} that drives both rendering ({@code format} on
     * the displayed text) and parsing ({@code parse} on commit). Tolerates
     * {@code null} — a {@code null} value falls back to the base class's
     * plain {@link BigDecimal#toPlainString()} behavior for the duration.
     * <p>
     * Replacing the format triggers an immediate re-render of the current
     * value through the new format. The property fires on instance
     * inequality, so mutating a format in place is not noticed —
     * see the class-level note on property identity.
     *
     * @return the number-format property
     * @defaultValue {@link NumberFormat#getNumberInstance()} captured at
     * construction time
     */
    public final ObjectProperty<NumberFormat> numberFormatProperty() {
        return numberFormat;
    }

    public final NumberFormat getNumberFormat() {
        // Field is null during super(...) construction: the base constructor
        // invokes the subclass-overridden createConverter()/createFilter() and
        // then setTextFormatter triggers a converter.toString(initialValue)
        // call — all before this class's field initializer runs. Returning
        // null lets the converter's null-fallback (BigDecimal.toPlainString)
        // handle that transient window; the post-super refreshDisplayedText()
        // re-renders with the live format once the field is initialized.
        return numberFormat == null ? null : numberFormat.get();
    }

    public final void setNumberFormat(NumberFormat numberFormat) {
        this.numberFormat.set(numberFormat);
    }
}

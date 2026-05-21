package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;

/**
 * {@link StringConverter} that drives {@link RXFormattedNumberField} rendering
 * and parsing through the field's live
 * {@link RXFormattedNumberField#numberFormatProperty() numberFormat}.
 * <p>
 * <b>Rendering.</b> Delegates to {@link NumberFormat#format(Object)} on the
 * field's current format. If the format is {@code null} (transient state
 * during base-class construction, or user reset), falls back to
 * {@link BigDecimal#toPlainString()}.
 * <p>
 * <b>Parsing.</b> If the format is a {@link DecimalFormat}, the converter
 * clones it and enables {@link DecimalFormat#setParseBigDecimal(boolean)
 * parseBigDecimal(true)} on the clone so {@code parse} returns full-precision
 * {@code BigDecimal}. The clone is cached per source-instance to avoid
 * cloning on every keystroke / commit. The user-supplied {@code NumberFormat}
 * is never mutated. Non-{@code DecimalFormat} sources fall back to
 * {@code source.parse(text, pp)} and convert {@code Number → BigDecimal} via
 * {@code toString()}; this may lose precision and is best avoided for
 * money-grade inputs.
 * <p>
 * Throws {@link NumberFormatException} when the format cannot consume the
 * full input string, so the JavaFX {@code TextFormatter.updateValue} catch
 * block rolls back the displayed text to the last valid value.
 * <p>
 * Internal class — declared {@code public} only so
 * {@link RXFormattedNumberField} (a different package) can instantiate it.
 * Not part of the stable public API.
 */
public final class FormattedNumberFieldConverter extends StringConverter<BigDecimal> {

    private final RXFormattedNumberField field;
    private NumberFormat cachedSource;
    private DecimalFormat cachedParseFormat;

    public FormattedNumberFieldConverter(RXFormattedNumberField field) {
        this.field = field;
    }

    @Override
    public String toString(BigDecimal value) {
        if (value == null) {
            return "";
        }
        NumberFormat nf = field.getNumberFormat();
        return nf == null ? value.toPlainString() : nf.format(value);
    }

    @Override
    public BigDecimal fromString(String text) {
        if (text == null) {
            return null;
        }
        String raw = text.trim();
        if (raw.isEmpty()) {
            return null;
        }

        NumberFormat source = field.getNumberFormat();
        if (source == null) {
            return parsePlain(raw);
        }

        if (source instanceof DecimalFormat df) {
            DecimalFormat parser = parserClone(df);
            ParsePosition pp = new ParsePosition(0);
            Number parsed = parser.parse(raw, pp);
            if (parsed == null || pp.getIndex() != raw.length()) {
                throw new NumberFormatException("Unparseable number: " + raw);
            }
            return (BigDecimal) parsed;
        }

        ParsePosition pp = new ParsePosition(0);
        Number n = source.parse(raw, pp);
        if (n == null || pp.getIndex() != raw.length()) {
            throw new NumberFormatException("Unparseable number: " + raw);
        }
        return new BigDecimal(n.toString());
    }

    private DecimalFormat parserClone(DecimalFormat source) {
        if (source != cachedSource) {
            DecimalFormat clone = (DecimalFormat) source.clone();
            clone.setParseBigDecimal(true);
            cachedSource = source;
            cachedParseFormat = clone;
        }
        return cachedParseFormat;
    }

    private static BigDecimal parsePlain(String raw) {
        if ("-".equals(raw) || "+".equals(raw) || ".".equals(raw)
                || "-.".equals(raw) || "+.".equals(raw)) {
            return null;
        }
        return new BigDecimal(raw);
    }
}

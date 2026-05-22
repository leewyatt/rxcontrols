package io.github.leewyatt.rxcontrols.internal.number;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParsePosition;

/**
 * {@link NumberFormat}-driven converter for formatted number fields.
 */
public final class FormattedNumberFieldConverter extends StringConverter<BigDecimal> {

    private final RXFormattedNumberField field;

    /**
     * Creates a converter bound to a formatted number field.
     *
     * @param field the owning field
     */
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
            DecimalFormat parser = (DecimalFormat) df.clone();
            parser.setParseBigDecimal(true);
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

    private static BigDecimal parsePlain(String raw) {
        if ("-".equals(raw) || "+".equals(raw) || ".".equals(raw)
                || "-.".equals(raw) || "+.".equals(raw)) {
            throw new NumberFormatException("Incomplete number: " + raw);
        }
        return new BigDecimal(raw);
    }
}

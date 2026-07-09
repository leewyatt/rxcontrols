package io.github.leewyatt.rxcontrols.internal.number;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
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
            return parseDecimal(parser, raw);
        }

        ParsePosition pp = new ParsePosition(0);
        Number n = source.parse(raw, pp);
        if (n == null || pp.getIndex() != raw.length()) {
            throw new NumberFormatException("Unparseable number: " + raw);
        }
        return new BigDecimal(n.toString());
    }

    /**
     * Parses {@code raw} with a big-decimal-configured {@link DecimalFormat}.
     * A strict parse is tried first; on failure the parse retries leniently so
     * the converter accepts exactly what {@link FormattedNumberFieldChangeFilter}
     * lets through. The filter treats the format's affix as optional (it strips
     * the affix before validating the numeric body), so a bare body such as
     * {@code "75"} under a percent format or {@code "100"} under a currency
     * format must still commit rather than be silently dropped. The retry
     * re-attaches the format's own positive affix around the sign-stripped body
     * and re-parses, which preserves DecimalFormat semantics (grouping,
     * decimals, and the percent multiplier); the sign is applied afterward.
     */
    private static BigDecimal parseDecimal(DecimalFormat parser, String raw) {
        BigDecimal strict = strictParse(parser, raw);
        if (strict != null) {
            return strict;
        }

        String posPrefix = parser.getPositivePrefix();
        String posSuffix = parser.getPositiveSuffix();
        String negPrefix = parser.getNegativePrefix();
        String negSuffix = parser.getNegativeSuffix();

        boolean negative = false;
        String body = raw;
        // Mirror the edit filter's stripAffix order — positive affix first — so a
        // format whose positive and negative affix coincide (percent's "%", suffix
        // currencies) does not misread an explicit "+" as a negative suffix.
        if (!posPrefix.isEmpty() && body.startsWith(posPrefix)) {
            body = body.substring(posPrefix.length());
        } else if (!negPrefix.isEmpty() && body.startsWith(negPrefix)) {
            body = body.substring(negPrefix.length());
            negative = true;
        }
        if (!posSuffix.isEmpty() && body.endsWith(posSuffix)) {
            body = body.substring(0, body.length() - posSuffix.length());
        } else if (!negSuffix.isEmpty() && body.endsWith(negSuffix)) {
            body = body.substring(0, body.length() - negSuffix.length());
            negative = true;
        }

        DecimalFormatSymbols symbols = parser.getDecimalFormatSymbols();
        char minusSign = symbols.getMinusSign();
        if (!body.isEmpty()) {
            char first = body.charAt(0);
            if (first == '-' || first == minusSign) {
                negative = true;
                body = body.substring(1);
            } else if (first == '+') {
                body = body.substring(1);
            }
        }
        // Grouping separators are cosmetic and the edit filter admits them in any
        // position; drop them so a filter-accepted body such as a trailing "5,"
        // re-parses instead of failing the DecimalFormat full-consume check.
        char groupSep = symbols.getGroupingSeparator();
        if (body.indexOf(groupSep) >= 0) {
            body = body.replace(String.valueOf(groupSep), "");
        }
        if (body.isEmpty()) {
            // Affix / sign only ("$", "%", "-"): an incomplete entry, not a cleared
            // field (empty text already returned null above). Throw so the commit
            // path reverts to the last valid value instead of dropping it to null.
            throw new NumberFormatException("Incomplete number: " + raw);
        }

        BigDecimal magnitude = strictParse(parser, posPrefix + body + posSuffix);
        if (magnitude == null) {
            throw new NumberFormatException("Unparseable number: " + raw);
        }
        return negative ? magnitude.negate() : magnitude;
    }

    private static BigDecimal strictParse(DecimalFormat parser, String text) {
        ParsePosition pp = new ParsePosition(0);
        Number parsed = parser.parse(text, pp);
        if (parsed == null || pp.getIndex() != text.length()) {
            return null;
        }
        return (BigDecimal) parsed;
    }

    private static BigDecimal parsePlain(String raw) {
        return NumberParsing.parsePlainDecimal(raw);
    }
}

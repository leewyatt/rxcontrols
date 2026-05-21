package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.scene.control.TextFormatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.function.UnaryOperator;

/**
 * Keystroke filter for {@link RXFormattedNumberField}. Reads the live
 * {@link RXFormattedNumberField#numberFormatProperty() numberFormat} on each
 * invocation so reconfiguration takes effect immediately.
 * <p>
 * For a {@link DecimalFormat} source the filter pulls grouping / decimal /
 * minus-sign characters from {@link DecimalFormatSymbols} and strips any
 * positive / negative prefix / suffix before validating the remaining body
 * against the digit + sign + separator vocabulary. This handles plain
 * numbers, currency ({@code "$1,234.56"}), percent ({@code "12.5%"}),
 * locale variants ({@code "1.234,56"}), and arbitrary user-defined patterns.
 * Incomplete editing states are admitted liberally — the empty string, the
 * prefix or suffix alone (mid-paste), a lone sign, a lone decimal separator.
 * <p>
 * For non-{@link DecimalFormat} sources or a {@code null} format, the filter
 * falls back to the base ASCII digit + {@code +/-} + {@code '.'} vocabulary.
 * <p>
 * Final commit validation belongs to the converter; the filter only guards
 * keystroke-level character vocabulary.
 * <p>
 * Internal class — declared {@code public} only so
 * {@link RXFormattedNumberField} (a different package) can instantiate it.
 * Not part of the stable public API.
 */
public final class FormattedNumberFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

    private final RXFormattedNumberField field;

    public FormattedNumberFieldChangeFilter(RXFormattedNumberField field) {
        this.field = field;
    }

    @Override
    public TextFormatter.Change apply(TextFormatter.Change change) {
        String proposed = change.getControlNewText();
        if (proposed == null || proposed.isEmpty()) {
            return change;
        }
        return isAcceptable(proposed) ? change : null;
    }

    private boolean isAcceptable(String text) {
        NumberFormat nf = field.getNumberFormat();
        if (!(nf instanceof DecimalFormat df)) {
            return baseAcceptable(text);
        }

        DecimalFormatSymbols syms = df.getDecimalFormatSymbols();
        char groupSep = syms.getGroupingSeparator();
        char decSep = syms.getDecimalSeparator();
        char minusSign = syms.getMinusSign();

        String body = stripAffix(text, df.getPositivePrefix(), df.getPositiveSuffix(),
                df.getNegativePrefix(), df.getNegativeSuffix());
        if (body.isEmpty()) {
            return true;
        }

        int decCount = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '+' || c == '-' || c == minusSign) {
                if (i != 0) {
                    return false;
                }
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c == groupSep) {
                continue;
            }
            if (c == decSep) {
                decCount++;
                if (decCount > 1) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    private static String stripAffix(String text,
                                     String posPrefix, String posSuffix,
                                     String negPrefix, String negSuffix) {
        String body = text;
        if (!posPrefix.isEmpty() && body.startsWith(posPrefix)) {
            body = body.substring(posPrefix.length());
        } else if (!negPrefix.isEmpty() && body.startsWith(negPrefix)) {
            body = body.substring(negPrefix.length());
        }
        if (!posSuffix.isEmpty() && body.endsWith(posSuffix)) {
            body = body.substring(0, body.length() - posSuffix.length());
        } else if (!negSuffix.isEmpty() && body.endsWith(negSuffix)) {
            body = body.substring(0, body.length() - negSuffix.length());
        }
        return body;
    }

    private static boolean baseAcceptable(String text) {
        if ("-".equals(text) || "+".equals(text)) {
            return true;
        }
        int decCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '-' || c == '+') {
                if (i != 0) {
                    return false;
                }
                continue;
            }
            if (c >= '0' && c <= '9') {
                continue;
            }
            if (c == '.') {
                decCount++;
                if (decCount > 1) {
                    return false;
                }
                continue;
            }
            return false;
        }
        return true;
    }
}

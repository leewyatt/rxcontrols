package io.github.leewyatt.rxcontrols.internal.number;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.scene.control.TextFormatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.function.UnaryOperator;

/**
 * Edit filter for ordinary localized number formats.
 */
public final class FormattedNumberFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

    private final RXFormattedNumberField field;

    /**
     * Creates a filter bound to a formatted number field.
     *
     * @param field the owning field
     */
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
            if (Character.digit(c, 10) >= 0) {
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

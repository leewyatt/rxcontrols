package io.github.leewyatt.rxcontrols.internal.number;

import javafx.scene.control.TextFormatter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Edit filter for decimal fields. With a {@code null} {@link NumberFormat} it
 * validates plain decimal text; a {@link DecimalFormat} makes the filter
 * format-aware (optional affix, localized separators and minus sign). Any other
 * format accepts every edit, since its notation is not known here; the commit
 * then parses the text with that format and rolls back what it cannot parse.
 */
public final class DecimalFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

    private final Supplier<NumberFormat> numberFormat;

    /**
     * Creates a filter reading the active format from the given supplier.
     *
     * @param numberFormat supplies the current format; may supply {@code null}
     *                     for plain decimal filtering
     */
    public DecimalFieldChangeFilter(Supplier<NumberFormat> numberFormat) {
        this.numberFormat = numberFormat;
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
        NumberFormat nf = numberFormat.get();
        if (nf == null) {
            return baseAcceptable(text);
        }
        if (!(nf instanceof DecimalFormat df)) {
            return true;
        }

        DecimalFormatSymbols syms = df.getDecimalFormatSymbols();
        char groupSep = syms.getGroupingSeparator();
        char decSep = syms.getDecimalSeparator();
        char minusSign = syms.getMinusSign();

        String body = text;
        // A stripped negative affix already carries the sign, so the body may not
        // add another one ("--5", "-$-5"). The longer affix is tried first, so a
        // negative affix that contains the positive one ("x" / "-x") is not cut
        // short; equal-length affixes keep the positive-first order.
        boolean signInAffix = false;
        String posPrefix = df.getPositivePrefix();
        String negPrefix = df.getNegativePrefix();
        if (negPrefix.length() > posPrefix.length() && body.startsWith(negPrefix)) {
            body = body.substring(negPrefix.length());
            signInAffix = true;
        } else if (!posPrefix.isEmpty() && body.startsWith(posPrefix)) {
            body = body.substring(posPrefix.length());
        } else if (!negPrefix.isEmpty() && body.startsWith(negPrefix)) {
            body = body.substring(negPrefix.length());
            signInAffix = true;
        }
        String posSuffix = df.getPositiveSuffix();
        String negSuffix = df.getNegativeSuffix();
        if (negSuffix.length() > posSuffix.length() && body.endsWith(negSuffix)) {
            body = body.substring(0, body.length() - negSuffix.length());
            signInAffix = true;
        } else if (!posSuffix.isEmpty() && body.endsWith(posSuffix)) {
            body = body.substring(0, body.length() - posSuffix.length());
        } else if (!negSuffix.isEmpty() && body.endsWith(negSuffix)) {
            body = body.substring(0, body.length() - negSuffix.length());
            signInAffix = true;
        }
        if (body.isEmpty()) {
            return true;
        }

        int decCount = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '+' || c == '-' || c == minusSign) {
                if (i != 0 || signInAffix) {
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

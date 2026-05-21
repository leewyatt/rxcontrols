package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXNumberField;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * Default keystroke filter for {@link RXNumberField}. Stateless, no locale
 * opinion — accepts ASCII digits {@code 0..9}, a leading {@code +} / {@code -},
 * and a single {@code '.'} as decimal point. Rejects letters, full-width
 * digits, multiple decimal points, and a sign anywhere but position zero.
 * <p>
 * Incomplete editing states pass through: {@code "-"}, {@code "+"},
 * {@code "1."}, {@code ".5"} are all accepted. Final parse / commit is the
 * converter's job; the filter only guards against visually-invalid
 * characters.
 * <p>
 * Internal class — declared {@code public} only so {@link RXNumberField} (a
 * different package) can instantiate it. Not part of the stable public API.
 */
public final class NumberFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

    @Override
    public TextFormatter.Change apply(TextFormatter.Change change) {
        String proposed = change.getControlNewText();
        if (proposed == null || proposed.isEmpty()) {
            return change;
        }
        return isAcceptable(proposed) ? change : null;
    }

    private static boolean isAcceptable(String text) {
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

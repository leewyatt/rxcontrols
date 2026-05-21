package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXIntegerField;
import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * Keystroke filter for {@link RXIntegerField}. Identical to
 * {@link NumberFieldChangeFilter} except the decimal point {@code '.'} is
 * not in the accepted vocabulary — only ASCII digits {@code 0..9} and a
 * leading {@code +} / {@code -}.
 * <p>
 * Internal class — declared {@code public} only so {@link RXIntegerField} (a
 * different package) can instantiate it. Not part of the stable public API.
 */
public final class IntegerFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

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
            return false;
        }
        return true;
    }
}

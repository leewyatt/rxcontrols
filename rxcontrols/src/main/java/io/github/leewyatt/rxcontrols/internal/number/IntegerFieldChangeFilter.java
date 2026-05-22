package io.github.leewyatt.rxcontrols.internal.number;

import javafx.scene.control.TextFormatter;

import java.util.function.UnaryOperator;

/**
 * Integer-number edit filter.
 */
public final class IntegerFieldChangeFilter implements UnaryOperator<TextFormatter.Change> {

    /**
     * Creates an integer edit filter.
     */
    public IntegerFieldChangeFilter() {
    }

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

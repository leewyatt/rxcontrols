package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/**
 * Converts a CSS string literal (e.g. {@code '*'} or {@code "x"}) into a
 * {@link Character}. The first character of the parsed value is used; the
 * remainder is discarded.
 * <p>
 * The converter <em>never</em> returns {@code null} — when the parsed value
 * is absent, empty, or not a string, it falls back to
 * {@link RXPasswordField#DEFAULT_ECHO_CHAR}. The fallback is for stylesheet
 * predictability: an unparseable CSS value (typo, wrong type, empty literal)
 * should render the default mask rather than propagate {@code null} through
 * to the skin. The property itself tolerates {@code null} (skin renders the
 * default in that case too), so the fallback is convention, not necessity.
 */
public final class EchoCharConverter extends StyleConverter<String, Character> {

    private static final EchoCharConverter INSTANCE = new EchoCharConverter();

    public static EchoCharConverter getInstance() {
        return INSTANCE;
    }

    private EchoCharConverter() {
    }

    @Override
    public Character convert(ParsedValue<String, Character> value, Font font) {
        Object raw = value.getValue();
        if (!(raw instanceof String s) || s.isEmpty()) {
            return RXPasswordField.DEFAULT_ECHO_CHAR;
        }
        return s.charAt(0);
    }

    @Override
    public String toString() {
        return "EchoCharConverter";
    }
}

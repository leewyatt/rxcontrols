package io.github.leewyatt.rxcontrols.skins;

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
 * {@link RXPasswordField#DEFAULT_ECHO_CHAR}. This guarantee matters because
 * the styleable property's {@code invalidated()} rejects {@code null} with
 * {@code NullPointerException}; letting {@code null} flow in during CSS
 * application would crash layout.
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

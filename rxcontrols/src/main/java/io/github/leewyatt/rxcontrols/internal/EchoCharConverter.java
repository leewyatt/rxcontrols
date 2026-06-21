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
 * is absent, empty, or not a string, it falls back to a configurable character:
 * {@link #getInstance()} uses {@link RXPasswordField#DEFAULT_ECHO_CHAR}, while
 * {@link #withFallback(char)} supplies a caller-chosen fallback so a control's
 * {@code -rx-echo-char} metadata can default to its own constant rather than
 * another control's. The fallback is for stylesheet predictability: an
 * unparseable CSS value (typo, wrong type, empty literal) should render the
 * default mask rather than propagate {@code null} through to the skin. The
 * property itself tolerates {@code null} (skin renders the default in that case
 * too), so the fallback is convention, not necessity.
 */
public final class EchoCharConverter extends StyleConverter<String, Character> {

    private static final EchoCharConverter INSTANCE =
            new EchoCharConverter(RXPasswordField.DEFAULT_ECHO_CHAR);

    private final char fallback;

    /**
     * @return the shared converter whose unparseable-value fallback is
     *         {@link RXPasswordField#DEFAULT_ECHO_CHAR}
     */
    public static EchoCharConverter getInstance() {
        return INSTANCE;
    }

    /**
     * Returns a converter that falls back to the given character when the CSS
     * value is absent, empty, or not a string. Use this so a control's
     * {@code -rx-echo-char} metadata falls back to its own default rather than
     * another control's constant.
     *
     * @param fallback the fallback echo character
     * @return a converter with that fallback
     */
    public static EchoCharConverter withFallback(char fallback) {
        return new EchoCharConverter(fallback);
    }

    private EchoCharConverter(char fallback) {
        this.fallback = fallback;
    }

    @Override
    public Character convert(ParsedValue<String, Character> value, Font font) {
        Object raw = value.getValue();
        if (!(raw instanceof String s) || s.isEmpty()) {
            return fallback;
        }
        return s.charAt(0);
    }

    @Override
    public String toString() {
        return "EchoCharConverter";
    }
}

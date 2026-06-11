package io.github.leewyatt.rxcontrols.internal;

import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

import java.util.Objects;
import java.util.function.Function;

/**
 * Maps CSS keyword identifiers to built-in preset instances, e.g.
 * {@code -rx-fill-animation: zigzag}. Unknown keywords resolve to
 * {@code null}, which use-sites treat as "fall back to default".
 *
 * @param <T> the preset type
 */
public final class KeywordConverter<T> extends StyleConverter<String, T> {

    private final Function<String, T> resolver;

    /**
     * Creates a converter backed by the given keyword resolver.
     *
     * @param resolver maps a keyword to a preset instance, or {@code null}
     * @throws NullPointerException if {@code resolver} is {@code null}
     */
    public KeywordConverter(Function<String, T> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
    }

    @Override
    public T convert(ParsedValue<String, T> value, Font font) {
        Object parsed = value.getValue();
        return parsed == null ? null : resolver.apply(parsed.toString());
    }
}

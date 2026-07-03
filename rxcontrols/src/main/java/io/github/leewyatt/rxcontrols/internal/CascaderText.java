package io.github.leewyatt.rxcontrols.internal;

import javafx.util.Callback;

/**
 * Internal helper that resolves a cascader item value to its display text. This
 * is the single source of the value-to-text fallback contract shared by the
 * built-in cell and the input field's default path text.
 *
 * <p>The {@code internal} package is not exported by the module, so this class
 * is reachable across the control's own packages but not part of the public API.
 */
public final class CascaderText {

    private CascaderText() {
    }

    /**
     * Resolves the display text for a value using the given factory, falling back
     * to {@code String.valueOf(value)} when the factory is {@code null}. A
     * {@code null} value, or a factory that returns {@code null}, yields the empty
     * string.
     *
     * @param factory item text factory, or {@code null}
     * @param value   value to render
     * @param <T>     application value type
     * @return display text, never {@code null}
     */
    public static <T> String resolve(Callback<T, String> factory, T value) {
        if (value == null) {
            return "";
        }
        if (factory == null) {
            return String.valueOf(value);
        }
        String text = factory.call(value);
        return text == null ? "" : text;
    }
}

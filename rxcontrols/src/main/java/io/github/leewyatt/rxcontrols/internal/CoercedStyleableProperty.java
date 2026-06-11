package io.github.leewyatt.rxcontrols.internal;

import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.StyleOrigin;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;

import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link StyleableProperty} facade that lets a property expose a CSS type
 * different from its Java type.
 *
 * <p>The CSS engine cannot run custom multi-value converters for custom
 * {@code -xx-} properties (the parser pre-assigns its own converter and the
 * engine prefers it, RT-37727), with {@code InsetsConverter} as the only
 * special-cased exception. This adapter rides that exception: the CSS
 * metadata declares {@code InsetsConverter} (or any cooperating converter)
 * with type {@code V}, and this facade coerces the applied value into the
 * target property's type {@code T}.</p>
 *
 * <p>Style-origin precedence is preserved: direct writes to the target
 * property (Java setters, bindings) register as {@link StyleOrigin#USER}, so
 * user-agent styles do not override them — the same semantics as
 * {@code StyleableObjectProperty}.</p>
 *
 * @param <V> the CSS-facing value type
 * @param <T> the target property type
 */
public final class CoercedStyleableProperty<V, T> implements StyleableProperty<V> {

    private final ObjectProperty<T> target;
    private final CssMetaData<? extends Styleable, V> metadata;
    private final Function<V, T> fromCss;
    private final Function<T, V> toCss;

    private StyleOrigin origin;
    private boolean applying;

    /**
     * Creates the facade around the given target property.
     *
     * @param target   the property holding the real value
     * @param metadata the CSS metadata this facade belongs to
     * @param fromCss  maps a CSS-applied value to the target type; must
     *                 accept {@code null}
     * @param toCss    maps the target value back to the CSS type for the
     *                 engine's bookkeeping; must accept {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public CoercedStyleableProperty(ObjectProperty<T> target,
                                    CssMetaData<? extends Styleable, V> metadata,
                                    Function<V, T> fromCss,
                                    Function<T, V> toCss) {
        this.target = Objects.requireNonNull(target, "target cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        this.fromCss = Objects.requireNonNull(fromCss, "fromCss cannot be null");
        this.toCss = Objects.requireNonNull(toCss, "toCss cannot be null");
        target.addListener(observable -> {
            if (!applying) {
                origin = StyleOrigin.USER;
            }
        });
    }

    @Override
    public CssMetaData<? extends Styleable, V> getCssMetaData() {
        return metadata;
    }

    @Override
    public void applyStyle(StyleOrigin styleOrigin, V value) {
        applying = true;
        try {
            target.set(fromCss.apply(value));
        } finally {
            applying = false;
        }
        origin = styleOrigin;
    }

    @Override
    public StyleOrigin getStyleOrigin() {
        return origin;
    }

    @Override
    public V getValue() {
        return toCss.apply(target.get());
    }

    @Override
    public void setValue(V value) {
        target.set(fromCss.apply(value));
    }
}

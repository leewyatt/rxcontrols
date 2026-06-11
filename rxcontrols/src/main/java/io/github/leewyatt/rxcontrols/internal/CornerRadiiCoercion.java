package io.github.leewyatt.rxcontrols.internal;

import javafx.geometry.Insets;
import javafx.scene.layout.CornerRadii;

/**
 * Coercion between the {@link Insets} CSS facade and {@link CornerRadii}, shared
 * by the {@code -rx-*-corner-radius} properties of decoration hosts (fill,
 * ripple). The CSS engine can only deliver multi-value custom properties
 * through the special-cased {@code InsetsConverter} (RT-37727), so corner-radius
 * properties expose an {@code Insets} CSS type and coerce here through
 * {@link CoercedStyleableProperty}.
 *
 * <p>CSS component order follows the {@code border-radius} convention:
 * top-left, top-right, bottom-right, bottom-left. Any negative component
 * selects automatic mirroring ({@code null}); all-zero is sharp corners
 * ({@link CornerRadii#EMPTY}).</p>
 */
public final class CornerRadiiCoercion {

    private CornerRadiiCoercion() {
    }

    /**
     * Maps a CSS-applied {@link Insets} to {@link CornerRadii}: {@code null} or
     * any negative component yields {@code null} (automatic mirroring), all
     * zero yields {@link CornerRadii#EMPTY}, otherwise an absolute-radius
     * {@code CornerRadii} in border-radius order.
     *
     * @param value the CSS insets value, may be {@code null}
     * @return the coerced corner radii, or {@code null} for automatic mirroring
     */
    public static CornerRadii fromInsets(Insets value) {
        if (value == null
                || value.getTop() < 0.0 || value.getRight() < 0.0
                || value.getBottom() < 0.0 || value.getLeft() < 0.0) {
            return null;
        }
        if (value.getTop() == 0.0 && value.getRight() == 0.0
                && value.getBottom() == 0.0 && value.getLeft() == 0.0) {
            return CornerRadii.EMPTY;
        }
        return new CornerRadii(value.getTop(), value.getRight(),
                value.getBottom(), value.getLeft(), false);
    }

    /**
     * Maps {@link CornerRadii} back to the {@link Insets} CSS facade using the
     * horizontal radii, for the engine's style bookkeeping.
     *
     * @param value the corner radii, may be {@code null}
     * @return the insets facade, or {@code null}
     */
    public static Insets toInsets(CornerRadii value) {
        if (value == null) {
            return null;
        }
        return new Insets(value.getTopLeftHorizontalRadius(),
                value.getTopRightHorizontalRadius(),
                value.getBottomRightHorizontalRadius(),
                value.getBottomLeftHorizontalRadius());
    }
}

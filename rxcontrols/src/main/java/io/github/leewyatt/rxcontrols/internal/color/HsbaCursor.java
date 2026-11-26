package io.github.leewyatt.rxcontrols.internal.color;

import javafx.scene.paint.Color;

/**
 * The editing position of a color view: hue, saturation, brightness and
 * opacity as doubles. It is kept apart from the view's {@link Color} value
 * because a color does not carry a hue when it is gray and loses the
 * saturation too when it is black; the cursor remembers them so the panel
 * does not jump while the user drags through those colors.
 *
 * <p>Components are stored clean (hue in {@code [0, 360]}, the others in
 * {@code [0, 1]}). The seed is a fully saturated, bright red.
 */
public final class HsbaCursor {

    private double hue;
    private double saturation = 1.0;
    private double brightness = 1.0;
    private double opacity = 1.0;

    /**
     * Creates a cursor at the seed position {@code hsb(0, 1, 1)}, opaque.
     */
    public HsbaCursor() {
    }

    // ==================== Components ====================

    /**
     * Returns the hue.
     *
     * @return the hue in degrees, {@code [0, 360]}
     */
    public double getHue() {
        return hue;
    }

    /**
     * Changes the hue, clamped to {@code [0, 360]}.
     *
     * @param value the hue in degrees
     */
    public void changeHue(double value) {
        hue = ColorMath.cleanHue(value);
    }

    /**
     * Returns the saturation.
     *
     * @return the saturation in {@code [0, 1]}
     */
    public double getSaturation() {
        return saturation;
    }

    /**
     * Changes the saturation, clamped to {@code [0, 1]}.
     *
     * @param value the saturation
     */
    public void changeSaturation(double value) {
        saturation = ColorMath.clean01(value);
    }

    /**
     * Returns the brightness.
     *
     * @return the brightness in {@code [0, 1]}
     */
    public double getBrightness() {
        return brightness;
    }

    /**
     * Changes the brightness, clamped to {@code [0, 1]}.
     *
     * @param value the brightness
     */
    public void changeBrightness(double value) {
        brightness = ColorMath.clean01(value);
    }

    /**
     * Returns the opacity.
     *
     * @return the opacity in {@code [0, 1]}
     */
    public double getOpacity() {
        return opacity;
    }

    /**
     * Changes the opacity, clamped to {@code [0, 1]}.
     *
     * @param value the opacity
     */
    public void changeOpacity(double value) {
        opacity = ColorMath.clean01(value);
    }

    // ==================== Colors ====================

    /**
     * Returns the color at the cursor, not quantized.
     *
     * @return the color
     */
    public Color toExactColor() {
        return ColorMath.hsb(hue, saturation, brightness, opacity);
    }

    /**
     * Returns the color at the cursor snapped to the 8-bit grid: the color a
     * user edit at this position writes.
     *
     * @return the quantized color
     */
    public Color toColor() {
        return ColorMath.quantize(toExactColor());
    }

    /**
     * Returns whether the given value is the color at the cursor, in which case
     * the cursor stays where it is instead of being derived from the value.
     *
     * @param value the value, or {@code null}
     * @return {@code true} if {@code value} equals {@link #toColor()}
     */
    public boolean matches(Color value) {
        return value != null && value.equals(toColor());
    }

    /**
     * Moves the cursor to a color. The opacity is always taken. A black color
     * only sets the brightness, keeping hue and saturation; a gray keeps the
     * hue. The black check comes first because black also has saturation 0.
     *
     * @param color the color; {@code null} leaves the cursor unchanged
     */
    public void absorb(Color color) {
        if (color == null) {
            return;
        }
        opacity = ColorMath.clean01(color.getOpacity());
        double[] hsb = ColorMath.toHsb(color);
        if (hsb[2] == 0.0) {
            brightness = 0.0;
        } else if (hsb[1] == 0.0) {
            saturation = 0.0;
            brightness = ColorMath.clean01(hsb[2]);
        } else {
            hue = ColorMath.cleanHue(hsb[0]);
            saturation = ColorMath.clean01(hsb[1]);
            brightness = ColorMath.clean01(hsb[2]);
        }
    }
}

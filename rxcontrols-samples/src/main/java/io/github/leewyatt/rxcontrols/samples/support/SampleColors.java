package io.github.leewyatt.rxcontrols.samples.support;

import javafx.scene.paint.Color;

import java.util.Random;

/**
 * Random color helpers shared by the RXControls samples. Colors are generated
 * in HSB space so brightness can be constrained per use case — a plain vivid
 * color, a dark one that reads on light backgrounds, or a light pastel that
 * reads on dark backgrounds.
 */
public final class SampleColors {

    private static final Random RANDOM = new Random();

    private static final double FULL_HUE = 360.0;

    // Any vivid color (saturated, mid-bright).
    private static final double ANY_SATURATION_MIN = 0.55;
    private static final double ANY_SATURATION_MAX = 0.90;
    private static final double ANY_BRIGHTNESS_MIN = 0.55;
    private static final double ANY_BRIGHTNESS_MAX = 0.90;

    // Dark and saturated — reads clearly on a light background.
    private static final double DARK_SATURATION_MIN = 0.45;
    private static final double DARK_SATURATION_MAX = 0.85;
    private static final double DARK_BRIGHTNESS_MIN = 0.30;
    private static final double DARK_BRIGHTNESS_MAX = 0.55;

    // Light pastel — reads clearly on a dark background.
    private static final double LIGHT_SATURATION_MIN = 0.20;
    private static final double LIGHT_SATURATION_MAX = 0.50;
    private static final double LIGHT_BRIGHTNESS_MIN = 0.85;
    private static final double LIGHT_BRIGHTNESS_MAX = 1.00;

    private SampleColors() {
    }

    /**
     * Returns a random vivid color with no brightness bias.
     *
     * @return a random color
     */
    public static Color random() {
        return hsb(ANY_SATURATION_MIN, ANY_SATURATION_MAX, ANY_BRIGHTNESS_MIN, ANY_BRIGHTNESS_MAX);
    }

    /**
     * Returns a random dark, saturated color that reads clearly against a light
     * background.
     *
     * @return a random dark color
     */
    public static Color randomDark() {
        return hsb(DARK_SATURATION_MIN, DARK_SATURATION_MAX, DARK_BRIGHTNESS_MIN, DARK_BRIGHTNESS_MAX);
    }

    /**
     * Returns a random light pastel color that reads clearly against a dark
     * background.
     *
     * @return a random light color
     */
    public static Color randomLight() {
        return hsb(LIGHT_SATURATION_MIN, LIGHT_SATURATION_MAX, LIGHT_BRIGHTNESS_MIN, LIGHT_BRIGHTNESS_MAX);
    }

    private static Color hsb(double satMin, double satMax, double brightMin, double brightMax) {
        return Color.hsb(RANDOM.nextDouble() * FULL_HUE,
                between(satMin, satMax), between(brightMin, brightMax));
    }

    private static double between(double min, double max) {
        return min + RANDOM.nextDouble() * (max - min);
    }
}

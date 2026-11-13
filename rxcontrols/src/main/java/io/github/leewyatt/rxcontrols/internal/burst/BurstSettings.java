package io.github.leewyatt.rxcontrols.internal.burst;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves the settings every burst host shares: duration and scale
 * fallbacks, how far a burst reaches for an anchor size, the canvas margin
 * and the palette derived from a single color.
 */
public final class BurstSettings {

    /**
     * Smallest usable burst scale; smaller values are raised to it.
     */
    public static final double MIN_SCALE = 0.25;

    /**
     * Largest usable burst scale; larger values are lowered to it.
     */
    public static final double MAX_SCALE = 4.0;

    // Reach of a burst at scale 1, in anchor sizes, and its floor in pixels.
    private static final double REACH_PER_ANCHOR = 1.6;
    private static final double MIN_REACH = 24.0;
    // Keeps the canvas well inside common texture limits even on HiDPI.
    private static final double MAX_REACH = 480.0;
    // Matches the presets' overshoot past the reach, plus two pixels each
    // side for anti-aliasing.
    private static final double MARGIN_PER_ANCHOR = 0.4;
    private static final double MARGIN_PIXELS = 2.0;
    private static final double PALETTE_HUE_SHIFT = 40.0;
    // Weyl increment: consecutive plays get well-spread seeds.
    private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
    private static final AtomicLong PLAYS = new AtomicLong();

    private BurstSettings() {
    }

    /**
     * Returns the duration to play, or {@code fallback} for a {@code null},
     * negative, unknown or indefinite one.
     *
     * @param duration the requested duration
     * @param fallback the default duration
     * @return the usable duration; may be zero, which plays nothing
     */
    public static Duration durationOrDefault(Duration duration, Duration fallback) {
        if (duration == null || duration.isUnknown() || duration.isIndefinite()
                || duration.lessThan(Duration.ZERO)) {
            return fallback;
        }
        return duration;
    }

    /**
     * Returns the scale to use: {@code fallback} for a non-finite or
     * non-positive value, otherwise clamped into
     * [{@link #MIN_SCALE}, {@link #MAX_SCALE}].
     *
     * @param scale    the requested scale
     * @param fallback the default scale
     * @return the usable scale
     */
    public static double scaleOrDefault(double scale, double fallback) {
        double value = Double.isFinite(scale) && scale > 0.0 ? scale : fallback;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    /**
     * Returns how far a burst reaches from its center.
     *
     * @param anchorSize the anchor's size
     * @param scale      a usable scale, see {@link #scaleOrDefault}
     * @return the reach in pixels, between 6 and 480
     */
    public static double reach(double anchorSize, double scale) {
        return Math.min(MAX_REACH, Math.max(MIN_REACH, anchorSize * REACH_PER_ANCHOR) * scale);
    }

    /**
     * Returns the side of the square canvas a burst needs: the reach plus
     * the presets' overshoot on every side.
     *
     * @param reach      the burst reach
     * @param anchorSize the anchor's size
     * @return the canvas side in whole pixels
     */
    public static double canvasSide(double reach, double anchorSize) {
        return Math.ceil(2.0 * (reach + anchorSize * MARGIN_PER_ANCHOR + MARGIN_PIXELS));
    }

    /**
     * Returns the particle colors: the palette when it has any color,
     * otherwise four colors derived from {@code color} (itself and three
     * hue-shifted companions; a non-{@code Color} paint is used alone).
     *
     * @param palette  the explicit palette, or {@code null}
     * @param color    the base color, or {@code null}
     * @param fallback the base color used when {@code color} is {@code null}
     * @return the colors to cycle through
     */
    public static List<Paint> palette(List<Paint> palette, Paint color, Paint fallback) {
        if (palette != null && !palette.isEmpty()) {
            return palette;
        }
        Paint base = color == null ? fallback : color;
        if (!(base instanceof Color)) {
            return base == null ? List.of() : List.of(base);
        }
        Color value = (Color) base;
        return List.of(value,
                value.deriveColor(PALETTE_HUE_SHIFT, 1.0, 1.0, 1.0),
                value.deriveColor(-PALETTE_HUE_SHIFT, 1.0, 1.0, 1.0),
                value.deriveColor(180.0, 1.0, 1.0, 1.0));
    }

    /**
     * Returns a fresh random seed for one play.
     *
     * @return the seed
     */
    public static long nextSeed() {
        return System.nanoTime() ^ (PLAYS.incrementAndGet() * SEED_STEP);
    }
}

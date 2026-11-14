package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable settings for one burst played through {@link RXBursts}: which
 * burst, how long, which colors, how far it reaches and the anchor size its
 * particles scale with.
 * Build one with {@link #builder()}, or use {@link #defaults()}.
 *
 * <pre>{@code
 * RXBurstOptions confetti = RXBurstOptions.builder()
 *         .animation(BurstAnimation.CONFETTI)
 *         .duration(Duration.millis(1200))
 *         .build();
 * button.addEventHandler(ActionEvent.ACTION, e -> RXBursts.play(button, confetti));
 * }</pre>
 */
public final class RXBurstOptions {

    /**
     * Default burst: {@link BurstAnimation#CLASSIC}.
     */
    public static final BurstAnimation DEFAULT_ANIMATION = BurstAnimation.CLASSIC;

    /**
     * Default duration, and the one played in place of a {@code null},
     * negative, unknown or indefinite duration.
     */
    public static final Duration DEFAULT_DURATION = Duration.millis(800.0);

    /**
     * Default scale, and the one played in place of a non-finite or
     * non-positive scale.
     */
    public static final double DEFAULT_SCALE = 1.0;

    private static final RXBurstOptions DEFAULTS = builder().build();

    private final BurstAnimation animation;
    private final Duration duration;
    private final Paint color;
    private final List<Paint> palette;
    private final double scale;
    private final double anchorSize;

    private RXBurstOptions(Builder builder) {
        this.animation = builder.animation;
        this.duration = builder.duration;
        this.color = builder.color;
        this.palette = builder.palette;
        this.scale = builder.scale;
        this.anchorSize = builder.anchorSize;
    }

    /**
     * Returns the default settings: {@link #DEFAULT_ANIMATION},
     * {@link #DEFAULT_DURATION}, the theme's color, {@link #DEFAULT_SCALE}
     * and an automatic anchor size.
     *
     * @return the shared default options
     */
    public static RXBurstOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a builder starting from the default settings.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the burst to play.
     *
     * @return the burst animation, or {@code null} to play nothing
     */
    public BurstAnimation getAnimation() {
        return animation;
    }

    /**
     * Returns how long the burst lasts.
     *
     * @return the duration, or {@code null} for the default
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Returns the color the palette is derived from when no palette is set.
     *
     * @return the color, or {@code null} for the theme's color
     */
    public Paint getColor() {
        return color;
    }

    /**
     * Returns the particle colors.
     *
     * @return an unmodifiable palette, empty to derive one from the color
     */
    public List<Paint> getPalette() {
        return palette;
    }

    /**
     * Returns how far the burst reaches, as a multiple of the default reach.
     *
     * @return the scale
     */
    public double getScale() {
        return scale;
    }

    /**
     * Returns the anchor size particle sizes and the reach scale with, in
     * pixels.
     *
     * @return the anchor size, or 0 or less for an automatic size
     */
    public double getAnchorSize() {
        return anchorSize;
    }

    /**
     * Builds {@link RXBurstOptions}.
     */
    public static final class Builder {

        private BurstAnimation animation = DEFAULT_ANIMATION;
        private Duration duration = DEFAULT_DURATION;
        private Paint color;
        private List<Paint> palette = List.of();
        private double scale = DEFAULT_SCALE;
        private double anchorSize;

        private Builder() {
        }

        /**
         * Sets the burst to play. Defaults to {@link #DEFAULT_ANIMATION}.
         *
         * @param value the burst animation, or {@code null} to play nothing
         * @return this builder
         */
        public Builder animation(BurstAnimation value) {
            this.animation = value;
            return this;
        }

        /**
         * Sets how long the burst lasts. Defaults to {@link #DEFAULT_DURATION};
         * a {@code null}, negative, unknown or indefinite value plays for the
         * default time, and {@code Duration.ZERO} plays nothing.
         *
         * @param value the duration
         * @return this builder
         */
        public Builder duration(Duration value) {
            this.duration = value;
            return this;
        }

        /**
         * Sets the color four particle colors are derived from when no
         * palette is set: the color itself and three hue-shifted companions.
         * Defaults to {@code null}, the theme's accent color.
         *
         * @param value the color, or {@code null} for the theme's color
         * @return this builder
         */
        public Builder color(Paint value) {
            this.color = value;
            return this;
        }

        /**
         * Sets the particle colors, cycled across the particles; {@code null}
         * elements are skipped. A {@code null} or empty palette derives the
         * colors from {@link #color(Paint)}.
         *
         * @param value the palette, copied
         * @return this builder
         */
        public Builder palette(List<Paint> value) {
            List<Paint> kept = new ArrayList<>();
            if (value != null) {
                for (Paint paint : value) {
                    if (paint != null) {
                        kept.add(paint);
                    }
                }
            }
            this.palette = List.copyOf(kept);
            return this;
        }

        /**
         * Scales how far the burst reaches from its center. At 1 the reach
         * is 1.6 times the {@link #anchorSize(double) anchor size}, or 24
         * pixels for an anchor smaller than 15 pixels; the scale multiplies
         * that, and the result is capped at 480 pixels. Clamped into
         * {@code [0.25, 4]} when used; a non-finite or non-positive value
         * plays at {@link #DEFAULT_SCALE}.
         *
         * @param value the scale
         * @return this builder
         */
        public Builder scale(double value) {
            this.scale = value;
            return this;
        }

        /**
         * Sets the anchor size in pixels: the size of the icon the burst
         * appears to come from, which particle sizes and the reach scale
         * with. 0 or less (the default) sizes it from the node a burst is
         * centered on, between 16 and 48 pixels, or 24 pixels for a burst at
         * a point.
         *
         * @param value the anchor size in pixels
         * @return this builder
         */
        public Builder anchorSize(double value) {
            this.anchorSize = value;
            return this;
        }

        /**
         * Builds the options.
         *
         * @return the immutable options
         */
        public RXBurstOptions build() {
            return new RXBurstOptions(this);
        }
    }
}

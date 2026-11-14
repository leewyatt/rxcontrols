package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.scene.canvas.GraphicsContext;

import java.util.Locale;

/**
 * Strategy for the particle burst a control plays at a moment of success,
 * such as a reaction button being selected.
 *
 * <p>An implementation draws one frame of the burst on a canvas as a pure
 * function of a {@link BurstFrame}: progress, center, reach, colors and a
 * random seed. It keeps no state between frames, must leave nothing visible
 * at progress 1 and paints nothing farther from the center than the reach
 * plus 0.4 anchor sizes, which is all the canvas is sure to cover. The
 * timing (one linear timeline per play, cleared canvas before every frame)
 * lives in the control's skin.</p>
 *
 * <p>The built-in presets are exposed as constants and selectable from CSS
 * via {@code -rx-burst-animation} keywords; parameterized variants are
 * created in Java, e.g. {@code new BurstAnimDots(10)}, and several bursts
 * can be layered with {@link #combine(BurstAnimation...)}.</p>
 */
public interface BurstAnimation {

    /**
     * Draws one frame. The caller clears the canvas before each frame; an
     * implementation restores any graphics state it changes.
     *
     * @param gc    the canvas graphics context
     * @param frame the frame to draw
     */
    void draw(GraphicsContext gc, BurstFrame frame);

    // ==================== Built-in Presets ====================

    /**
     * A shock ring grows from the anchor and fades out.
     */
    BurstAnimation RING = new BurstAnimRing();

    /**
     * Pairs of dots fly out from the anchor and shrink away.
     */
    BurstAnimation DOTS = new BurstAnimDots();

    /**
     * A shock ring followed closely by pairs of dots.
     */
    BurstAnimation CLASSIC = new BurstAnimCombined(RING, new BurstAnimDelayed(DOTS, 0.12));

    /**
     * Four-pointed sparkles twinkle around the anchor.
     */
    BurstAnimation SPARKLES = new BurstAnimSparkles();

    /**
     * Strokes radiate from the anchor, stretching and collapsing.
     */
    BurstAnimation RAYS = new BurstAnimRays();

    /**
     * Small hearts float up in a fan, swaying and fading.
     */
    BurstAnimation HEARTS = new BurstAnimHearts();

    /**
     * Hollow bubbles rise with a wobble and pop.
     */
    BurstAnimation BUBBLES = new BurstAnimBubbles();

    /**
     * Confetti strips scatter, spin, turn over and fall.
     */
    BurstAnimation CONFETTI = new BurstAnimConfetti();

    /**
     * A flash, then two rings of trailing sparks that droop and flicker out.
     */
    BurstAnimation FIREWORKS = new BurstAnimFireworks();

    /**
     * A flash, a shock wave, spinning shards and drifting smoke.
     */
    BurstAnimation EXPLOSION = new BurstAnimExplosion();

    // ==================== Factories ====================

    /**
     * Layers several bursts: they are drawn in the given order, each over the
     * previous one, at the same progress.
     *
     * @param layers the bursts to layer; {@code null} elements are ignored
     * @return the combined burst, which draws nothing when no layer is left
     */
    static BurstAnimation combine(BurstAnimation... layers) {
        return new BurstAnimCombined(layers);
    }

    /**
     * Resolves a CSS keyword to a built-in preset.
     *
     * <p>Keywords are case-insensitive and accept {@code _} for {@code -}:
     * {@code classic}, {@code ring}, {@code dots}, {@code sparkles},
     * {@code rays}, {@code hearts}, {@code bubbles}, {@code confetti},
     * {@code fireworks} and {@code explosion}. {@code none} and unknown
     * keywords resolve to {@code null}; the caller decides what that falls
     * back to.</p>
     *
     * @param keyword the keyword
     * @return the preset, or {@code null} for {@code none} or an unknown
     *         keyword
     */
    static BurstAnimation valueOf(String keyword) {
        if (keyword == null) {
            return null;
        }
        switch (keyword.trim().toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "classic":
                return CLASSIC;
            case "ring":
                return RING;
            case "dots":
                return DOTS;
            case "sparkles":
                return SPARKLES;
            case "rays":
                return RAYS;
            case "hearts":
                return HEARTS;
            case "bubbles":
                return BUBBLES;
            case "confetti":
                return CONFETTI;
            case "fireworks":
                return FIREWORKS;
            case "explosion":
                return EXPLOSION;
            default:
                return null;
        }
    }
}

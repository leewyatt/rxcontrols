package io.github.leewyatt.rxcontrols.animation.burst;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * One frame of a burst: where it is centered, how far it reaches, how far
 * along it is, which colors it uses and which random seed shapes it.
 *
 * <p>A {@link BurstAnimation} draws a frame as a pure function of these
 * values, so the same frame always produces the same picture. Randomness
 * comes only from {@link #random(int, int)}, which is derived from
 * {@link #seed()}; a new seed per play makes every burst slightly
 * different.</p>
 *
 * @param progress   the burst progress, clamped into {@code [0, 1]} (NaN
 *                   reads as 0)
 * @param centerX    the burst center x, in canvas coordinates (non-finite
 *                   reads as 0)
 * @param centerY    the burst center y, in canvas coordinates (non-finite
 *                   reads as 0)
 * @param radius     how far particles may travel from the center, in pixels
 *                   (negative or non-finite reads as 0)
 * @param anchorSize the approximate diameter of the node the burst comes
 *                   from, which particle sizes scale with (negative or
 *                   non-finite reads as 0)
 * @param colors     the particle colors, cycled by {@link #color(int)};
 *                   {@code null} elements are dropped
 * @param seed       the random seed of this play
 */
public record BurstFrame(double progress, double centerX, double centerY,
                         double radius, double anchorSize, List<Paint> colors, long seed) {

    private static final Paint FALLBACK_COLOR = Color.GRAY;

    /**
     * Normalizes the frame: clamps the progress, replaces unusable
     * coordinates and sizes with 0 and copies the colors into an
     * unmodifiable list without {@code null} elements.
     *
     * @param progress   the burst progress
     * @param centerX    the burst center x
     * @param centerY    the burst center y
     * @param radius     the burst reach
     * @param anchorSize the anchor diameter
     * @param colors     the particle colors, or {@code null} for none
     * @param seed       the random seed
     */
    public BurstFrame {
        progress = RXMath.clamp0To1(progress);
        centerX = Double.isFinite(centerX) ? centerX : 0.0;
        centerY = Double.isFinite(centerY) ? centerY : 0.0;
        radius = RXMath.sanitizeFiniteNonNegative(radius);
        anchorSize = RXMath.sanitizeFiniteNonNegative(anchorSize);
        colors = copyWithoutNulls(colors);
    }

    /**
     * Returns a deterministic pseudo-random number for one particle.
     *
     * @param index the particle index
     * @param salt  distinguishes several numbers drawn for the same particle
     * @return a number in {@code [0, 1)} that depends only on the seed, the
     *         index and the salt
     */
    public double random(int index, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (index + 1L) + 0xC2B2AE3D27D4EB4FL * (salt + 1L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    /**
     * Returns the color for a particle, cycling through {@link #colors()}.
     *
     * @param index the particle index; negative indices wrap around too
     * @return the color, or gray when there are no colors
     */
    public Paint color(int index) {
        if (colors.isEmpty()) {
            return FALLBACK_COLOR;
        }
        return colors.get(Math.floorMod(index, colors.size()));
    }

    /**
     * Returns a copy of this frame at another progress, for animations that
     * play a part of the burst on their own schedule.
     *
     * @param value the new progress, clamped into {@code [0, 1]}
     * @return the copied frame
     */
    public BurstFrame withProgress(double value) {
        return new BurstFrame(value, centerX, centerY, radius, anchorSize, colors, seed);
    }

    private static List<Paint> copyWithoutNulls(List<Paint> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        // Iterated rather than contains(null): immutable lists throw on a
        // null query.
        List<Paint> kept = new ArrayList<>(source.size());
        for (Paint paint : source) {
            if (paint != null) {
                kept.add(paint);
            }
        }
        return List.copyOf(kept);
    }
}

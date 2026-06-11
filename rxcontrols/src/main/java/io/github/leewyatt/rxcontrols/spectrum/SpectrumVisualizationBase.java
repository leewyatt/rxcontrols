package io.github.leewyatt.rxcontrols.spectrum;

import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;

/**
 * Abstract base for {@link SpectrumVisualization} implementations. Provides a
 * geometry-cache template — {@link #rebuildGeometry(SpectrumContext)} is only
 * invoked when the size, band count, gap ratio, or bar paint changed since the
 * last frame — plus shared helpers for Cartesian bar geometry and
 * amplitude-keyed gradient remapping.
 */
public abstract class SpectrumVisualizationBase implements SpectrumVisualization {

    /**
     * Minimum visible extent (in pixels) drawn for a band at zero amplitude,
     * giving every effect a visible baseline shape in the silent state.
     */
    protected static final double MIN_BAR_PIXELS = 2.0;

    private double cachedWidth = -1.0;
    private double cachedHeight = -1.0;
    private int cachedBandCount = -1;
    private double cachedGapRatio = -1.0;
    private Paint cachedBarFill;
    private boolean geometryValid;

    @Override
    public final void render(SpectrumContext context) {
        if (!geometryValid
                || context.width() != cachedWidth
                || context.height() != cachedHeight
                || context.bandCount() != cachedBandCount
                || context.gapRatio() != cachedGapRatio
                || context.barFill() != cachedBarFill) {
            cachedWidth = context.width();
            cachedHeight = context.height();
            cachedBandCount = context.bandCount();
            cachedGapRatio = context.gapRatio();
            cachedBarFill = context.barFill();
            geometryValid = true;
            rebuildGeometry(context);
        }
        draw(context);
    }

    /**
     * Recomputes cached geometry (slot positions, remapped gradients, ...).
     * Called from {@link #render(SpectrumContext)} whenever width, height,
     * band count, gap ratio, or bar paint changed.
     *
     * @param context the current rendering context
     */
    protected abstract void rebuildGeometry(SpectrumContext context);

    /**
     * Draws one frame using the geometry prepared by
     * {@link #rebuildGeometry(SpectrumContext)}.
     *
     * @param context the current rendering context
     */
    protected abstract void draw(SpectrumContext context);

    /**
     * Marks the cached geometry stale so the next frame rebuilds it. Effect
     * parameter setters call this so changes take effect on the next frame.
     */
    protected final void invalidateGeometry() {
        geometryValid = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dispose() {
        invalidateGeometry();
    }

    /**
     * Remaps a proportional {@link LinearGradient} onto the given absolute
     * axis, preserving stops and cycle method. Drawing each bar directly with
     * a proportional gradient would stretch the full gradient into every bar's
     * own bounds; remapping onto the shared amplitude axis is what makes the
     * paint amplitude-keyed. Non-proportional gradients and all other paint
     * types are returned unchanged.
     *
     * @param paint  the configured paint
     * @param startX the absolute x of the zero-amplitude end
     * @param startY the absolute y of the zero-amplitude end
     * @param endX   the absolute x of the full-amplitude end
     * @param endY   the absolute y of the full-amplitude end
     * @return the remapped gradient, or {@code paint} unchanged
     */
    protected static Paint absoluteGradient(Paint paint, double startX, double startY,
                                            double endX, double endY) {
        if (paint instanceof LinearGradient lg && lg.isProportional()) {
            return new LinearGradient(startX, startY, endX, endY, false,
                    lg.getCycleMethod(), lg.getStops());
        }
        return paint;
    }

    /**
     * Computes the snapped left edge of every bar slot and returns the shared
     * bar width: {@code slot = width / n}, {@code gap = gapRatio * slot},
     * {@code barWidth = slot - gap} (floored, at least 1px),
     * {@code barX[i] = round(i * slot + gap / 2)}.
     *
     * @param width    the drawable width
     * @param n        the band count ({@code barX.length} must equal {@code n})
     * @param gapRatio the clamped gap ratio
     * @param barX     the output array receiving each bar's left edge
     * @return the bar width in pixels
     */
    protected static double fillBarPositions(double width, int n, double gapRatio, double[] barX) {
        double slot = width / n;
        double gap = slot * gapRatio;
        double barWidth = Math.max(1.0, Math.floor(slot - gap));
        for (int i = 0; i < n; i++) {
            barX[i] = Math.rint(i * slot + gap / 2.0);
        }
        return barWidth;
    }
}

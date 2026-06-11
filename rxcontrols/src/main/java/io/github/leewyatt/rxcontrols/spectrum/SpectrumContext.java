package io.github.leewyatt.rxcontrols.spectrum;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;

/**
 * Read-only rendering context handed to a {@link SpectrumVisualization} once
 * per frame. The skin owns a single instance and refreshes its fields before
 * each render call; visualizations must not retain it across frames.
 *
 * <p>All values are pre-sanitized: levels and peaks are smoothed amplitudes in
 * {@code [0, 1]} already arranged in visual slot order per
 * {@link BandLayout}, paints are never {@code null}, and the gap ratio is
 * clamped to {@code [0, }{@link RXAudioSpectrum#MAX_BAR_GAP_RATIO}{@code ]}.
 */
public final class SpectrumContext {

    private GraphicsContext graphicsContext;
    private double width;
    private double height;
    private int bandCount;
    private double[] levels;
    private double[] peaks;
    private boolean showPeaks;
    private Paint barFill;
    private Paint peakFill;
    private double gapRatio;
    private double deltaSeconds;

    SpectrumContext() {
    }

    void update(GraphicsContext graphicsContext, double width, double height, int bandCount,
                double[] levels, double[] peaks, boolean showPeaks,
                Paint barFill, Paint peakFill, double gapRatio, double deltaSeconds) {
        this.graphicsContext = graphicsContext;
        this.width = width;
        this.height = height;
        this.bandCount = bandCount;
        this.levels = levels;
        this.peaks = peaks;
        this.showPeaks = showPeaks;
        // GraphicsContext.setFill(null) silently keeps the previous paint
        // (stateful trap), so null is resolved to the default here instead.
        this.barFill = barFill == null ? RXAudioSpectrum.DEFAULT_BAR_FILL : barFill;
        this.peakFill = peakFill == null ? RXAudioSpectrum.DEFAULT_PEAK_FILL : peakFill;
        this.gapRatio = Double.isNaN(gapRatio)
                ? RXAudioSpectrum.DEFAULT_BAR_GAP_RATIO
                : RXMath.clamp(gapRatio, 0.0, RXAudioSpectrum.MAX_BAR_GAP_RATIO);
        this.deltaSeconds = deltaSeconds;
    }

    /**
     * Returns the target graphics context.
     *
     * @return the graphics context of the skin's canvas
     */
    public GraphicsContext graphicsContext() {
        return graphicsContext;
    }

    /**
     * Returns the content-area width (the canvas spans the content area).
     *
     * @return the drawable width in pixels
     */
    public double width() {
        return width;
    }

    /**
     * Returns the content-area height (the canvas spans the content area).
     *
     * @return the drawable height in pixels
     */
    public double height() {
        return height;
    }

    /**
     * Returns the number of display bands.
     *
     * @return the band count
     */
    public int bandCount() {
        return bandCount;
    }

    /**
     * Returns the smoothed amplitude of the given visual slot.
     *
     * @param i the visual slot index in {@code [0, bandCount())}
     * @return the amplitude in {@code [0, 1]}
     */
    public double level(int i) {
        return levels[i];
    }

    /**
     * Returns the peak-cap amplitude of the given visual slot.
     *
     * @param i the visual slot index in {@code [0, bandCount())}
     * @return the peak amplitude in {@code [0, 1]}
     */
    public double peak(int i) {
        return peaks[i];
    }

    /**
     * Returns whether peak caps should be drawn. Visualizations that support
     * peaks must skip them when this is {@code false}.
     *
     * @return {@code true} if peak caps are enabled
     */
    public boolean showPeaks() {
        return showPeaks;
    }

    /**
     * Returns the bar paint, never {@code null}.
     *
     * @return the bar paint
     */
    public Paint barFill() {
        return barFill;
    }

    /**
     * Returns the peak-cap paint, never {@code null}.
     *
     * @return the peak-cap paint
     */
    public Paint peakFill() {
        return peakFill;
    }

    /**
     * Returns the gap ratio between adjacent bars, clamped to
     * {@code [0, }{@link RXAudioSpectrum#MAX_BAR_GAP_RATIO}{@code ]}.
     *
     * @return the gap ratio
     */
    public double gapRatio() {
        return gapRatio;
    }

    /**
     * Returns the clamped duration of this frame, for visualization-internal
     * animation state. {@code 0} on seed/resume frames.
     *
     * @return the frame duration in seconds
     */
    public double deltaSeconds() {
        return deltaSeconds;
    }
}

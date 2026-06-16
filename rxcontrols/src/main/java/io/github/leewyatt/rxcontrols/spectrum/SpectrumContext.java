package io.github.leewyatt.rxcontrols.spectrum;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
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
public interface SpectrumContext {

    /**
     * Returns the target graphics context.
     *
     * @return the graphics context of the skin's canvas
     */
    GraphicsContext graphicsContext();

    /**
     * Returns the content-area width (the canvas spans the content area).
     *
     * @return the drawable width in pixels
     */
    double width();

    /**
     * Returns the content-area height (the canvas spans the content area).
     *
     * @return the drawable height in pixels
     */
    double height();

    /**
     * Returns the number of display bands.
     *
     * @return the band count
     */
    int bandCount();

    /**
     * Returns the smoothed amplitude of the given visual slot.
     *
     * @param i the visual slot index in {@code [0, bandCount())}
     * @return the amplitude in {@code [0, 1]}
     */
    double level(int i);

    /**
     * Returns the peak-cap amplitude of the given visual slot.
     *
     * @param i the visual slot index in {@code [0, bandCount())}
     * @return the peak amplitude in {@code [0, 1]}
     */
    double peak(int i);

    /**
     * Returns whether peak caps should be drawn. Visualizations that support
     * peaks must skip them when this is {@code false}.
     *
     * @return {@code true} if peak caps are enabled
     */
    boolean showPeaks();

    /**
     * Returns the bar paint, never {@code null}.
     *
     * @return the bar paint
     */
    Paint barFill();

    /**
     * Returns the peak-cap paint, never {@code null}.
     *
     * @return the peak-cap paint
     */
    Paint peakFill();

    /**
     * Returns the gap ratio between adjacent bars, clamped to
     * {@code [0, }{@link RXAudioSpectrum#MAX_BAR_GAP_RATIO}{@code ]}.
     *
     * @return the gap ratio
     */
    double gapRatio();

    /**
     * Returns the clamped duration of this frame, for visualization-internal
     * animation state. {@code 0} on seed/resume frames.
     *
     * @return the frame duration in seconds
     */
    double deltaSeconds();
}

package io.github.leewyatt.rxcontrols.internal.spectrum;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.spectrum.SpectrumContext;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;

/**
 * Mutable implementation of the public read-only {@link SpectrumContext}
 * contract. The audio spectrum skin owns a single instance and refreshes it
 * before each render call.
 */
public final class MutableSpectrumContext implements SpectrumContext {

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

    /**
     * Creates an empty mutable spectrum context.
     */
    public MutableSpectrumContext() {
    }

    /**
     * Refreshes the context values for the next render call.
     *
     * @param graphicsContext the target graphics context
     * @param width the drawable width
     * @param height the drawable height
     * @param bandCount the number of visual bands
     * @param levels the smoothed band levels
     * @param peaks the peak-cap levels
     * @param showPeaks whether peak caps should be drawn
     * @param barFill the bar paint, or {@code null} for the default
     * @param peakFill the peak paint, or {@code null} for the default
     * @param gapRatio the bar gap ratio
     * @param deltaSeconds the frame duration in seconds
     */
    public void update(GraphicsContext graphicsContext, double width, double height, int bandCount,
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
     * {@inheritDoc}
     */
    @Override
    public GraphicsContext graphicsContext() {
        return graphicsContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double width() {
        return width;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double height() {
        return height;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int bandCount() {
        return bandCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double level(int i) {
        return levels[i];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double peak(int i) {
        return peaks[i];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean showPeaks() {
        return showPeaks;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Paint barFill() {
        return barFill;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Paint peakFill() {
        return peakFill;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double gapRatio() {
        return gapRatio;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double deltaSeconds() {
        return deltaSeconds;
    }
}

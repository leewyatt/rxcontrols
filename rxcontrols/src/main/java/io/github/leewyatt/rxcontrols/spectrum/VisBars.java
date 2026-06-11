package io.github.leewyatt.rxcontrols.spectrum;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;

/**
 * Classic bottom-anchored vertical bars with hold-then-fall peak caps. The
 * default visualization of {@code RXAudioSpectrum}. At zero amplitude each
 * band keeps a thin baseline stub so the silent control stays visible.
 */
public class VisBars extends SpectrumVisualizationBase {

    private static final double PEAK_CAP_HEIGHT = 2.0;

    private double[] barX;

    private double barWidth;

    private Paint fill;

    @Override
    protected void rebuildGeometry(SpectrumContext context) {
        int n = context.bandCount();
        if (barX == null || barX.length != n) {
            barX = new double[n];
        }
        barWidth = fillBarPositions(context.width(), n, context.gapRatio(), barX);
        fill = absoluteGradient(context.barFill(), 0.0, context.height(), 0.0, 0.0);
    }

    @Override
    protected void draw(SpectrumContext context) {
        GraphicsContext gc = context.graphicsContext();
        double height = context.height();
        int n = context.bandCount();

        gc.setFill(fill);
        for (int i = 0; i < n; i++) {
            double barHeight = Math.max(MIN_BAR_PIXELS, context.level(i) * height);
            gc.fillRect(barX[i], height - barHeight, barWidth, barHeight);
        }

        if (context.showPeaks()) {
            gc.setFill(context.peakFill());
            for (int i = 0; i < n; i++) {
                double peakHeight = context.peak(i) * height;
                if (peakHeight <= MIN_BAR_PIXELS) {
                    continue;
                }
                gc.fillRect(barX[i], height - peakHeight - PEAK_CAP_HEIGHT, barWidth, PEAK_CAP_HEIGHT);
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        barX = null;
        fill = null;
    }
}

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

    private double[] barWidth;

    private Paint fill;

    @Override
    protected void rebuildGeometry(SpectrumContext context) {
        int n = context.bandCount();
        if (barX == null || barX.length != n) {
            barX = new double[n];
            barWidth = new double[n];
        }
        fillBarGeometry(context.width(), n, context.gapRatio(), barX, barWidth);
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
            gc.fillRect(barX[i], height - barHeight, barWidth[i], barHeight);
        }

        if (context.showPeaks()) {
            gc.setFill(context.peakFill());
            for (int i = 0; i < n; i++) {
                double peakHeight = context.peak(i) * height;
                if (peakHeight <= MIN_BAR_PIXELS) {
                    continue;
                }
                // Pin the cap at the canvas edge so it stays visible at full amplitude.
                double capY = Math.max(0.0, height - peakHeight - PEAK_CAP_HEIGHT);
                gc.fillRect(barX[i], capY, barWidth[i], PEAK_CAP_HEIGHT);
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        barX = null;
        barWidth = null;
        fill = null;
    }
}

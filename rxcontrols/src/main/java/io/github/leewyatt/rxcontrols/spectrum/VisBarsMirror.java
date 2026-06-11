package io.github.leewyatt.rxcontrols.spectrum;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;

/**
 * Vertical bars mirrored across the horizontal centre line, the lower
 * reflection rendered with reduced opacity. At zero amplitude each band keeps
 * a thin stub on both sides of the centre line so the silent control stays
 * visible.
 */
public class VisBarsMirror extends SpectrumVisualizationBase {

    /** Opacity applied to the reflected lower half. */
    private static final double MIRROR_ALPHA = 0.45;

    private static final double PEAK_CAP_HEIGHT = 2.0;

    private double[] barX;

    private double[] barWidth;

    private Paint upperFill;

    private Paint lowerFill;

    @Override
    protected void rebuildGeometry(SpectrumContext context) {
        int n = context.bandCount();
        if (barX == null || barX.length != n) {
            barX = new double[n];
            barWidth = new double[n];
        }
        fillBarGeometry(context.width(), n, context.gapRatio(), barX, barWidth);
        double midY = context.height() / 2.0;
        upperFill = absoluteGradient(context.barFill(), 0.0, midY, 0.0, 0.0);
        lowerFill = absoluteGradient(context.barFill(), 0.0, midY, 0.0, context.height());
    }

    @Override
    protected void draw(SpectrumContext context) {
        GraphicsContext gc = context.graphicsContext();
        int n = context.bandCount();
        double height = context.height();
        double midY = height / 2.0;

        gc.setFill(upperFill);
        for (int i = 0; i < n; i++) {
            double halfHeight = halfHeight(context.level(i), midY);
            gc.fillRect(barX[i], midY - halfHeight, barWidth[i], halfHeight);
        }

        gc.setGlobalAlpha(MIRROR_ALPHA);
        gc.setFill(lowerFill);
        for (int i = 0; i < n; i++) {
            gc.fillRect(barX[i], midY, barWidth[i], halfHeight(context.level(i), midY));
        }
        gc.setGlobalAlpha(1.0);

        if (context.showPeaks()) {
            gc.setFill(context.peakFill());
            for (int i = 0; i < n; i++) {
                double peakHeight = context.peak(i) * midY;
                if (peakHeight <= MIN_BAR_PIXELS / 2.0) {
                    continue;
                }
                // Pin the caps at the canvas edges so they stay visible at full amplitude.
                double capY = Math.max(0.0, midY - peakHeight - PEAK_CAP_HEIGHT);
                gc.fillRect(barX[i], capY, barWidth[i], PEAK_CAP_HEIGHT);
            }
            gc.setGlobalAlpha(MIRROR_ALPHA);
            for (int i = 0; i < n; i++) {
                double peakHeight = context.peak(i) * midY;
                if (peakHeight <= MIN_BAR_PIXELS / 2.0) {
                    continue;
                }
                double capY = Math.min(height - PEAK_CAP_HEIGHT, midY + peakHeight);
                gc.fillRect(barX[i], capY, barWidth[i], PEAK_CAP_HEIGHT);
            }
            gc.setGlobalAlpha(1.0);
        }
    }

    private static double halfHeight(double level, double midY) {
        return Math.max(MIN_BAR_PIXELS / 2.0, level * midY);
    }

    @Override
    public void dispose() {
        super.dispose();
        barX = null;
        barWidth = null;
        upperFill = null;
        lowerFill = null;
    }
}

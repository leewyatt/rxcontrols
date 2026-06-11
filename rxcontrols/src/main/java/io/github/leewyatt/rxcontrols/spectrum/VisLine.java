package io.github.leewyatt.rxcontrols.spectrum;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Single polyline through the band amplitudes, stroked with the
 * amplitude-keyed paint. Ignores peak caps and the bar gap ratio. At zero
 * amplitude it renders a flat baseline just above the bottom edge.
 */
public class VisLine extends SpectrumVisualizationBase {

    private static final double DEFAULT_LINE_WIDTH = 2.0;

    private double lineWidth = DEFAULT_LINE_WIDTH;

    private double[] xs;

    private double[] ys;

    private Paint stroke;

    /**
     * Creates a line visualization with the default stroke width.
     */
    public VisLine() {
    }

    /**
     * Creates a line visualization with the given stroke width.
     *
     * @param lineWidth the stroke width in pixels
     */
    public VisLine(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    /**
     * Returns the stroke width of the line.
     *
     * @return the stroke width in pixels
     */
    public double getLineWidth() {
        return lineWidth;
    }

    /**
     * Sets the stroke width of the line. Non-positive or NaN values fall back
     * to the default at render time.
     *
     * @param lineWidth the stroke width in pixels
     */
    public void setLineWidth(double lineWidth) {
        this.lineWidth = lineWidth;
    }

    @Override
    protected void rebuildGeometry(SpectrumContext context) {
        int n = context.bandCount();
        if (xs == null || xs.length != n) {
            xs = new double[n];
            ys = new double[n];
        }
        double slot = context.width() / n;
        for (int i = 0; i < n; i++) {
            xs[i] = (i + 0.5) * slot;
        }
        stroke = absoluteGradient(context.barFill(), 0.0, context.height(), 0.0, 0.0);
    }

    @Override
    protected void draw(SpectrumContext context) {
        GraphicsContext gc = context.graphicsContext();
        int n = context.bandCount();
        double height = context.height();
        double width = lineWidthOrDefault();
        double span = Math.max(0.0, height - width);
        for (int i = 0; i < n; i++) {
            ys[i] = height - width / 2.0 - context.level(i) * span;
        }
        gc.setStroke(stroke);
        gc.setLineWidth(width);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);
        gc.strokePolyline(xs, ys, n);
    }

    private double lineWidthOrDefault() {
        if (Double.isNaN(lineWidth) || lineWidth <= 0.0) {
            return DEFAULT_LINE_WIDTH;
        }
        return lineWidth;
    }

    @Override
    public void dispose() {
        super.dispose();
        xs = null;
        ys = null;
        stroke = null;
    }
}

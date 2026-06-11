package io.github.leewyatt.rxcontrols.spectrum;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Paint;
import javafx.scene.shape.StrokeLineCap;

/**
 * Polar visualization: radial bars growing outwards from an inner circle,
 * one full revolution. The amplitude axis is the radius, so a single linear
 * gradient cannot key all directions; the paint is remapped per band along
 * its own inner-to-outer axis and cached with the geometry. At zero amplitude
 * the bands form a thin ring around the inner hole.
 */
public class VisRadial extends SpectrumVisualizationBase {

    private static final double DEFAULT_INNER_RADIUS_RATIO = 0.35;

    private static final double DEFAULT_START_ANGLE = -90.0;

    private static final double MAX_INNER_RADIUS_RATIO = 0.95;

    private static final double PEAK_TICK_LENGTH = 3.0;

    private double innerRadiusRatio = DEFAULT_INNER_RADIUS_RATIO;

    private double startAngle = DEFAULT_START_ANGLE;

    private double[] cosT;

    private double[] sinT;

    private Paint[] bandPaints;

    /** True when every band shares one paint reference (solid or non-proportional
     * fill), so the stroke can be set once instead of per band. */
    private boolean uniformPaint;

    private double centerX;

    private double centerY;

    private double innerRadius;

    private double outerRadius;

    private double barThickness;

    /**
     * Creates a radial visualization with the default inner radius and start
     * angle (12 o'clock).
     */
    public VisRadial() {
    }

    /**
     * Returns the inner hole radius as a fraction of the outer radius.
     *
     * @return the inner radius ratio
     */
    public double getInnerRadiusRatio() {
        return innerRadiusRatio;
    }

    /**
     * Sets the inner hole radius as a fraction of the outer radius. Clamped to
     * {@code [0, 0.95]} at render time; NaN falls back to the default.
     *
     * @param innerRadiusRatio the inner radius ratio
     */
    public void setInnerRadiusRatio(double innerRadiusRatio) {
        this.innerRadiusRatio = innerRadiusRatio;
        invalidateGeometry();
    }

    /**
     * Returns the angle of band 0 in degrees ({@code -90} is 12 o'clock).
     *
     * @return the start angle in degrees
     */
    public double getStartAngle() {
        return startAngle;
    }

    /**
     * Sets the angle of band 0 in degrees. NaN falls back to the default.
     *
     * @param startAngle the start angle in degrees
     */
    public void setStartAngle(double startAngle) {
        this.startAngle = startAngle;
        invalidateGeometry();
    }

    @Override
    protected void rebuildGeometry(SpectrumContext context) {
        int n = context.bandCount();
        if (cosT == null || cosT.length != n) {
            cosT = new double[n];
            sinT = new double[n];
            bandPaints = new Paint[n];
        }
        centerX = context.width() / 2.0;
        centerY = context.height() / 2.0;
        outerRadius = Math.min(context.width(), context.height()) / 2.0;
        double ratio = Double.isNaN(innerRadiusRatio)
                ? DEFAULT_INNER_RADIUS_RATIO
                : RXMath.clamp(innerRadiusRatio, 0.0, MAX_INNER_RADIUS_RATIO);
        innerRadius = ratio * outerRadius;
        double angle0 = Math.toRadians(Double.isNaN(startAngle) ? DEFAULT_START_ANGLE : startAngle);

        double innerCircumference = 2.0 * Math.PI * innerRadius;
        barThickness = Math.max(1.0, innerCircumference / n * (1.0 - context.gapRatio()));

        // Per-band gradient instances are rebuilt only on resize / paint
        // change, so the allocation frequency stays at resize frequency.
        for (int i = 0; i < n; i++) {
            double angle = angle0 + i * 2.0 * Math.PI / n;
            cosT[i] = Math.cos(angle);
            sinT[i] = Math.sin(angle);
            bandPaints[i] = absoluteGradient(context.barFill(),
                    centerX + cosT[i] * innerRadius, centerY + sinT[i] * innerRadius,
                    centerX + cosT[i] * outerRadius, centerY + sinT[i] * outerRadius);
        }
        // absoluteGradient only forks per-band instances for a proportional
        // gradient; any other fill yields the same reference for every band.
        uniformPaint = true;
        for (int i = 1; i < n; i++) {
            if (bandPaints[i] != bandPaints[0]) {
                uniformPaint = false;
                break;
            }
        }
    }

    @Override
    protected void draw(SpectrumContext context) {
        GraphicsContext gc = context.graphicsContext();
        int n = context.bandCount();
        double range = outerRadius - innerRadius;

        gc.setLineWidth(barThickness);
        gc.setLineCap(StrokeLineCap.BUTT);
        if (uniformPaint) {
            gc.setStroke(bandPaints[0]);
        }
        for (int i = 0; i < n; i++) {
            double length = Math.max(MIN_BAR_PIXELS, context.level(i) * range);
            if (!uniformPaint) {
                gc.setStroke(bandPaints[i]);
            }
            gc.strokeLine(centerX + cosT[i] * innerRadius,
                    centerY + sinT[i] * innerRadius,
                    centerX + cosT[i] * (innerRadius + length),
                    centerY + sinT[i] * (innerRadius + length));
        }

        if (context.showPeaks()) {
            gc.setStroke(context.peakFill());
            for (int i = 0; i < n; i++) {
                double peakLength = context.peak(i) * range;
                if (peakLength <= MIN_BAR_PIXELS) {
                    continue;
                }
                double tickEnd = Math.min(outerRadius, innerRadius + peakLength + PEAK_TICK_LENGTH);
                double tickStart = tickEnd - PEAK_TICK_LENGTH;
                gc.strokeLine(centerX + cosT[i] * tickStart,
                        centerY + sinT[i] * tickStart,
                        centerX + cosT[i] * tickEnd,
                        centerY + sinT[i] * tickEnd);
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        cosT = null;
        sinT = null;
        bandPaints = null;
    }
}

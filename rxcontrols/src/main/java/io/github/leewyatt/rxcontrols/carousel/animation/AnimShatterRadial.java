package io.github.leewyatt.rxcontrols.carousel.animation;

import java.util.List;
import java.util.Random;

/**
 * Radial shatter transition. Fragments explode outward from a specified
 * origin point, with fragments closer to the origin shattering first
 * and those further away following in a ripple-like sequence.
 *
 * <p>This animation is direction-independent: both FORWARD and BACKWARD
 * produce the same radial explosion from the configured origin.</p>
 *
 * <p>The origin is specified in normalized coordinates (0.0 to 1.0),
 * where (0.5, 0.5) is the page center. Values outside 0–1 are allowed,
 * placing the explosion origin outside the visible page area.</p>
 *
 * <p><b>Note:</b> This animation uses snapshots and is not resize-safe.
 * If the carousel is resized during the animation, it will jump to its
 * end state automatically.</p>
 *
 * @see AnimShatter
 */
public class AnimShatterRadial extends AnimShatter {

    private final double originX;
    private final double originY;
    private final boolean randomOrigin;

    /**
     * Creates a radial shatter animation with a random origin point.
     * Each transition picks a different random point for the explosion.
     */
    public AnimShatterRadial() {
        this.originX = 0;
        this.originY = 0;
        this.randomOrigin = true;
    }

    /**
     * Creates a radial shatter animation with a fixed origin point.
     *
     * @param originX the X origin in normalized coordinates (0.0 = left, 1.0 = right)
     * @param originY the Y origin in normalized coordinates (0.0 = top, 1.0 = bottom)
     */
    public AnimShatterRadial(double originX, double originY) {
        this.originX = originX;
        this.originY = originY;
        this.randomOrigin = false;
    }

    /**
     * Creates a radial shatter animation with a fixed origin and custom grid.
     *
     * @param cols    number of grid columns (2 to 12)
     * @param rows    number of grid rows (2 to 10)
     * @param originX the X origin in normalized coordinates
     * @param originY the Y origin in normalized coordinates
     */
    public AnimShatterRadial(int cols, int rows, double originX, double originY) {
        super(cols, rows);
        this.originX = originX;
        this.originY = originY;
        this.randomOrigin = false;
    }

    /**
     * Creates a radial shatter animation with a random origin and custom grid.
     *
     * @param cols number of grid columns (2 to 12)
     * @param rows number of grid rows (2 to 10)
     */
    public AnimShatterRadial(int cols, int rows) {
        super(cols, rows);
        this.originX = 0;
        this.originY = 0;
        this.randomOrigin = true;
    }

    @Override
    protected void computeFlightData(List<double[]> triangles,
                                      double paneWidth, double paneHeight,
                                      boolean forward, double flightDistance,
                                      double[] flightDx, double[] flightDy,
                                      double[] delays, Random rng) {
        double ox;
        double oy;
        if (randomOrigin) {
            ox = rng.nextDouble() * paneWidth;
            oy = rng.nextDouble() * paneHeight;
        } else {
            ox = originX * paneWidth;
            oy = originY * paneHeight;
        }

        double maxDist = Math.sqrt(paneWidth * paneWidth + paneHeight * paneHeight);

        for (int i = 0; i < triangles.size(); i++) {
            double[] tri = triangles.get(i);
            double cx = (tri[0] + tri[2] + tri[4]) / 3.0;
            double cy = (tri[1] + tri[3] + tri[5]) / 3.0;

            double dx = cx - ox;
            double dy = cy - oy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double len = Math.max(1, dist);

            flightDx[i] = (dx / len) * flightDistance;
            flightDy[i] = (dy / len) * flightDistance;

            delays[i] = (dist / maxDist) * 0.5;
        }
    }
}

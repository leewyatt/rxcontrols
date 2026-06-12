package io.github.leewyatt.rxcontrols.animation.line;

import javafx.geometry.Bounds;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Objects;

/**
 * Line effect whose full-length bars slide into their resting position while
 * fading in: each selected edge carries one bar displaced by
 * {@code (1 - progress) * offset} along the edge's outward axis, with opacity
 * equal to the progress.
 *
 * <p>A positive offset starts the bar farther outside the reference box and
 * converges inward (on paired edges this is the closing-in effect); a
 * negative offset starts it inside, sliding outward across the content into
 * place; a zero offset fades in place.</p>
 */
public final class LineAnimSlide implements LineAnimation {

    /**
     * Default slide-in distance in pixels.
     */
    public static final double DEFAULT_OFFSET = 10.0;

    private final LineEdges edges;
    private final double offset;
    private final LineEdges[] sides;

    /**
     * Creates the effect for the given edges and slide-in distance.
     *
     * @param edges  the edges carrying bars
     * @param offset the start displacement in pixels along the outward axis;
     *               positive starts outside the reference box, negative
     *               inside, zero fades in place
     * @throws NullPointerException if {@code edges} is {@code null}
     */
    public LineAnimSlide(LineEdges edges, double offset) {
        this.edges = Objects.requireNonNull(edges, "edges cannot be null");
        this.offset = offset;
        this.sides = LineGeometry.sidesOf(edges);
    }

    /**
     * Returns the edges carrying bars.
     *
     * @return the edge selection
     */
    public LineEdges getEdges() {
        return edges;
    }

    /**
     * Returns the start displacement along the outward axis.
     *
     * @return the slide-in distance in pixels
     */
    public double getOffset() {
        return offset;
    }

    @Override
    public int barCount() {
        return sides.length;
    }

    @Override
    public void update(List<? extends Region> bars, double progress,
                       Bounds reference, double thickness, double gap) {
        int index = 0;
        for (LineEdges side : sides) {
            boolean horizontal = LineGeometry.isHorizontal(side);
            double cross = LineGeometry.crossOf(side, reference, thickness, gap)
                    + (1.0 - progress) * offset * LineGeometry.outwardSignOf(side);
            Region bar = bars.get(index++);
            bar.setOpacity(progress);
            if (horizontal) {
                bar.resizeRelocate(reference.getMinX(), cross, reference.getWidth(), thickness);
            } else {
                bar.resizeRelocate(cross, reference.getMinY(), thickness, reference.getHeight());
            }
        }
    }
}

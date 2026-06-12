package io.github.leewyatt.rxcontrols.animation.line;

import javafx.geometry.Bounds;
import javafx.scene.layout.Region;

import java.util.List;
import java.util.Objects;

/**
 * Line effect whose bars grow in length with the progress: each selected edge
 * carries a bar extending from the given origin to the full edge length.
 *
 * <p>With {@link LineOrigin#EDGES} each edge carries two segments growing
 * from both ends and meeting at the center; all other origins use a single
 * bar per edge.</p>
 */
public final class LineAnimExtend implements LineAnimation {

    private final LineEdges edges;
    private final LineOrigin origin;
    private final LineEdges[] sides;

    /**
     * Creates the effect for the given edges and growth origin.
     *
     * @param edges  the edges carrying bars
     * @param origin the growth origin along each edge
     * @throws NullPointerException if {@code edges} or {@code origin} is
     *                              {@code null}
     */
    public LineAnimExtend(LineEdges edges, LineOrigin origin) {
        this.edges = Objects.requireNonNull(edges, "edges cannot be null");
        this.origin = Objects.requireNonNull(origin, "origin cannot be null");
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
     * Returns the growth origin along each edge.
     *
     * @return the growth origin
     */
    public LineOrigin getOrigin() {
        return origin;
    }

    @Override
    public int barCount() {
        return sides.length * (origin == LineOrigin.EDGES ? 2 : 1);
    }

    @Override
    public void update(List<? extends Region> bars, double progress,
                       Bounds reference, double thickness, double gap) {
        int index = 0;
        for (LineEdges side : sides) {
            boolean horizontal = LineGeometry.isHorizontal(side);
            double mainMin = horizontal ? reference.getMinX() : reference.getMinY();
            double full = horizontal ? reference.getWidth() : reference.getHeight();
            double cross = LineGeometry.crossOf(side, reference, thickness, gap);
            if (origin == LineOrigin.EDGES) {
                double length = progress * full / 2.0;
                double overlap = Math.min(LineGeometry.SEAM_OVERLAP, length);
                place(bars.get(index++), horizontal,
                        mainMin, length + overlap, cross, thickness);
                place(bars.get(index++), horizontal,
                        mainMin + full - length - overlap, length + overlap, cross, thickness);
            } else {
                double length = progress * full;
                double main;
                switch (origin) {
                    case START:
                        main = mainMin;
                        break;
                    case END:
                        main = mainMin + full - length;
                        break;
                    default:
                        main = mainMin + (full - length) / 2.0;
                        break;
                }
                place(bars.get(index++), horizontal, main, length, cross, thickness);
            }
        }
    }

    private static void place(Region bar, boolean horizontal,
                              double main, double length, double cross, double thickness) {
        bar.setOpacity(1.0);
        if (horizontal) {
            bar.resizeRelocate(main, cross, length, thickness);
        } else {
            bar.resizeRelocate(cross, main, thickness, length);
        }
    }
}

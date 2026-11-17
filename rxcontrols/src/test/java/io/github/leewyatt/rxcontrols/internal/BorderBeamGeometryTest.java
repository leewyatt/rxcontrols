package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.layout.CornerRadii;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link BorderBeamGeometry}: the track a beam travels on, the resolution
 * of corner radii and the ring path.
 */
public class BorderBeamGeometryTest {

    private static final double EPSILON = 1.0e-9;
    private static final double WIDTH = 360.0;
    private static final double HEIGHT = 170.0;

    // ==================== Track ====================

    /** Verifies the track radius is the beam size, limited to half the shorter side. */
    @Test
    public void trackRadiusIsLimitedToHalfTheShorterSide() {
        assertEquals(40.0, BorderBeamGeometry.trackRadius(WIDTH, HEIGHT, 40.0), EPSILON);
        assertEquals(85.0, BorderBeamGeometry.trackRadius(WIDTH, HEIGHT, 100.0), EPSILON);
        assertEquals(0.0, BorderBeamGeometry.trackRadius(WIDTH, 0.0, 100.0), EPSILON);
    }

    /** Verifies the track starts after the top-left corner and runs clockwise. */
    @Test
    public void trackStartsOnTheTopEdgeAndRunsClockwise() {
        double radius = 40.0;
        double length = BorderBeamGeometry.trackLength(WIDTH, HEIGHT, radius);
        double horizontal = WIDTH - 2.0 * radius;
        double vertical = HEIGHT - 2.0 * radius;
        double arc = Math.PI * radius / 2.0;

        assertPoint(radius, 0.0, 0.0, at(radius, 0.0));
        assertPoint(radius + horizontal / 2.0, 0.0, 0.0, at(radius, horizontal / 2.0 / length));
        double diagonal = radius * Math.cos(Math.PI / 4.0);
        assertPoint(WIDTH - radius + diagonal, radius - diagonal, 45.0, at(radius, (horizontal + arc / 2.0) / length));
        assertPoint(WIDTH, radius + vertical / 2.0, 90.0, at(radius, (horizontal + arc + vertical / 2.0) / length));
        assertPoint(WIDTH - radius - horizontal / 2.0, HEIGHT, 180.0,
                at(radius, (1.5 * horizontal + 2.0 * arc + vertical) / length));
        assertPoint(0.0, HEIGHT - radius - vertical / 2.0, 270.0,
                at(radius, (2.0 * horizontal + 3.0 * arc + 1.5 * vertical) / length));
    }

    /** Verifies equal shares of a lap are equal distances, across corners and the seam. */
    @Test
    public void trackIsTravelledAtConstantSpeed() {
        double radius = 85.0;
        int steps = 2000;
        double expected = BorderBeamGeometry.trackLength(WIDTH, HEIGHT, radius) / steps;
        double[] previous = at(radius, 0.0);
        for (int i = 1; i <= steps; i++) {
            double[] current = at(radius, (double) i / steps);
            double distance = Math.hypot(current[0] - previous[0], current[1] - previous[1]);
            // A chord of an arc is slightly shorter than the arc.
            assertEquals(expected, distance, expected * 1.0e-3, "step " + i);
            double turn = Math.abs(current[2] - previous[2]);
            assertTrue(turn < 1.0 || turn > 359.0, "direction jumps at step " + i);
            previous = current;
        }
        assertPoint(radius, 0.0, 0.0, at(radius, 1.0));
        assertPoint(radius, 0.0, 0.0, at(radius, 3.0));
    }

    /** Verifies a track without straight parts or without corners stays finite. */
    @Test
    public void degenerateTracksStayFinite() {
        for (double radius : new double[]{0.0, 85.0}) {
            for (int i = 0; i <= 100; i++) {
                double[] point = new double[3];
                BorderBeamGeometry.pointOnTrack(170.0, 170.0, radius, i / 100.0, point);
                for (double value : point) {
                    assertTrue(Double.isFinite(value));
                }
            }
        }
    }

    // ==================== Corner Radii ====================

    /** Verifies percentages resolve against the box and invalid radii become zero. */
    @Test
    public void radiiResolveAgainstTheBox() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        BorderBeamGeometry.resolveRadii(new CornerRadii(0.1, true), 200.0, 100.0, radii);
        assertArrayEquals(new double[]{20.0, 10.0, 20.0, 10.0, 20.0, 10.0, 20.0, 10.0}, radii, EPSILON);

        BorderBeamGeometry.resolveRadii(new CornerRadii(4.0, 8.0, 12.0, 16.0, false), 200.0, 100.0, radii);
        assertArrayEquals(new double[]{4.0, 4.0, 8.0, 8.0, 12.0, 12.0, 16.0, 16.0}, radii, EPSILON);

        BorderBeamGeometry.resolveRadii(null, 200.0, 100.0, radii);
        assertArrayEquals(new double[BorderBeamGeometry.RADII_LENGTH], radii, EPSILON);
    }

    /** Verifies oversized radii shrink together into a capsule. */
    @Test
    public void oversizedRadiiShrinkByACommonFactor() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        BorderBeamGeometry.resolveRadii(new CornerRadii(999.0), 200.0, 60.0, radii);
        for (double radius : radii) {
            assertEquals(30.0, radius, EPSILON);
        }
    }

    /** Verifies an offset keeps round corners concentric and square corners square. */
    @Test
    public void offsetKeepsCornersConcentric() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        BorderBeamGeometry.resolveRadii(new CornerRadii(8.0, 0.0, 2.0, 8.0, false), 200.0, 100.0, radii);
        BorderBeamGeometry.offsetRadii(radii, 4.0, 208.0, 108.0);
        assertArrayEquals(new double[]{12.0, 12.0, 0.0, 0.0, 6.0, 6.0, 12.0, 12.0}, radii, EPSILON);

        BorderBeamGeometry.offsetRadii(radii, -10.0, 188.0, 88.0);
        assertArrayEquals(new double[]{2.0, 2.0, 0.0, 0.0, 0.0, 0.0, 2.0, 2.0}, radii, EPSILON);
    }

    // ==================== Ring ====================

    /** Verifies the ring is an outer and an inner outline with the inner radii reduced. */
    @Test
    public void ringHasAnInnerOutlineWithReducedRadii() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        BorderBeamGeometry.resolveRadii(new CornerRadii(8.0), 200.0, 100.0, radii);
        List<PathElement> elements = new ArrayList<>();
        BorderBeamGeometry.buildRing(elements, 200.0, 100.0, radii, 3.0);

        assertEquals(2, count(elements, MoveTo.class));
        assertEquals(2, count(elements, ClosePath.class));
        List<ArcTo> arcs = arcs(elements);
        assertEquals(8, arcs.size());
        assertEquals(8.0, arcs.get(0).getRadiusX(), EPSILON);
        assertEquals(5.0, arcs.get(4).getRadiusX(), EPSILON);
        assertEquals(5.0, arcs.get(4).getRadiusY(), EPSILON);
        MoveTo inner = (MoveTo) elements.get(elements.size() / 2);
        assertEquals(3.0 + 5.0, inner.getX(), EPSILON);
        assertEquals(3.0, inner.getY(), EPSILON);
    }

    /** Verifies the inner outline turns square once the line is wider than the radius. */
    @Test
    public void innerOutlineTurnsSquareUnderAWideLine() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        BorderBeamGeometry.resolveRadii(new CornerRadii(8.0), 200.0, 100.0, radii);
        List<PathElement> elements = new ArrayList<>();
        BorderBeamGeometry.buildRing(elements, 200.0, 100.0, radii, 10.0);
        assertEquals(4, arcs(elements).size());
        assertEquals(2, count(elements, MoveTo.class));
    }

    /** Verifies a line too wide to leave an inner box covers the whole box. */
    @Test
    public void lineWiderThanTheBoxLeavesNoHole() {
        double[] radii = new double[BorderBeamGeometry.RADII_LENGTH];
        List<PathElement> elements = new ArrayList<>(List.of(new MoveTo(1.0, 1.0)));
        BorderBeamGeometry.buildRing(elements, 200.0, 100.0, radii, 50.0);
        assertEquals(1, count(elements, MoveTo.class));
        assertEquals(1, count(elements, ClosePath.class));
    }

    // ==================== Helpers ====================

    private static double[] at(double radius, double fraction) {
        double[] point = new double[3];
        BorderBeamGeometry.pointOnTrack(WIDTH, HEIGHT, radius, fraction, point);
        return point;
    }

    private static void assertPoint(double x, double y, double angle, double[] actual) {
        assertEquals(x, actual[0], 1.0e-6);
        assertEquals(y, actual[1], 1.0e-6);
        assertEquals(angle, actual[2], 1.0e-6);
    }

    private static long count(List<PathElement> elements, Class<? extends PathElement> type) {
        return elements.stream().filter(type::isInstance).count();
    }

    private static List<ArcTo> arcs(List<PathElement> elements) {
        List<ArcTo> arcs = new ArrayList<>();
        for (PathElement element : elements) {
            if (element instanceof ArcTo) {
                arcs.add((ArcTo) element);
            }
        }
        return arcs;
    }
}

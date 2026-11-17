package io.github.leewyatt.rxcontrols.internal;

import javafx.scene.layout.CornerRadii;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;

import java.util.Arrays;
import java.util.List;

/**
 * Geometry of a border beam: the track a beam travels on, and the ring that
 * limits where it is visible.
 *
 * <p>The two are separate shapes. The ring follows the corner radii of the
 * decorated box; the track is a rounded rectangle on the outer edge of the
 * ring whose corner radius is the beam's own size, so that a beam turns a
 * corner gradually whatever the corner of the box looks like.</p>
 */
public final class BorderBeamGeometry {

    /**
     * Number of values in a corner radii array: a horizontal and a vertical
     * radius for the top-left, top-right, bottom-right and bottom-left corner,
     * in that order.
     */
    public static final int RADII_LENGTH = 8;

    private BorderBeamGeometry() {
    }

    // ==================== Track ====================

    /**
     * Returns the corner radius of the track of a beam in a box.
     *
     * @param width    the box width
     * @param height   the box height
     * @param beamSize the side of the beam
     * @return the beam size, limited to half the shorter side of the box
     */
    public static double trackRadius(double width, double height, double beamSize) {
        return Math.max(0.0, Math.min(beamSize, Math.min(width, height) / 2.0));
    }

    /**
     * Returns the length of one lap of the track.
     *
     * @param width  the box width
     * @param height the box height
     * @param radius the corner radius of the track
     * @return the perimeter of the rounded rectangle
     */
    public static double trackLength(double width, double height, double radius) {
        return 2.0 * (width - 2.0 * radius) + 2.0 * (height - 2.0 * radius) + 2.0 * Math.PI * radius;
    }

    /**
     * Finds the point of the track at a share of its length. The track starts
     * at the left end of the straight part of the top edge and runs clockwise;
     * every part takes a share equal to its length.
     *
     * @param width    the box width
     * @param height   the box height
     * @param radius   the corner radius of the track
     * @param fraction the share of the lap; only its fractional part counts
     * @param out      receives the x and y of the point and the direction of
     *                 travel in degrees, {@code 0} pointing right and {@code 90}
     *                 pointing down
     */
    public static void pointOnTrack(double width, double height, double radius, double fraction, double[] out) {
        double horizontal = width - 2.0 * radius;
        double vertical = height - 2.0 * radius;
        double arc = Math.PI * radius / 2.0;
        double distance = (fraction - Math.floor(fraction)) * trackLength(width, height, radius);

        if (distance <= horizontal) {
            set(out, radius + distance, 0.0, 0.0);
            return;
        }
        distance -= horizontal;
        if (distance <= arc) {
            setOnArc(out, width - radius, radius, radius, -90.0 + 90.0 * distance / arc);
            return;
        }
        distance -= arc;
        if (distance <= vertical) {
            set(out, width, radius + distance, 90.0);
            return;
        }
        distance -= vertical;
        if (distance <= arc) {
            setOnArc(out, width - radius, height - radius, radius, 90.0 * distance / arc);
            return;
        }
        distance -= arc;
        if (distance <= horizontal) {
            set(out, width - radius - distance, height, 180.0);
            return;
        }
        distance -= horizontal;
        if (distance <= arc) {
            setOnArc(out, radius, height - radius, radius, 90.0 + 90.0 * distance / arc);
            return;
        }
        distance -= arc;
        if (distance <= vertical) {
            set(out, 0.0, height - radius - distance, 270.0);
            return;
        }
        distance -= vertical;
        // Rounding may leave a hair more than the last arc is long.
        setOnArc(out, radius, radius, radius, 180.0 + 90.0 * Math.min(1.0, arc == 0.0 ? 0.0 : distance / arc));
    }

    private static void set(double[] out, double x, double y, double angle) {
        out[0] = x;
        out[1] = y;
        out[2] = angle;
    }

    private static void setOnArc(double[] out, double centerX, double centerY, double radius, double degrees) {
        double radians = Math.toRadians(degrees);
        set(out, centerX + radius * Math.cos(radians), centerY + radius * Math.sin(radians), degrees + 90.0);
    }

    // ==================== Corner Radii ====================

    /**
     * Resolves corner radii against a box: percentages become lengths, negative
     * and non-finite radii become zero, and radii too large for the box shrink
     * by a common factor until adjacent corners no longer overlap.
     *
     * @param radii  the corner radii, or {@code null} for square corners
     * @param width  the box width
     * @param height the box height
     * @param out    receives the {@value #RADII_LENGTH} resolved radii
     */
    public static void resolveRadii(CornerRadii radii, double width, double height, double[] out) {
        if (radii == null) {
            Arrays.fill(out, 0.0);
            return;
        }
        out[0] = length(radii.getTopLeftHorizontalRadius(), radii.isTopLeftHorizontalRadiusAsPercentage(), width);
        out[1] = length(radii.getTopLeftVerticalRadius(), radii.isTopLeftVerticalRadiusAsPercentage(), height);
        out[2] = length(radii.getTopRightHorizontalRadius(), radii.isTopRightHorizontalRadiusAsPercentage(), width);
        out[3] = length(radii.getTopRightVerticalRadius(), radii.isTopRightVerticalRadiusAsPercentage(), height);
        out[4] = length(radii.getBottomRightHorizontalRadius(),
                radii.isBottomRightHorizontalRadiusAsPercentage(), width);
        out[5] = length(radii.getBottomRightVerticalRadius(), radii.isBottomRightVerticalRadiusAsPercentage(), height);
        out[6] = length(radii.getBottomLeftHorizontalRadius(), radii.isBottomLeftHorizontalRadiusAsPercentage(), width);
        out[7] = length(radii.getBottomLeftVerticalRadius(), radii.isBottomLeftVerticalRadiusAsPercentage(), height);
        fitRadii(out, width, height);
    }

    private static double length(double value, boolean percentage, double side) {
        double length = percentage ? value * side : value;
        return Double.isFinite(length) && length > 0.0 ? length : 0.0;
    }

    /**
     * Moves the edge the radii belong to outward by a distance, or inward for a
     * negative one, keeping the corners concentric. A square corner stays
     * square, and a radius never drops below zero.
     *
     * @param radii    the resolved radii, changed in place
     * @param distance the distance the edge moves outward
     * @param width    the width of the box after the move
     * @param height   the height of the box after the move
     */
    public static void offsetRadii(double[] radii, double distance, double width, double height) {
        for (int corner = 0; corner < RADII_LENGTH; corner += 2) {
            if (radii[corner] > 0.0 && radii[corner + 1] > 0.0) {
                radii[corner] = Math.max(0.0, radii[corner] + distance);
                radii[corner + 1] = Math.max(0.0, radii[corner + 1] + distance);
            }
        }
        fitRadii(radii, width, height);
    }

    private static void fitRadii(double[] radii, double width, double height) {
        double factor = 1.0;
        factor = Math.min(factor, share(width, radii[0] + radii[2]));
        factor = Math.min(factor, share(width, radii[6] + radii[4]));
        factor = Math.min(factor, share(height, radii[1] + radii[7]));
        factor = Math.min(factor, share(height, radii[3] + radii[5]));
        for (int i = 0; i < RADII_LENGTH; i++) {
            radii[i] *= factor;
        }
    }

    private static double share(double side, double sum) {
        return sum > side ? Math.max(0.0, side) / sum : 1.0;
    }

    // ==================== Ring ====================

    /**
     * Replaces the given path elements with a ring: the outline of a rounded
     * box followed by the outline of the same box moved inward by the line
     * width. Filled with the even-odd rule, only the band between the two is
     * covered. A line too wide to leave an inner box covers the whole box.
     *
     * @param elements  the path elements to replace
     * @param width     the width of the outer box, whose origin is {@code 0, 0}
     * @param height    the height of the outer box
     * @param radii     the resolved radii of the outer box
     * @param lineWidth the width of the band
     */
    public static void buildRing(List<PathElement> elements, double width, double height,
                                 double[] radii, double lineWidth) {
        elements.clear();
        addRoundedBox(elements, 0.0, 0.0, width, height, radii, 0.0);
        double innerWidth = width - 2.0 * lineWidth;
        double innerHeight = height - 2.0 * lineWidth;
        if (innerWidth > 0.0 && innerHeight > 0.0) {
            addRoundedBox(elements, lineWidth, lineWidth, innerWidth, innerHeight, radii, lineWidth);
        }
    }

    private static void addRoundedBox(List<PathElement> elements, double x, double y, double width, double height,
                                      double[] radii, double inset) {
        double topLeftH = Math.max(0.0, radii[0] - inset);
        double topLeftV = Math.max(0.0, radii[1] - inset);
        double topRightH = Math.max(0.0, radii[2] - inset);
        double topRightV = Math.max(0.0, radii[3] - inset);
        double bottomRightH = Math.max(0.0, radii[4] - inset);
        double bottomRightV = Math.max(0.0, radii[5] - inset);
        double bottomLeftH = Math.max(0.0, radii[6] - inset);
        double bottomLeftV = Math.max(0.0, radii[7] - inset);

        elements.add(new MoveTo(x + topLeftH, y));
        elements.add(new LineTo(x + width - topRightH, y));
        addCorner(elements, topRightH, topRightV, x + width, y + topRightV);
        elements.add(new LineTo(x + width, y + height - bottomRightV));
        addCorner(elements, bottomRightH, bottomRightV, x + width - bottomRightH, y + height);
        elements.add(new LineTo(x + bottomLeftH, y + height));
        addCorner(elements, bottomLeftH, bottomLeftV, x, y + height - bottomLeftV);
        elements.add(new LineTo(x, y + topLeftV));
        addCorner(elements, topLeftH, topLeftV, x + topLeftH, y);
        elements.add(new ClosePath());
    }

    // A corner that lost either radius is square: the two edges already meet.
    private static void addCorner(List<PathElement> elements, double radiusX, double radiusY,
                                  double endX, double endY) {
        if (radiusX > 0.0 && radiusY > 0.0) {
            elements.add(new ArcTo(radiusX, radiusY, 0.0, endX, endY, false, true));
        } else {
            elements.add(new LineTo(endX, endY));
        }
    }
}

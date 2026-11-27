package io.github.leewyatt.rxcontrols.internal.icon;

import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;

import java.util.Locale;

/**
 * Turns an icon's path into the outline a region can scale to its view box.
 *
 * <p>A region scales its shape so that the shape's bounds fill the region, and
 * those bounds cover every point of the path, curve control points included.
 * A drawing that reaches outside the view box, or a curve whose control points
 * do, would therefore be scaled down and shifted. The outline is the path's
 * filled area clipped to the view box, as SVG clips it, with every curve split
 * until its control points lie inside the view box; it is filled with the
 * non-zero rule.</p>
 */
public final class IconOutline {

    private static final int MAX_SPLITS = 8;
    private static final double TOLERANCE = 1.0e-3;

    private IconOutline() {
    }

    /**
     * Returns the outline of a path inside a view box, as SVG path data.
     *
     * @param path     the SVG path data
     * @param fillRule the fill rule of the path
     * @param minX     the left edge of the view box
     * @param minY     the top edge of the view box
     * @param width    the view box width
     * @param height   the view box height
     * @return the outline, to be filled with the non-zero rule; empty when nothing
     *         is drawn inside the view box
     */
    public static String of(String path, FillRule fillRule, double minX, double minY, double width, double height) {
        SVGPath source = new SVGPath();
        source.setContent(path);
        source.setFillRule(fillRule);
        Shape clipped = Shape.intersect(source, new Rectangle(minX, minY, width, height));
        if (!(clipped instanceof Path outline)) {
            return "";
        }
        Box box = new Box(minX, minY, minX + width, minY + height);
        StringBuilder data = new StringBuilder();
        double x = 0.0;
        double y = 0.0;
        double startX = 0.0;
        double startY = 0.0;
        for (PathElement element : outline.getElements()) {
            if (element instanceof MoveTo move) {
                x = move.getX();
                y = move.getY();
                startX = x;
                startY = y;
                data.append('M');
                box.point(data, x, y);
            } else if (element instanceof LineTo line) {
                x = line.getX();
                y = line.getY();
                data.append('L');
                box.point(data, x, y);
            } else if (element instanceof QuadCurveTo quad) {
                // A quadratic curve as the cubic curve that draws it.
                double c1x = x + 2.0 / 3.0 * (quad.getControlX() - x);
                double c1y = y + 2.0 / 3.0 * (quad.getControlY() - y);
                double c2x = quad.getX() + 2.0 / 3.0 * (quad.getControlX() - quad.getX());
                double c2y = quad.getY() + 2.0 / 3.0 * (quad.getControlY() - quad.getY());
                box.cubic(data, new double[]{x, y, c1x, c1y, c2x, c2y, quad.getX(), quad.getY()}, 0);
                x = quad.getX();
                y = quad.getY();
            } else if (element instanceof CubicCurveTo cubic) {
                box.cubic(data, new double[]{x, y, cubic.getControlX1(), cubic.getControlY1(),
                        cubic.getControlX2(), cubic.getControlY2(), cubic.getX(), cubic.getY()}, 0);
                x = cubic.getX();
                y = cubic.getY();
            } else if (element instanceof ClosePath) {
                data.append('Z');
                x = startX;
                y = startY;
            }
        }
        return data.toString();
    }

    /** The view box; writes points clamped into it. */
    private record Box(double minX, double minY, double maxX, double maxY) {

        void cubic(StringBuilder data, double[] c, int depth) {
            if (depth >= MAX_SPLITS || (contains(c[2], c[3]) && contains(c[4], c[5]))) {
                data.append('C');
                point(data, c[2], c[3]);
                data.append(' ');
                point(data, c[4], c[5]);
                data.append(' ');
                point(data, c[6], c[7]);
                return;
            }
            // Split in half (de Casteljau): the control points close in on the curve.
            double ax = (c[0] + c[2]) / 2.0;
            double ay = (c[1] + c[3]) / 2.0;
            double bx = (c[2] + c[4]) / 2.0;
            double by = (c[3] + c[5]) / 2.0;
            double cx = (c[4] + c[6]) / 2.0;
            double cy = (c[5] + c[7]) / 2.0;
            double dx = (ax + bx) / 2.0;
            double dy = (ay + by) / 2.0;
            double ex = (bx + cx) / 2.0;
            double ey = (by + cy) / 2.0;
            double mx = (dx + ex) / 2.0;
            double my = (dy + ey) / 2.0;
            cubic(data, new double[]{c[0], c[1], ax, ay, dx, dy, mx, my}, depth + 1);
            cubic(data, new double[]{mx, my, ex, ey, cx, cy, c[6], c[7]}, depth + 1);
        }

        boolean contains(double x, double y) {
            return x >= minX - TOLERANCE && x <= maxX + TOLERANCE && y >= minY - TOLERANCE && y <= maxY + TOLERANCE;
        }

        void point(StringBuilder data, double x, double y) {
            data.append(format(Math.max(minX, Math.min(maxX, x)))).append(' ')
                    .append(format(Math.max(minY, Math.min(maxY, y))));
        }
    }

    private static String format(double value) {
        String text = String.format(Locale.ROOT, "%.4f", value);
        int end = text.length();
        while (text.charAt(end - 1) == '0') {
            end--;
        }
        if (text.charAt(end - 1) == '.') {
            end--;
        }
        text = text.substring(0, end);
        return text.equals("-0") ? "0" : text;
    }
}

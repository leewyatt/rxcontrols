package io.github.leewyatt.rxcontrols.tools.icons;

import java.awt.geom.Path2D;

/**
 * Parses SVG path data into a {@link Path2D}. Every command is supported;
 * elliptical arcs become cubic curves.
 */
final class SvgPathParser {

    private final String text;
    private final Path2D.Double path = new Path2D.Double();
    private int index;
    private double x;
    private double y;
    private double startX;
    private double startY;
    private double controlX;
    private double controlY;
    private char previous;

    private SvgPathParser(String text) {
        this.text = text;
    }

    /**
     * Parses path data.
     *
     * @param data the SVG path data
     * @return the path, with the non-zero winding rule
     * @throws IllegalArgumentException if the data is malformed
     */
    static Path2D.Double parse(String data) {
        SvgPathParser parser = new SvgPathParser(data);
        parser.run();
        return parser.path;
    }

    private void run() {
        char command = 0;
        while (true) {
            skipSeparators();
            if (index >= text.length()) {
                return;
            }
            char c = text.charAt(index);
            if (Character.isLetter(c)) {
                command = c;
                index++;
            } else if (command == 0) {
                throw new IllegalArgumentException("Path data must start with a command: " + text);
            } else if (command == 'M') {
                command = 'L';
            } else if (command == 'm') {
                command = 'l';
            }
            execute(command);
        }
    }

    private void execute(char command) {
        boolean relative = Character.isLowerCase(command);
        double ox = relative ? x : 0.0;
        double oy = relative ? y : 0.0;
        switch (Character.toUpperCase(command)) {
            case 'M' -> {
                x = ox + number();
                y = oy + number();
                path.moveTo(x, y);
                startX = x;
                startY = y;
            }
            case 'L' -> {
                x = ox + number();
                y = oy + number();
                path.lineTo(x, y);
            }
            case 'H' -> {
                x = ox + number();
                path.lineTo(x, y);
            }
            case 'V' -> {
                y = oy + number();
                path.lineTo(x, y);
            }
            case 'C' -> {
                double x1 = ox + number();
                double y1 = oy + number();
                double x2 = ox + number();
                double y2 = oy + number();
                cubic(x1, y1, x2, y2, ox + number(), oy + number());
            }
            case 'S' -> {
                boolean smooth = "CcSs".indexOf(previous) >= 0;
                double x1 = smooth ? 2.0 * x - controlX : x;
                double y1 = smooth ? 2.0 * y - controlY : y;
                double x2 = ox + number();
                double y2 = oy + number();
                cubic(x1, y1, x2, y2, ox + number(), oy + number());
            }
            case 'Q' -> {
                double x1 = ox + number();
                double y1 = oy + number();
                quad(x1, y1, ox + number(), oy + number());
            }
            case 'T' -> {
                boolean smooth = "QqTt".indexOf(previous) >= 0;
                double x1 = smooth ? 2.0 * x - controlX : x;
                double y1 = smooth ? 2.0 * y - controlY : y;
                quad(x1, y1, ox + number(), oy + number());
            }
            case 'A' -> {
                double rx = Math.abs(number());
                double ry = Math.abs(number());
                double angle = number();
                boolean large = flag();
                boolean sweep = flag();
                arc(rx, ry, angle, large, sweep, ox + number(), oy + number());
            }
            case 'Z' -> {
                path.closePath();
                x = startX;
                y = startY;
            }
            default -> throw new IllegalArgumentException("Unknown path command " + command);
        }
        previous = command;
    }

    private void cubic(double x1, double y1, double x2, double y2, double x3, double y3) {
        path.curveTo(x1, y1, x2, y2, x3, y3);
        controlX = x2;
        controlY = y2;
        x = x3;
        y = y3;
    }

    private void quad(double x1, double y1, double x2, double y2) {
        path.quadTo(x1, y1, x2, y2);
        controlX = x1;
        controlY = y1;
        x = x2;
        y = y2;
    }

    // The endpoint-to-center conversion of the SVG specification, then one cubic
    // curve per quarter turn at most.
    private void arc(double rx, double ry, double angleDegrees, boolean large, boolean sweep, double x2, double y2) {
        double x1 = x;
        double y1 = y;
        x = x2;
        y = y2;
        if (x1 == x2 && y1 == y2) {
            return;
        }
        if (rx == 0.0 || ry == 0.0) {
            path.lineTo(x2, y2);
            return;
        }
        double phi = Math.toRadians(angleDegrees);
        double cos = Math.cos(phi);
        double sin = Math.sin(phi);
        double dx = (x1 - x2) / 2.0;
        double dy = (y1 - y2) / 2.0;
        double x1p = cos * dx + sin * dy;
        double y1p = -sin * dx + cos * dy;
        double lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
        if (lambda > 1.0) {
            double scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }
        double numerator = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
        double denominator = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
        double coefficient = Math.sqrt(Math.max(0.0, numerator / denominator)) * (large == sweep ? -1.0 : 1.0);
        double cxp = coefficient * rx * y1p / ry;
        double cyp = -coefficient * ry * x1p / rx;
        double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0;
        double cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;
        double ux = (x1p - cxp) / rx;
        double uy = (y1p - cyp) / ry;
        double vx = (-x1p - cxp) / rx;
        double vy = (-y1p - cyp) / ry;
        double theta = Math.atan2(uy, ux);
        double delta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && delta > 0.0) {
            delta -= 2.0 * Math.PI;
        } else if (sweep && delta < 0.0) {
            delta += 2.0 * Math.PI;
        }
        int segments = (int) Math.ceil(Math.abs(delta) / (Math.PI / 2.0) - 1.0e-9);
        double step = delta / segments;
        double t = 4.0 / 3.0 * Math.tan(step / 4.0);
        for (int i = 0; i < segments; i++) {
            double a1 = theta + i * step;
            double a2 = a1 + step;
            double p0x = Math.cos(a1);
            double p0y = Math.sin(a1);
            double p3x = Math.cos(a2);
            double p3y = Math.sin(a2);
            double p1x = p0x - t * p0y;
            double p1y = p0y + t * p0x;
            double p2x = p3x + t * p3y;
            double p2y = p3y - t * p3x;
            path.curveTo(mapX(p1x, p1y, cx, rx, ry, cos, sin), mapY(p1x, p1y, cy, rx, ry, cos, sin),
                    mapX(p2x, p2y, cx, rx, ry, cos, sin), mapY(p2x, p2y, cy, rx, ry, cos, sin),
                    i == segments - 1 ? x2 : mapX(p3x, p3y, cx, rx, ry, cos, sin),
                    i == segments - 1 ? y2 : mapY(p3x, p3y, cy, rx, ry, cos, sin));
        }
    }

    private static double mapX(double ux, double uy, double cx, double rx, double ry, double cos, double sin) {
        return cx + rx * cos * ux - ry * sin * uy;
    }

    private static double mapY(double ux, double uy, double cy, double rx, double ry, double cos, double sin) {
        return cy + rx * sin * ux + ry * cos * uy;
    }

    // ==================== Tokens ====================

    private void skipSeparators() {
        while (index < text.length() && (Character.isWhitespace(text.charAt(index)) || text.charAt(index) == ',')) {
            index++;
        }
    }

    private boolean flag() {
        skipSeparators();
        if (index >= text.length() || (text.charAt(index) != '0' && text.charAt(index) != '1')) {
            throw new IllegalArgumentException("Expected an arc flag at " + index + " in " + text);
        }
        return text.charAt(index++) == '1';
    }

    private double number() {
        skipSeparators();
        int start = index;
        if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
            index++;
        }
        boolean dot = false;
        while (index < text.length()) {
            char c = text.charAt(index);
            if (Character.isDigit(c)) {
                index++;
            } else if (c == '.' && !dot) {
                dot = true;
                index++;
            } else if ((c == 'e' || c == 'E') && index > start) {
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
            } else {
                break;
            }
        }
        if (start == index) {
            throw new IllegalArgumentException("Expected a number at " + index + " in " + text);
        }
        return Double.parseDouble(text.substring(start, index));
    }
}

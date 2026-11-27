package io.github.leewyatt.rxcontrols.tools.icons;

import io.github.leewyatt.rxcontrols.RXIconData;
import javafx.scene.shape.FillRule;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merges the filled elements of an SVG icon into one outline, for icons whose
 * elements use different fill rules or transforms. Each element is filled with
 * its own rule and transform, the results are united, and the outline of the
 * union is written as one path filled with the non-zero rule.
 *
 * <p>This runs at build time with {@code java.awt.geom}, which the library itself
 * does not use. The merged outline is checked against the original by drawing
 * both; an icon whose drawings differ is rejected.</p>
 */
public final class SvgOutlineMerger {

    private static final Set<String> SHAPES = Set.of("path", "rect", "circle", "ellipse", "polygon", "polyline");
    private static final Set<String> SKIPPED = Set.of("title", "desc", "metadata", "defs");
    private static final Pattern TRANSFORM = Pattern.compile("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)");
    private static final int CHECK_SIZE = 128;
    private static final int MAX_DIFFERENT_PIXELS = 8;

    private SvgOutlineMerger() {
    }

    /**
     * Reads an SVG file and merges its filled elements into one outline.
     *
     * @param name the icon name
     * @param file the SVG file
     * @return the merged icon, filled with the non-zero rule
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the file has content this merger does not handle, or
     *                                  the merged outline does not draw like the original
     */
    public static RXIconData merge(String name, Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            return merge(name, factory.createXMLStreamReader(in));
        } catch (XMLStreamException error) {
            throw new IllegalArgumentException("Not valid SVG markup: " + error.getMessage(), error);
        }
    }

    private static RXIconData merge(String name, XMLStreamReader xml) throws XMLStreamException {
        double[] viewBox = null;
        Deque<State> states = new ArrayDeque<>();
        List<Shape> filled = new ArrayList<>();
        int skipDepth = 0;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (skipDepth > 0) {
                    skipDepth--;
                } else {
                    states.pop();
                }
                continue;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if (skipDepth > 0) {
                skipDepth++;
                continue;
            }
            String element = xml.getLocalName();
            if (viewBox == null) {
                if (!element.equals("svg")) {
                    throw new IllegalArgumentException("The root element is <" + element + ">, not <svg>");
                }
                viewBox = viewBox(xml.getAttributeValue(null, "viewBox"));
                states.push(new State(new AffineTransform(), Path2D.WIND_NON_ZERO, true));
                continue;
            }
            if (SKIPPED.contains(element)) {
                skipDepth = 1;
                continue;
            }
            State state = states.peek().with(xml);
            states.push(state);
            if (element.equals("g")) {
                continue;
            }
            if (!SHAPES.contains(element)) {
                throw new IllegalArgumentException("Unsupported element <" + element + ">");
            }
            if (state.filled) {
                Path2D path = new Path2D.Double(state.windingRule);
                path.append(geometry(element, xml), false);
                filled.add(state.transform.createTransformedShape(path));
            }
        }
        if (viewBox == null || filled.isEmpty()) {
            throw new IllegalArgumentException("The SVG has no filled shape");
        }
        Area union = new Area();
        for (Shape shape : filled) {
            union.add(new Area(shape));
        }
        String outline = outline(union);
        check(filled, outline, viewBox);
        return new RXIconData(name, viewBox[0], viewBox[1], viewBox[2], viewBox[3], outline, FillRule.NON_ZERO);
    }

    // ==================== Elements ====================

    private static Shape geometry(String element, XMLStreamReader xml) {
        switch (element) {
            case "path":
                return SvgPathParser.parse(attribute(xml, "d", ""));
            case "rect": {
                double x = number(xml, "x");
                double y = number(xml, "y");
                double w = number(xml, "width");
                double h = number(xml, "height");
                double rx = number(xml, "rx");
                double ry = xml.getAttributeValue(null, "ry") == null ? rx : number(xml, "ry");
                rx = Math.min(rx == 0.0 ? ry : rx, w / 2.0);
                ry = Math.min(ry, h / 2.0);
                return new RoundRectangle2D.Double(x, y, w, h, 2.0 * rx, 2.0 * ry);
            }
            case "circle": {
                double r = number(xml, "r");
                return new Ellipse2D.Double(number(xml, "cx") - r, number(xml, "cy") - r, 2.0 * r, 2.0 * r);
            }
            case "ellipse": {
                double rx = number(xml, "rx");
                double ry = number(xml, "ry");
                return new Ellipse2D.Double(number(xml, "cx") - rx, number(xml, "cy") - ry, 2.0 * rx, 2.0 * ry);
            }
            default: {
                String[] values = attribute(xml, "points", "").strip().split("[\\s,]+");
                Path2D path = new Path2D.Double();
                for (int i = 0; i + 1 < values.length; i += 2) {
                    double px = Double.parseDouble(values[i]);
                    double py = Double.parseDouble(values[i + 1]);
                    if (i == 0) {
                        path.moveTo(px, py);
                    } else {
                        path.lineTo(px, py);
                    }
                }
                path.closePath();
                return path;
            }
        }
    }

    private static double[] viewBox(String text) {
        if (text == null) {
            throw new IllegalArgumentException("The <svg> element has no viewBox");
        }
        String[] parts = text.strip().split("[\\s,]+");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Malformed viewBox: " + text);
        }
        return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
    }

    private static String attribute(XMLStreamReader xml, String name, String fallback) {
        String value = xml.getAttributeValue(null, name);
        return value == null ? fallback : value;
    }

    private static double number(XMLStreamReader xml, String name) {
        String value = xml.getAttributeValue(null, name);
        return value == null ? 0.0 : Double.parseDouble(value.strip().replace("px", ""));
    }

    // ==================== Outline ====================

    private static String outline(Area area) {
        StringBuilder path = new StringBuilder();
        double[] c = new double[6];
        for (PathIterator it = area.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> path.append('M').append(f(c[0])).append(' ').append(f(c[1]));
                case PathIterator.SEG_LINETO -> path.append('L').append(f(c[0])).append(' ').append(f(c[1]));
                case PathIterator.SEG_QUADTO -> path.append('Q').append(f(c[0])).append(' ').append(f(c[1]))
                        .append(' ').append(f(c[2])).append(' ').append(f(c[3]));
                case PathIterator.SEG_CUBICTO -> path.append('C').append(f(c[0])).append(' ').append(f(c[1]))
                        .append(' ').append(f(c[2])).append(' ').append(f(c[3]))
                        .append(' ').append(f(c[4])).append(' ').append(f(c[5]));
                default -> path.append('Z');
            }
        }
        return path.toString();
    }

    private static String f(double value) {
        String text = String.format(Locale.ROOT, "%.3f", value);
        text = text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
        return text.equals("-0") ? "0" : text;
    }

    // Draws the original elements and the merged outline and compares the pixels.
    private static void check(List<Shape> original, String outline, double[] viewBox) {
        BufferedImage expected = new BufferedImage(CHECK_SIZE, CHECK_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage actual = new BufferedImage(CHECK_SIZE, CHECK_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        AffineTransform toImage = AffineTransform.getScaleInstance(CHECK_SIZE / viewBox[2], CHECK_SIZE / viewBox[3]);
        toImage.translate(-viewBox[0], -viewBox[1]);
        Graphics2D g = graphics(expected, toImage);
        original.forEach(g::fill);
        g.dispose();
        Path2D merged = new Path2D.Double(Path2D.WIND_NON_ZERO);
        merged.append(SvgPathParser.parse(outline), false);
        g = graphics(actual, toImage);
        g.fill(merged);
        g.dispose();
        // Curves are flattened a little differently once Area has split them, so
        // pixels on an edge may differ, and a hair-thin tip may cover a pixel center
        // in one drawing only. A wrong merge (a lost element, a filled hole) differs
        // in many pixels away from the edges.
        int different = 0;
        for (int y = 0; y < CHECK_SIZE; y++) {
            for (int x = 0; x < CHECK_SIZE; x++) {
                if (value(expected, x, y) != value(actual, x, y) && awayFromEdges(expected, x, y)) {
                    different++;
                }
            }
        }
        if (different > MAX_DIFFERENT_PIXELS) {
            throw new IllegalArgumentException("The merged outline differs from the original in "
                    + different + " pixels away from the edges");
        }
    }

    private static boolean awayFromEdges(BufferedImage image, int x, int y) {
        int center = value(image, x, y);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < CHECK_SIZE && ny < CHECK_SIZE && value(image, nx, ny) != center) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int value(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xff;
    }

    private static Graphics2D graphics(BufferedImage image, AffineTransform transform) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(Color.WHITE);
        g.transform(transform);
        return g;
    }

    // ==================== Inherited state ====================

    private record State(AffineTransform transform, int windingRule, boolean filled) {

        State with(XMLStreamReader xml) {
            for (String unsupported : List.of("clip-path", "mask", "filter", "stroke", "opacity", "fill-opacity",
                    "style")) {
                String value = xml.getAttributeValue(null, unsupported);
                if (value != null && !(unsupported.equals("stroke") && value.equals("none"))
                        && !(unsupported.endsWith("opacity") && Double.parseDouble(value) >= 1.0)) {
                    throw new IllegalArgumentException("The attribute " + unsupported + " is not supported");
                }
            }
            AffineTransform next = new AffineTransform(transform);
            String transformText = xml.getAttributeValue(null, "transform");
            if (transformText != null) {
                next.concatenate(parseTransform(transformText));
            }
            int rule = windingRule;
            String ruleText = xml.getAttributeValue(null, "fill-rule");
            if (ruleText != null) {
                rule = ruleText.equals("evenodd") ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO;
            }
            boolean fill = filled;
            String fillText = xml.getAttributeValue(null, "fill");
            if (fillText != null) {
                fill = !fillText.equals("none");
            }
            return new State(next, rule, fill);
        }
    }

    static AffineTransform parseTransform(String text) {
        AffineTransform result = new AffineTransform();
        Matcher matcher = TRANSFORM.matcher(text);
        while (matcher.find()) {
            String[] parts = matcher.group(2).strip().split("[\\s,]+");
            double[] v = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                v[i] = Double.parseDouble(parts[i]);
            }
            switch (matcher.group(1)) {
                case "matrix" -> result.concatenate(new AffineTransform(v[0], v[1], v[2], v[3], v[4], v[5]));
                case "translate" -> result.translate(v[0], v.length > 1 ? v[1] : 0.0);
                case "scale" -> result.scale(v[0], v.length > 1 ? v[1] : v[0]);
                case "rotate" -> {
                    if (v.length == 3) {
                        result.rotate(Math.toRadians(v[0]), v[1], v[2]);
                    } else {
                        result.rotate(Math.toRadians(v[0]));
                    }
                }
                case "skewX" -> result.shear(Math.tan(Math.toRadians(v[0])), 0.0);
                default -> result.shear(0.0, Math.tan(Math.toRadians(v[0])));
            }
        }
        return result;
    }
}

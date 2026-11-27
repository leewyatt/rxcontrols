package io.github.leewyatt.rxcontrols.internal.icon;

import io.github.leewyatt.rxcontrols.RXIconData;
import javafx.scene.shape.FillRule;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a single-color icon from SVG markup: the view box, and the filled
 * geometry of {@code path}, {@code rect}, {@code circle}, {@code ellipse},
 * {@code polygon} and {@code polyline} elements, also inside {@code g} groups.
 *
 * <p>Elements with {@code fill="none"} or that are hidden are skipped. Markup
 * that a single filled path cannot show is rejected: strokes, transforms,
 * clipping, masks, gradients, partial opacity, embedded styles, references and
 * text.</p>
 */
public final class SvgIconReader {

    private static final Set<String> SHAPES = Set.of("path", "rect", "circle", "ellipse", "polygon", "polyline", "line");
    private static final Set<String> SKIPPED = Set.of("title", "desc", "metadata", "defs");
    private static final Set<String> REJECTED_ATTRIBUTES = Set.of("transform", "clip-path", "mask", "filter");
    private static final Set<String> OPACITY_ATTRIBUTES = Set.of("opacity", "fill-opacity");

    private SvgIconReader() {
    }

    /**
     * Reads an icon from a stream.
     *
     * @param name the icon name
     * @param in   the SVG markup
     * @return the icon
     * @throws IllegalArgumentException if the markup is not valid SVG or cannot be shown as one filled path
     * @throws UncheckedIOException     if the stream cannot be read
     */
    public static RXIconData read(String name, InputStream in) {
        XMLInputFactory factory = factory();
        try {
            return read(name, factory.createXMLStreamReader(in));
        } catch (XMLStreamException error) {
            throw failure(error);
        }
    }

    /**
     * Reads an icon from a character stream.
     *
     * @param name   the icon name
     * @param reader the SVG markup
     * @return the icon
     * @throws IllegalArgumentException if the markup is not valid SVG or cannot be shown as one filled path
     */
    public static RXIconData read(String name, Reader reader) {
        XMLInputFactory factory = factory();
        try {
            return read(name, factory.createXMLStreamReader(reader));
        } catch (XMLStreamException error) {
            throw failure(error);
        }
    }

    // The parser reports a failed read as a parse error; keep it an I/O error.
    private static RuntimeException failure(XMLStreamException error) {
        Throwable cause = error.getNestedException() != null ? error.getNestedException() : error.getCause();
        if (cause instanceof IOException io) {
            return new UncheckedIOException(io);
        }
        return new IllegalArgumentException("Not valid SVG markup: " + error.getMessage(), error);
    }

    private static XMLInputFactory factory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        return factory;
    }

    private static RXIconData read(String name, XMLStreamReader xml) throws XMLStreamException {
        try {
            double[] viewBox = null;
            Deque<Style> styles = new ArrayDeque<>();
            StringBuilder path = new StringBuilder();
            List<FillRule> rules = new ArrayList<>();
            int skipDepth = 0;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.END_ELEMENT) {
                    if (skipDepth > 0) {
                        skipDepth--;
                    } else {
                        styles.pop();
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
                Map<String, String> attributes = attributes(xml);
                if (viewBox == null) {
                    if (!element.equals("svg")) {
                        throw new IllegalArgumentException("The root element is <" + element + ">, not <svg>");
                    }
                    viewBox = viewBox(attributes);
                    styles.push(Style.ROOT.with(element, attributes));
                    continue;
                }
                if (SKIPPED.contains(element)) {
                    skipDepth = 1;
                    continue;
                }
                Style style = styles.peek().with(element, attributes);
                styles.push(style);
                if (element.equals("g")) {
                    continue;
                }
                if (!SHAPES.contains(element)) {
                    throw new IllegalArgumentException("Unsupported element <" + element + ">");
                }
                if (!style.painted()) {
                    continue;
                }
                String geometry = geometry(element, attributes);
                if (!geometry.isEmpty()) {
                    if (path.length() > 0) {
                        path.append(' ');
                    }
                    path.append(geometry);
                    rules.add(style.fillRule);
                }
            }
            if (viewBox == null) {
                throw new IllegalArgumentException("No <svg> element");
            }
            if (path.length() == 0) {
                throw new IllegalArgumentException("The SVG has no filled shape");
            }
            FillRule rule = rules.get(0);
            for (FillRule other : rules) {
                if (other != rule) {
                    throw new IllegalArgumentException("The SVG mixes the non-zero and even-odd fill rules");
                }
            }
            return new RXIconData(name, viewBox[0], viewBox[1], viewBox[2], viewBox[3], path.toString(), rule);
        } finally {
            xml.close();
        }
    }

    private static Map<String, String> attributes(XMLStreamReader xml) {
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            String prefix = xml.getAttributePrefix(i);
            String local = xml.getAttributeLocalName(i);
            if (prefix != null && !prefix.isEmpty()) {
                if (prefix.equals("xlink") && local.equals("href")) {
                    throw new IllegalArgumentException("References (xlink:href) are not supported");
                }
                continue;
            }
            attributes.put(local, xml.getAttributeValue(i).strip());
        }
        String style = attributes.remove("style");
        if (style != null) {
            for (String declaration : style.split(";")) {
                int colon = declaration.indexOf(':');
                if (colon > 0) {
                    attributes.put(declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            declaration.substring(colon + 1).strip());
                }
            }
        }
        for (String rejected : REJECTED_ATTRIBUTES) {
            if (attributes.containsKey(rejected)) {
                throw new IllegalArgumentException("The attribute " + rejected + " is not supported");
            }
        }
        for (String opacity : OPACITY_ATTRIBUTES) {
            String value = attributes.get(opacity);
            if (value != null && number(value, opacity) < 1.0) {
                throw new IllegalArgumentException("Partial opacity (" + opacity + "=" + value + ") is not supported");
            }
        }
        if (attributes.containsKey("href")) {
            throw new IllegalArgumentException("References (href) are not supported");
        }
        return attributes;
    }

    private static double[] viewBox(Map<String, String> attributes) {
        String viewBox = attributes.get("viewBox");
        if (viewBox != null) {
            String[] parts = viewBox.split("[\\s,]+");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Malformed viewBox: " + viewBox);
            }
            return new double[]{number(parts[0], "viewBox"), number(parts[1], "viewBox"),
                    number(parts[2], "viewBox"), number(parts[3], "viewBox")};
        }
        String width = attributes.get("width");
        String height = attributes.get("height");
        if (width == null || height == null) {
            throw new IllegalArgumentException("The <svg> element has neither a viewBox nor a width and height");
        }
        return new double[]{0.0, 0.0, number(width, "width"), number(height, "height")};
    }

    private static String geometry(String element, Map<String, String> attributes) {
        switch (element) {
            case "path": {
                String d = attributes.get("d");
                return d == null ? "" : d;
            }
            case "rect":
                return rect(number(attributes, "x"), number(attributes, "y"),
                        number(attributes, "width"), number(attributes, "height"),
                        attributes.get("rx"), attributes.get("ry"));
            case "circle": {
                double r = number(attributes, "r");
                return ellipse(number(attributes, "cx"), number(attributes, "cy"), r, r);
            }
            case "ellipse":
                return ellipse(number(attributes, "cx"), number(attributes, "cy"),
                        number(attributes, "rx"), number(attributes, "ry"));
            case "polygon":
                return points(attributes.get("points"), true);
            case "polyline":
                return points(attributes.get("points"), false);
            default:
                // A line encloses no area, so filling it draws nothing.
                return "";
        }
    }

    private static String rect(double x, double y, double w, double h, String rxText, String ryText) {
        if (w <= 0.0 || h <= 0.0) {
            return "";
        }
        double rx = rxText == null ? -1.0 : number(rxText, "rx");
        double ry = ryText == null ? -1.0 : number(ryText, "ry");
        if (rx < 0.0) {
            rx = Math.max(ry, 0.0);
        }
        if (ry < 0.0) {
            ry = rx;
        }
        rx = Math.min(rx, w / 2.0);
        ry = Math.min(ry, h / 2.0);
        if (rx == 0.0 || ry == 0.0) {
            return "M" + f(x) + " " + f(y) + "H" + f(x + w) + "V" + f(y + h) + "H" + f(x) + "Z";
        }
        String arc = "A" + f(rx) + " " + f(ry) + " 0 0 1 ";
        return "M" + f(x + rx) + " " + f(y)
                + "H" + f(x + w - rx) + arc + f(x + w) + " " + f(y + ry)
                + "V" + f(y + h - ry) + arc + f(x + w - rx) + " " + f(y + h)
                + "H" + f(x + rx) + arc + f(x) + " " + f(y + h - ry)
                + "V" + f(y + ry) + arc + f(x + rx) + " " + f(y) + "Z";
    }

    private static String ellipse(double cx, double cy, double rx, double ry) {
        if (rx <= 0.0 || ry <= 0.0) {
            return "";
        }
        String arc = "A" + f(rx) + " " + f(ry) + " 0 1 0 ";
        return "M" + f(cx - rx) + " " + f(cy) + arc + f(cx + rx) + " " + f(cy) + arc + f(cx - rx) + " " + f(cy) + "Z";
    }

    private static String points(String text, boolean closed) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] parts = text.strip().split("[\\s,]+");
        if (parts.length < 4 || parts.length % 2 != 0) {
            throw new IllegalArgumentException("Malformed points: " + text);
        }
        StringBuilder path = new StringBuilder("M");
        for (int i = 0; i < parts.length; i += 2) {
            if (i > 0) {
                path.append('L');
            }
            path.append(f(number(parts[i], "points"))).append(' ').append(f(number(parts[i + 1], "points")));
        }
        if (closed) {
            path.append('Z');
        }
        return path.toString();
    }

    private static double number(Map<String, String> attributes, String name) {
        String value = attributes.get(name);
        return value == null ? 0.0 : number(value, name);
    }

    private static double number(String text, String what) {
        String value = text.strip();
        if (value.endsWith("px")) {
            value = value.substring(0, value.length() - 2).strip();
        }
        try {
            double number = Double.parseDouble(value);
            if (!Double.isFinite(number)) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Unsupported number for " + what + ": " + text, error);
        }
    }

    /** Formats a number without a trailing ".0". */
    static String f(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** The inherited painting state of an element. */
    private static final class Style {

        static final Style ROOT = new Style(true, false, FillRule.NON_ZERO, true);

        final boolean filled;
        final boolean stroked;
        final FillRule fillRule;
        final boolean shown;

        private Style(boolean filled, boolean stroked, FillRule fillRule, boolean shown) {
            this.filled = filled;
            this.stroked = stroked;
            this.fillRule = fillRule;
            this.shown = shown;
        }

        Style with(String element, Map<String, String> attributes) {
            boolean fill = filled;
            String fillValue = attributes.get("fill");
            if (fillValue != null) {
                if (fillValue.startsWith("url(")) {
                    throw new IllegalArgumentException("Gradient and pattern fills are not supported");
                }
                fill = !fillValue.equals("none") && !fillValue.equals("transparent");
            }
            boolean stroke = stroked;
            String strokeValue = attributes.get("stroke");
            if (strokeValue != null) {
                stroke = !strokeValue.equals("none") && !strokeValue.equals("transparent");
            }
            FillRule rule = fillRule;
            String ruleValue = attributes.get("fill-rule");
            if (ruleValue != null) {
                rule = ruleValue.equals("evenodd") ? FillRule.EVEN_ODD : FillRule.NON_ZERO;
            }
            boolean visible = shown
                    && !"none".equals(attributes.get("display"))
                    && !"hidden".equals(attributes.get("visibility"));
            Style style = new Style(fill, stroke, rule, visible);
            if (style.stroked && style.shown && SHAPES.contains(element)) {
                throw new IllegalArgumentException("Strokes are not supported; convert them to filled outlines");
            }
            return style;
        }

        boolean painted() {
            return filled && shown;
        }
    }
}

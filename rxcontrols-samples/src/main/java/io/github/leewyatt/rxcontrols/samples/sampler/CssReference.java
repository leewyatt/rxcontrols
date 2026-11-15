package io.github.leewyatt.rxcontrols.samples.sampler;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The CSS tab of a page, generated from a live instance rather than written by hand: the
 * style-class structure its skin builds, and its {@code -rx-} properties with the values
 * the default stylesheet gives them.
 */
final class CssReference {

    private static final String RX_PROPERTY_PREFIX = "-rx-";
    private static final int MAX_DEPTH = 6;
    private static final int MAX_STRUCTURE_ROWS = 60;

    private CssReference() {
    }

    /**
     * One style-class path of the skin.
     *
     * @param selector the selector that reaches the node from the control root
     * @param node     the node class
     */
    record StructureRow(String selector, String node) {
    }

    /**
     * One {@code -rx-} property.
     *
     * @param property the CSS property name
     * @param value    the value on a default instance, formatted as CSS
     */
    record PropertyRow(String property, String value) {
    }

    // ==================== View ====================

    static Node create(SamplerVariant variant, Node playground) {
        VBox content = new VBox(16.0);
        content.getStyleClass().add("css-reference");

        String typeName = variant.typeName();
        Node fresh = SamplerInstances.create(variant.type());
        Node sample = fresh != null ? fresh : liveInstance(variant.type(), playground);
        if (sample == null) {
            content.getChildren().add(note(Node.class.isAssignableFrom(variant.type())
                    ? typeName + " has no default instance to inspect."
                    : typeName + " is not a node, so it has no style classes of its own; "
                    + "style the node it works with."));
        } else {
            StackPane holder = fresh != null ? styleDetached(fresh) : null;
            content.getChildren().addAll(
                    note((fresh != null ? "Generated from a default " : "Generated from the playground's ")
                            + typeName + ". State pseudo-classes are not listed. Click a name to copy it."),
                    structureSection(structureOf(sample)),
                    propertySection(propertiesOf(sample)));
            if (holder != null) {
                release(holder, fresh);
            }
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("css-scroll");
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static Node liveInstance(Class<?> type, Node playground) {
        Node found = playground == null ? null : SamplerInstances.find(playground, type);
        if (found != null) {
            found.applyCss();
        }
        return found;
    }

    // The skin is created by the first CSS pass, which needs a scene; some skins build
    // part of their structure in layout.
    private static StackPane styleDetached(Node sample) {
        StackPane holder = new StackPane(sample);
        new Scene(holder);
        holder.resize(480.0, 360.0);
        holder.applyCss();
        holder.layout();
        holder.applyCss();
        return holder;
    }

    private static void release(StackPane holder, Node sample) {
        holder.getChildren().clear();
        if (sample instanceof Control control) {
            control.setSkin(null);
        }
    }

    private static Node structureSection(List<StructureRow> rows) {
        GridPane table = table("Selector", "Node");
        int row = 1;
        for (StructureRow r : rows) {
            table.addRow(row++, code(r.selector()), cell(r.node()));
        }
        return section("Structure", table, rows.size() <= 1
                ? "No internal style classes: the node lays out the children you give it." : null);
    }

    private static Node propertySection(List<PropertyRow> rows) {
        GridPane table = table("Property", "Default");
        int row = 1;
        for (PropertyRow r : rows) {
            table.addRow(row++, code(r.property()), cell(r.value()));
        }
        return section("CSS properties", table, rows.isEmpty() ? "No -rx- properties." : null);
    }

    private static VBox section(String title, GridPane table, String emptyText) {
        Label heading = new Label(title);
        heading.getStyleClass().add("css-heading");
        VBox box = new VBox(heading);
        box.getStyleClass().add("css-section");
        if (emptyText != null) {
            box.getChildren().add(note(emptyText));
        }
        if (table.getRowCount() > 1) {
            box.getChildren().add(table);
        }
        return box;
    }

    private static GridPane table(String first, String second) {
        GridPane table = new GridPane();
        table.getStyleClass().add("css-table");
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(55.0);
        ColumnConstraints right = new ColumnConstraints();
        right.setHgrow(Priority.ALWAYS);
        table.getColumnConstraints().addAll(left, right);
        Label a = new Label(first);
        Label b = new Label(second);
        a.getStyleClass().add("column-header");
        b.getStyleClass().add("column-header");
        table.addRow(0, a, b);
        return table;
    }

    private static Label code(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("code");
        label.setCursor(Cursor.HAND);
        label.setTooltip(new Tooltip("Click to copy"));
        label.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            ClipboardContent clip = new ClipboardContent();
            clip.putString(text);
            Clipboard.getSystemClipboard().setContent(clip);
        });
        return label;
    }

    private static Label cell(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("value");
        return label;
    }

    private static Label note(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("css-note");
        label.setWrapText(true);
        return label;
    }

    // ==================== Extraction ====================

    /**
     * Lists the style-class paths under a styled node, starting with the node itself.
     * Nested controls are listed but not entered: their own pages describe them.
     */
    static List<StructureRow> structureOf(Node sample) {
        Map<String, String> paths = new LinkedHashMap<>();
        String root = "." + lastStyleClass(sample, typeName(sample));
        paths.put(root, typeName(sample));
        walk(sample, root, " > ", 0, paths);
        List<StructureRow> rows = new ArrayList<>();
        paths.forEach((selector, node) -> rows.add(new StructureRow(selector, node)));
        return rows;
    }

    private static void walk(Node node, String path, String combinator, int depth, Map<String, String> out) {
        if (!(node instanceof Parent parent) || depth >= MAX_DEPTH) {
            return;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (out.size() >= MAX_STRUCTURE_ROWS) {
                return;
            }
            if (child instanceof Text) {
                continue;
            }
            String styleClass = lastStyleClass(child, null);
            if (styleClass == null) {
                walk(child, path, " ", depth + 1, out);
                continue;
            }
            String selector = path + combinator + "." + styleClass;
            out.putIfAbsent(selector, typeName(child));
            if (!(child instanceof Control)) {
                walk(child, selector, " > ", depth + 1, out);
            }
        }
    }

    // A control lists its JavaFX base class first and its own class last.
    private static String lastStyleClass(Node node, String fallback) {
        List<String> classes = node.getStyleClass();
        return classes.isEmpty() ? fallback : classes.get(classes.size() - 1);
    }

    private static String typeName(Node node) {
        Class<?> type = node.getClass();
        while (type.getSimpleName().isEmpty()) {
            type = type.getSuperclass();
        }
        return type.getSimpleName();
    }

    /**
     * Lists the {@code -rx-} properties of a styled node with their current values.
     */
    static List<PropertyRow> propertiesOf(Node sample) {
        Map<String, PropertyRow> rows = new LinkedHashMap<>();
        for (CssMetaData<? extends Styleable, ?> meta : sample.getCssMetaData()) {
            String name = meta.getProperty();
            if (name.startsWith(RX_PROPERTY_PREFIX)) {
                rows.putIfAbsent(name, new PropertyRow(name, format(valueOf(meta, sample))));
            }
        }
        return new ArrayList<>(rows.values());
    }

    @SuppressWarnings("unchecked")
    private static Object valueOf(CssMetaData<? extends Styleable, ?> meta, Node sample) {
        CssMetaData<Styleable, ?> typed = (CssMetaData<Styleable, ?>) meta;
        StyleableProperty<?> property = typed.getStyleableProperty(sample);
        return property != null ? property.getValue() : typed.getInitialValue(sample);
    }

    static String format(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Color color) {
            return hex(color);
        }
        if (value instanceof Duration duration) {
            return number(duration.toMillis()) + "ms";
        }
        if (value instanceof Enum<?> constant) {
            return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
        if (value instanceof Number n) {
            return number(n.doubleValue());
        }
        if (value instanceof Insets insets) {
            return number(insets.getTop()) + " " + number(insets.getRight()) + " "
                    + number(insets.getBottom()) + " " + number(insets.getLeft());
        }
        if (value instanceof Font font) {
            return font.getFamily() + " " + number(font.getSize());
        }
        return value.toString();
    }

    private static String number(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static String hex(Color color) {
        String rgb = String.format(Locale.ROOT, "#%02x%02x%02x", channel(color.getRed()), channel(color.getGreen()),
                channel(color.getBlue()));
        return color.getOpacity() < 1.0 ? rgb + String.format(Locale.ROOT, "%02x", channel(color.getOpacity())) : rgb;
    }

    private static int channel(double value) {
        return (int) Math.round(value * 255.0);
    }
}

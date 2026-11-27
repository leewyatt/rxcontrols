package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXColorPresetGroup;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.internal.color.ColorSwatch;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The preset section of a color view: a scroll pane of preset groups, each a
 * flow of swatches, under a collapsible {@link RXTitledPane} header when the
 * group has a label.
 *
 * <p>Each group's swatches are one Tab stop: the arrow keys move a keyboard
 * cursor (the {@code :focused} pseudo-class on a swatch, shown from the first
 * navigation key), Home / End jump to the ends, Enter / Space pick. A click
 * picks the swatch under the pointer. Group nodes are reused while the same
 * group instance stays in the list, so a group keeps its expanded state; input
 * handlers sit on the section's container, never on the rebuilt nodes.
 */
final class ColorPresetSection {

    // ==================== Host ====================

    /** What the section needs from the view's skin. */
    interface Host {

        /** The current value, or {@code null}. */
        Color value();

        /** Whether the opacity is edited; when not, selection ignores it. */
        boolean alphaEnabled();

        /** Picks a preset color as one complete user edit. */
        void pick(Color color);
    }

    // ==================== Constants ====================

    private static final PseudoClass CURSOR = PseudoClass.getPseudoClass("focused");
    private static final double SWATCH_SIZE = 24.0;
    private static final double MAX_HEIGHT = 240.0;

    // ==================== Nodes ====================

    private final Host host;
    // Fitted to its width with no horizontal bar, so its height depends on its width;
    // ScrollPane does not say so itself, and parents would ask for its height at the
    // FlowPane's default wrap length instead of the width it gets.
    private final ScrollPane scroll = new ScrollPane() {
        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }
    };
    private final VBox groupsBox = new VBox();
    private final Map<RXColorPresetGroup, GroupNode> cache = new IdentityHashMap<>();
    private final List<GroupNode> shown = new ArrayList<>();

    ColorPresetSection(Host host) {
        this.host = host;
        groupsBox.getStyleClass().add("groups");
        scroll.getStyleClass().add("presets");
        scroll.setContent(groupsBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setFocusTraversable(false);
        scroll.setMaxHeight(MAX_HEIGHT);
        // The swatches wrap to the width the panel gives them and never widen it: a
        // horizontal FlowPane asks for at least its prefWrapLength (400 by default).
        scroll.setPrefWidth(0.0);
        scroll.setVisible(false);
        scroll.setManaged(false);
    }

    /** Returns the section node. */
    Node node() {
        return scroll;
    }

    /** Wires input and the preset list; everything is released when the disposer runs. */
    void install(SkinDisposer disposer, ObservableList<RXColorPresetGroup> presets) {
        // Filters: a labeled group sits in a titled pane, and a skinned control
        // consumes the mouse events that reach it, so they never bubble up here.
        disposer.registerEventFilter(groupsBox, MouseEvent.MOUSE_PRESSED, this::onPressed);
        disposer.registerEventFilter(groupsBox, MouseEvent.MOUSE_CLICKED, this::onClicked);
        disposer.registerEventHandler(groupsBox, KeyEvent.KEY_PRESSED, this::onKey);
        disposer.registerListener(presets, () -> rebuild(presets));
        disposer.registerDisposeTask(() -> {
            groupsBox.getChildren().clear();
            cache.clear();
            shown.clear();
        });
        rebuild(presets);
    }

    // ==================== Build ====================

    private void rebuild(List<RXColorPresetGroup> presets) {
        shown.clear();
        Map<RXColorPresetGroup, GroupNode> kept = new IdentityHashMap<>();
        for (RXColorPresetGroup group : presets) {
            if (group == null || kept.containsKey(group)) {
                continue;
            }
            GroupNode node = cache.get(group);
            if (node == null) {
                node = new GroupNode(group);
            }
            kept.put(group, node);
            shown.add(node);
        }
        cache.clear();
        cache.putAll(kept);
        List<Node> roots = new ArrayList<>(shown.size());
        for (GroupNode node : shown) {
            roots.add(node.root);
        }
        groupsBox.getChildren().setAll(roots);
        boolean any = !shown.isEmpty();
        scroll.setVisible(any);
        scroll.setManaged(any);
        refresh();
    }

    // ==================== Selection ====================

    /** Marks the swatches equal to the value on the 8-bit grid. */
    void refresh() {
        Color value = host.value();
        boolean ignoreOpacity = !host.alphaEnabled();
        for (GroupNode node : shown) {
            for (ColorSwatch swatch : node.swatches) {
                swatch.markSelected(ColorMath.sameOnGrid(swatch.getColor(), value, ignoreOpacity));
            }
        }
    }

    // ==================== Input ====================

    private void onPressed(MouseEvent event) {
        GroupNode node = groupOf(event.getTarget());
        if (node != null && event.getButton() == MouseButton.PRIMARY && swatchOf(event.getTarget()) != null) {
            // Focus first: a field being edited finishes before the pick.
            node.grid.requestFocus();
        }
    }

    private void onClicked(MouseEvent event) {
        ColorSwatch swatch = swatchOf(event.getTarget());
        GroupNode node = groupOf(event.getTarget());
        if (swatch == null || node == null || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        event.consume();
        node.moveCursor(node.swatches.indexOf(swatch), false);
        host.pick(swatch.getColor());
    }

    private void onKey(KeyEvent event) {
        GroupNode node = groupOf(event.getTarget());
        if (node == null || event.getTarget() != node.grid || node.swatches.isEmpty()) {
            return;
        }
        int last = node.swatches.size() - 1;
        int current = Math.max(0, node.cursor);
        int next;
        switch (event.getCode()) {
            case LEFT:
                next = current - 1;
                break;
            case RIGHT:
                next = current + 1;
                break;
            case UP:
                next = current - node.columns();
                break;
            case DOWN:
                next = current + node.columns();
                break;
            case HOME:
                next = 0;
                break;
            case END:
                next = last;
                break;
            case ENTER:
            case SPACE:
                event.consume();
                if (node.cursor >= 0) {
                    host.pick(node.swatches.get(node.cursor).getColor());
                }
                return;
            default:
                return;
        }
        event.consume();
        if (node.cursor < 0) {
            // The first navigation key shows the cursor where it was, or on the first swatch.
            next = current;
        }
        node.moveCursor(Math.max(0, Math.min(last, next)), true);
    }

    private GroupNode groupOf(Object target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null && node != groupsBox) {
            for (GroupNode group : shown) {
                if (group.grid == node) {
                    return group;
                }
            }
            node = node.getParent();
        }
        return null;
    }

    private static ColorSwatch swatchOf(Object target) {
        Node node = target instanceof Node ? (Node) target : null;
        while (node != null) {
            if (node instanceof ColorSwatch) {
                return (ColorSwatch) node;
            }
            node = node.getParent();
        }
        return null;
    }

    // ==================== Group node ====================

    private static final class GroupNode {

        private final FlowPane grid = new FlowPane();
        private final Node root;
        private final List<ColorSwatch> swatches = new ArrayList<>();
        /** The keyboard cursor index, or -1 while hidden. */
        private int cursor = -1;

        private GroupNode(RXColorPresetGroup group) {
            grid.getStyleClass().add("swatches");
            grid.setFocusTraversable(true);
            grid.setAccessibleRole(AccessibleRole.LIST_VIEW);
            for (Color color : group.colors()) {
                ColorSwatch swatch = new ColorSwatch("preset");
                swatch.setPrefSize(SWATCH_SIZE, SWATCH_SIZE);
                swatch.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                swatch.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                swatch.setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
                Region check = new Region();
                check.getStyleClass().add("check");
                swatch.addDecoration(check);
                swatch.showColor(color);
                swatches.add(swatch);
            }
            grid.getChildren().addAll(swatches);
            grid.focusedProperty().addListener(observable -> {
                if (!grid.isFocused()) {
                    moveCursor(cursor, false);
                    cursor = -1;
                }
            });
            String label = group.label();
            if (label == null || label.isBlank()) {
                grid.getStyleClass().add("group");
                grid.setAccessibleText("Preset colors");
                root = grid;
            } else {
                RXTitledPane pane = new RXTitledPane(label, grid);
                pane.getStyleClass().add("group");
                pane.setExpanded(group.defaultExpanded());
                grid.setAccessibleText(label);
                root = pane;
            }
        }

        private void moveCursor(int index, boolean visible) {
            for (int i = 0; i < swatches.size(); i++) {
                swatches.get(i).pseudoClassStateChanged(CURSOR, visible && i == index);
            }
            cursor = index;
        }

        /** Swatches per row: those laid out on the first row's line. */
        private int columns() {
            if (swatches.isEmpty()) {
                return 1;
            }
            double firstY = swatches.get(0).getLayoutY();
            int count = 0;
            for (ColorSwatch swatch : swatches) {
                if (swatch.getLayoutY() != firstY) {
                    break;
                }
                count++;
            }
            return Math.max(1, count);
        }
    }
}

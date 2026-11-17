package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import io.github.leewyatt.rxcontrols.SidebarTestSupport;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.host;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.label;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.layout;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.row;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@code RXSidebarSkin}: the container tree and the rows it builds
 * for the item tree, the locked width, the zero-jump icon column and the
 * per-level indent, the header / footer slots, and what happens to the
 * caller's graphic nodes on skin replacement and dispose.
 */
public class RXSidebarSkinTest {

    private static final double EPSILON = 0.01;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
        // One test hides its window; the toolkit must outlive it.
        Platform.setImplicitExit(false);
    }

    /**
     * The three lists land in their sections, in order, and follow list
     * changes at any depth.
     */
    @Test
    public void rowsMirrorTheItemTree() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem top = RXSidebarItem.of("Top");
            RXSidebarItem a = RXSidebarItem.of("A");
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            RXSidebarItem bottom = RXSidebarItem.of("Bottom");
            sidebar.getTopItems().add(top);
            sidebar.getItems().addAll(a, group);
            sidebar.getBottomItems().add(bottom);
            host(sidebar);

            VBox container = (VBox) sidebar.getChildrenUnmodifiable().get(0);
            assertEquals(List.of("header", "top", "content", "bottom", "footer"),
                    container.getChildren().stream().map(node -> node.getStyleClass().get(
                            node.getStyleClass().size() - 1)).toList());
            assertEquals(List.of("Top"), texts(container.getChildren().get(1)));
            assertEquals(List.of("A", "Group", "Child"),
                    texts(((ScrollPane) container.getChildren().get(2)).getContent()));
            assertEquals(List.of("Bottom"), texts(container.getChildren().get(3)));

            RXSidebarItem late = RXSidebarItem.of("Late");
            group.getItems().add(0, late);
            sidebar.getItems().remove(a);
            assertEquals(List.of("Group", "Late", "Child"),
                    texts(((ScrollPane) container.getChildren().get(2)).getContent()));
        });
    }

    /**
     * A group's child rows take layout space only while the group is expanded.
     */
    @Test
    public void childRowsShowOnlyWhileExpanded() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            sidebar.getItems().add(group);
            Scene scene = host(sidebar);

            Node pane = sidebar.lookup(".children");
            assertFalse(pane.isManaged());
            group.setExpanded(true);
            layout(scene);
            assertTrue(pane.isManaged());
            assertTrue(pane.getLayoutBounds().getHeight() >= row(sidebar, child).getHeight());

            sidebar.setMode(SidebarMode.MINI);
            assertFalse(pane.isManaged(), "MINI folds a top-level group away");
            assertTrue(group.isExpanded(), "without touching its expanded state");
            sidebar.setMode(SidebarMode.EXPANDED);
            assertTrue(pane.isManaged());
        });
    }

    /**
     * Hiding an item hides its row and its whole subtree.
     */
    @Test
    public void invisibleItemsAreNotShown() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            group.setExpanded(true);
            sidebar.getItems().add(group);
            host(sidebar);

            group.setVisible(false);
            assertFalse(row(sidebar, group).isManaged());
            assertFalse(sidebar.lookup(".children").isManaged());
        });
    }

    /**
     * The width is locked to the mode's width: min == pref == max.
     */
    @Test
    public void widthIsLockedInBothModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            host(sidebar);

            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.minWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.maxWidth(-1), EPSILON);

            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.minWidth(-1), EPSILON);
            assertEquals(sidebar.prefWidth(-1), sidebar.maxWidth(-1), EPSILON);
        });
    }

    /**
     * Negative, NaN, infinite and inverted widths fall back to usable ones.
     */
    @Test
    public void illegalWidthsResolveLeniently() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Scene scene = host(sidebar);

            sidebar.setMiniWidth(-50.0);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);

            sidebar.setMiniWidth(64.0);
            sidebar.setExpandedWidth(Double.NaN);
            sidebar.setMode(SidebarMode.EXPANDED);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            sidebar.setExpandedWidth(40.0);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            sidebar.setExpandedWidth(RXSidebar.DEFAULT_EXPANDED_WIDTH);
            sidebar.setMiniWidth(Double.POSITIVE_INFINITY);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);

            sidebar.setMiniWidth(RXSidebar.DEFAULT_MINI_WIDTH);
            sidebar.setExpandedWidth(Double.POSITIVE_INFINITY);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), EPSILON);
            sidebar.setMode(SidebarMode.EXPANDED);
            assertEquals(RXSidebar.DEFAULT_EXPANDED_WIDTH, sidebar.prefWidth(-1), EPSILON);

            layout(scene);
            assertFalse(Double.isNaN(sidebar.getWidth()), "the width must never be NaN");
        });
    }

    /**
     * A top-level graphic sits centred on miniWidth and does not move between
     * the modes; MINI hides the text.
     */
    @Test
    public void iconColumnDoesNotMoveBetweenModes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = icon();
            RXSidebarItem item = RXSidebarItem.of("Item", icon);
            sidebar.getItems().add(item);
            Scene scene = host(sidebar);

            double expandedX = icon.localToScene(0.0, 0.0).getX();
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH / 2.0, expandedX + icon.getWidth() / 2.0, 1.0);
            assertTrue(label(row(sidebar, item)).isVisible());

            sidebar.setMode(SidebarMode.MINI);
            layout(scene);
            assertEquals(expandedX, icon.localToScene(0.0, 0.0).getX(), EPSILON);
            assertFalse(label(row(sidebar, item)).isVisible());

            sidebar.setMiniWidth(96.0);
            layout(scene);
            assertEquals(96.0 / 2.0, icon.localToScene(icon.getWidth() / 2.0, 0.0).getX(), 1.0);
        });
    }

    /**
     * Each nesting level moves a row's content right by the indent.
     */
    @Test
    public void nestingIndentsByTheIndentProperty() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem leaf = RXSidebarItem.of("Leaf");
            RXSidebarItem inner = RXSidebarItem.group("Inner", null, leaf);
            RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner);
            outer.setExpanded(true);
            inner.setExpanded(true);
            sidebar.getItems().add(outer);
            Scene scene = host(sidebar);

            double outerX = textX(sidebar, outer);
            assertEquals(outerX + RXSidebar.DEFAULT_INDENT, textX(sidebar, inner), EPSILON);
            assertEquals(outerX + 2 * RXSidebar.DEFAULT_INDENT, textX(sidebar, leaf), EPSILON);

            sidebar.setIndent(10.0);
            layout(scene);
            assertEquals(outerX + 20.0, textX(sidebar, leaf), EPSILON);

            sidebar.setIndent(Double.NaN);
            layout(scene);
            assertEquals(outerX + 2 * RXSidebar.DEFAULT_INDENT, textX(sidebar, leaf), EPSILON);
        });
    }

    /**
     * A caption shows its text in EXPANDED and a divider line in MINI; a
     * separator is a line in both.
     */
    @Test
    public void captionsBecomeLinesInMini() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarHeader header = new RXSidebarHeader("Section");
            sidebar.getItems().addAll(header, new RXSidebarSeparator(), RXSidebarItem.of("Item"));
            host(sidebar);

            Region caption = SidebarTestSupport.node(sidebar, "caption", header.getText());
            assertTrue(caption.isManaged());
            assertEquals(1, sidebar.lookupAll(".separator").stream().filter(Node::isManaged).count());

            sidebar.setMode(SidebarMode.MINI);
            assertFalse(caption.isManaged());
            assertEquals(2, sidebar.lookupAll(".separator").stream().filter(Node::isManaged).count());
        });
    }

    /**
     * The header and footer slots hold the nodes and collapse when empty.
     */
    @Test
    public void headerAndFooterSlots() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            host(sidebar);
            Parent headerSlot = (Parent) sidebar.lookup(".header");
            Parent footerSlot = (Parent) sidebar.lookup(".footer");
            assertFalse(headerSlot.isManaged());

            Label header = new Label("H");
            Label footer = new Label("F");
            sidebar.setHeader(header);
            sidebar.setFooter(footer);
            assertEquals(List.of(header), headerSlot.getChildrenUnmodifiable());
            assertEquals(List.of(footer), footerSlot.getChildrenUnmodifiable());
            assertTrue(headerSlot.isManaged());

            sidebar.setHeader(null);
            assertTrue(headerSlot.getChildrenUnmodifiable().isEmpty());
            assertFalse(headerSlot.isManaged());
        });
    }

    /**
     * An item's id and style classes are copied to its row and kept current.
     */
    @Test
    public void idAndStyleClassesReachTheRow() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem item = RXSidebarItem.of("Item");
            item.setId("home");
            item.getStyleClass().add("accent");
            sidebar.getItems().add(item);
            host(sidebar);

            Region row = row(sidebar, item);
            assertEquals("home", row.getId());
            assertTrue(row.getStyleClass().containsAll(List.of("item", "accent")));

            item.getStyleClass().setAll("other");
            assertEquals(List.of("item", "other"), row.getStyleClass());
        });
    }

    /**
     * A replacing skin takes the graphic and trailing nodes into its own rows,
     * and disposing the replaced skin leaves them there.
     */
    @Test
    public void aReplacingSkinKeepsTheCallersNodes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = icon();
            Label count = new Label("3");
            RXSidebarItem item = RXSidebarItem.of("Item", icon);
            item.setTrailing(count);
            sidebar.getItems().add(item);
            Scene scene = host(sidebar);
            Region oldRow = row(sidebar, item);

            sidebar.setSkin(new RXSidebarSkin(sidebar));
            layout(scene);

            Region newRow = row(sidebar, item);
            assertTrue(newRow != oldRow);
            assertSame(newRow, icon.getParent());
            assertSame(newRow, count.getParent().getParent());
            assertEquals(1, sidebar.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Disposing the skin with nothing replacing it releases the caller's
     * nodes and removes the container; a second dispose is harmless.
     */
    @Test
    public void disposeReleasesTheCallersNodes() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = icon();
            Label count = new Label("3");
            RXSidebarItem item = RXSidebarItem.of("Item", icon);
            item.setTrailing(count);
            sidebar.getItems().add(item);
            host(sidebar);
            RXSidebarSkin skin = (RXSidebarSkin) sidebar.getSkin();

            sidebar.setSkin(null);
            assertNull(icon.getParent());
            assertNull(count.getParent());
            assertTrue(sidebar.getChildrenUnmodifiable().isEmpty());
            assertDoesNotThrow(skin::dispose);

            assertDoesNotThrow(() -> item.setText("Renamed"));
            assertDoesNotThrow(() -> sidebar.getItems().add(RXSidebarItem.of("More")));
        });
    }

    /**
     * An item becomes a group with its first child item and a leaf again
     * without its last.
     */
    @Test
    public void leafAndGroupConvertWithTheChildList() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem item = RXSidebarItem.of("Item");
            item.setExpanded(true);
            sidebar.getItems().add(item);
            host(sidebar);
            Node arrow = row(sidebar, item).lookup(".disclosure");
            assertNull(sidebar.lookup(".children"));
            assertFalse(arrow.isManaged());

            Region icon = icon();
            RXSidebarItem child = RXSidebarItem.of("Child", icon);
            item.getItems().add(child);
            assertTrue(sidebar.lookup(".children").isManaged());
            assertTrue(arrow.isManaged());
            assertSame(row(sidebar, child), icon.getParent());

            item.getItems().clear();
            assertNull(sidebar.lookup(".children"));
            assertFalse(arrow.isManaged());
            assertNull(icon.getParent());
        });
    }

    /**
     * Removing an item from the tree releases its graphic and stops following
     * the item.
     */
    @Test
    public void removingAnItemReleasesItsGraphic() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region icon = icon();
            RXSidebarItem item = RXSidebarItem.of("Item", icon);
            sidebar.getItems().add(item);
            host(sidebar);

            sidebar.getItems().remove(item);
            assertNull(icon.getParent());

            Region later = icon();
            item.setGraphic(later);
            assertNull(later.getParent());
        });
    }

    // ==================== Filter ====================

    /**
     * A filter leaves the matches, what is above and below them, and the
     * captions of sections with a shown item; separators hide.
     */
    @Test
    public void aFilterNarrowsTheRows() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem page = RXSidebarItem.of("RXSidebar");
            page.getKeywords().add("Navigation");
            RXSidebarItem group = RXSidebarItem.group("Controls", null, page, RXSidebarItem.of("Badge"));
            RXSidebarHeader first = new RXSidebarHeader("First");
            RXSidebarHeader second = new RXSidebarHeader("Second");
            RXSidebarSeparator separator = new RXSidebarSeparator();
            separator.getStyleClass().add("divider");
            sidebar.getItems().addAll(first, RXSidebarItem.of("Home"), separator, second, group);
            sidebar.getBottomItems().add(RXSidebarItem.of("Settings"));
            host(sidebar);

            sidebar.setFilter(RXSidebar.textFilter("navi"));
            assertEquals(List.of("Controls", "RXSidebar"), shownTexts(sidebar));
            assertFalse(SidebarTestSupport.node(sidebar, "caption", first.getText()).isVisible());
            assertTrue(SidebarTestSupport.node(sidebar, "caption", second.getText()).isVisible());
            assertFalse(sidebar.lookup(".divider").isVisible());

            sidebar.setFilter(RXSidebar.textFilter("controls"));
            assertEquals(List.of("Controls", "RXSidebar", "Badge"), shownTexts(sidebar));

            sidebar.setFilter(RXSidebar.textFilter(" "));
            assertEquals(List.of("Home", "Controls", "Settings"), shownTexts(sidebar));
            assertTrue(sidebar.lookup(".divider").isVisible());
        });
    }

    /**
     * While filtering, groups are held open without their expanded state being
     * read or written, and the user cannot fold them.
     */
    @Test
    public void aFilterHoldsGroupsOpen() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            sidebar.getItems().add(group);
            Scene scene = host(sidebar);

            sidebar.setFilter(item -> item == child);
            assertFalse(group.isExpanded());
            assertTrue(SidebarTestSupport.hasPseudoClass(row(sidebar, group), "expanded"));
            assertEquals(List.of("Group", "Child"), shownTexts(sidebar));

            SidebarTestSupport.click(row(sidebar, group));
            row(sidebar, group).requestFocus();
            SidebarTestSupport.press(scene, KeyCode.LEFT);
            assertFalse(group.isExpanded());
            assertEquals(List.of("Group", "Child"), shownTexts(sidebar));

            row(sidebar, child).requestFocus();
            sidebar.setFilter(null);
            assertEquals(List.of("Group"), shownTexts(sidebar));
            assertSame(row(sidebar, group), scene.getFocusOwner());
        });
    }

    /**
     * The filter is asked when it or the tree changes; a filtered-out selection
     * stays selected.
     */
    @Test
    public void filteringFollowsTheTreeAndKeepsTheSelection() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem alpha = RXSidebarItem.of("Alpha");
            sidebar.getItems().addAll(alpha, RXSidebarItem.of("Beta"));
            host(sidebar);
            sidebar.selectItem(alpha);

            sidebar.setFilter(RXSidebar.textFilter("b"));
            assertEquals(List.of("Beta"), shownTexts(sidebar));
            assertSame(alpha, sidebar.getSelectedItem());

            sidebar.getItems().addAll(RXSidebarItem.of("Bravo"), RXSidebarItem.of("Charlie"));
            assertEquals(List.of("Beta", "Bravo"), shownTexts(sidebar));
        });
    }

    /**
     * The placeholder replaces the scrolling list only while a filter leaves
     * none of it on display.
     */
    @Test
    public void placeholderShowsWhenTheFilterLeavesNothing() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Label placeholder = new Label("No results");
            sidebar.setPlaceholder(placeholder);
            sidebar.getItems().add(RXSidebarItem.of("Alpha"));
            sidebar.getBottomItems().add(RXSidebarItem.of("Zulu"));
            host(sidebar);
            assertNull(placeholder.getScene());

            sidebar.setFilter(RXSidebar.textFilter("z"));
            layout(sidebar.getScene());
            assertSame(sidebar.getScene(), placeholder.getScene());
            assertEquals(List.of("Zulu"), shownTexts(sidebar));

            sidebar.setFilter(RXSidebar.textFilter("a"));
            assertNull(placeholder.getScene());
            assertEquals(List.of("Alpha"), shownTexts(sidebar));

            sidebar.setFilter(RXSidebar.textFilter("z"));
            sidebar.setSkin(null);
            assertNull(placeholder.getParent());
        });
    }

    /**
     * A visible flag changing under a filter is taken into account, and the
     * placeholder stays away while the sidebar rests in MINI.
     */
    @Test
    public void filteringFollowsVisibilityAndMode() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Label placeholder = new Label("No results");
            sidebar.setPlaceholder(placeholder);
            RXSidebarHeader caption = new RXSidebarHeader("Caption");
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            sidebar.getItems().addAll(caption, group);
            host(sidebar);

            sidebar.setFilter(RXSidebar.textFilter("child"));
            assertTrue(SidebarTestSupport.node(sidebar, "caption", caption.getText()).isVisible());
            child.setVisible(false);
            assertEquals(List.of(), shownTexts(sidebar));
            assertSame(sidebar.getScene(), placeholder.getScene());

            sidebar.setMode(SidebarMode.MINI);
            assertNull(placeholder.getScene());
            child.setVisible(true);
            assertEquals(List.of("Group"), shownTexts(sidebar));
        });
    }

    /**
     * A list too long for the sidebar scrolls; the header and footer keep their
     * preferred height.
     */
    @Test
    public void aLongListDoesNotSqueezeTheHeaderAndFooter() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region header = new Region();
            header.setPrefHeight(60.0);
            Region footer = new Region();
            footer.setPrefHeight(30.0);
            sidebar.setHeader(header);
            sidebar.setFooter(footer);
            for (int i = 0; i < 40; i++) {
                sidebar.getItems().add(RXSidebarItem.of("Item " + i));
            }
            sidebar.setPrefHeight(300.0);
            host(sidebar);

            assertEquals(60.0, header.getHeight(), 0.5);
            assertEquals(30.0, footer.getHeight(), 0.5);
        });
    }

    /**
     * Rows of the scrolling list line up with the rows of the pinned bands.
     */
    @Test
    public void scrollingRowsLineUpWithPinnedRows() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem pinned = RXSidebarItem.of("Pinned");
            RXSidebarItem scrolling = RXSidebarItem.of("Scrolling");
            sidebar.getTopItems().add(pinned);
            sidebar.getItems().add(scrolling);
            host(sidebar);

            Region pinnedRow = row(sidebar, pinned);
            Region scrollingRow = row(sidebar, scrolling);
            Bounds pinnedBounds = pinnedRow.localToScene(pinnedRow.getLayoutBounds());
            Bounds scrollingBounds = scrollingRow.localToScene(scrollingRow.getLayoutBounds());
            assertEquals(pinnedBounds.getMinX(), scrollingBounds.getMinX(), EPSILON);
            assertEquals(pinnedBounds.getWidth(), scrollingBounds.getWidth(), EPSILON);
        });
    }

    /**
     * A row under the pressed pointer shows the pressed fill, not the fill of
     * the focus the press gives it.
     */
    @Test
    public void aPressedRowShowsThePressedFill() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem pressed = RXSidebarItem.action("Pressed", null, event -> { });
            RXSidebarItem focused = RXSidebarItem.action("Focused", null, event -> { });
            sidebar.getItems().addAll(pressed, focused);
            sidebar.setAnimated(false);
            Stage stage = new Stage();
            stage.setScene(new Scene(new Pane(sidebar), 400, 300));
            stage.show();
            try {
                Region focusedRow = row(sidebar, focused);
                focusedRow.requestFocus();
                layout(stage.getScene());
                Paint focusFill = focusedRow.getBackground().getFills().get(0).getFill();

                Region pressedRow = row(sidebar, pressed);
                pressedRow.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                        false, false, false, false, true, false, false, true, false, true, null));
                layout(stage.getScene());
                assertNotEquals(focusFill, pressedRow.getBackground().getFills().get(0).getFill());
            } finally {
                stage.hide();
            }
        });
    }

    /**
     * A pinned band takes room only while one of its items is on display.
     */
    @Test
    public void aPinnedBandWithNothingShownTakesNoRoom() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem pinned = RXSidebarItem.of("Pinned");
            sidebar.getTopItems().add(pinned);
            sidebar.getItems().add(RXSidebarItem.of("Page"));
            host(sidebar);

            Node top = sidebar.lookup(".top");
            assertTrue(top.isManaged());
            assertFalse(sidebar.lookup(".bottom").isManaged());

            sidebar.setFilter(RXSidebar.textFilter("page"));
            assertFalse(top.isManaged());
            sidebar.setFilter(null);
            pinned.setVisible(false);
            assertFalse(top.isManaged());
        });
    }

    // ==================== Helpers ====================

    // The texts of the rows on display, in scene-graph order.
    private static List<String> shownTexts(RXSidebar sidebar) {
        List<String> texts = new ArrayList<>();
        for (Node node : sidebar.lookupAll(".item")) {
            boolean shown = node.getScene() != null;
            for (Node current = node; shown && current != sidebar; current = current.getParent()) {
                shown = current.isVisible();
            }
            if (shown) {
                texts.add(label((Region) node).getText());
            }
        }
        return texts;
    }

    private static Region icon() {
        Region icon = new Region();
        icon.getStyleClass().add("graphic");
        return icon;
    }

    private static double textX(RXSidebar sidebar, RXSidebarItem item) {
        return label(row(sidebar, item)).localToScene(0.0, 0.0).getX();
    }

    // The texts of the rows under a section, in scene-graph order.
    private static List<String> texts(Node section) {
        List<String> texts = new ArrayList<>();
        collectTexts(section, texts);
        return texts;
    }

    private static void collectTexts(Node node, List<String> texts) {
        if (node.getStyleClass().contains("item")) {
            texts.add(label((Region) node).getText());
            return;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTexts(child, texts);
            }
        }
    }
}

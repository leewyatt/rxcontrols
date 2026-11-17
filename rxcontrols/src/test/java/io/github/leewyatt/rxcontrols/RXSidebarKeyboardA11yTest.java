package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.click;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.host;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.press;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.row;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the sidebar's interaction: activation by click and by key, arrow
 * roving over the rows on display, the single Tab stop, and what the rows
 * report to assistive technology.
 *
 * <p>Focus is asserted through the scene's focus owner rather than
 * {@code Node.isFocused}, which is false whenever the window is not focused and
 * therefore always false in these headless runs.</p>
 */
public class RXSidebarKeyboardA11yTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
    }

    // ==================== Activation ====================

    /**
     * Activating a page selects it and fires its action; an action only fires;
     * a group only toggles.
     */
    @Test
    public void activationFollowsTheKindOfItem() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            List<String> fired = new ArrayList<>();
            RXSidebarItem page = RXSidebarItem.of("Page");
            page.setOnAction(event -> fired.add("page:" + (event.getSource() == page)));
            RXSidebarItem action = RXSidebarItem.action("Action", null, event -> fired.add("action"));
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            group.setOnAction(event -> fired.add("group"));
            sidebar.getItems().addAll(page, action, group);
            Scene scene = host(sidebar);

            click(row(sidebar, page));
            assertSame(page, sidebar.getSelectedItem());
            assertSame(row(sidebar, page), scene.getFocusOwner());

            click(row(sidebar, action));
            assertSame(page, sidebar.getSelectedItem());

            click(row(sidebar, group));
            assertTrue(group.isExpanded());
            press(scene, KeyCode.ENTER);
            assertFalse(group.isExpanded());
            assertSame(page, sidebar.getSelectedItem());

            assertEquals(List.of("page:true", "action"), fired);
        });
    }

    /**
     * Enter and Space activate the focused row.
     */
    @Test
    public void enterAndSpaceActivate() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem a = RXSidebarItem.of("A");
            RXSidebarItem b = RXSidebarItem.of("B");
            sidebar.getItems().addAll(a, b);
            Scene scene = host(sidebar);

            row(sidebar, a).requestFocus();
            press(scene, KeyCode.SPACE);
            assertSame(a, sidebar.getSelectedItem());
            press(scene, KeyCode.DOWN);
            press(scene, KeyCode.ENTER);
            assertSame(b, sidebar.getSelectedItem());
        });
    }

    /**
     * A disabled item, and every item under a disabled group, cannot be
     * activated.
     */
    @Test
    public void disabledItemsDoNotActivate() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            group.setExpanded(true);
            group.setDisable(true);
            sidebar.getItems().add(group);
            host(sidebar);

            assertTrue(row(sidebar, child).isDisabled());
            click(row(sidebar, child));
            click(row(sidebar, group));
            assertNull(sidebar.getSelectedItem());
            assertTrue(group.isExpanded());
        });
    }

    /**
     * A group whose expanded property is bound is not toggled by the user.
     */
    @Test
    public void aBoundGroupIsNotToggled() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            RXSidebarItem driver = new RXSidebarItem();
            group.expandedProperty().bind(driver.expandedProperty());
            sidebar.getItems().add(group);
            host(sidebar);

            click(row(sidebar, group));
            assertFalse(group.isExpanded());
        });
    }

    // ==================== Roving ====================

    /**
     * Up / Down move over the rows on display across the three lists and wrap;
     * Home / End jump to the ends. Rows of a collapsed group, hidden and
     * disabled rows, captions and separators are skipped.
     */
    @Test
    public void arrowsRoveOverTheRowsOnDisplay() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem top = RXSidebarItem.of("Top");
            RXSidebarItem open = RXSidebarItem.group("Open", null, RXSidebarItem.of("Shown"));
            open.setExpanded(true);
            RXSidebarItem closed = RXSidebarItem.group("Closed", null, RXSidebarItem.of("Folded"));
            RXSidebarItem disabled = RXSidebarItem.of("Disabled");
            disabled.setDisable(true);
            RXSidebarItem hidden = RXSidebarItem.of("Hidden");
            hidden.setVisible(false);
            RXSidebarItem bottom = RXSidebarItem.of("Bottom");
            sidebar.getTopItems().add(top);
            sidebar.getItems().addAll(new RXSidebarHeader("Caption"), open, new RXSidebarSeparator(),
                    closed, disabled, hidden);
            sidebar.getBottomItems().add(bottom);
            Scene scene = host(sidebar);

            row(sidebar, top).requestFocus();
            List<String> visited = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                press(scene, KeyCode.DOWN);
                visited.add(focusedText(scene));
            }
            assertEquals(List.of("Open", "Shown", "Closed", "Bottom", "Top", "Open"), visited);

            press(scene, KeyCode.UP);
            press(scene, KeyCode.UP);
            assertEquals("Bottom", focusedText(scene));
            press(scene, KeyCode.HOME);
            assertEquals("Top", focusedText(scene));
            press(scene, KeyCode.END);
            assertEquals("Bottom", focusedText(scene));
        });
    }

    /**
     * Right opens a closed group and then moves into it; Left moves to the
     * parent and then closes it. On a leaf Right does nothing.
     */
    @Test
    public void leftAndRightWalkTheTree() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem child = RXSidebarItem.of("Child");
            RXSidebarItem group = RXSidebarItem.group("Group", null, child);
            sidebar.getItems().addAll(group, RXSidebarItem.of("After"));
            Scene scene = host(sidebar);

            row(sidebar, group).requestFocus();
            press(scene, KeyCode.RIGHT);
            assertTrue(group.isExpanded());
            assertEquals("Group", focusedText(scene));
            press(scene, KeyCode.RIGHT);
            assertEquals("Child", focusedText(scene));
            press(scene, KeyCode.RIGHT);
            assertEquals("Child", focusedText(scene));

            press(scene, KeyCode.LEFT);
            assertEquals("Group", focusedText(scene));
            assertTrue(group.isExpanded());
            press(scene, KeyCode.LEFT);
            assertFalse(group.isExpanded());
            press(scene, KeyCode.LEFT);
            assertEquals("Group", focusedText(scene));
        });
    }

    /**
     * Right leaves a bound group and a top-level group in MINI as they are.
     */
    @Test
    public void rightDoesNotOpenWhatItMayNotWrite() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem bound = RXSidebarItem.group("Bound", null, RXSidebarItem.of("A"));
            bound.expandedProperty().bind(new SimpleBooleanProperty(false));
            RXSidebarItem free = RXSidebarItem.group("Free", null, RXSidebarItem.of("B"));
            sidebar.getItems().addAll(bound, free);
            Scene scene = host(sidebar);

            row(sidebar, bound).requestFocus();
            press(scene, KeyCode.RIGHT);
            assertFalse(bound.isExpanded());

            sidebar.setMode(SidebarMode.MINI);
            row(sidebar, free).requestFocus();
            press(scene, KeyCode.RIGHT);
            assertFalse(free.isExpanded());
        });
    }

    /**
     * Under a right-to-left orientation Left and Right swap.
     */
    @Test
    public void leftAndRightSwapInRightToLeft() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            sidebar.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            sidebar.getItems().add(group);
            Scene scene = host(sidebar);

            row(sidebar, group).requestFocus();
            press(scene, KeyCode.LEFT);
            assertTrue(group.isExpanded());
            press(scene, KeyCode.RIGHT);
            assertFalse(group.isExpanded());
        });
    }

    /**
     * Folding a subtree away from under the focus — by collapsing the group or
     * by switching to MINI — moves focus to the group's row.
     */
    @Test
    public void collapsingOverTheFocusMovesItToTheGroup() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem leaf = RXSidebarItem.of("Leaf");
            RXSidebarItem inner = RXSidebarItem.group("Inner", null, leaf);
            RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner);
            outer.setExpanded(true);
            inner.setExpanded(true);
            sidebar.getItems().add(outer);
            Scene scene = host(sidebar);

            row(sidebar, leaf).requestFocus();
            inner.setExpanded(false);
            assertEquals("Inner", focusedText(scene));

            inner.setExpanded(true);
            row(sidebar, leaf).requestFocus();
            sidebar.setMode(SidebarMode.MINI);
            assertEquals("Outer", focusedText(scene));
        });
    }

    /**
     * Every group above the selection carries :child-selected, open or not.
     */
    @Test
    public void groupsAboveTheSelectionAreMarked() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem leaf = RXSidebarItem.of("Leaf");
            RXSidebarItem inner = RXSidebarItem.group("Inner", null, leaf);
            RXSidebarItem outer = RXSidebarItem.group("Outer", null, inner);
            RXSidebarItem other = RXSidebarItem.of("Other");
            sidebar.getItems().addAll(outer, other);
            host(sidebar);

            sidebar.selectItem(leaf);
            assertTrue(hasPseudoClass(row(sidebar, outer), "child-selected"));
            assertTrue(hasPseudoClass(row(sidebar, inner), "child-selected"));
            assertFalse(hasPseudoClass(row(sidebar, leaf), "child-selected"));

            outer.setExpanded(false);
            assertTrue(hasPseudoClass(row(sidebar, outer), "child-selected"));
            assertTrue(hasPseudoClass(row(sidebar, outer), "collapsed"));

            sidebar.selectItem(other);
            assertFalse(hasPseudoClass(row(sidebar, outer), "child-selected"));
        });
    }

    /**
     * Arrow keys pressed in header content are left to that content.
     */
    @Test
    public void headerContentKeepsItsArrowKeys() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Region headerContent = new Region();
            headerContent.setFocusTraversable(true);
            sidebar.setHeader(headerContent);
            sidebar.getItems().add(RXSidebarItem.of("A"));
            Scene scene = host(sidebar);
            List<KeyCode> received = new ArrayList<>();
            headerContent.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                received.add(event.getCode());
                event.consume();
            });

            headerContent.requestFocus();
            press(scene, KeyCode.DOWN);
            assertEquals(List.of(KeyCode.DOWN), received);
            assertSame(headerContent, scene.getFocusOwner());
        });
    }

    // ==================== Tab stop ====================

    /**
     * Exactly one row is a Tab stop: the focused one, else the selected one,
     * else the first.
     */
    @Test
    public void singleTabStopFollowsFocusThenSelection() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem a = RXSidebarItem.of("A");
            RXSidebarItem b = RXSidebarItem.of("B");
            RXSidebarItem c = RXSidebarItem.of("C");
            sidebar.getItems().addAll(a, b, c);
            Button outside = new Button("Outside");
            Scene scene = host(sidebar, outside);
            assertEquals("A", tabStop(sidebar, a, b, c));

            sidebar.selectItem(b);
            assertEquals("B", tabStop(sidebar, a, b, c));

            row(sidebar, c).requestFocus();
            assertEquals("C", tabStop(sidebar, a, b, c));
            sidebar.selectItem(a);
            assertEquals("C", tabStop(sidebar, a, b, c), "selection must not strand the focused row");

            outside.requestFocus();
            assertEquals("A", tabStop(sidebar, a, b, c));

            sidebar.getItems().remove(a);
            assertEquals("B", tabStop(sidebar, b, c));
            assertSame(outside, scene.getFocusOwner());
        });
    }

    // ==================== Accessibility ====================

    /**
     * A row reports its text, selection and, for a group, whether it is
     * expanded.
     */
    @Test
    public void rowsReportTheirStateToAssistiveTechnology() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem page = RXSidebarItem.of("Page");
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            sidebar.getItems().addAll(page, group);
            host(sidebar);
            Region pageRow = row(sidebar, page);
            Region groupRow = row(sidebar, group);

            assertSame(AccessibleRole.RADIO_BUTTON, pageRow.getAccessibleRole());
            assertSame(AccessibleRole.BUTTON, groupRow.getAccessibleRole());
            page.setSelectable(false);
            assertSame(AccessibleRole.BUTTON, pageRow.getAccessibleRole());
            page.setSelectable(true);
            assertEquals("Page", pageRow.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            page.setAccessibleText("Home page");
            assertEquals("Home page", pageRow.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            assertEquals(false, pageRow.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            sidebar.selectItem(page);
            assertEquals(true, pageRow.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertTrue(hasPseudoClass(pageRow, "selected"));

            assertEquals(false, groupRow.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            group.setExpanded(true);
            assertEquals(true, groupRow.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertTrue(hasPseudoClass(groupRow, "expanded"));
        });
    }

    /**
     * In MINI a top-level item without child items shows its text as a
     * tooltip, unless it has a tooltip of its own.
     */
    @Test
    public void miniLendsTheTextAsATooltip() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            RXSidebarItem plain = RXSidebarItem.of("Plain");
            RXSidebarItem own = RXSidebarItem.of("Own");
            own.setTooltip(new Tooltip("Mine"));
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            sidebar.getItems().addAll(plain, own, group);
            host(sidebar);

            assertNull(help(sidebar, plain));
            assertEquals("Mine", help(sidebar, own));

            sidebar.setMode(SidebarMode.MINI);
            assertEquals("Plain", help(sidebar, plain));
            assertEquals("Mine", help(sidebar, own));
            assertNull(help(sidebar, group));
            plain.setText("Renamed");
            assertEquals("Renamed", help(sidebar, plain));

            own.setTooltip(null);
            assertEquals("Own", help(sidebar, own));

            sidebar.setMode(SidebarMode.EXPANDED);
            assertNull(help(sidebar, plain));
        });
    }

    // ==================== Helpers ====================

    private static String focusedText(Scene scene) {
        return SidebarTestSupport.label((Region) scene.getFocusOwner()).getText();
    }

    private static String tabStop(RXSidebar sidebar, RXSidebarItem... items) {
        List<String> stops = new ArrayList<>();
        for (RXSidebarItem item : items) {
            if (row(sidebar, item).isFocusTraversable()) {
                stops.add(item.getText());
            }
        }
        assertEquals(1, stops.size(), "exactly one Tab stop, got " + stops);
        return stops.get(0);
    }

    private static Object help(RXSidebar sidebar, RXSidebarItem item) {
        return row(sidebar, item).queryAccessibleAttribute(AccessibleAttribute.HELP);
    }
}

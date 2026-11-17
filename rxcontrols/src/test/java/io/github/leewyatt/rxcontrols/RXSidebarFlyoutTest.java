package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.row;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the flyouts of {@link RXSidebar} in MINI: which rows they hold and
 * when, driven through the keyboard and the model in a showing window.
 */
public class RXSidebarFlyoutTest {

    private Stage stage;
    private RXSidebar sidebar;
    private RXSidebarItem leaf;
    private RXSidebarItem inner;
    private RXSidebarItem page;
    private RXSidebarItem outer;
    // Looked up before any flyout opens: a row in a flyout is no longer below
    // the sidebar.
    private Region outerRow;
    private Region pageRow;
    private Region innerRow;
    private Region leafRow;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
        // The tests hide their window; the toolkit must outlive the last one.
        Platform.setImplicitExit(false);
    }

    @AfterEach
    public void hideStage() throws Exception {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
            }
        });
    }

    /**
     * The flyout trigger defaults to hover.
     */
    @Test
    public void triggerDefaultsToHover() throws Exception {
        runOnFx(() -> assertSame(FlyoutTrigger.HOVER, new RXSidebar().getFlyoutTrigger()));
    }

    /**
     * Activating a top-level group in MINI moves the rows of its child items
     * into a flyout and focuses the first; Escape brings them back and returns
     * focus to the group.
     */
    @Test
    public void activatingAGroupOpensItsFlyout() throws Exception {
        runOnFx(() -> {
            show();
            assertSame(sidebar.getScene(), pageRow.getScene());

            outerRow.requestFocus();
            press(outerRow, KeyCode.ENTER);
            assertNotSame(sidebar.getScene(), pageRow.getScene());
            assertTrue(hasPseudoClass(outerRow, "showing"));
            assertSame(pageRow, pageRow.getScene().getFocusOwner());
            SidebarTestSupport.layout(pageRow.getScene());
            assertEquals(12.0, SidebarTestSupport.label(pageRow).getLayoutX(), 0.5); // no indent, no icon inset
            // The sidebar's row rules and color tokens reach into the flyout.
            assertEquals(40.0, pageRow.getHeight(), 0.5);
            Region panel = (Region) pageRow.getScene().getRoot().lookup(".rx-sidebar-flyout");
            assertEquals(1, panel.getBackground().getFills().size());

            press(pageRow, KeyCode.ESCAPE);
            assertSame(sidebar.getScene(), pageRow.getScene());
            assertFalse(hasPseudoClass(outerRow, "showing"));
            assertSame(outerRow, sidebar.getScene().getFocusOwner());
        });
    }

    /**
     * Right on a group in a flyout opens the next level; Left closes it again
     * and returns focus to the group's row.
     */
    @Test
    public void flyoutsOpenLevelByLevel() throws Exception {
        runOnFx(() -> {
            show();
            outerRow.requestFocus();
            press(outerRow, KeyCode.RIGHT);
            press(pageRow, KeyCode.DOWN);
            assertSame(innerRow, innerRow.getScene().getFocusOwner());

            press(innerRow, KeyCode.RIGHT);
            assertNotSame(innerRow.getScene(), leafRow.getScene());
            assertNotSame(sidebar.getScene(), leafRow.getScene());
            assertSame(leafRow, leafRow.getScene().getFocusOwner());

            press(leafRow, KeyCode.LEFT);
            assertSame(innerRow.getScene(), leafRow.getScene());
            assertNotSame(sidebar.getScene(), innerRow.getScene());
            assertSame(innerRow, innerRow.getScene().getFocusOwner());
        });
    }

    /**
     * Activating a leaf in a flyout selects it and closes the whole chain.
     */
    @Test
    public void activatingALeafClosesTheChain() throws Exception {
        runOnFx(() -> {
            show();
            outerRow.requestFocus();
            press(outerRow, KeyCode.ENTER);
            innerRow.requestFocus();
            press(innerRow, KeyCode.ENTER);
            press(leafRow, KeyCode.ENTER);

            assertSame(leaf, sidebar.getSelectedItem());
            assertSame(sidebar.getScene(), leafRow.getScene());
            assertSame(sidebar.getScene(), innerRow.getScene());
            assertTrue(hasPseudoClass(outerRow, "child-selected"));
        });
    }

    /**
     * Whatever takes the group away from the flyout brings the rows back:
     * leaving MINI, the group being disabled, hidden or removed, and the skin
     * going away.
     */
    @Test
    public void flyoutClosesWhenItsGroupCannotOwnIt() throws Exception {
        runOnFx(() -> {
            show();

            openOuter();
            sidebar.setMode(SidebarMode.EXPANDED);
            assertSame(sidebar.getScene(), pageRow.getScene());
            sidebar.setMode(SidebarMode.MINI);

            openOuter();
            outer.setDisable(true);
            assertSame(sidebar.getScene(), pageRow.getScene());
            outer.setDisable(false);

            openOuter();
            outer.setVisible(false);
            assertSame(sidebar.getScene(), pageRow.getScene());
            outer.setVisible(true);

            openOuter();
            sidebar.setSkin(null);
            assertNull(pageRow.getScene());
        });
    }

    /**
     * Hiding the window with a chain of flyouts open brings every row back.
     */
    @Test
    public void hidingTheWindowClosesTheChain() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            innerRow.requestFocus();
            press(innerRow, KeyCode.ENTER);
            assertNotSame(innerRow.getScene(), leafRow.getScene());

            stage.hide();
            assertSame(sidebar.getScene(), pageRow.getScene());
            assertSame(sidebar.getScene(), innerRow.getScene());
            assertSame(sidebar.getScene(), leafRow.getScene());
            assertFalse(hasPseudoClass(outerRow, "showing"));
        });
    }

    /**
     * Emptying a group whose flyout has a further level open closes the chain
     * without an error.
     */
    @Test
    public void emptyingAGroupClosesItsChain() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            innerRow.requestFocus();
            press(innerRow, KeyCode.ENTER);
            assertNotSame(innerRow.getScene(), leafRow.getScene());

            outer.getItems().clear();
            assertNull(pageRow.getScene());
            assertNull(leafRow.getScene());
            assertFalse(hasPseudoClass(outerRow, "showing"));
        });
    }

    /**
     * Disabling the sidebar closes its flyouts, and a flyout is as wide as its
     * content asks for rather than as the row it hangs off.
     */
    @Test
    public void flyoutFollowsDisableAndItsOwnWidth() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            innerRow.requestFocus();
            press(innerRow, KeyCode.ENTER);
            Region first = (Region) pageRow.getScene().getRoot().lookup(".rx-sidebar-flyout");
            Region second = (Region) leafRow.getScene().getRoot().lookup(".rx-sidebar-flyout");
            SidebarTestSupport.layout(first.getScene());
            SidebarTestSupport.layout(second.getScene());
            assertEquals(first.prefWidth(-1), first.getWidth(), 0.5);
            assertEquals(second.prefWidth(-1), second.getWidth(), 0.5);

            sidebar.setDisable(true);
            assertSame(sidebar.getScene(), pageRow.getScene());
            assertSame(sidebar.getScene(), leafRow.getScene());
        });
    }

    /**
     * Child items added while the flyout is open appear in it.
     */
    @Test
    public void anOpenFlyoutFollowsTheChildList() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            Scene flyoutScene = pageRow.getScene();
            RXSidebarItem added = RXSidebarItem.of("Added");
            outer.getItems().add(added);
            assertSame(flyoutScene, findRow(flyoutScene, "Added").getScene());

            sidebar.getItems().remove(outer);
            assertNull(rowIn(flyoutScene, "Added"));
        });
    }

    // ==================== Helpers ====================

    private void show() {
        sidebar = new RXSidebar();
        sidebar.setAnimated(false);
        leaf = RXSidebarItem.of("Leaf");
        inner = RXSidebarItem.group("Inner", null, leaf);
        page = RXSidebarItem.of("Page");
        outer = RXSidebarItem.group("Outer", null, page, inner);
        sidebar.getItems().addAll(outer, RXSidebarItem.of("Other"));
        sidebar.setMode(SidebarMode.MINI);
        stage = new Stage();
        stage.setScene(new Scene(new Pane(sidebar), 400, 300));
        stage.show();
        SidebarTestSupport.layout(stage.getScene());
        outerRow = row(sidebar, outer);
        pageRow = row(sidebar, page);
        innerRow = row(sidebar, inner);
        leafRow = row(sidebar, leaf);
    }

    private void openOuter() {
        outerRow.requestFocus();
        press(outerRow, KeyCode.ENTER);
        assertNotSame(sidebar.getScene(), pageRow.getScene());
    }

    private static void press(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static Node findRow(Scene scene, String text) {
        Node found = rowIn(scene, text);
        assertTrue(found != null, "no row \"" + text + "\" in the scene");
        return found;
    }

    private static Node rowIn(Scene scene, String text) {
        for (Node node : scene.getRoot().lookupAll(".item")) {
            if (text.equals(SidebarTestSupport.label((Region) node).getText())) {
                return node;
            }
        }
        return null;
    }
}

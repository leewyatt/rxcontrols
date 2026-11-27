package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.Mode;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.hold;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.press;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.release;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.row;
import static io.github.leewyatt.rxcontrols.SidebarTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the flyouts of {@link RXSidebar} in MINI: which rows they hold and
 * when, driven through the keyboard, the pointer under both triggers and the
 * model, in a showing window.
 */
public class RXSidebarFlyoutTest {

    // The delay before a hovered top-level group opens its flyout.
    private static final long HOVER_DELAY_MILLIS = 100;

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
            // No indent and no icon column: the text starts at the row's inset.
            assertEquals(pageRow.snappedLeftInset(), SidebarTestSupport.label(pageRow).getLayoutX(), 0.5);
            // The sidebar's row rules and color tokens reach into the flyout.
            assertTrue(pageRow.getPadding().getTop() > 0.0);
            Region card = (Region) pageRow.getScene().getRoot().lookup(".rx-popup");
            assertNotNull(card.getBackground());

            // Focus in the window behind is somewhere else by now.
            SidebarTestSupport.node(sidebar, "item", "Other").requestFocus();
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

            // Focus in the flyout behind is somewhere else by now.
            pageRow.requestFocus();
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
            sidebar.setMode(Mode.EXPANDED);
            assertSame(sidebar.getScene(), pageRow.getScene());
            sidebar.setMode(Mode.MINI);

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

    /**
     * Hiding the last child item a group has on display closes its flyout.
     */
    @Test
    public void aGroupLeftWithNothingToShowClosesItsFlyout() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            page.setVisible(false);
            assertNotSame(sidebar.getScene(), innerRow.getScene());

            inner.setVisible(false);
            assertSame(sidebar.getScene(), innerRow.getScene());
            assertFalse(hasPseudoClass(outerRow, "showing"));
        });
    }

    /**
     * A held Enter opens the flyout and stops there: the repeated presses do
     * not activate the row that took focus.
     */
    @Test
    public void aHeldKeyDoesNotActivateTheRowItFocused() throws Exception {
        runOnFx(() -> {
            show();
            outerRow.requestFocus();
            hold(outerRow, KeyCode.ENTER);
            assertSame(pageRow, pageRow.getScene().getFocusOwner());

            hold(pageRow, KeyCode.ENTER);
            assertNull(sidebar.getSelectedItem());
            assertNotSame(sidebar.getScene(), pageRow.getScene());

            release(pageRow, KeyCode.ENTER);
            hold(pageRow, KeyCode.ENTER);
            assertSame(page, sidebar.getSelectedItem());
        });
    }

    /**
     * Escape closes a flyout opened with the pointer, which holds no focus,
     * and is consumed there.
     */
    @Test
    public void escapeStopsAtAFlyoutOpenedWithThePointer() throws Exception {
        runOnFx(() -> {
            show();
            sidebar.setFlyoutTrigger(FlyoutTrigger.CLICK);
            SidebarTestSupport.click(outerRow);
            Scene flyoutScene = pageRow.getScene();
            assertNotSame(sidebar.getScene(), flyoutScene);
            assertNull(flyoutScene.getFocusOwner());
            boolean[] passedOn = new boolean[1];
            flyoutScene.getWindow().addEventHandler(KeyEvent.KEY_PRESSED, event -> passedOn[0] = true);

            Event.fireEvent(flyoutScene, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false));
            assertSame(sidebar.getScene(), pageRow.getScene());
            assertFalse(passedOn[0]);
        });
    }

    /**
     * A section caption in a flyout starts where the text of its rows starts.
     */
    @Test
    public void aCaptionInAFlyoutLinesUpWithTheRows() throws Exception {
        runOnFx(() -> {
            show();
            outer.getItems().add(0, new RXSidebarCaption("Section"));
            openOuter();
            Scene flyoutScene = pageRow.getScene();
            SidebarTestSupport.layout(flyoutScene);
            Node caption = flyoutScene.getRoot().lookup(".caption > .label");
            assertEquals(SidebarTestSupport.label(pageRow).localToScene(0.0, 0.0).getX(),
                    caption.localToScene(0.0, 0.0).getX(), 0.5);
        });
    }

    /**
     * Under the click trigger a click opens the group's flyout without taking
     * focus, a click on another group moves the flyout there, and a second
     * click closes it.
     */
    @Test
    public void clickingOpensAndClosesUnderTheClickTrigger() throws Exception {
        runOnFx(() -> {
            show();
            RXSidebarItem child = RXSidebarItem.of("Second child");
            RXSidebarItem second = RXSidebarItem.group("Second", null, child);
            sidebar.getItems().add(second);
            sidebar.setFlyoutTrigger(FlyoutTrigger.CLICK);
            SidebarTestSupport.layout(stage.getScene());
            Region secondRow = row(sidebar, second);
            Region childRow = row(sidebar, child);

            SidebarTestSupport.click(outerRow);
            assertNotSame(sidebar.getScene(), pageRow.getScene());
            assertNull(pageRow.getScene().getFocusOwner());

            SidebarTestSupport.click(secondRow);
            assertSame(sidebar.getScene(), pageRow.getScene());
            assertNotSame(sidebar.getScene(), childRow.getScene());

            SidebarTestSupport.click(secondRow);
            assertSame(sidebar.getScene(), childRow.getScene());
        });
    }

    /**
     * Under the hover trigger a click on a group whose flyout is open leaves
     * it open.
     */
    @Test
    public void clickingKeepsTheFlyoutUnderTheHoverTrigger() throws Exception {
        runOnFx(() -> {
            show();
            SidebarTestSupport.click(outerRow);
            assertNotSame(sidebar.getScene(), pageRow.getScene());

            SidebarTestSupport.click(outerRow);
            assertNotSame(sidebar.getScene(), pageRow.getScene());
        });
    }

    /**
     * Under the hover trigger, also when the trigger is null, the pointer
     * resting on a top-level group opens its flyout and the pointer leaving
     * closes it, each after a delay.
     */
    @Test
    public void hoveringOpensAndClosesTheFirstLevel() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        runOnFx(() -> {
            show();
            sidebar.setFlyoutTrigger(null);
            pageRow.sceneProperty().addListener((obs, old, scene) -> {
                if (scene != null && scene != sidebar.getScene()) {
                    opened.countDown();
                } else if (scene == sidebar.getScene() && opened.getCount() == 0) {
                    closed.countDown();
                }
            });
            hover(outerRow, MouseEvent.MOUSE_ENTERED);
            assertSame(sidebar.getScene(), pageRow.getScene());
        });
        assertTrue(opened.await(3, TimeUnit.SECONDS), "the flyout opens after the hover delay");

        runOnFx(() -> hover(outerRow, MouseEvent.MOUSE_EXITED));
        assertTrue(closed.await(3, TimeUnit.SECONDS), "the flyout closes after the hover delay");
    }

    /**
     * Under the click trigger the pointer resting on a group opens nothing.
     */
    @Test
    public void hoveringOpensNothingUnderTheClickTrigger() throws Exception {
        runOnFx(() -> {
            show();
            sidebar.setFlyoutTrigger(FlyoutTrigger.CLICK);
            hover(outerRow, MouseEvent.MOUSE_ENTERED);
        });
        Thread.sleep(3 * HOVER_DELAY_MILLIS);
        runOnFx(() -> assertSame(sidebar.getScene(), pageRow.getScene()));
    }

    /**
     * Inside a flyout the pointer opens the next level at once when it enters
     * a group, and closes that level when it enters another row.
     */
    @Test
    public void hoveringInsideAFlyoutFollowsThePointer() throws Exception {
        runOnFx(() -> {
            show();
            openOuter();
            hover(innerRow, MouseEvent.MOUSE_ENTERED);
            assertNotSame(innerRow.getScene(), leafRow.getScene());
            assertNotSame(sidebar.getScene(), leafRow.getScene());

            hover(pageRow, MouseEvent.MOUSE_ENTERED);
            assertSame(innerRow.getScene(), leafRow.getScene());
            assertNotSame(sidebar.getScene(), pageRow.getScene());
        });
    }

    // ==================== Helpers ====================

    private static void hover(Node target, EventType<MouseEvent> type) {
        target.fireEvent(new MouseEvent(type, 5, 5, 5, 5, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, true, false, false, null));
    }

    private void show() {
        sidebar = new RXSidebar();
        sidebar.setAnimated(false);
        leaf = RXSidebarItem.of("Leaf");
        inner = RXSidebarItem.group("Inner", null, leaf);
        page = RXSidebarItem.of("Page");
        outer = RXSidebarItem.group("Outer", null, page, inner);
        sidebar.getItems().addAll(outer, RXSidebarItem.of("Other"));
        sidebar.setMode(Mode.MINI);
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

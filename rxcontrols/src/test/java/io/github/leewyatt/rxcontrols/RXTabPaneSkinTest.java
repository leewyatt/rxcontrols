package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTabPaneSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless skin tests for {@link RXTabPaneSkin}: selected/first/last pseudo-classes,
 * cell mirroring of {@code id/style/styleClass}, indicator geometry targets, content
 * detach, the no-selection render, keyboard navigation (automatic + manual), and
 * accessibility roles/attributes. Visual concerns (ripple, focus-ring look, slide
 * animation frames) are left to real-machine verification.
 */
public class RXTabPaneSkinTest {

    private static final double EPSILON = 0.5;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass FIRST = PseudoClass.getPseudoClass("first");
    private static final PseudoClass LAST = PseudoClass.getPseudoClass("last");

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Skin & pseudo-classes ====================

    @Test
    public void skinTypeAndLayoutDoNotThrow() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            assertInstanceOf(RXTabPaneSkin.class, pane.getSkin());
        });
    }

    @Test
    public void selectedPseudoClassTracksSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(1);
            pane.layout();
            assertFalse(cellAt(pane, 0).getPseudoClassStates().contains(SELECTED));
            assertTrue(cellAt(pane, 1).getPseudoClassStates().contains(SELECTED));
        });
    }

    @Test
    public void firstAndLastPseudoClasses() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            assertTrue(cellAt(pane, 0).getPseudoClassStates().contains(FIRST));
            assertTrue(cellAt(pane, 2).getPseudoClassStates().contains(LAST));
            assertFalse(cellAt(pane, 1).getPseudoClassStates().contains(FIRST));
            assertFalse(cellAt(pane, 1).getPseudoClassStates().contains(LAST));
        });
    }

    // ==================== Cell mirroring ====================

    @Test
    public void cellMirrorsStyleClass() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = laidOut(new RXTabPane(a, tab("B")));
            a.getStyleClass().add("highlight");
            Node cell = cellAt(pane, 0);
            assertTrue(cell.getStyleClass().contains("highlight"));
            // The structural class is preserved alongside the mirrored user class.
            assertTrue(cell.getStyleClass().contains("tab"));
        });
    }

    @Test
    public void cellMirrorsIdAndStyle() throws Exception {
        runOnFx(() -> {
            RXTab a = tab("A");
            RXTabPane pane = laidOut(new RXTabPane(a, tab("B")));
            a.setId("first-tab");
            a.setStyle("-fx-opacity: 0.5;");
            Node cell = cellAt(pane, 0);
            assertEquals("first-tab", cell.getId());
            assertTrue(cell.getStyle().contains("-fx-opacity"));
        });
    }

    // ==================== Indicator geometry ====================

    @Test
    public void indicatorVisibleAtSelectedCellSnapped() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setAnimated(false);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            pane.layout();

            Region indicator = (Region) pane.lookup(".indicator");
            assertNotNull(indicator);
            assertTrue(indicator.isVisible());
            Node selectedCell = cellAt(pane, 1);
            // Non-animated: the indicator snaps onto the selected cell's geometry.
            assertEquals(selectedCell.getLayoutX(), indicator.getTranslateX(), EPSILON);
            assertEquals(selectedCell.getLayoutBounds().getWidth(), indicator.getWidth(), EPSILON);
        });
    }

    @Test
    public void indicatorHiddenWhenNoSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            pane.getSelectionModel().clearSelection();
            pane.layout();
            Region indicator = (Region) pane.lookup(".indicator");
            assertFalse(indicator.isVisible());
        });
    }

    // ==================== Content detach ====================

    @Test
    public void onlySelectedContentIsAttached() throws Exception {
        runOnFx(() -> {
            Label contentA = new Label("page A");
            Label contentB = new Label("page B");
            RXTabPane pane = laidOut(new RXTabPane(RXTab.of("A", contentA), RXTab.of("B", contentB)));
            Parent content = (Parent) pane.lookup(".content");
            assertTrue(content.getChildrenUnmodifiable().contains(contentA));
            assertFalse(content.getChildrenUnmodifiable().contains(contentB));

            pane.getSelectionModel().select(1);
            pane.layout();
            assertFalse(content.getChildrenUnmodifiable().contains(contentA));
            assertTrue(content.getChildrenUnmodifiable().contains(contentB));
        });
    }

    @Test
    public void contentDetachedWhenNoSelection() throws Exception {
        runOnFx(() -> {
            Label contentA = new Label("page A");
            RXTabPane pane = laidOut(new RXTabPane(RXTab.of("A", contentA)));
            Parent content = (Parent) pane.lookup(".content");
            assertTrue(content.getChildrenUnmodifiable().contains(contentA));

            pane.getSelectionModel().clearSelection();
            pane.layout();
            assertFalse(content.getChildrenUnmodifiable().contains(contentA));
        });
    }

    // ==================== Keyboard (automatic) ====================

    @Test
    public void arrowRightSelectsNext() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.RIGHT);
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    @Test
    public void arrowRightWrapsToFirst() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(2);
            press(pane, KeyCode.RIGHT);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    @Test
    public void arrowLeftWrapsToLast() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.LEFT);
            assertEquals(2, pane.getSelectedIndex());
        });
    }

    @Test
    public void arrowSkipsDisabled() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), disabledTab("B"), tab("C")));
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.RIGHT);
            assertEquals(2, pane.getSelectedIndex());
        });
    }

    @Test
    public void homeAndEnd() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(1);
            press(pane, KeyCode.END);
            assertEquals(2, pane.getSelectedIndex());
            press(pane, KeyCode.HOME);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    @Test
    public void verticalArrowsIgnoredWhenHorizontal() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B")));
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.DOWN);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    // ==================== Keyboard (manual) ====================

    @Test
    public void manualArrowMovesFocusNotSelection() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSelectionFollowsFocus(false);
            laidOut(pane);
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.RIGHT);
            // Selection unchanged; the roving focus moved to B (reported via FOCUS_ITEM).
            assertEquals(0, pane.getSelectedIndex());
            Node focusItem = (Node) pane.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM);
            assertSame(cellAt(pane, 1), focusItem);
        });
    }

    @Test
    public void manualSpaceSelectsFocusedTab() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setSelectionFollowsFocus(false);
            laidOut(pane);
            pane.getSelectionModel().select(0);
            press(pane, KeyCode.RIGHT);
            press(pane, KeyCode.SPACE);
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    // ==================== Accessibility ====================

    @Test
    public void accessibilityRolesAndAttributes() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(1);
            pane.layout();

            assertEquals(AccessibleRole.TAB_PANE, pane.getAccessibleRole());
            assertEquals(3, pane.queryAccessibleAttribute(AccessibleAttribute.ITEM_COUNT));
            assertSame(cellAt(pane, 1), pane.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM));

            Node cell = cellAt(pane, 1);
            assertEquals(AccessibleRole.TAB_ITEM, cell.getAccessibleRole());
            assertEquals("B", cell.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            assertEquals(Boolean.TRUE, cell.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    @Test
    public void iconOnlyTabReportsAccessibleText() throws Exception {
        runOnFx(() -> {
            RXTab icon = RXTab.of(null, new StackPane(), new Label("page"));
            icon.setAccessibleText("Phone");
            RXTabPane pane = laidOut(new RXTabPane(icon, tab("B")));
            Node cell = cellAt(pane, 0);
            assertEquals("Phone", cell.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        });
    }

    // ==================== Keyboard scope / orientation ====================

    @Test
    public void arrowFromFocusedContentIsIgnored() throws Exception {
        runOnFx(() -> {
            StackPane contentB = new StackPane();
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), RXTab.of("B", contentB), tab("C")));
            pane.getSelectionModel().select(1);
            // An arrow key targeted at a node inside the tab content bubbles to the
            // pane with target == content; the tablist must not hijack it.
            contentB.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                    false, false, false, false));
            assertEquals(1, pane.getSelectedIndex());
        });
    }

    @Test
    public void rightToLeftMirrorsHorizontalArrows() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = new RXTabPane(tab("A"), tab("B"), tab("C"));
            pane.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            laidOut(pane);
            pane.getSelectionModel().select(1);
            // In RTL, Right advances toward the start of the model.
            press(pane, KeyCode.RIGHT);
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    // ==================== Dispose ====================

    @Test
    public void disposedSkinRemovesKeyHandler() throws Exception {
        runOnFx(() -> {
            RXTabPane pane = laidOut(new RXTabPane(tab("A"), tab("B"), tab("C")));
            pane.getSelectionModel().select(0);
            pane.getSkin().dispose();
            // The key handler was registered through the disposer; after dispose an
            // arrow key must no longer move the selection. Fire without laying out
            // (the disposed skin must not be re-invoked).
            pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.RIGHT,
                    false, false, false, false));
            assertEquals(0, pane.getSelectedIndex());
        });
    }

    // ==================== Helpers ====================

    private static RXTabPane laidOut(RXTabPane pane) {
        StackPane root = new StackPane(pane);
        new Scene(root, 640, 400);
        root.applyCss();
        root.layout();
        return pane;
    }

    private static Node cellAt(RXTabPane pane, int index) {
        return (Node) pane.queryAccessibleAttribute(AccessibleAttribute.ITEM_AT_INDEX, index);
    }

    private static void press(RXTabPane pane, KeyCode code) {
        pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
        pane.layout();
    }

    private static RXTab tab(String text) {
        return new RXTab(text);
    }

    private static RXTab disabledTab(String text) {
        RXTab tab = new RXTab(text);
        tab.setDisable(true);
        return tab;
    }

    private static void runOnFx(FxAction action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}

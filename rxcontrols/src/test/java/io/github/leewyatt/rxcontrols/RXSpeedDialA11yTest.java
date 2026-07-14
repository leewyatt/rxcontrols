package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSpeedDial.Direction;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keyboard and accessibility tests for {@link RXSpeedDial}.
 */
public class RXSpeedDialA11yTest {

    private static final boolean MAC = RXOS.isMacOS();

    private Stage stage;

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
        Platform.setImplicitExit(false);
    }

    /**
     * Closes any stage opened by focus-sensitive tests.
     *
     * @throws Exception if the FX-thread cleanup fails
     */
    @AfterEach
    public void cleanup() throws Exception {
        runOnFx(() -> {
            if (stage != null) {
                stage.hide();
                stage = null;
            }
        });
    }

    /**
     * Verifies roles and action accessible text binding.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void rolesAndActionAccessibleTextAreBound() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Run", new Region());
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            dial.open();
            applyCssAndLayout(dial);

            RXFloatingActionButton mainFab = mainFab(dial);
            RXFloatingActionButton actionFab = actionFabs(dial).get(0);
            assertEquals(AccessibleRole.BUTTON, mainFab.getAccessibleRole());
            assertEquals(AccessibleRole.BUTTON, actionFab.getAccessibleRole());
            assertEquals("Run", actionFab.getAccessibleText());

            action.setText("Rename");
            assertEquals("Rename", actionFab.getAccessibleText());
        });
    }

    /**
     * Verifies action FABs leave and re-enter the Tab order with state changes.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void actionFocusTraversableTracksShowingVisibleAndDisable() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Run", new Region());
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            attachAndApplyCss(dial);
            RXFloatingActionButton actionFab = actionFabs(dial).get(0);

            assertFalse(actionFab.isFocusTraversable());

            dial.open();
            assertTrue(actionFab.isFocusTraversable());

            action.setDisable(true);
            assertFalse(actionFab.isFocusTraversable());

            action.setDisable(false);
            assertTrue(actionFab.isFocusTraversable());

            action.setVisible(false);
            assertFalse(actionFab.isFocusTraversable());

            action.setVisible(true);
            assertTrue(actionFab.isFocusTraversable());

            dial.setDisable(true);
            assertFalse(actionFab.isFocusTraversable());

            dial.setDisable(false);
            assertTrue(actionFab.isFocusTraversable());

            dial.close();
            assertFalse(actionFab.isFocusTraversable());
        });
    }

    /**
     * Verifies animated open and close update action FAB Tab-order state immediately.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void animatedActionFocusTraversableTracksAnimationPhase() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction action = new RXSpeedDialAction("Run", new Region());
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimationDuration(Duration.seconds(5.0));
            attachAndApplyCss(dial);
            RXFloatingActionButton actionFab = actionFabs(dial).get(0);

            assertFalse(actionFab.isFocusTraversable());

            dial.open();
            assertTrue(actionFab.isFocusTraversable());

            dial.close();
            assertFalse(actionFab.isFocusTraversable());

            dial.skinProperty().set(null);
        });
    }

    /**
     * Verifies axis navigation skips disabled and invisible actions.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void axisNavigationSkipsNonNavigableActions() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction first = new RXSpeedDialAction("First", new Region());
            RXSpeedDialAction disabled = new RXSpeedDialAction("Disabled", new Region());
            RXSpeedDialAction hidden = new RXSpeedDialAction("Hidden", new Region());
            RXSpeedDialAction last = new RXSpeedDialAction("Last", new Region());
            disabled.setDisable(true);
            hidden.setVisible(false);
            RXSpeedDial dial = new RXSpeedDial(new Region(), first, disabled, hidden, last);
            dial.setAnimated(false);
            dial.setDirection(Direction.UP);
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            List<RXFloatingActionButton> fabs = actionFabs(dial);
            RXFloatingActionButton mainFab = mainFab(dial);

            mainFab.requestFocus();
            KeyEvent backwardFromMain = fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.DOWN);
            assertFalse(backwardFromMain.isConsumed());
            assertSame(mainFab, dial.getScene().getFocusOwner());

            fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.UP);
            assertSame(fabs.get(0), dial.getScene().getFocusOwner());

            fireKey((Node) dial.getScene().getFocusOwner(), KeyEvent.KEY_PRESSED, KeyCode.UP);
            assertSame(fabs.get(3), dial.getScene().getFocusOwner());

            fireKey((Node) dial.getScene().getFocusOwner(), KeyEvent.KEY_PRESSED, KeyCode.UP);
            assertSame(fabs.get(3), dial.getScene().getFocusOwner());

            fireKey((Node) dial.getScene().getFocusOwner(), KeyEvent.KEY_PRESSED, KeyCode.DOWN);
            assertSame(fabs.get(0), dial.getScene().getFocusOwner());

            fireKey((Node) dial.getScene().getFocusOwner(), KeyEvent.KEY_PRESSED, KeyCode.DOWN);
            assertSame(mainFab, dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies direction-specific axis keys move through action FABs without wrapping.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void axisNavigationDirectionMatrixUsesSpatialKeys() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(),
                    new RXSpeedDialAction("First", new Region()),
                    new RXSpeedDialAction("Second", new Region()));
            dial.setAnimated(false);
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            List<RXFloatingActionButton> fabs = actionFabs(dial);
            RXFloatingActionButton mainFab = mainFab(dial);

            assertDirectionNavigation(dial, Direction.UP, KeyCode.UP, KeyCode.DOWN, mainFab, fabs);
            assertDirectionNavigation(dial, Direction.DOWN, KeyCode.DOWN, KeyCode.UP, mainFab, fabs);
            assertDirectionNavigation(dial, Direction.LEFT, KeyCode.LEFT, KeyCode.RIGHT, mainFab, fabs);
            assertDirectionNavigation(dial, Direction.RIGHT, KeyCode.RIGHT, KeyCode.LEFT, mainFab, fabs);
        });
    }

    /**
     * Verifies Home and End move to the first and last navigable action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void homeAndEndMoveToBoundaryActions() throws Exception {
        runOnFx(() -> {
            RXSpeedDialAction disabledFirst = new RXSpeedDialAction("Disabled first", new Region());
            RXSpeedDialAction first = new RXSpeedDialAction("First", new Region());
            RXSpeedDialAction last = new RXSpeedDialAction("Last", new Region());
            RXSpeedDialAction hiddenLast = new RXSpeedDialAction("Hidden last", new Region());
            disabledFirst.setDisable(true);
            hiddenLast.setVisible(false);
            RXSpeedDial dial = new RXSpeedDial(new Region(), disabledFirst, first, last, hiddenLast);
            dial.setAnimated(false);
            showInStage(dial);
            dial.open();
            applyCssAndLayout(dial);
            List<RXFloatingActionButton> fabs = actionFabs(dial);
            RXFloatingActionButton mainFab = mainFab(dial);

            mainFab.requestFocus();
            fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.END);
            assertSame(fabs.get(2), dial.getScene().getFocusOwner());

            fireKey((Node) dial.getScene().getFocusOwner(), KeyEvent.KEY_PRESSED, KeyCode.HOME);
            assertSame(fabs.get(1), dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies orthogonal keys and collapsed dials are left to default traversal.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nonHandledNavigationKeysAreNotConsumed() throws Exception {
        runOnFx(() -> {
            RXSpeedDial dial = new RXSpeedDial(new Region(), new RXSpeedDialAction("Run", new Region()));
            dial.setAnimated(false);
            dial.setDirection(Direction.UP);
            showInStage(dial);
            RXFloatingActionButton mainFab = mainFab(dial);

            mainFab.requestFocus();
            KeyEvent collapsed = fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.UP);
            assertFalse(collapsed.isConsumed());
            assertSame(mainFab, dial.getScene().getFocusOwner());

            dial.open();
            KeyEvent orthogonal = fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.LEFT);
            assertFalse(orthogonal.isConsumed());
            assertSame(mainFab, dial.getScene().getFocusOwner());
        });
    }

    /**
     * Verifies Button behavior still provides keyboard activation.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardActivationUsesButtonBehavior() throws Exception {
        runOnFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            RXSpeedDialAction action =
                    new RXSpeedDialAction("Run", new Region(), event -> calls.incrementAndGet());
            RXSpeedDial dial = new RXSpeedDial(new Region(), action);
            dial.setAnimated(false);
            showInStage(dial);
            RXFloatingActionButton mainFab = mainFab(dial);

            mainFab.requestFocus();
            fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            fireKey(mainFab, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertTrue(dial.isShowing());

            RXFloatingActionButton actionFab = actionFabs(dial).get(0);
            actionFab.requestFocus();
            fireKey(actionFab, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            fireKey(actionFab, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, calls.get());
            assertFalse(dial.isShowing());

            mainFab.requestFocus();
            fireKey(mainFab, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
            fireKey(mainFab, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertEquals(!MAC, dial.isShowing());

            if (!MAC) {
                actionFab.requestFocus();
                fireKey(actionFab, KeyEvent.KEY_PRESSED, KeyCode.ENTER);
                fireKey(actionFab, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
                assertEquals(2, calls.get());
                assertFalse(dial.isShowing());
            }
        });
    }

    private static StackPane attachAndApplyCss(RXSpeedDial dial) {
        StackPane root = new StackPane(dial);
        new Scene(root, 260.0, 260.0);
        root.applyCss();
        root.applyCss();
        root.layout();
        return root;
    }

    private StackPane showInStage(RXSpeedDial dial) {
        StackPane root = new StackPane(dial);
        stage = new Stage();
        stage.setScene(new Scene(root, 260.0, 260.0));
        stage.show();
        stage.requestFocus();
        root.applyCss();
        root.layout();
        return root;
    }

    private static void applyCssAndLayout(RXSpeedDial dial) {
        Parent root = dial.getScene().getRoot();
        root.applyCss();
        root.applyCss();
        root.layout();
    }

    private static List<Node> actionCells(RXSpeedDial dial) {
        return actionsLayer(dial).getChildrenUnmodifiable().stream().toList();
    }

    private static List<RXFloatingActionButton> actionFabs(RXSpeedDial dial) {
        return actionCells(dial).stream()
                .map(RXSpeedDialA11yTest::actionFab)
                .toList();
    }

    private static RXFloatingActionButton actionFab(Node actionCell) {
        Parent parent = (Parent) actionCell;
        return parent.getChildrenUnmodifiable().stream()
                .filter(RXFloatingActionButton.class::isInstance)
                .map(RXFloatingActionButton.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static RXFloatingActionButton mainFab(RXSpeedDial dial) {
        return dial.lookupAll(".rx-fab").stream()
                .filter(RXFloatingActionButton.class::isInstance)
                .map(RXFloatingActionButton.class::cast)
                .filter(fab -> fab.getSize() == RXFloatingActionButton.Size.STANDARD)
                .findFirst()
                .orElseThrow();
    }

    private static Pane actionsLayer(RXSpeedDial dial) {
        return dial.lookupAll(".actions").stream()
                .filter(Pane.class::isInstance)
                .map(Pane.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static void assertDirectionNavigation(RXSpeedDial dial, Direction direction,
                                                  KeyCode forwardKey, KeyCode backwardKey,
                                                  RXFloatingActionButton mainFab,
                                                  List<RXFloatingActionButton> actionFabs) {
        dial.setDirection(direction);
        applyCssAndLayout(dial);

        mainFab.requestFocus();
        KeyEvent backwardFromMain = fireKey(mainFab, KeyEvent.KEY_PRESSED, backwardKey);
        assertFalse(backwardFromMain.isConsumed());
        assertSame(mainFab, dial.getScene().getFocusOwner());

        fireKey(mainFab, KeyEvent.KEY_PRESSED, forwardKey);
        assertSame(actionFabs.get(0), dial.getScene().getFocusOwner());

        fireKey(actionFabs.get(0), KeyEvent.KEY_PRESSED, forwardKey);
        assertSame(actionFabs.get(1), dial.getScene().getFocusOwner());

        fireKey(actionFabs.get(1), KeyEvent.KEY_PRESSED, backwardKey);
        assertSame(actionFabs.get(0), dial.getScene().getFocusOwner());
    }

    private static KeyEvent fireKey(Node target, EventType<KeyEvent> type, KeyCode code) {
        KeyEvent event = new KeyEvent(target, target, type, "", "", code,
                false, false, false, false);
        target.fireEvent(event);
        return event;
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        if (error[0] != null) {
            throw new AssertionError(error[0]);
        }
    }
}

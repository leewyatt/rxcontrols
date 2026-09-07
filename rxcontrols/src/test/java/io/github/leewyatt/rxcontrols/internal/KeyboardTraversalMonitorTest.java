package io.github.leewyatt.rxcontrols.internal;

import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link KeyboardTraversalMonitor}: a traversal key arms the scene
 * flag, a pointer press clears it, activation keys leave it alone, and a scene that
 * has seen no input at all reports no keyboard traversal.
 */
public class KeyboardTraversalMonitorTest {

    /**
     * Starts the JavaFX toolkit so scenes can be built.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /** A null scene can never be mid-traversal, and installing on one is a no-op. */
    @Test
    public void nullSceneReportsNoTraversal() {
        KeyboardTraversalMonitor.install(null);
        assertFalse(KeyboardTraversalMonitor.isKeyboardTraversal(null));
    }

    /**
     * An unmonitored scene reports no traversal even after a traversal key.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void unmonitoredSceneReportsNoTraversal() throws Exception {
        runOnFx(() -> {
            Scene scene = new Scene(new Group(), 200.0, 100.0);
            fire(scene, key(KeyCode.TAB));
            assertFalse(KeyboardTraversalMonitor.isKeyboardTraversal(scene));
        });
    }

    /**
     * Installing twice keeps the single monitor, so the flag is not reset by a second
     * node joining the same scene.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void installIsIdempotent() throws Exception {
        runOnFx(() -> {
            Scene scene = newScene();
            fire(scene, key(KeyCode.TAB));
            KeyboardTraversalMonitor.install(scene);
            assertTrue(KeyboardTraversalMonitor.isKeyboardTraversal(scene));
        });
    }

    /**
     * A monitored scene that has seen no input reports no traversal, so the focus a
     * window hands out when it opens is not mistaken for keyboard focus.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void freshSceneReportsNoTraversal() throws Exception {
        runOnFx(() -> assertFalse(KeyboardTraversalMonitor.isKeyboardTraversal(newScene())));
    }

    /**
     * TAB and the arrow keys arm the flag; a pointer press clears it again.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void traversalKeysArmAndPointerPressClears() throws Exception {
        runOnFx(() -> {
            Scene scene = newScene();

            fire(scene, key(KeyCode.TAB));
            assertTrue(KeyboardTraversalMonitor.isKeyboardTraversal(scene));

            fire(scene, mousePress());
            assertFalse(KeyboardTraversalMonitor.isKeyboardTraversal(scene));

            fire(scene, key(KeyCode.DOWN));
            assertTrue(KeyboardTraversalMonitor.isKeyboardTraversal(scene));
        });
    }

    /**
     * An activation key pressed on an already traversed-to control must not clear
     * the flag, otherwise the focus indicator would blink away on SPACE.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void activationKeyLeavesTraversalArmed() throws Exception {
        runOnFx(() -> {
            Scene scene = newScene();

            fire(scene, key(KeyCode.TAB));
            fire(scene, key(KeyCode.SPACE));
            assertTrue(KeyboardTraversalMonitor.isKeyboardTraversal(scene));

            fire(scene, key(KeyCode.ENTER));
            assertTrue(KeyboardTraversalMonitor.isKeyboardTraversal(scene));
        });
    }

    /**
     * An activation key on a scene that has only seen a pointer press must not arm
     * the flag either.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void activationKeyAfterPointerPressStaysCleared() throws Exception {
        runOnFx(() -> {
            Scene scene = newScene();

            fire(scene, mousePress());
            fire(scene, key(KeyCode.SPACE));
            assertFalse(KeyboardTraversalMonitor.isKeyboardTraversal(scene));
        });
    }

    // ==================== Helpers ====================

    private static Scene newScene() {
        Scene scene = new Scene(new Group(), 200.0, 100.0);
        KeyboardTraversalMonitor.install(scene);
        return scene;
    }

    private static void fire(Scene scene, javafx.event.Event event) {
        scene.getRoot().fireEvent(event);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static MouseEvent mousePress() {
        return new MouseEvent(MouseEvent.MOUSE_PRESSED, 5.0, 5.0, 5.0, 5.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, true, null);
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }
}

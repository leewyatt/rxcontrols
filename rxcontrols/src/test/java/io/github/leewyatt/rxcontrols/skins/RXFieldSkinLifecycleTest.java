package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.RXNumberField;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FX integration tests for {@link RXFieldBaseSkin} and its subclasses, covering
 * the construct → populate slots → skin-replacement → dispose lifecycle. These
 * lock in the shared {@link SkinDisposer} cleanup: side-node wrappers are
 * released on dispose, skin replacement re-parents side nodes without leaking,
 * and the password / number skins tear down their extra resources cleanly.
 */
public class RXFieldSkinLifecycleTest {

    /**
     * Starts the JavaFX toolkit before constructing skins.
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

    /**
     * Verifies the base skin wraps both side nodes and releases them on
     * dispose (the disposer's wrapper-release task runs).
     */
    @Test
    public void disposeReleasesSideNodeWrappers() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            Label left = new Label("L");
            Label right = new Label("R");
            field.setLeft(left);
            field.setRight(right);

            RXTextFieldSkin skin = new RXTextFieldSkin(field);
            field.setSkin(skin);

            assertInstanceOf(StackPane.class, left.getParent());
            assertTrue(((StackPane) left.getParent()).getStyleClass().contains("left-wrapper"));
            assertInstanceOf(StackPane.class, right.getParent());
            assertTrue(((StackPane) right.getParent()).getStyleClass().contains("right-wrapper"));

            skin.dispose();

            // releaseWrapper cleared the wrappers, detaching the user nodes.
            assertNull(left.getParent());
            assertNull(right.getParent());
        });
    }

    /**
     * Verifies replacing the skin re-parents an existing side node into the new
     * skin's wrapper, and that the slot setters still work afterwards. This is
     * the path the base skin's wrapper-release task exists to protect.
     */
    @Test
    public void skinReplacementReparentsSideNode() {
        runOnFx(() -> {
            RXTextField field = new RXTextField();
            Label left = new Label("L");
            field.setLeft(left);

            field.setSkin(new RXTextFieldSkin(field));
            assertNotNull(left.getParent());
            Node oldWrapper = left.getParent();

            // JFX17's Control.skin setter SHORT-CIRCUITS a replacement of the
            // same skin class (the new instance is neither installed nor
            // disposed), so the swap must use a distinct class to be real.
            field.setSkin(new RXTextFieldSkin(field) { });
            assertInstanceOf(StackPane.class, left.getParent(),
                    "the side node must be re-parented into the new skin's wrapper");
            assertNotSame(oldWrapper, left.getParent(),
                    "a real swap must build a new wrapper");
            long wrapperCount = field.getChildrenUnmodifiable().stream()
                    .filter(n -> n.getStyleClass().contains("left-wrapper"))
                    .count();
            assertEquals(1, wrapperCount, "no ghost wrapper from the old skin may remain");

            Label replacement = new Label("L2");
            field.setLeft(replacement);
            assertNotNull(replacement.getParent());
            assertNull(left.getParent());
            // A leaked old-skin listener would resurrect a ghost wrapper here.
            long wrappersAfterSetLeft = field.getChildrenUnmodifiable().stream()
                    .filter(n -> n.getStyleClass().contains("left-wrapper"))
                    .count();
            assertEquals(1, wrappersAfterSetLeft,
                    "old-skin listeners must stay dead after dispose");
        });
    }

    /**
     * Pins the skin-level mask guard: before the dynamic display binding is
     * installed (the skin here is constructed but never attached), maskText
     * must never reveal plain text, even while revealPassword is true.
     */
    @Test
    public void maskTextNeverRevealsBeforeInstall() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            field.setRevealPassword(true);
            RXMaterialPasswordFieldSkin skin = new RXMaterialPasswordFieldSkin(field);
            assertEquals(String.valueOf(RXMaterialPasswordField.DEFAULT_ECHO_CHAR).repeat(6),
                    skin.maskText("secret"),
                    "an uninstalled skin must never reveal plain text");
        });
    }

    /**
     * Drives a real Material password skin into mask degradation (an injected
     * decoy makes the text-node discovery ambiguous) and pins the degradation
     * wiring: the reveal button is withdrawn and a click on it no longer flips
     * revealPassword.
     */
    @Test
    public void materialPasswordDegradationHidesRevealButtonAndGatesClicks() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            // Ghost SkinBase adds a decoy TextFieldSkin-lookalike (clipped pane
            // + bound-layoutX text) into the control's children, so the real
            // skin's discovery sees two candidates and fails closed.
            Pane decoy = new Pane();
            decoy.setClip(new Rectangle());
            Text decoyText = new Text();
            decoyText.layoutXProperty().bind(new SimpleDoubleProperty(0));
            decoy.getChildren().add(decoyText);
            new SkinBase<RXMaterialPasswordField>(field) {
                {
                    getChildren().add(decoy);
                }
            };

            RXMaterialPasswordFieldSkin skin = new RXMaterialPasswordFieldSkin(field);
            Node revealButton = field.lookup(".reveal-button");
            assertNotNull(revealButton, "reveal button exists before the degraded attach");

            field.setSkin(skin);

            assertNull(field.lookup(".reveal-button"),
                    "degradation must withdraw the reveal button");
            // The handler is still on the detached button node; firing it must
            // be a no-op because the dynamic binding never installed.
            Event.fireEvent(revealButton, click());
            assertFalse(field.isRevealPassword(),
                    "a click must not flip revealPassword while the mask cannot be lifted");
        });
    }

    private static MouseEvent click() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, null);
    }

    /**
     * Verifies the password skin's reveal toggle and disposal run cleanly. The
     * dynamic display binding and toggle listeners are now owned by the shared
     * disposer, so neither toggling nor disposing should throw.
     */
    @Test
    public void passwordFieldRevealLifecycleIsClean() {
        runOnFx(() -> {
            RXPasswordField field = new RXPasswordField("secret");
            field.setRight(new Label("eye"));

            RXPasswordFieldSkin skin = new RXPasswordFieldSkin(field);
            field.setSkin(skin);

            field.setRevealPassword(true);
            field.setRevealPassword(false);

            skin.dispose();
            // Toggling after dispose must not reach a dangling listener.
            field.setRevealPassword(true);
        });
    }

    /**
     * Verifies the Material password skin tears down its extra disposer-owned
     * resources cleanly: the shared {@code PasswordMaskSupport}, the reveal-button
     * handler, and the {@code showRevealButton} listener. Toggling reveal /
     * showRevealButton after dispose must not reach a dangling listener.
     */
    @Test
    public void materialPasswordRevealLifecycleIsClean() {
        runOnFx(() -> {
            RXMaterialPasswordField field = new RXMaterialPasswordField("secret");
            field.setLabelText("Password");

            RXMaterialPasswordFieldSkin skin = new RXMaterialPasswordFieldSkin(field);
            field.setSkin(skin);

            field.setRevealPassword(true);
            field.setRevealPassword(false);
            field.setShowRevealButton(false);
            field.setShowRevealButton(true);

            skin.dispose();
            // Toggling after dispose must not reach a dangling listener.
            field.setRevealPassword(true);
            field.setShowRevealButton(false);
        });
    }

    /**
     * Verifies the number-field skin (ENTER handler registered through the
     * shared disposer) constructs and disposes cleanly.
     */
    @Test
    public void numberFieldDisposeIsClean() {
        runOnFx(() -> {
            RXNumberField field = new RXNumberField();
            RXNumberFieldSkin skin = new RXNumberFieldSkin(field);
            field.setSkin(skin);
            skin.dispose();
        });
    }

    private void runOnFx(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("FX task did not complete in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for FX task", e);
        }
        Throwable t = failure.get();
        if (t instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (t instanceof Error error) {
            throw error;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }
}

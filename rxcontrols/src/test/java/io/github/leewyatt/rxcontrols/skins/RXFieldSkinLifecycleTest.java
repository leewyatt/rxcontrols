package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.RXNumberField;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

            field.setSkin(new RXTextFieldSkin(field));
            assertInstanceOf(StackPane.class, left.getParent());

            Label replacement = new Label("L2");
            field.setLeft(replacement);
            assertNotNull(replacement.getParent());
            assertNull(left.getParent());
        });
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

            field.setShowPassword(true);
            field.setShowPassword(false);

            skin.dispose();
            // Toggling after dispose must not reach a dangling listener.
            field.setShowPassword(true);
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

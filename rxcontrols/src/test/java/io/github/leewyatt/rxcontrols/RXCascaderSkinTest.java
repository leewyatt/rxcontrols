package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Skin-level tests for {@link io.github.leewyatt.rxcontrols.skins.RXCascaderSkin}:
 * the display arrow and clear affordances are shape-backed {@code Region}s, so
 * mounting the control under the real user-agent stylesheet must parse their
 * {@code -fx-shape} and apply it.
 */
public class RXCascaderSkinTest {

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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
     * Verifies the arrow and clear graphics resolve to {@code Region}s whose
     * {@code -fx-shape} is parsed and applied by the user-agent stylesheet.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void displayIconsAreShapeBackedRegions() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.setClearable(true);
            cascader.getRootItems().add(new RXCascaderItem<>("root"));

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Region arrow = (Region) cascader.lookup(".arrow-button > .arrow");
            Region clearGraphic = (Region) cascader.lookup(".clear-button > .graphic");
            assertNotNull(arrow, "arrow region should exist");
            assertNotNull(clearGraphic, "clear graphic region should exist");
            assertNotNull(arrow.getShape(), "arrow -fx-shape should be parsed and applied");
            assertNotNull(clearGraphic.getShape(), "clear -fx-shape should be parsed and applied");
        });
    }

    /**
     * Verifies the field's default path text (no {@code pathTextFactory} set)
     * joins each node's text via the item text factory, falling back to
     * {@code String.valueOf(value)} when none is set.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void defaultPathTextUsesItemTextFactoryWithFallback() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> root = new RXCascaderItem<>("bj");
            RXCascaderItem<String> child = new RXCascaderItem<>("sh");
            root.getChildren().add(child);
            cascader.getRootItems().add(root);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Label field = (Label) cascader.lookup(".display .label");
            assertNotNull(field, "display label should exist");

            cascader.getView().activate(child);
            // No factory: fall back to String.valueOf(value), joined with " / ".
            assertEquals("bj / sh", field.getText());

            // Factory: derive each node's text from the value.
            cascader.setItemTextFactory(value -> value == null ? "" : value.toUpperCase());
            assertEquals("BJ / SH", field.getText());
        });
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }
}

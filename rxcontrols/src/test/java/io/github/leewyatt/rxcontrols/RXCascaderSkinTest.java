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

            cascader.select(child);
            // No factory: fall back to String.valueOf(value), joined with " / ".
            assertEquals("bj / sh", field.getText());

            // Factory: derive each node's text from the value.
            cascader.setItemTextFactory(value -> value == null ? "" : value.toUpperCase());
            assertEquals("BJ / SH", field.getText());
        });
    }

    /**
     * Verifies the field updates when a selected path item's value changes: the
     * skin observes the displayed path items' {@code valueProperty} (D3, fixed in
     * Phase 5).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void fieldUpdatesWhenSelectedItemValueChanges() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> root = new RXCascaderItem<>("root");
            RXCascaderItem<String> child = new RXCascaderItem<>("child");
            root.getChildren().add(child);
            cascader.getRootItems().add(root);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Label field = (Label) cascader.lookup(".display .label");
            assertNotNull(field, "display label should exist");

            cascader.select(child);
            assertEquals("root / child", field.getText(), "precondition: field shows the selected path");

            child.setValue("child2");
            assertEquals("root / child2", field.getText(),
                    "field must update when a selected path item's value changes");
        });
    }

    /**
     * Verifies the field mirrors value changes for a selection made BEFORE the
     * skin was created (skins are lazy): the skin must bind path-value listeners
     * at construction, not only on later selection changes.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void fieldUpdatesForSelectionMadeBeforeSkinCreated() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> root = new RXCascaderItem<>("root");
            RXCascaderItem<String> child = new RXCascaderItem<>("child");
            root.getChildren().add(child);
            cascader.getRootItems().add(root);

            // Select before the control is in a scene, so no skin exists yet.
            cascader.select(child);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Label field = (Label) cascader.lookup(".display .label");
            assertNotNull(field, "display label should exist");
            assertEquals("root / child", field.getText());

            child.setValue("renamed");
            assertEquals("root / renamed", field.getText(),
                    "field must mirror a value change for a selection made before the skin existed");
        });
    }

    /**
     * Verifies a custom separator joins the levels of the default field text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void separatorCustomizesDefaultPathText() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> root = new RXCascaderItem<>("a");
            RXCascaderItem<String> child = new RXCascaderItem<>("b");
            root.getChildren().add(child);
            cascader.getRootItems().add(root);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Label field = (Label) cascader.lookup(".display .label");
            cascader.select(child);
            assertEquals("a / b", field.getText(), "default separator joins levels");

            cascader.setSeparator(" > ");
            assertEquals("a > b", field.getText(), "custom separator is applied");
        });
    }

    /**
     * Verifies {@code showAllLevels=false} renders only the last level in the field.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void showAllLevelsFalseShowsOnlyLastLevel() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            RXCascaderItem<String> root = new RXCascaderItem<>("a");
            RXCascaderItem<String> child = new RXCascaderItem<>("b");
            root.getChildren().add(child);
            cascader.getRootItems().add(root);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            Label field = (Label) cascader.lookup(".display .label");
            cascader.select(child);
            assertEquals("a / b", field.getText(), "full path by default");

            cascader.setShowAllLevels(false);
            assertEquals("b", field.getText(), "only the last level when showAllLevels is false");
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

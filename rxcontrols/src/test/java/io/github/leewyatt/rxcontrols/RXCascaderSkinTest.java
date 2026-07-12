package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.util.StringConverter;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * Verifies the cascader follows ComboBox sizing: a fill container such as
     * {@link StackPane} must not stretch the field beyond its preferred size by
     * default.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void stackPaneDoesNotStretchCascaderBeyondPreferredSize() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("root"));

            StackPane root = new StackPane(cascader);
            new Scene(root, 480.0, 180.0);
            root.applyCss();
            root.layout();

            assertEquals(cascader.prefWidth(-1), cascader.getWidth(), 0.5);
            assertEquals(cascader.prefHeight(cascader.getWidth()), cascader.getHeight(), 0.5);
            assertTrue(cascader.getWidth() < root.getWidth(),
                    "cascader width must not fill the StackPane by default");
            assertTrue(cascader.getHeight() < root.getHeight(),
                    "cascader height must not fill the StackPane by default");
        });
    }

    /**
     * Verifies the field's default path text (no {@code pathTextFactory} set)
     * joins each node's text via the converter, falling back to
     * {@code String.valueOf(value)} when none is set.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void defaultPathTextUsesConverterWithFallback() throws InterruptedException {
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
            // No converter: fall back to String.valueOf(value), joined with " / ".
            assertEquals("bj / sh", field.getText());

            // Converter: derive each node's text from the value.
            cascader.setConverter(new StringConverter<>() {
                @Override
                public String toString(String value) {
                    return value == null ? "" : value.toUpperCase();
                }

                @Override
                public String fromString(String text) {
                    return text;
                }
            });
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

    /**
     * Verifies Escape is not consumed when no popup is open, so it bubbles to an
     * enclosing dialog / cancel button (the skin only consumes it while showing).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void escapeIsNotConsumedWhenPopupClosed() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("root"));

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertFalse(cascader.isShowing(), "precondition: the popup is closed");
            KeyEvent escape = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE,
                    false, false, false, false);
            Event.fireEvent(cascader, escape);

            assertFalse(escape.isConsumed(),
                    "Escape must bubble when no popup is open so a dialog still sees it");
        });
    }

    /**
     * Verifies Enter does not open the popup and is not consumed when the popup is
     * closed, so it bubbles to an enclosing form's default / submit button (aligned
     * with ComboBox, where Enter never opens the popup).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void enterIsNotConsumedWhenPopupClosed() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("root"));

            StackPane parent = new StackPane(cascader);
            Scene scene = new Scene(parent);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            // Probe an ancestor: an unconsumed key bubbles up to it (this stands in
            // for an enclosing form's default button); a consumed one does not.
            boolean[] reachedAncestor = {false};
            parent.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedAncestor[0] = true);

            assertFalse(cascader.isShowing(), "precondition: the popup is closed");
            Event.fireEvent(cascader, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER,
                    false, false, false, false));

            assertFalse(cascader.isShowing(), "Enter must not open the popup");
            assertTrue(reachedAncestor[0],
                    "Enter must bubble to an ancestor when the popup is closed so a default button still sees it");
        });
    }

    /**
     * Verifies a modified toggle key (Alt+F4) is not consumed, so it bubbles to an
     * ancestor — the skin must not hijack the OS window-close combo. The popup
     * toggle keys require the bare / Alt-only ComboBox modifier set.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void altF4IsNotConsumed() throws InterruptedException {
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(new RXCascaderItem<>("root"));

            StackPane parent = new StackPane(cascader);
            Scene scene = new Scene(parent);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            boolean[] reachedAncestor = {false};
            parent.addEventHandler(KeyEvent.KEY_PRESSED, event -> reachedAncestor[0] = true);

            // shift, ctrl, alt, meta — Alt+F4
            Event.fireEvent(cascader, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F4,
                    false, false, true, false));

            assertTrue(reachedAncestor[0],
                    "Alt+F4 must bubble so the OS window-close shortcut is not hijacked");
        });
    }

    /**
     * Verifies a discarded, never-disposed cascader is collectible while its item
     * tree lives: the skin observes the displayed items' {@code valueProperty}
     * weakly, so long-lived application items do not pin the control.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void discardedSkinIsCollectibleWhileItemTreeLives() throws InterruptedException {
        RXCascaderItem<String> root = new RXCascaderItem<>("root");
        RXCascaderItem<String> child = new RXCascaderItem<>("child");
        root.getChildren().add(child);
        // Strong references to the tree outlive the GC probe (held here); the
        // cascader must not stay reachable through the items' value listeners.
        List<RXCascaderItem<String>> tree = List.of(root, child);

        AtomicReference<WeakReference<RXCascader<String>>> probe = new AtomicReference<>();
        runOnFx(() -> {
            RXCascader<String> cascader = new RXCascader<>();
            cascader.getRootItems().add(root);

            Scene scene = new Scene(new StackPane(cascader));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            cascader.select(child);
            probe.set(new WeakReference<>(cascader));
        });

        assertReclaimable(probe.get(),
                "a discarded cascader must be collectible while its item tree lives");
        assertEquals(2, tree.size(), "the item tree stays strongly reachable past the probe");
    }

    private static void assertReclaimable(WeakReference<?> reference, String message)
            throws InterruptedException {
        for (int i = 0; i < 50 && reference.get() != null; i++) {
            System.gc();
            Thread.sleep(10L);
        }
        assertNull(reference.get(), message);
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

package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skin-level tests for {@link io.github.leewyatt.rxcontrols.skins.RXCascaderViewSkin}:
 * per-column ordinal style classes, {@code visibleRowCount}-driven preferred
 * height, and author-CSS override of {@code -fx-fixed-cell-size}.
 */
public class RXCascaderViewSkinTest {

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
     * Verifies each rendered column carries a 0-based ordinal style class, and
     * the second column appears only after a branch is expanded.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void columnsCarryOrdinalStyleClasses() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            asia.getChildren().setAll(List.of(item("china"), item("japan")));
            view.getRootItems().setAll(List.of(asia));

            Scene scene = new Scene(new StackPane(view));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            // Only the root column exists before expanding.
            assertEquals(1, view.lookupAll(".rx-cascader-column-0").size());
            assertEquals(0, view.lookupAll(".rx-cascader-column-1").size());

            view.expand(asia);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(1, view.lookupAll(".rx-cascader-column-0").size());
            assertEquals(1, view.lookupAll(".rx-cascader-column-1").size());
        });
    }

    /**
     * Verifies increasing {@code visibleRowCount} increases the preferred height
     * by {@code Δrows × fixedCellSize}.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void visibleRowCountDrivesPrefHeight() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.getRootItems().setAll(List.of(item("a"), item("b"), item("c")));

            Scene scene = new Scene(new StackPane(view));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            view.setVisibleRowCount(4);
            double four = view.prefHeight(-1.0);
            view.setVisibleRowCount(6);
            double six = view.prefHeight(-1.0);

            double delta = six - four;
            assertTrue(delta > 0.0, "prefHeight should grow with visibleRowCount, delta=" + delta);
            assertEquals(2 * RXCascaderView.DEFAULT_FIXED_CELL_SIZE, delta, 1.0,
                    "two extra rows should add 2 x fixedCellSize");
        });
    }

    /**
     * Verifies author CSS ({@code -fx-fixed-cell-size}) overrides the code default
     * on the internal column list views.
     *
     * @throws Exception if the temp stylesheet cannot be created or the FX task fails
     */
    @Test
    public void authorCssOverridesFixedCellSize() throws Exception {
        Path css = Files.createTempFile("rx-cascader-skin-test", ".css");
        Files.writeString(css, ".rx-cascader-column { -fx-fixed-cell-size: 50; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                view.getRootItems().setAll(List.of(item("a"), item("b")));

                Scene scene = new Scene(new StackPane(view));
                scene.getStylesheets().add(css.toUri().toString());
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                ListView<?> column = (ListView<?>) view.lookup(".rx-cascader-column-0");
                assertNotNull(column, "root column should exist");
                assertEquals(50.0, column.getFixedCellSize(), 0.001,
                        "author CSS should override the code default fixedCellSize");
            });
        } finally {
            Files.deleteIfExists(css);
        }
    }

    private static RXCascaderItem<String> item(String text) {
        return new RXCascaderItem<>(text, text);
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

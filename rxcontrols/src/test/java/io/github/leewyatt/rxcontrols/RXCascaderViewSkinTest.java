package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * Verifies author CSS targeting a single column ordinal class changes only
     * that column's preferred width, and the view's preferred width is the sum of
     * the per-column preferred widths plus the view insets.
     *
     * @throws Exception if the temp stylesheet cannot be created or the FX task fails
     */
    @Test
    public void authorCssOverridesSingleColumnPrefWidth() throws Exception {
        Path css = Files.createTempFile("rx-cascader-skin-width", ".css");
        Files.writeString(css, ".rx-cascader-column-1 { -fx-pref-width: 300; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                RXCascaderItem<String> asia = item("asia");
                asia.getChildren().setAll(List.of(item("china"), item("japan")));
                view.getRootItems().setAll(List.of(asia));

                Scene scene = new Scene(new StackPane(view));
                scene.getStylesheets().add(css.toUri().toString());
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                view.expand(asia);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                ListView<?> col0 = (ListView<?>) view.lookup(".rx-cascader-column-0");
                ListView<?> col1 = (ListView<?>) view.lookup(".rx-cascader-column-1");
                assertNotNull(col0, "root column should exist");
                assertNotNull(col1, "second column should exist after expand");

                assertEquals(RXCascaderView.DEFAULT_COLUMN_WIDTH, col0.prefWidth(-1.0), 0.001,
                        "first column keeps the default width");
                assertEquals(300.0, col1.prefWidth(-1.0), 0.001,
                        "author CSS should widen only the second column");

                double expected = view.getInsets().getLeft()
                        + col0.prefWidth(-1.0) + col1.prefWidth(-1.0)
                        + view.getInsets().getRight();
                assertEquals(expected, view.prefWidth(-1.0), 0.5,
                        "view prefWidth should be the sum of column pref widths plus insets");
            });
        } finally {
            Files.deleteIfExists(css);
        }
    }

    /**
     * Verifies loading uses its own shape-backed region and hides the branch
     * arrow until loading finishes.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void loadingGlyphIsSeparateFromBranchArrow() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> branch = item("branch");
            branch.setLeafHint(false);
            branch.setLoading(true);
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".rx-cascader-column-0");
            assertNotNull(column, "root column should exist");
            column.applyCss();
            column.layout();

            Region arrow = (Region) column.lookup(".rx-cascader-cell > .container > .arrow");
            Region loading = (Region) column.lookup(".rx-cascader-cell > .container > .loading");
            assertNotNull(arrow, "branch arrow region should exist");
            assertNotNull(loading, "loading region should exist");
            assertNotNull(arrow.getShape(), "arrow -fx-shape should be parsed and applied");
            assertNotNull(loading.getShape(), "loading -fx-shape should be parsed and applied");

            assertFalse(arrow.isVisible(), "arrow should be hidden while loading");
            assertTrue(loading.isVisible(), "loading glyph should be visible while loading");

            branch.setLoading(false);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertTrue(arrow.isVisible(), "arrow should reappear for a loaded branch");
            assertFalse(loading.isVisible(), "loading glyph should hide after loading");
        });
    }

    /**
     * Verifies a {@link RXCascaderCell} subclass overriding {@code createContent}
     * renders the custom node while keeping the cell's style class and contract.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void cellFactoryCreateContentOverrideRendersCustomNode() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setCellFactory(v -> new RXCascaderCell<>(v) {
                @Override
                protected Node createContent(RXCascaderItem<String> cellItem) {
                    return new Label("X-" + cellItem.getText());
                }
            });
            view.getRootItems().setAll(List.of(item("a")));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".rx-cascader-column-0");
            assertNotNull(column, "root column should exist");
            column.applyCss();
            column.layout();

            Label content = (Label) column.lookup(".rx-cascader-cell .label");
            assertNotNull(content, "custom content label should be present");
            assertEquals("X-a", content.getText(), "createContent override should render");
        });
    }

    /**
     * Verifies the loose cell-factory type accepts a plain {@link ListCell}: it
     * renders without the built-in contract and without the cell style class.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void nakedCellFactoryIsAccepted() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setCellFactory(v -> new ListCell<>() {
                @Override
                protected void updateItem(RXCascaderItem<String> cellItem, boolean empty) {
                    super.updateItem(cellItem, empty);
                    setText(empty || cellItem == null ? null : "naked:" + cellItem.getText());
                }
            });
            view.getRootItems().setAll(List.of(item("a")));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".rx-cascader-column-0");
            assertNotNull(column, "root column should exist");
            column.applyCss();
            column.layout();

            assertEquals(0, column.lookupAll(".rx-cascader-cell").size(),
                    "a naked ListCell does not carry the rx-cascader-cell style class");
        });
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

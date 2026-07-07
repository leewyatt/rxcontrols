package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXCascaderItem.LoadState;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
            assertEquals(1, view.lookupAll(".column0").size());
            assertEquals(0, view.lookupAll(".column1").size());

            view.expand(asia);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(1, view.lookupAll(".column0").size());
            assertEquals(1, view.lookupAll(".column1").size());
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
        Files.writeString(css, ".column { -fx-fixed-cell-size: 50; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                view.getRootItems().setAll(List.of(item("a"), item("b")));

                Scene scene = new Scene(new StackPane(view));
                scene.getStylesheets().add(css.toUri().toString());
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                ListView<?> column = (ListView<?>) view.lookup(".column0");
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
        Files.writeString(css, ".column1 { -fx-pref-width: 300; }");
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

                ListView<?> col0 = (ListView<?>) view.lookup(".column0");
                ListView<?> col1 = (ListView<?>) view.lookup(".column1");
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
     * Verifies author CSS spacing / padding on the columns box is counted in the
     * view's preferred size, so the popup (which follows the view's pref width) is
     * sized correctly. Measured as a delta against the un-styled baseline.
     *
     * @throws Exception if the temp stylesheet cannot be created or the FX task fails
     */
    @Test
    public void columnsBoxSpacingAndPaddingCountInViewPrefSize() throws Exception {
        Path css = Files.createTempFile("rx-cascader-columns-box", ".css");
        Files.writeString(css, ".rx-cascader-view > .columns { -fx-spacing: 8; -fx-padding: 6; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                RXCascaderItem<String> asia = item("asia");
                asia.getChildren().setAll(List.of(item("china"), item("japan")));
                view.getRootItems().setAll(List.of(asia));

                Scene scene = new Scene(new StackPane(view));
                scene.getRoot().applyCss();
                scene.getRoot().layout();
                view.expand(asia);
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                double baseWidth = view.prefWidth(-1.0);
                double baseHeight = view.prefHeight(-1.0);

                scene.getStylesheets().add(css.toUri().toString());
                scene.getRoot().applyCss();
                scene.getRoot().layout();

                // Two columns => one 8px spacing gap; 6px padding on all sides
                // (12px per axis). The columns box css leaves the column widths
                // unchanged, so the deltas isolate the spacing + padding.
                assertEquals(baseWidth + 8.0 + 12.0, view.prefWidth(-1.0), 0.5,
                        "columns box spacing + horizontal padding must count in view prefWidth");
                assertEquals(baseHeight + 12.0, view.prefHeight(-1.0), 0.5,
                        "columns box vertical padding must count in view prefHeight");
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
            branch.setLoadState(LoadState.LOADING);
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".column0");
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

            branch.setLoadState(LoadState.LOADED);
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
                    return new Label("X-" + cellItem.getValue());
                }
            });
            view.getRootItems().setAll(List.of(item("a")));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".column0");
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
                    setText(empty || cellItem == null ? null : "naked:" + cellItem.getValue());
                }
            });
            view.getRootItems().setAll(List.of(item("a")));

            Scene scene = new Scene(new StackPane(view), 260, 220);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> column = (ListView<?>) view.lookup(".column0");
            assertNotNull(column, "root column should exist");
            column.applyCss();
            column.layout();

            assertEquals(0, column.lookupAll(".rx-cascader-cell").size(),
                    "a naked ListCell does not carry the rx-cascader-cell style class");
        });
    }

    /**
     * Verifies deferred columns: while a lazy frontier is loading no next column
     * appears, and the column shows up only once the load completes (driven by
     * the skin's frontier monitor).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void loadingFrontierDefersNextColumn() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            CompletableFuture<List<RXCascaderItem<String>>> future = new CompletableFuture<>();
            view.setChildrenLoader(item -> future);
            RXCascaderItem<String> branch = item("branch");
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 320, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            view.expand(branch);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(LoadState.LOADING, branch.getLoadState(), "frontier should be loading after expand");
            assertEquals(0, view.lookupAll(".column1").size(),
                    "no next column while the frontier is loading");

            // Completing on the FX thread runs completeLoad inline; the frontier
            // monitor then rebuilds and the second column appears.
            RXCascaderItem<String> child = item("child");
            child.setLeafHint(true);
            future.complete(List.of(child));
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(LoadState.LOADED, branch.getLoadState(), "frontier should be loaded after completion");
            assertEquals(1, view.lookupAll(".column1").size(),
                    "the next column appears once loading completes");
        });
    }

    /**
     * Verifies the always-present root column shows the placeholder when the root
     * item list is empty — the one empty-column case that needs no forced branch.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void emptyRootColumnShowsPlaceholder() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setEmptyText("Nothing here"); // no root items at all

            Scene scene = new Scene(new StackPane(view), 320, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> rootColumn = (ListView<?>) view.lookup(".column0");
            assertNotNull(rootColumn, "the root column always renders");
            assertTrue(rootColumn.getItems().isEmpty(), "the root list is empty");
            assertTrue(rootColumn.getPlaceholder() instanceof Label);
            assertEquals("Nothing here", ((Label) rootColumn.getPlaceholder()).getText(),
                    "an empty root column shows the placeholder");
        });
    }

    /**
     * Verifies a forced empty branch renders a frontier column whose placeholder
     * shows the default empty text.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void emptyColumnUsesDefaultPlaceholderText() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setChildrenLoader(branchItem -> CompletableFuture.completedFuture(List.of()));
            RXCascaderItem<String> branch = item("branch");
            branch.setLeafHint(false); // a forced branch stays a non-leaf when loaded empty
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 320, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            view.expand(branch); // completed loader resolves inline to an empty frontier column
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> emptyColumn = (ListView<?>) view.lookup(".column1");
            assertNotNull(emptyColumn, "a forced empty branch renders a frontier column");
            assertTrue(emptyColumn.getItems().isEmpty(), "the forced branch loaded to zero children");
            assertTrue(emptyColumn.getPlaceholder() instanceof Label, "the empty column uses a label placeholder");
            assertEquals("No data", ((Label) emptyColumn.getPlaceholder()).getText(),
                    "the default empty text renders");
        });
    }

    /**
     * Verifies the empty-column placeholder honors a preset {@code emptyText} and
     * updates reactively when it changes on an already-rendered column.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void emptyColumnReflectsEmptyTextChange() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setEmptyText("暂无数据");
            view.setChildrenLoader(branchItem -> CompletableFuture.completedFuture(List.of()));
            RXCascaderItem<String> branch = item("branch");
            branch.setLeafHint(false);
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 320, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            view.expand(branch);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> emptyColumn = (ListView<?>) view.lookup(".column1");
            assertEquals("暂无数据", ((Label) emptyColumn.getPlaceholder()).getText(),
                    "a preset empty text renders in the empty column");

            view.setEmptyText("empty");
            assertEquals("empty", ((Label) emptyColumn.getPlaceholder()).getText(),
                    "changing empty text updates the existing column placeholder reactively");
        });
    }

    /**
     * Verifies revealing the current selection navigates the columns down to the
     * selected leaf and shows its mark (the popup-open behavior of M-08). Before
     * reveal, select alone leaves the columns at the root.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void revealSelectedPathExpandsAndHighlights() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> europe = item("europe");
            RXCascaderItem<String> germany = item("germany");
            RXCascaderItem<String> berlin = item("berlin");
            germany.getChildren().add(berlin);
            europe.getChildren().add(germany);
            view.getRootItems().setAll(List.of(europe));
            view.select(berlin);

            Scene scene = new Scene(new StackPane(view), 480, 260);
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            assertEquals(0, view.lookupAll(".column1").size(),
                    "select alone does not navigate the columns");

            view.revealSelectedPath();
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertEquals(1, view.lookupAll(".column0").size());
            assertEquals(1, view.lookupAll(".column1").size());
            assertEquals(1, view.lookupAll(".column2").size(),
                    "columns expand down to the selected leaf's column");

            var checks = view.lookupAll(".rx-cascader-cell > .container > .selected-check");
            assertEquals(1, checks.stream().filter(Node::isVisible).count(),
                    "the revealed selected leaf shows its selection mark");
        });
    }

    /**
     * Verifies revealing scrolls tall columns so the active branch and the selected
     * leaf become visible instead of staying below the fold. Each column has many
     * siblings with the target row last, and a small visibleRowCount keeps the
     * viewport short.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void revealScrollsTallColumnsToTheSelection() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setVisibleRowCount(3); // short viewport: deep rows fall below the fold

            List<RXCascaderItem<String>> roots = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                roots.add(item("root" + i));
            }
            RXCascaderItem<String> europe = item("europe");
            roots.add(europe); // last root, below the fold

            List<RXCascaderItem<String>> leaves = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                leaves.add(item("city" + i));
            }
            RXCascaderItem<String> berlin = item("berlin");
            leaves.add(berlin); // last leaf, below the fold
            europe.getChildren().setAll(leaves);
            view.getRootItems().setAll(roots);
            view.select(berlin);

            Scene scene = new Scene(new StackPane(view), 420, 150);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            view.revealSelectedPath();
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            scene.getRoot().layout(); // second pass so the queued scroll renders its cells

            ListView<?> rootColumn = (ListView<?>) view.lookup(".column0");
            ListView<?> leafColumn = (ListView<?>) view.lookup(".column1");
            assertTrue(rendersItem(rootColumn, europe),
                    "the root column scrolls so the active branch becomes visible");
            assertTrue(rendersItem(leafColumn, berlin),
                    "the leaf column scrolls so the selected leaf becomes visible");
        });
    }

    private static boolean rendersItem(ListView<?> column, RXCascaderItem<?> target) {
        return column.lookupAll(".rx-cascader-cell").stream()
                .anyMatch(node -> node instanceof ListCell<?> cell && cell.getItem() == target);
    }

    /**
     * Verifies restamping the positional ordinals removes only the {@code columnN}
     * ordinal classes, not the base {@code column} class or an author class that
     * merely shares the prefix (e.g. {@code column-custom}).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void restampingKeepsBaseAndAuthorPrefixedColumnClasses() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            asia.getChildren().add(item("beijing"));
            view.getRootItems().setAll(List.of(asia, item("europe")));

            Scene scene = new Scene(new StackPane(view), 420, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            ListView<?> rootColumn = (ListView<?>) view.lookup(".column0");
            assertNotNull(rootColumn, "root column should exist");
            // An author class sharing the "column" prefix but not an ordinal.
            rootColumn.getStyleClass().add("column-custom");

            // Expanding adds a second column and re-syncs, which restamps ordinals
            // on the reused root column.
            view.expand(asia);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            assertTrue(rootColumn.getStyleClass().contains("column"),
                    "the base column class survives restamping");
            assertTrue(rootColumn.getStyleClass().contains("column0"),
                    "the reused root column keeps ordinal column0");
            assertTrue(rootColumn.getStyleClass().contains("column-custom"),
                    "an author class sharing the column prefix must survive restamping");
        });
    }

    /**
     * Verifies single-selection mode shows the check mark only on the selected
     * leaf, while the left slot stays reserved (managed) on every row.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void singleSelectionShowsCheckOnSelectedLeaf() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            RXCascaderItem<String> beijing = item("beijing");
            RXCascaderItem<String> shanghai = item("shanghai");
            asia.getChildren().setAll(List.of(beijing, shanghai));
            view.getRootItems().setAll(List.of(asia));

            Scene scene = new Scene(new StackPane(view), 420, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            view.expand(asia);
            view.activate(beijing);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            var checks = view.lookupAll(".rx-cascader-cell > .container > .selected-check");
            assertFalse(checks.isEmpty(), "selected-check nodes should be present");
            assertEquals(1, checks.stream().filter(Node::isVisible).count(),
                    "only the selected leaf shows the check mark");
            assertTrue(checks.stream().allMatch(Node::isManaged),
                    "single mode keeps the left slot reserved on every row");
        });
    }

    /**
     * Verifies multiple-selection mode neither shows nor reserves the
     * single-selection check slot (the check box takes that side).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void multipleSelectionHidesSelectedCheckSlot() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> asia = item("asia");
            asia.getChildren().setAll(List.of(item("beijing"), item("shanghai")));
            view.getRootItems().setAll(List.of(asia));

            Scene scene = new Scene(new StackPane(view), 420, 240);
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            var checks = view.lookupAll(".rx-cascader-cell > .container > .selected-check");
            assertFalse(checks.isEmpty(), "selected-check nodes should be present");
            assertTrue(checks.stream().noneMatch(Node::isVisible),
                    "no check mark in multiple mode");
            assertTrue(checks.stream().noneMatch(Node::isManaged),
                    "multiple mode does not reserve the single-selection slot");
        });
    }

    /**
     * Verifies an inline (already-completed) loader produces the correct column
     * count: each expand resolves LOADING-&gt;LOADED within a single pulse and the
     * deferred frontier column is appended, so the final count is depth + 1.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void inlineLoaderExpandYieldsCorrectColumnCount() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.setChildrenLoader(it -> {
                if ("root".equals(it.getValue())) {
                    return CompletableFuture.completedFuture(List.of(item("a")));
                }
                if ("a".equals(it.getValue())) {
                    return CompletableFuture.completedFuture(List.of(item("a1")));
                }
                return CompletableFuture.completedFuture(List.of());
            });
            RXCascaderItem<String> root = item("root");
            view.getRootItems().setAll(List.of(root));

            Scene scene = new Scene(new StackPane(view), 600, 240);
            relayout(scene);

            // Depth 0: only the root column.
            assertEquals(1, view.lookupAll(".column0").size());
            assertEquals(0, view.lookupAll(".column1").size());

            view.expand(root);
            relayout(scene);
            // Depth 1: the inline completion appends the children column in one pulse.
            assertEquals(1, view.lookupAll(".column1").size());
            assertEquals(0, view.lookupAll(".column2").size());

            RXCascaderItem<String> a = root.getChildren().get(0);
            view.expand(a);
            relayout(scene);
            // Depth 2: three columns total.
            assertEquals(1, view.lookupAll(".column2").size());
        });
    }

    /**
     * Baseline navigation harness: expanding deeper adds columns and switching to
     * a shallower branch drops them. Guards the per-step column count contract
     * the Phase 3 tail-diff rewrite must preserve.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void navigationColumnCountHarness() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            RXCascaderItem<String> china = item("china");
            china.getChildren().setAll(List.of(item("beijing"), item("shanghai")));
            asia.getChildren().setAll(List.of(china, item("japan")));
            RXCascaderItem<String> europe = item("europe");
            europe.getChildren().setAll(List.of(item("france")));
            view.getRootItems().setAll(List.of(asia, europe));

            Scene scene = new Scene(new StackPane(view), 800, 240);
            relayout(scene);
            assertEquals(1, columnCount(view), "only the root column initially");

            view.expand(asia);
            relayout(scene);
            assertEquals(2, columnCount(view), "expanding asia adds its children column");

            view.expand(china);
            relayout(scene);
            assertEquals(3, columnCount(view), "expanding china adds a third column");

            view.expand(europe);
            relayout(scene);
            assertEquals(2, columnCount(view), "switching to europe drops china's column");
        });
    }

    /**
     * Verifies a descendant cell reflects an ancestor becoming disabled
     * (effective-disabled): the cell observes its whole ancestor chain's
     * disabled state, not only its own item's (D1, fixed in Phase 3).
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void ancestorDisabledRefreshesDescendantCell() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> ancestor = item("ancestor");
            RXCascaderItem<String> leaf = item("leaf");
            ancestor.getChildren().add(leaf);
            view.getRootItems().setAll(List.of(ancestor));

            Scene scene = new Scene(new StackPane(view), 420, 240);
            relayout(scene);

            view.expand(ancestor);
            relayout(scene);

            ListView<?> leafColumn = (ListView<?>) view.lookup(".column1");
            assertNotNull(leafColumn, "the descendant column should exist after expand");
            leafColumn.applyCss();
            leafColumn.layout();

            Node leafCell = filledCell(leafColumn, leaf);
            assertNotNull(leafCell, "the leaf's cell should be realized");
            assertFalse(leafCell.isDisabled(), "precondition: descendant cell starts enabled");

            ancestor.setDisable(true);
            relayout(scene);
            leafColumn.applyCss();
            leafColumn.layout();

            leafCell = filledCell(leafColumn, leaf);
            assertNotNull(leafCell);
            assertTrue(leafCell.isDisabled(),
                    "descendant cell must reflect an ancestor becoming disabled (effective-disabled)");
        });
    }

    /**
     * Verifies the keep-prefix / replace-tail diff reuses the unchanged prefix
     * column instances and only rebuilds the changed tail when navigating to a
     * sibling branch.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void tailDiffReusesPrefixColumnsNoTeardown() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> a = item("a");
            a.getChildren().setAll(List.of(item("a1"), item("a2")));
            RXCascaderItem<String> b = item("b");
            b.getChildren().setAll(List.of(item("b1")));
            view.getRootItems().setAll(List.of(a, b));

            Scene scene = new Scene(new StackPane(view), 800, 240);
            relayout(scene);

            view.expand(a);
            relayout(scene);
            ListView<?> rootColumn = (ListView<?>) view.lookup(".column0");
            ListView<?> aColumn = (ListView<?>) view.lookup(".column1");
            assertNotNull(rootColumn);
            assertNotNull(aColumn);

            view.expand(b);
            relayout(scene);
            ListView<?> rootColumnAfter = (ListView<?>) view.lookup(".column0");
            ListView<?> bColumn = (ListView<?>) view.lookup(".column1");

            assertSame(rootColumn, rootColumnAfter, "the unchanged root column is reused, not torn down");
            assertNotSame(aColumn, bColumn, "the changed tail column is replaced");
            assertSame(b.getChildren(), bColumn.getItems(), "column 1 now backs b's children");
        });
    }

    /**
     * Verifies ordinal style classes are restamped by position after a tail-diff:
     * navigating from a deep path to a shallower sibling drops the stale higher
     * ordinal and leaves each ordinal lookup pointing at the current branch.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void reusedColumnRestampsOrdinalStyleClass() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> a = item("a");
            RXCascaderItem<String> a1 = item("a1");
            a1.getChildren().setAll(List.of(item("a1x")));
            a.getChildren().setAll(List.of(a1));
            RXCascaderItem<String> b = item("b");
            b.getChildren().setAll(List.of(item("b1")));
            view.getRootItems().setAll(List.of(a, b));

            Scene scene = new Scene(new StackPane(view), 900, 240);
            relayout(scene);

            view.expand(a);
            view.expand(a1);
            relayout(scene);
            assertEquals(1, view.lookupAll(".column2").size(), "deep path has a third column");

            view.expand(b);
            relayout(scene);

            assertEquals(0, view.lookupAll(".column2").size(),
                    "the stale tail ordinal is removed after navigating shallower");
            ListView<?> column1 = (ListView<?>) view.lookup(".column1");
            assertNotNull(column1);
            assertSame(b.getChildren(), column1.getItems(),
                    "the column-1 ordinal points at the current branch, not a stale one");
        });
    }

    /**
     * Verifies setting the view's columnWidth / rowHeight applies to the rendered
     * columns at runtime.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void columnWidthAndRowHeightApplyToColumns() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.getRootItems().setAll(List.of(item("a"), item("b")));

            Scene scene = new Scene(new StackPane(view));
            relayout(scene);

            view.setColumnWidth(250.0);
            view.setRowHeight(50.0);
            relayout(scene);

            ListView<?> column = (ListView<?>) view.lookup(".column0");
            assertNotNull(column, "root column should exist");
            assertEquals(250.0, column.prefWidth(-1.0), 0.001, "column adopts the view's columnWidth");
            assertEquals(50.0, column.getFixedCellSize(), 0.001, "column adopts the view's rowHeight");
        });
    }

    /**
     * Verifies {@code -rx-column-width} on the view sets the column default, while
     * author CSS targeting {@code .column} still wins over it.
     *
     * @throws Exception if the temp stylesheet cannot be created or the FX task fails
     */
    @Test
    public void rxColumnWidthCssAppliesAndAuthorColumnCssWins() throws Exception {
        Path css = Files.createTempFile("rx-cascader-colwidth", ".css");
        Files.writeString(css, ".rx-cascader-view { -rx-column-width: 250; }"
                + " .column1 { -fx-pref-width: 300; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                RXCascaderItem<String> asia = item("asia");
                asia.getChildren().setAll(List.of(item("china"), item("japan")));
                view.getRootItems().setAll(List.of(asia));

                Scene scene = new Scene(new StackPane(view));
                scene.getStylesheets().add(css.toUri().toString());
                relayout(scene);

                assertEquals(250.0, view.getColumnWidth(), 0.001, "-rx-column-width sets the view property");

                view.expand(asia);
                relayout(scene);

                ListView<?> col0 = (ListView<?>) view.lookup(".column0");
                ListView<?> col1 = (ListView<?>) view.lookup(".column1");
                assertNotNull(col0, "root column should exist");
                assertNotNull(col1, "second column should exist after expand");
                assertEquals(250.0, col0.prefWidth(-1.0), 0.001, "column adopts the -rx-column-width default");
                assertEquals(300.0, col1.prefWidth(-1.0), 0.001, "author .column CSS wins over the default");
            });
        } finally {
            Files.deleteIfExists(css);
        }
    }

    /**
     * Verifies a runtime {@code columnWidth} change does not clobber a column whose
     * width was set by author CSS (the "author wins" contract holds at runtime).
     *
     * @throws Exception if the temp stylesheet cannot be created or the FX task fails
     */
    @Test
    public void runtimeColumnWidthDoesNotClobberAuthorColumnCss() throws Exception {
        Path css = Files.createTempFile("rx-cascader-runtime-width", ".css");
        Files.writeString(css, ".column0 { -fx-pref-width: 300; }");
        try {
            runOnFx(() -> {
                RXCascaderView<String> view = new RXCascaderView<>();
                view.getRootItems().setAll(List.of(item("a"), item("b")));
                Scene scene = new Scene(new StackPane(view));
                scene.getStylesheets().add(css.toUri().toString());
                relayout(scene);

                ListView<?> col0 = (ListView<?>) view.lookup(".column0");
                assertNotNull(col0, "root column should exist");
                assertEquals(300.0, col0.prefWidth(-1.0), 0.001, "author CSS sets the column width");

                view.setColumnWidth(250.0);
                relayout(scene);
                assertEquals(300.0, col0.prefWidth(-1.0), 0.001,
                        "author column CSS still wins after a runtime columnWidth change");
            });
        } finally {
            Files.deleteIfExists(css);
        }
    }

    /**
     * Verifies a non-positive rowHeight falls back to the default so fixed-cell
     * mode (and a sane row slot height) is preserved.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void nonPositiveRowHeightFallsBackToDefault() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            view.getRootItems().setAll(List.of(item("a")));
            Scene scene = new Scene(new StackPane(view));
            relayout(scene);

            view.setRowHeight(0.0);
            relayout(scene);

            ListView<?> col0 = (ListView<?>) view.lookup(".column0");
            assertNotNull(col0, "root column should exist");
            assertEquals(RXCascaderView.DEFAULT_FIXED_CELL_SIZE, col0.getFixedCellSize(), 0.001,
                    "a non-positive rowHeight falls back to the default fixed cell size");
        });
    }

    /**
     * Verifies the active-path highlight on a reused (tail-diff prefix) column is
     * refreshed when navigation switches siblings, instead of going stale.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void inActivePathPseudoClassRefreshesOnReusedColumn() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> a = item("a");
            a.getChildren().setAll(List.of(item("a1")));
            RXCascaderItem<String> b = item("b");
            b.getChildren().setAll(List.of(item("b1")));
            view.getRootItems().setAll(List.of(a, b));

            Scene scene = new Scene(new StackPane(view), 600, 240);
            relayout(scene);
            ListView<?> rootColumn = (ListView<?>) view.lookup(".column0");
            assertNotNull(rootColumn, "root column should exist");

            view.expand(a);
            relayout(scene);
            rootColumn.applyCss();
            rootColumn.layout();
            assertTrue(inActivePath(rootColumn, a), "a is in the active path after expanding it");
            assertFalse(inActivePath(rootColumn, b), "b is not in the active path");

            view.expand(b);
            relayout(scene);
            rootColumn.applyCss();
            rootColumn.layout();
            assertFalse(inActivePath(rootColumn, a),
                    "a's active-path highlight on the reused root column is cleared");
            assertTrue(inActivePath(rootColumn, b), "b now shows the active-path highlight");
        });
    }

    /**
     * Verifies a failed lazy branch's cell carries the {@code :load-failed} pseudo
     * class so the failure (and that re-expanding retries) is discoverable.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void failedBranchCellHasLoadFailedPseudoClass() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> branch = item("branch");
            view.setChildrenLoader(it -> CompletableFuture.failedFuture(new IllegalStateException("boom")));
            view.getRootItems().setAll(List.of(branch));

            Scene scene = new Scene(new StackPane(view), 300, 200);
            relayout(scene);

            view.expand(branch);
            relayout(scene);
            ListView<?> col0 = (ListView<?>) view.lookup(".column0");
            assertNotNull(col0, "root column should exist");
            col0.applyCss();
            col0.layout();

            assertEquals(LoadState.FAILED, branch.getLoadState());
            Node cell = filledCell(col0, branch);
            assertNotNull(cell, "branch cell should be realized");
            assertTrue(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("load-failed")),
                    "a failed branch cell carries the :load-failed pseudo class");
        });
    }

    /**
     * Verifies inline keyboard navigation: the first arrow seeds the keyboard
     * focus, Right expands the focused branch and moves into its column, Down
     * moves within the column, and Left steps back to the expanded ancestor.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardNavigationTraversesColumns() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            RXCascaderItem<String> china = item("china");
            china.getChildren().setAll(List.of(item("shanghai"), item("hangzhou")));
            asia.getChildren().setAll(List.of(china, item("japan")));
            view.getRootItems().setAll(List.of(asia));
            Scene scene = new Scene(new StackPane(view));
            relayout(scene);

            fireKey(view, KeyCode.DOWN);
            assertEquals(0, focusedIndex(view, ".column0"), "first arrow seeds the first row");

            fireKey(view, KeyCode.RIGHT);
            relayout(scene);
            assertEquals(2, columnCount(view), "Right expands the focused branch");
            assertEquals(0, focusedIndex(view, ".column1"), "focus moves into the child column");
            assertEquals(-1, focusedIndex(view, ".column0"), "the parent column focus is cleared");

            fireKey(view, KeyCode.DOWN);
            assertEquals(1, focusedIndex(view, ".column1"));

            fireKey(view, KeyCode.LEFT);
            assertEquals(0, focusedIndex(view, ".column0"), "Left refocuses the expanded ancestor");
            assertEquals(-1, focusedIndex(view, ".column1"));
        });
    }

    /**
     * Verifies Up / Down skip disabled rows and clamp at the enabled edges.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardVerticalNavigationSkipsDisabledItems() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> locked = item("locked");
            locked.setDisable(true);
            view.getRootItems().setAll(List.of(item("first"), locked, item("third")));
            relayout(new Scene(new StackPane(view)));

            fireKey(view, KeyCode.DOWN);
            fireKey(view, KeyCode.DOWN);
            assertEquals(2, focusedIndex(view, ".column0"), "Down skips the disabled row");
            fireKey(view, KeyCode.UP);
            assertEquals(0, focusedIndex(view, ".column0"), "Up skips it back");
            fireKey(view, KeyCode.UP);
            assertEquals(0, focusedIndex(view, ".column0"), "clamped at the first enabled row");
        });
    }

    /**
     * Verifies Enter selects the focused leaf in single mode and Space toggles
     * its check in multiple mode.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void keyboardEnterAndSpaceActivateFocusedLeaf() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> single = new RXCascaderView<>();
            RXCascaderItem<String> leafItem = item("leaf");
            single.getRootItems().setAll(List.of(leafItem));
            relayout(new Scene(new StackPane(single)));
            fireKey(single, KeyCode.DOWN);
            fireKey(single, KeyCode.ENTER);
            assertNotNull(single.getSelectedPath(), "Enter selects the focused leaf");
            assertSame(leafItem, single.getSelectedPath().getLeaf());

            RXCascaderView<String> multiple = new RXCascaderView<>();
            multiple.setSelectionMode(SelectionMode.MULTIPLE);
            RXCascaderItem<String> checkable = item("checkable");
            multiple.getRootItems().setAll(List.of(checkable));
            relayout(new Scene(new StackPane(multiple)));
            fireKey(multiple, KeyCode.DOWN);
            fireKey(multiple, KeyCode.SPACE);
            assertTrue(checkable.isChecked(), "Space toggles the focused leaf's check on");
            fireKey(multiple, KeyCode.SPACE);
            assertFalse(checkable.isChecked(), "Space toggles it back off");
        });
    }

    /**
     * Verifies a selection change re-renders in place: no cell is rebound (no
     * updateItem storm, no custom-content rebuild — the flicker bug), while the
     * {@code :active} pseudo class still moves between the live cells through
     * the cells' own view-state listeners.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void selectionChangeDoesNotRebindCells() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> asia = item("asia");
            RXCascaderItem<String> shanghai = item("shanghai");
            RXCascaderItem<String> hangzhou = item("hangzhou");
            asia.getChildren().setAll(List.of(shanghai, hangzhou));
            view.getRootItems().setAll(List.of(asia));
            AtomicInteger contentBuilds = new AtomicInteger();
            view.setCellFactory(v -> new RXCascaderCell<>(v) {
                @Override
                protected Node createContent(RXCascaderItem<String> item) {
                    contentBuilds.incrementAndGet();
                    return super.createContent(item);
                }
            });
            // Sized scene: the virtual flow only materializes cells with a viewport.
            Scene scene = new Scene(new StackPane(view), 420, 240);
            relayout(scene);
            view.expand(asia);
            relayout(scene);

            int builds = contentBuilds.get();
            view.activate(shanghai);
            relayout(scene);
            assertEquals(builds, contentBuilds.get(),
                    "selecting a leaf must not rebind or rebuild any cell content");
            Node selected = filledCell((ListView<?>) view.lookup(".column1"), shanghai);
            assertTrue(selected.getPseudoClassStates().contains(PseudoClass.getPseudoClass("active")),
                    "the selected cell picks up :active through its own listener");

            view.activate(hangzhou);
            assertEquals(builds, contentBuilds.get(),
                    "changing the selection must not rebind either");
            Node previous = filledCell((ListView<?>) view.lookup(".column1"), shanghai);
            assertFalse(previous.getPseudoClassStates().contains(PseudoClass.getPseudoClass("active")),
                    "the previous cell clears :active without a rebuild");
        });
    }

    /**
     * Verifies the cells track loader-driven leaf semantics in place: clearing
     * the children loader turns an unloaded childless branch back into a leaf
     * on the live cell, without any column refresh.
     *
     * @throws InterruptedException if the FX task is interrupted
     */
    @Test
    public void clearingLoaderFlipsCellLeafPseudoWithoutRebuild() throws InterruptedException {
        runOnFx(() -> {
            RXCascaderView<String> view = new RXCascaderView<>();
            RXCascaderItem<String> root = item("root");
            view.getRootItems().setAll(List.of(root));
            view.setChildrenLoader(it -> new CompletableFuture<>());
            // Sized scene: the virtual flow only materializes cells with a viewport.
            Scene scene = new Scene(new StackPane(view), 420, 240);
            relayout(scene);
            Node cell = filledCell((ListView<?>) view.lookup(".column0"), root);
            assertNotNull(cell, "root cell should exist");
            assertFalse(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("leaf")),
                    "an unloaded childless item is a branch while a loader is set");

            view.setChildrenLoader(null);
            assertTrue(cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("leaf")),
                    "clearing the loader flips the childless item back to a leaf in place");
        });
    }

    private static int focusedIndex(RXCascaderView<?> view, String columnSelector) {
        ListView<?> column = (ListView<?>) view.lookup(columnSelector);
        assertNotNull(column, columnSelector + " should exist");
        return column.getFocusModel().getFocusedIndex();
    }

    private static void fireKey(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, KeyEvent.CHAR_UNDEFINED, "", code,
                false, false, false, false));
    }

    private static RXCascaderItem<String> item(String text) {
        return new RXCascaderItem<>(text);
    }

    private static int columnCount(RXCascaderView<?> view) {
        return view.lookupAll(".column").size();
    }

    private static Node filledCell(ListView<?> column, RXCascaderItem<?> item) {
        for (Node node : column.lookupAll(".rx-cascader-cell")) {
            if (node instanceof ListCell) {
                ListCell<?> cell = (ListCell<?>) node;
                if (cell.getItem() == item) {
                    return node;
                }
            }
        }
        return null;
    }

    private static boolean inActivePath(ListView<?> column, RXCascaderItem<?> item) {
        Node cell = filledCell(column, item);
        return cell != null && cell.getPseudoClassStates().contains(PseudoClass.getPseudoClass("in-active-path"));
    }

    private static void relayout(Scene scene) {
        scene.getRoot().applyCss();
        scene.getRoot().layout();
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

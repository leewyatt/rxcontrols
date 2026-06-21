package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skin / virtualization tests for {@link RXGridView}: column derivation, the
 * item-index to row/column mapping, null-item rendering, scroll consumption,
 * churn discipline, placeholder and the {@code :empty} state. Each test drives a
 * real (headless) layout pass.
 */
public class RXGridViewSkinTest {

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
        // Pin modena so the inner ScrollBar gets a real measured width.
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void emptyGridReportsEmptyMetricsAndPseudoClass() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = new RXGridView<>();
            pump(host(grid, 400, 300));
            assertEquals(0, grid.getRowCount());
            assertTrue(grid.getActualColumnCount() >= 1);
            assertTrue(grid.getVisibleRange().isEmpty());
            assertTrue(grid.getPseudoClassStates().stream()
                    .anyMatch(pc -> pc.getPseudoClassName().equals("empty")));
        });
    }

    @Test
    public void columnCountDerivedFromWidthWithoutScrollBar() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(6);
            grid.setCellWidth(100);
            grid.setCellHeight(100);
            grid.setHgap(10);
            grid.setVgap(10);
            // 6 items in <=2 rows of 110px fit in 300px height: no vertical scroll bar.
            pump(host(grid, 350, 300));
            // floor((350 + 10) / (100 + 10)) = 3
            assertEquals(3, grid.getActualColumnCount());
            assertEquals(2, grid.getRowCount());
        });
    }

    @Test
    public void forcedColumnCountOverridesWidth() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(20);
            grid.setColumnCount(5);
            pump(host(grid, 800, 300));
            assertEquals(5, grid.getActualColumnCount());
            assertEquals(4, grid.getRowCount());
        });
    }

    @Test
    public void maxColumnsClampsDerivedCount() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(20);
            grid.setCellWidth(50);
            grid.setHgap(0);
            StackPane root = host(grid, 800, 300);
            pump(root);
            assertTrue(grid.getActualColumnCount() > 2, "unclamped width yields many columns");

            grid.setMaxColumns(2);
            pump(root);
            assertEquals(2, grid.getActualColumnCount(), "maxColumns clamps the derived count");
        });
    }

    @Test
    public void hgapParticipatesInColumnCount() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(6);
            grid.setCellWidth(100);
            grid.setHgap(0);
            pump(host(grid, 350, 300));
            // floor((350 + 0) / (100 + 0)) = 3
            assertEquals(3, grid.getActualColumnCount());

            grid.setHgap(60);
            pump(host(grid, 350, 300));
            // floor((350 + 60) / (100 + 60)) = floor(410/160) = 2
            assertEquals(2, grid.getActualColumnCount());
        });
    }

    @Test
    public void cellKnowsItsRowAndColumn() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(20);
            grid.setColumnCount(3);
            pump(host(grid, 400, 400));
            RXGridCell<?> cell = cellByIndex(grid, 7);
            assertNotNull(cell, "cell for index 7 should be realized");
            assertEquals(2, cell.getRowIndex(), "7 / 3 = row 2");
            assertEquals(1, cell.getColumnIndex(), "7 % 3 = column 1");
        });
    }

    @Test
    public void nullItemRendersNonEmpty() throws Exception {
        onFx(() -> {
            ObservableList<String> items = FXCollections.observableArrayList("a", "b", null, "d");
            RXGridView<String> grid = new RXGridView<>(items);
            grid.setColumnCount(4);
            pump(host(grid, 600, 300));
            RXGridCell<?> cell = cellByIndex(grid, 2);
            assertNotNull(cell, "a null item still has a (non-empty) cell");
            assertNull(cell.getItem());
            assertFalse(cell.isEmpty(), "null item at a valid index is not empty");
        });
    }

    @Test
    public void scrollToConsumesPendingRequestOnce() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(200);
            grid.setColumnCount(2);
            StackPane root = host(grid, 400, 300);
            pump(root);
            grid.scrollTo(150);
            assertTrue(grid.hasPendingScroll());
            pump(root);
            assertFalse(grid.hasPendingScroll(), "pending scroll consumed by the layout pass");
            assertTrue(grid.getVisibleRange().firstIndex() > 0, "viewport scrolled away from the top");
        });
    }

    @Test
    public void changingCellWidthReusesCellsWhileFactoryChangeRecreates() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(30);
            grid.setColumnCount(3); // keep the column count fixed so width only resizes cells
            StackPane root = host(grid, 400, 400);
            pump(root);

            Set<Node> before = new HashSet<>(grid.lookupAll(".rx-grid-cell"));
            assertFalse(before.isEmpty());

            grid.setCellWidth(80);
            pump(root);
            Set<Node> afterResize = new HashSet<>(grid.lookupAll(".rx-grid-cell"));
            assertEquals(before, afterResize, "cell-width change must reuse cell instances, not recreate");

            grid.setCellFactory(gv -> new RXGridCell<>());
            pump(root);
            Set<Node> afterFactory = new HashSet<>(grid.lookupAll(".rx-grid-cell"));
            assertTrue(java.util.Collections.disjoint(before, afterFactory),
                    "cell-factory change must recreate cells");
        });
    }

    @Test
    public void placeholderShownOnlyWhenEmpty() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = new RXGridView<>();
            Label placeholder = new Label("Empty");
            grid.setPlaceholder(placeholder);
            StackPane root = host(grid, 400, 300);
            pump(root);
            assertTrue(placeholder.isVisible() && placeholder.getScene() != null,
                    "placeholder is shown while the grid is empty");

            grid.setItems(FXCollections.observableArrayList("a", "b", "c"));
            pump(root);
            assertNull(placeholder.getScene(), "placeholder removed once items appear");
            assertFalse(grid.getVisibleRange().isEmpty());
        });
    }

    @Test
    public void disposeStopsReactingToItemMutations() throws Exception {
        onFx(() -> {
            ObservableList<String> items = FXCollections.observableArrayList("a", "b", "c");
            RXGridView<String> grid = new RXGridView<>(items);
            StackPane root = host(grid, 400, 300);
            pump(root);
            int rowsBefore = grid.getRowCount();

            grid.setSkin(null); // disposes the current skin
            items.add("d"); // must not throw or be observed by the disposed skin
            assertEquals(rowsBefore, grid.getRowCount(),
                    "a disposed skin must not keep updating metrics");
        });
    }

    @Test
    public void stretchModeFillsTheRowEqually() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(8);
            grid.setColumnCount(4);
            grid.setHgap(20);
            grid.setItemsJustify(RXGridJustify.STRETCH);
            pump(host(grid, 400, 400));

            List<RXGridCell<?>> cells = rowCells(grid, 0);
            assertEquals(4, cells.size());
            double width0 = cells.get(0).getWidth();
            assertTrue(width0 > 0);
            for (RXGridCell<?> cell : cells) {
                assertEquals(width0, cell.getWidth(), 1.0, "stretched cells share one width");
            }
            assertEquals(0.0, cells.get(0).getLayoutX(), 1.0, "first cell hugs the leading edge");
            double gap = cells.get(1).getLayoutX() - (cells.get(0).getLayoutX() + width0);
            assertEquals(20.0, gap, 1.0, "inter-cell gap equals hgap");
        });
    }

    @Test
    public void itemsJustifyDistributesRowSlack() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(8);
            grid.setColumnCount(4);
            grid.setCellWidth(60);
            grid.setHgap(10);
            StackPane root = host(grid, 400, 400);

            grid.setItemsJustify(RXGridJustify.START);
            pump(root);
            double startX = rowCells(grid, 0).get(0).getLayoutX();

            grid.setItemsJustify(RXGridJustify.END);
            pump(root);
            double endX = rowCells(grid, 0).get(0).getLayoutX();

            grid.setItemsJustify(RXGridJustify.CENTER);
            pump(root);
            double centerX = rowCells(grid, 0).get(0).getLayoutX();

            assertEquals(0.0, startX, 1.0, "START packs cells against the leading edge");
            assertTrue(endX > centerX && centerX > startX, "slack grows START < CENTER < END");
            assertEquals(endX / 2.0, centerX, 1.0, "CENTER splits the slack");
        });
    }

    @Test
    public void spaceModesDistributeRowSlack() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(8);
            grid.setColumnCount(4);
            grid.setCellWidth(60);
            grid.setHgap(10);
            StackPane root = host(grid, 400, 400);

            // Fixture: N=4, cellWidth=60, hgap=10, row width 400 (no scroll bar),
            // so slack S = 400 - (4*60 + 3*10) = 130. Absolute values pin each divisor.
            grid.setItemsJustify(RXGridJustify.SPACE_BETWEEN);
            pump(root);
            double betweenEdge = firstCellX(grid);
            double betweenGap = firstGap(grid);
            assertEquals(0.0, betweenEdge, 0.5, "SPACE_BETWEEN keeps the edges flush");
            assertEquals(10.0 + 130.0 / 3.0, betweenGap, 0.5, "between gap = hgap + S/(N-1)");

            grid.setItemsJustify(RXGridJustify.SPACE_AROUND);
            pump(root);
            double aroundEdge = firstCellX(grid);
            double aroundGap = firstGap(grid);
            assertEquals(130.0 / 8.0, aroundEdge, 0.5, "AROUND edge = S/(2N)");
            assertEquals(10.0 + 130.0 / 4.0, aroundGap, 0.5, "AROUND gap = hgap + S/N");

            grid.setItemsJustify(RXGridJustify.SPACE_EVENLY);
            pump(root);
            double evenlyEdge = firstCellX(grid);
            double evenlyGap = firstGap(grid);
            assertEquals(130.0 / 5.0, evenlyEdge, 0.5, "EVENLY edge = S/(N+1)");
            assertEquals(10.0 + 130.0 / 5.0, evenlyGap, 0.5, "EVENLY gap = hgap + S/(N+1)");

            assertTrue(betweenEdge < aroundEdge && aroundEdge < evenlyEdge,
                    "the edge gap grows BETWEEN < AROUND < EVENLY");
        });
    }

    @Test
    public void maxCellWidthCapsAndCentersStretch() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(4);
            grid.setColumnCount(2);
            grid.setCellWidth(60);
            grid.setHgap(10);
            grid.setItemsJustify(RXGridJustify.STRETCH);
            StackPane root = host(grid, 400, 400);
            pump(root);

            // Uncapped: cells grow well past cellWidth and hug the leading edge.
            assertTrue(rowCells(grid, 0).get(0).getWidth() > 100.0,
                    "uncapped STRETCH grows cells to fill the row");
            assertEquals(0.0, firstCellX(grid), 1.0, "uncapped STRETCH starts at the leading edge");

            // Capped: cells stop at maxCellWidth and the block is centered.
            grid.setMaxCellWidth(80);
            pump(root);
            List<RXGridCell<?>> capped = rowCells(grid, 0);
            assertEquals(80.0, capped.get(0).getWidth(), 1.0, "STRETCH is capped at maxCellWidth");
            // N=2, cw=80, hgap=10, W=400 -> startX = (400 - (2*80 + 10)) / 2 = 115.
            assertEquals(115.0, firstCellX(grid), 1.0, "the capped block is centered");
            double lastRight = capped.get(1).getLayoutX() + capped.get(1).getWidth();
            assertEquals(400.0, firstCellX(grid) + lastRight, 1.0, "the centered block is symmetric in the row");

            // A cap below cellWidth is degenerate and must not shrink cells below cellWidth.
            grid.setMaxCellWidth(40);
            pump(root);
            assertEquals(60.0, rowCells(grid, 0).get(0).getWidth(), 1.0,
                    "a cap below cellWidth leaves cells at cellWidth");
        });
    }

    @Test
    public void shortFinalRowHasNoStaleCells() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(7);
            grid.setColumnCount(3);
            pump(host(grid, 400, 600));

            assertEquals(1, rowCells(grid, 2).size(), "row 2 holds the single 7th item");
            for (Node node : grid.lookupAll(".rx-grid-cell")) {
                if (node instanceof RXGridCell<?> cell && !cell.isEmpty()) {
                    assertTrue(cell.getIndex() < 7, "no cell maps an out-of-range index");
                }
            }
        });
    }

    @Test
    public void itemMutationsUpdateRowCountAndContent() throws Exception {
        onFx(() -> {
            ObservableList<String> items = FXCollections.observableArrayList();
            for (int i = 0; i < 6; i++) {
                items.add("Item " + i);
            }
            RXGridView<String> grid = new RXGridView<>(items);
            grid.setColumnCount(3);
            StackPane root = host(grid, 400, 600);
            pump(root);
            assertEquals(2, grid.getRowCount());

            items.addAll("Item 6", "Item 7", "Item 8");
            pump(root);
            assertEquals(3, grid.getRowCount());
            assertNotNull(cellByIndex(grid, 8), "a newly added item is realized");

            items.remove(5, items.size());
            pump(root);
            assertEquals(2, grid.getRowCount());
            assertNull(cellByIndex(grid, 8), "a removed item leaves no cell behind");
        });
    }

    @Test
    public void cellHeightChangeResizesRealizedCells() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(10);
            grid.setColumnCount(1);
            grid.setCellHeight(50);
            grid.setVgap(10);
            StackPane root = host(grid, 200, 400);
            pump(root);
            assertEquals(50.0, cellByIndex(grid, 0).getHeight(), 1.0);

            grid.setCellHeight(80);
            pump(root);
            assertEquals(80.0, cellByIndex(grid, 0).getHeight(), 1.0,
                    "the row slot tracks cellHeight so realized cells resize");
        });
    }

    @Test
    public void visibleRangeTailIsClampedToItemCount() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(7);
            grid.setColumnCount(3);
            pump(host(grid, 400, 600));
            RXGridVisibleRange range = grid.getVisibleRange();
            assertEquals(0, range.firstIndex());
            assertEquals(6, range.lastIndex(), "tail clamps to itemCount-1, not lastRow*cols+cols-1");
            assertEquals(7, range.size());
        });
    }

    @Test
    public void scrollBarWidthIsAccountedForInColumnCount() throws Exception {
        onFx(() -> {
            RXGridView<String> noBar = grid(6);
            noBar.setCellWidth(40);
            noBar.setHgap(0);
            pump(host(noBar, 320, 600)); // 6 items fit in one row: no vertical scroll bar
            int colsNoBar = noBar.getActualColumnCount();
            assertEquals(8, colsNoBar, "320 / 40 = 8 columns without a scroll bar");

            RXGridView<String> withBar = grid(2000);
            withBar.setCellWidth(40);
            withBar.setHgap(0);
            StackPane root = host(withBar, 320, 300); // overflow forces the scroll bar
            pump(root);
            int colsWithBar = withBar.getActualColumnCount();
            assertTrue(colsWithBar < colsNoBar,
                    "the measured scroll-bar width reduces the content width (no 18px hack)");
            pump(root);
            assertEquals(colsWithBar, withBar.getActualColumnCount(), "column count is stable, not oscillating");
        });
    }

    @Test
    public void scrollToItemScrollsThroughTheSkin() throws Exception {
        onFx(() -> {
            RXGridView<String> grid = grid(400);
            grid.setColumnCount(2);
            StackPane root = host(grid, 400, 300);
            pump(root);
            grid.scrollTo("Item 300");
            pump(root);
            assertFalse(grid.hasPendingScroll());
            assertTrue(grid.getVisibleRange().firstIndex() > 0, "scrolled away from the top");
        });
    }

    // ==================== Helpers ====================

    private static RXGridView<String> grid(int count) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add("Item " + i);
        }
        return new RXGridView<>(items);
    }

    private static StackPane host(RXGridView<?> grid, double w, double h) {
        StackPane root = new StackPane(grid);
        new Scene(root, w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static RXGridCell<?> cellByIndex(RXGridView<?> grid, int index) {
        for (Node node : grid.lookupAll(".rx-grid-cell")) {
            if (node instanceof RXGridCell<?> cell && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    private static List<RXGridCell<?>> rowCells(RXGridView<?> grid, int row) {
        List<RXGridCell<?>> cells = new ArrayList<>();
        for (Node node : grid.lookupAll(".rx-grid-cell")) {
            if (node instanceof RXGridCell<?> cell && !cell.isEmpty() && cell.getRowIndex() == row) {
                cells.add(cell);
            }
        }
        cells.sort(Comparator.comparingInt((RXGridCell<?> cell) -> cell.getColumnIndex()));
        return cells;
    }

    private static double firstCellX(RXGridView<?> grid) {
        return rowCells(grid, 0).get(0).getLayoutX();
    }

    private static double firstGap(RXGridView<?> grid) {
        List<RXGridCell<?>> cells = rowCells(grid, 0);
        RXGridCell<?> first = cells.get(0);
        RXGridCell<?> second = cells.get(1);
        return second.getLayoutX() - (first.getLayoutX() + first.getWidth());
    }

    private static void onFx(Runnable body) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception exception) {
            throw exception;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}

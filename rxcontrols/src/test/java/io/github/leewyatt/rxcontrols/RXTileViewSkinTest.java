package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXTileViewActionEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 * Skin / virtualization tests for {@link RXTileView}: column derivation, the
 * item-index to row/column mapping, large-list virtualization, scroll-bar width
 * accounting, scrolling (pending requests, wheel, NEAREST), the resize anchor,
 * churn discipline, in-row layout and the placeholder. Each test drives a real
 * (headless) layout pass.
 */
public class RXTileViewSkinTest {

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
        // Pin modena so the inner ScrollBar gets a real measured breadth.
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ==================== Column derivation & metrics ====================

    @Test
    public void emptyViewReportsEmptyMetricsAndPseudoClass() throws Exception {
        onFx(() -> {
            RXTileView<String> view = new RXTileView<>();
            pump(host(view, 400, 300));
            assertEquals(0, view.getRowCount());
            assertTrue(view.getActualColumnCount() >= 1);
            assertTrue(view.getVisibleRange().isEmpty());
            assertTrue(view.getPseudoClassStates().stream()
                    .anyMatch(pc -> pc.getPseudoClassName().equals("empty")));
        });
    }

    @Test
    public void columnCountDerivedFromWidthWithoutScrollBar() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(6);
            view.setCellWidth(100);
            view.setCellHeight(100);
            view.setHgap(10);
            view.setVgap(10);
            pump(host(view, 350, 300));
            // floor((350 + 10) / (100 + 10)) = 3
            assertEquals(3, view.getActualColumnCount());
            assertEquals(2, view.getRowCount());
        });
    }

    @Test
    public void forcedColumnCountOverridesWidth() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(5);
            pump(host(view, 800, 300));
            assertEquals(5, view.getActualColumnCount());
            assertEquals(4, view.getRowCount());
        });
    }

    @Test
    public void maxColumnsClampsDerivedAndForcedCount() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setCellWidth(50);
            view.setHgap(0);
            StackPane root = host(view, 800, 300);
            pump(root);
            assertTrue(view.getActualColumnCount() > 2, "unclamped width yields many columns");

            view.setMaxColumns(2);
            pump(root);
            assertEquals(2, view.getActualColumnCount(), "maxColumns clamps the derived count");

            view.setColumnCount(5);
            pump(root);
            assertEquals(2, view.getActualColumnCount(), "maxColumns also caps a forced columnCount");
        });
    }

    @Test
    public void hgapParticipatesInColumnCount() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(6);
            view.setCellWidth(100);
            view.setHgap(0);
            StackPane root = host(view, 350, 300);
            pump(root);
            assertEquals(3, view.getActualColumnCount());

            view.setHgap(60);
            pump(root);
            // floor((350 + 60) / (100 + 60)) = floor(410 / 160) = 2
            assertEquals(2, view.getActualColumnCount());
        });
    }

    @Test
    public void lenientGapsResolveAtLayoutUseSites() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(6);
            view.setCellWidth(100);
            view.setCellHeight(100);
            // cellWidth / cellHeight coerce+throw, so only the lenient gaps can be illegal.
            view.setHgap(Double.NaN);
            view.setVgap(Double.POSITIVE_INFINITY);
            pump(host(view, 350, 300));
            // floor((350 + 0) / (100 + 0)) = 3 — illegal hgap falls back to zero.
            assertEquals(3, view.getActualColumnCount());
            RXTileCell<?> cell = cellByIndex(view, 0);
            assertNotNull(cell);
            assertEquals(100.0, cell.getWidth(), 1.0);
            assertEquals(100.0, cell.getHeight(), 1.0);
        });
    }

    // ==================== Virtualization & geometry ====================

    @Test
    public void cellKnowsItsRowAndColumn() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            pump(host(view, 400, 400));
            RXTileCell<?> cell = cellByIndex(view, 7);
            assertNotNull(cell, "cell for index 7 should be realized");
            assertEquals(2, cell.getRowIndex(), "7 / 3 = row 2");
            assertEquals(1, cell.getColumnIndex(), "7 % 3 = column 1");
        });
    }

    @Test
    public void largeListRealizesOnlyVisibleCells() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(10_000);
            view.setColumnCount(4);
            view.setCellHeight(100);
            view.setVgap(10);
            pump(host(view, 500, 400));
            int realized = view.lookupAll(".rx-tile-cell").size();
            assertTrue(realized > 0, "some cells are realized");
            assertTrue(realized < 100,
                    "only the visible window is realized, not all 10000 items (was " + realized + ")");
        });
    }

    @Test
    public void doublePrecisionGeometryAtMillionRows() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(1_000_000);
            view.setColumnCount(1);
            view.setCellHeight(100);
            view.setVgap(10);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.scrollTo(999_999);
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertFalse(range.isEmpty());
            assertEquals(999_999, range.lastIndex(), "the last item is reachable with no precision loss");
            assertTrue(range.firstIndex() > 999_000, "scrolled to the very end");
        });
    }

    @Test
    public void shortFinalRowHasNoStaleCells() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(7);
            view.setColumnCount(3);
            pump(host(view, 400, 600));
            // 7 items, 3 columns: rows 0-1 full (6 cells), row 2 holds only index 6.
            assertEquals(1, rowCells(view, 2).size(), "the short final row holds exactly one cell");
            assertNull(cellByIndex(view, 7), "no stale cell past the last item");
        });
    }

    @Test
    public void visibleRangeTailIsClampedToItemCount() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(7);
            view.setColumnCount(3);
            pump(host(view, 400, 600));
            RXTileVisibleRange range = view.getVisibleRange();
            assertEquals(0, range.firstIndex());
            assertEquals(6, range.lastIndex(), "tail clamps to itemCount-1, not lastRow*cols+cols-1");
            assertEquals(7, range.size());
        });
    }

    @Test
    public void scrollBarWidthIsAccountedForInColumnCount() throws Exception {
        onFx(() -> {
            RXTileView<String> noBar = tiles(6);
            noBar.setCellWidth(40);
            noBar.setHgap(0);
            pump(host(noBar, 320, 600)); // 6 items fit without a vertical scroll bar
            int colsNoBar = noBar.getActualColumnCount();
            assertEquals(8, colsNoBar, "320 / 40 = 8 columns without a scroll bar");

            RXTileView<String> withBar = tiles(2000);
            withBar.setCellWidth(40);
            withBar.setHgap(0);
            StackPane root = host(withBar, 320, 300); // overflow forces the scroll bar
            pump(root);
            int colsWithBar = withBar.getActualColumnCount();
            assertTrue(colsWithBar < colsNoBar,
                    "the measured scroll-bar breadth reduces the content width (no 18px hack)");
            pump(root);
            assertEquals(colsWithBar, withBar.getActualColumnCount(), "column count is stable, not oscillating");
        });
    }

    // ==================== Scrolling & resize ====================

    @Test
    public void scrollToConsumesPendingRequestOnce() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(150);
            pump(root);
            assertFalse(view.hasPendingScroll(), "the pending request is consumed on the next pass");
            assertTrue(view.getVisibleRange().firstIndex() > 0, "scrolled away from the top");
        });
    }

    @Test
    public void scrollToItemScrollsThroughTheSkin() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo("Item 300");
            pump(root);
            assertFalse(view.hasPendingScroll());
            RXTileVisibleRange range = view.getVisibleRange();
            assertTrue(range.firstIndex() <= 300 && 300 <= range.lastIndex(),
                    "the requested item is in the visible range");
        });
    }

    @Test
    public void nearestAlignmentLeavesVisibleItemPut() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);

            view.scrollTo(2, RXGridScrollAlignment.NEAREST);
            pump(root);
            assertEquals(0, view.getVisibleRange().firstIndex(), "an already-visible target does not move");

            view.scrollTo(100, RXGridScrollAlignment.NEAREST);
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertTrue(range.firstIndex() <= 100 && 100 <= range.lastIndex(),
                    "an off-screen NEAREST target scrolls just enough to reveal it");
        });
    }

    @Test
    public void wheelScrollMovesViewportAndClamps() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            Node viewport = view.lookup(".viewport");
            assertNotNull(viewport, "viewport node present");

            // Wheel down (negative deltaY) reveals lower content.
            fireWheel(viewport, -400);
            pump(root);
            assertTrue(view.getVisibleRange().firstIndex() > 0, "wheel scrolled down");

            // Wheel up past the top clamps at zero.
            fireWheel(viewport, 100_000);
            pump(root);
            assertEquals(0, view.getVisibleRange().firstIndex(), "clamped at the top");
        });
    }

    @Test
    public void emptyViewportReleasesWheelAfterPreviouslyScrollable() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            Node viewport = view.lookup(".viewport");
            assertNotNull(viewport, "viewport node present");
            AtomicInteger bubbledWheelEvents = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbledWheelEvents.incrementAndGet());

            fireWheel(viewport, -400);
            pump(root);
            assertEquals(0, bubbledWheelEvents.get(), "scrollable viewport consumes wheel events");
            assertTrue(view.getVisibleRange().firstIndex() > 0, "wheel scrolled down");

            view.getItems().clear();
            pump(root);
            assertTrue(view.getVisibleRange().isEmpty(), "empty view publishes an empty range");
            fireWheel(viewport, -400);
            assertEquals(1, bubbledWheelEvents.get(), "empty viewport leaves wheel events for an enclosing scroller");
        });
    }

    @Test
    public void columnCountChangeKeepsTopItemStable() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(100);
            view.setColumnCount(4);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(40);
            pump(root);
            assertEquals(40, view.getVisibleRange().firstIndex());

            // Reflow to 6 columns: the previously-top item must stay visible.
            view.setColumnCount(6);
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertTrue(range.firstIndex() <= 40 && 40 <= range.lastIndex(),
                    "the resize anchor keeps the top item visible across a reflow (was " + range + ")");
        });
    }

    @Test
    public void lastPageClampOnShrink() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(400);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(399);
            pump(root);
            assertTrue(view.getVisibleRange().firstIndex() > 0);

            items.remove(5, items.size());
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertFalse(range.isEmpty(), "the viewport is not left blank after a shrink");
            assertEquals(0, range.firstIndex(), "scroll clamped back to the top for the smaller list");
        });
    }

    // ==================== Churn discipline ====================

    @Test
    public void changingCellWidthReusesCellsWhileFactoryChangeRecreates() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 600);
            pump(root);
            List<Node> before = new ArrayList<>(view.lookupAll(".rx-tile-cell"));
            assertFalse(before.isEmpty());

            view.setCellHeight(80);
            pump(root);
            List<Node> afterResize = new ArrayList<>(view.lookupAll(".rx-tile-cell"));
            assertTrue(afterResize.containsAll(before), "a size change reuses the same cell instances");

            view.setCellFactory(v -> new RXTileCell<>());
            pump(root);
            List<Node> afterFactory = new ArrayList<>(view.lookupAll(".rx-tile-cell"));
            assertTrue(afterFactory.stream().noneMatch(before::contains),
                    "a cell-factory change recreates the pool (disjoint instances)");
        });
    }

    @Test
    public void itemMutationsUpdateRowCountAndContent() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(6);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 600);
            pump(root);
            assertEquals(2, view.getRowCount());

            items.addAll("Item 6", "Item 7", "Item 8");
            pump(root);
            assertEquals(3, view.getRowCount());
            assertNotNull(cellByIndex(view, 8), "a newly added item is realized");

            items.remove(5, items.size());
            pump(root);
            assertEquals(2, view.getRowCount());
            assertNull(cellByIndex(view, 8), "a removed item leaves no cell behind");
        });
    }

    @Test
    public void disposeStopsReactingToItemMutations() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(6);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 600);
            pump(root);
            int rowsBefore = view.getRowCount();

            view.setSkin(null); // disposes the current skin
            items.addAll("Item 6", "Item 7", "Item 8"); // must not be observed by the disposed skin
            assertEquals(rowsBefore, view.getRowCount(),
                    "a disposed skin no longer reacts to item mutations");
        });
    }

    // ==================== In-row layout ====================

    @Test
    public void stretchModeFillsTheRowEqually() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(3);
            view.setColumnCount(3);
            view.setHgap(10);
            view.setStretchCells(true);
            pump(host(view, 320, 300));
            List<RXTileCell<?>> cells = rowCells(view, 0);
            assertEquals(3, cells.size());
            // (320 - 2*10) / 3 = 100 each, flush to the leading edge.
            assertEquals(0.0, cells.get(0).getLayoutX(), 1.0);
            assertEquals(100.0, cells.get(0).getWidth(), 1.5);
            assertEquals(100.0, cells.get(1).getWidth(), 1.5);
        });
    }

    @Test
    public void itemsJustifyPositionsTheBlock() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(2);
            view.setColumnCount(2);
            view.setCellWidth(100);
            view.setHgap(0);

            view.setItemsJustify(RXGridJustify.START);
            StackPane root = host(view, 400, 300);
            pump(root);
            double startX = rowCells(view, 0).get(0).getLayoutX();
            assertEquals(0.0, startX, 1.0, "START hugs the leading edge");

            view.setItemsJustify(RXGridJustify.CENTER);
            pump(root);
            double centerX = rowCells(view, 0).get(0).getLayoutX();

            view.setItemsJustify(RXGridJustify.END);
            pump(root);
            double endX = rowCells(view, 0).get(0).getLayoutX();

            assertTrue(centerX > startX && endX > centerX, "CENTER and END shift the block right");
            assertEquals(endX / 2.0, centerX, 1.0, "CENTER offset is half of END");
        });
    }

    @Test
    public void nullItemRendersNonEmpty() throws Exception {
        onFx(() -> {
            ObservableList<String> items = FXCollections.observableArrayList("a", null, "c");
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            pump(host(view, 400, 300));
            RXTileCell<?> cell = cellByIndex(view, 1);
            assertNotNull(cell, "the null-item cell is realized");
            assertFalse(cell.isEmpty(), "a null at a valid index is a value, not an empty cell");
            assertNull(cell.getItem());
        });
    }

    @Test
    public void placeholderShownOnlyWhenEmpty() throws Exception {
        onFx(() -> {
            ObservableList<String> items = FXCollections.observableArrayList();
            RXTileView<String> view = new RXTileView<>(items);
            Region placeholder = new Region();
            placeholder.getStyleClass().add("my-placeholder");
            view.setPlaceholder(placeholder);
            StackPane root = host(view, 400, 300);
            pump(root);
            assertNotNull(view.lookup(".my-placeholder"), "placeholder shows while empty");
            assertTrue(placeholder.isVisible());

            items.add("first");
            pump(root);
            assertNull(view.lookup(".my-placeholder"), "placeholder is removed once items appear");
            assertNotNull(cellByIndex(view, 0));
        });
    }

    // ==================== Animation-hook inertness (PR2) ====================

    @Test
    public void noTranslationLeftOnCellsWithoutAnimation() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(40);
            view.setColumnCount(4);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            RXTileCell<?> cell = cellByIndex(view, 0);
            assertNotNull(cell);
            assertEquals(0.0, cell.getTranslateX(), 0.0001, "no animation infrastructure runs in PR2");
            assertEquals(0.0, cell.getTranslateY(), 0.0001);
        });
    }

    @Test
    public void nearestAlignmentScrollsUpToRevealItemAboveTheWindow() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(200);
            pump(root);
            assertTrue(view.getVisibleRange().firstIndex() > 20, "scrolled well down first");

            // Target a row above the window: NEAREST scrolls UP just enough.
            view.scrollTo(10, RXGridScrollAlignment.NEAREST);
            pump(root);
            assertEquals(10, view.getVisibleRange().firstIndex(),
                    "NEAREST scrolls backwards to land the target at the top edge");
        });
    }

    @Test
    public void itemsListSwapRebindsAndDetachesOldList() throws Exception {
        onFx(() -> {
            ObservableList<String> listA = items(6);
            RXTileView<String> view = new RXTileView<>(listA);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 600);
            pump(root);
            assertEquals(3, view.getRowCount());

            ObservableList<String> listB = items(20);
            view.setItems(listB);
            pump(root);
            assertEquals(10, view.getRowCount(), "the view reflects the new list");

            // Mutating the OLD list must no longer affect the view (listener detached).
            listA.clear();
            pump(root);
            assertEquals(10, view.getRowCount(), "the old list's ChangeListener was detached on swap");
        });
    }

    @Test
    public void scrollBarDragMovesViewportWithoutOscillating() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            ScrollBar vbar = (ScrollBar) view.lookup(".scroll-bar");
            assertNotNull(vbar, "the viewport's vertical scroll bar is present");
            assertTrue(vbar.getMax() > 0, "content overflows so the bar has a range");
            int colsBefore = view.getActualColumnCount();

            vbar.setValue(vbar.getMax() / 2.0);
            pump(root);
            assertTrue(view.getVisibleRange().firstIndex() > 0, "dragging the bar scrolls the viewport");

            pump(root);
            assertEquals(colsBefore, view.getActualColumnCount(),
                    "the bar-value feedback guard prevents column oscillation");
        });
    }

    @Test
    public void shortFinalRowStaysColumnAligned() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(7);
            view.setColumnCount(3);
            view.setCellWidth(100);
            view.setHgap(0);
            view.setItemsJustify(RXGridJustify.CENTER);
            pump(host(view, 400, 600));
            double fullRowX = rowCells(view, 0).get(0).getLayoutX();
            double shortRowX = rowCells(view, 2).get(0).getLayoutX();
            assertEquals(fullRowX, shortRowX, 1.0,
                    "the short final row uses the full column count, so its cell stays column-aligned");
        });
    }

    @Test
    public void sizingContractIsVirtualizedNotProportional() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(1000);
            view.setCellWidth(100);
            view.setCellHeight(80);
            view.setHgap(10);
            view.setVgap(10);
            pump(host(view, 400, 300));
            double slot = 80 + 10;
            assertTrue(view.prefHeight(-1) < 20 * slot,
                    "prefHeight is a few rows, never proportional to itemCount (was " + view.prefHeight(-1) + ")");
            assertEquals(Double.MAX_VALUE, view.maxWidth(-1), 0.0, "container max width");
            assertEquals(Double.MAX_VALUE, view.maxHeight(-1), 0.0, "container max height");
        });
    }

    @Test
    public void singleSectionKeyGroupsAllItemsUnderOneHeader() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(10);
            view.setColumnCount(2);
            view.setSectionKeyFactory(item -> "group");
            pump(host(view, 400, 600));
            assertEquals(1, view.getSections().size(), "all items share one key -> one section");
            assertEquals("group", view.getSections().get(0).key());
            // 1 header row + ceil(10 / 2) = 5 data rows = 6 visual rows.
            assertEquals(6, view.getRowCount(), "rowCount counts the header row plus data rows");
            assertNotNull(view.getVisibleSection(), "the visible section is published");
            assertNotNull(cellByIndex(view, 0), "cells render below the header");
        });
    }

    @Test
    public void pendingScrollSurvivesZeroHeightPass() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            view.scrollTo(100);
            // A layout pass at zero height cannot apply the scroll; the request must
            // survive (not be consumed-and-lost) so a later sized pass can honor it.
            StackPane root = host(view, 400, 0);
            pump(root);
            assertTrue(view.hasPendingScroll(),
                    "a scroll request is kept armed across a zero-height consume pass");
        });
    }

    @Test
    public void temporaryZeroSizePreservesScrollPosition() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(150);
            pump(root);
            RXTileVisibleRange before = view.getVisibleRange();
            assertTrue(before.firstIndex() <= 150 && 150 <= before.lastIndex());

            view.resize(400, 0);
            view.layout();
            assertTrue(view.getVisibleRange().isEmpty(), "zero-height layout publishes an empty visible range");

            view.resize(400, 300);
            view.layout();
            pump(root);
            RXTileVisibleRange after = view.getVisibleRange();
            assertTrue(after.firstIndex() <= 150 && 150 <= after.lastIndex(),
                    "restoring a non-empty view after zero height keeps the previous scroll position");
        });
    }

    // ==================== Sections (PR3) ====================

    @Test
    public void headerRowsRealizedWhenGrouped() throws Exception {
        onFx(() -> {
            RXTileView<String> view = grouped4();
            pump(host(view, 400, 600));
            assertEquals(2, view.getSections().size());
            assertEquals(2, headers(view).size(), "one header per visible section");
            assertNotNull(cellByIndex(view, 0));
            assertNotNull(cellByIndex(view, 3));
        });
    }

    @Test
    public void rowCountCountsHeadersButVisibleRangeRowsAreDataOnly() throws Exception {
        onFx(() -> {
            RXTileView<String> view = grouped4();
            pump(host(view, 400, 600));
            // each section: 1 header row + 1 data row -> 4 visual rows
            assertEquals(4, view.getRowCount(), "rowCount counts header rows");
            RXTileVisibleRange range = view.getVisibleRange();
            assertEquals(0, range.firstIndex());
            assertEquals(3, range.lastIndex());
            assertEquals(0, range.firstRow(), "visibleRange rows exclude header rows");
            assertEquals(1, range.lastRow());
        });
    }

    @Test
    public void threeStateShowSectionHeaders() throws Exception {
        onFx(() -> {
            RXTileView<String> flat = tiles(4);
            flat.setColumnCount(2);
            pump(host(flat, 400, 600));
            assertEquals(0, headers(flat).size(), "A: no factory -> no headers");
            assertEquals(2, flat.getRowCount());
            assertNull(flat.getVisibleSection());

            RXTileView<String> shown = grouped4();
            pump(host(shown, 400, 600));
            assertEquals(2, headers(shown).size(), "B: factory + show -> header rows");
            assertNotNull(shown.getVisibleSection());

            RXTileView<String> hidden = grouped4();
            hidden.setShowSectionHeaders(false);
            pump(host(hidden, 400, 600));
            assertEquals(0, headers(hidden).size(), "C: factory + hide -> no header rows");
            assertEquals(2, hidden.getSections().size(), "C: sections still computed");
            assertNotNull(hidden.getVisibleSection(), "C: visibleSection still works");
        });
    }

    @Test
    public void eachSectionStartsAFreshRow() throws Exception {
        onFx(() -> {
            // a: 5 items, b: 3 items; cols 3, headers off for a clean row check.
            ObservableList<String> items = FXCollections.observableArrayList(
                    "a0", "a1", "a2", "a3", "a4", "b0", "b1", "b2");
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            view.setSectionKeyFactory(s -> s.substring(0, 1));
            view.setShowSectionHeaders(false);
            pump(host(view, 400, 600));
            RXTileCell<?> a4 = cellByIndex(view, 4);
            RXTileCell<?> b0 = cellByIndex(view, 5);
            assertNotNull(a4);
            assertNotNull(b0);
            assertEquals(0, b0.getColumnIndex(), "section B's first item starts at column 0");
            assertTrue(b0.getRowIndex() > a4.getRowIndex(),
                    "B starts a fresh data row below A's short last row");
        });
    }

    @Test
    public void defaultHeaderShowsSectionKeyText() throws Exception {
        onFx(() -> {
            RXTileView<String> view = grouped4();
            pump(host(view, 400, 600));
            assertTrue(headers(view).stream().anyMatch(h -> "a".equals(h.getText())),
                    "the default header renders the section key as text");
        });
    }

    @Test
    public void sectionHeaderFactoryChangeRecreatesHeaderPool() throws Exception {
        onFx(() -> {
            RXTileView<String> view = grouped4();
            StackPane root = host(view, 400, 600);
            pump(root);
            List<Node> before = new ArrayList<>(view.lookupAll(".rx-tile-section-header"));
            assertFalse(before.isEmpty());
            view.setSectionHeaderFactory(v -> new RXTileSectionCell());
            pump(root);
            List<Node> after = new ArrayList<>(view.lookupAll(".rx-tile-section-header"));
            assertTrue(after.stream().noneMatch(before::contains),
                    "a header-factory change recreates the header pool");
        });
    }

    @Test
    public void headersRecycledOnScroll() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(200, 4);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            assertEquals(200, view.getSections().size());
            assertTrue(headers(view).size() < 30,
                    "bounded header realization at the top (was " + headers(view).size() + ")");

            view.scrollToSection("s150");
            pump(root);
            assertTrue(headers(view).size() < 30,
                    "still bounded after scrolling deep (was " + headers(view).size() + ")");
            assertEquals("s150", view.getVisibleSection().key(), "scrolled to the deep section");
        });
    }

    @Test
    public void scrollToSectionLandsTheSection() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(10, 4);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollToSection("s5");
            pump(root);
            assertNotNull(view.getVisibleSection());
            assertEquals("s5", view.getVisibleSection().key(), "the requested section is at the top");
            RXTileSectionCell header = headers(view).stream()
                    .filter(cell -> "s5".equals(cell.getItem().key()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(header, "the requested section header is realized");
            assertEquals(0.0, header.getLayoutY(), 0.0001, "the section header lands at the viewport top");
        });
    }

    @Test
    public void scrollToSectionIndexDistinguishesDuplicateKeys() throws Exception {
        onFx(() -> {
            // keys x, y, x, z — "x" appears in two non-adjacent sections. The trailing
            // z section supplies enough content below the second "x" so scrolling to it
            // is not clamped (a last section could never reach the top).
            ObservableList<String> items = FXCollections.observableArrayList(
                    "x0", "x1", "y0", "y1", "x2", "x3");
            for (int i = 0; i < 10; i++) {
                items.add("z" + i);
            }
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(2);
            view.setSectionKeyFactory(s -> s.substring(0, 1));
            StackPane root = host(view, 400, 250);
            pump(root);
            assertEquals(4, view.getSections().size());
            view.scrollToSectionIndex(2); // the SECOND "x" section (sectionIndex 2)
            pump(root);
            assertEquals(2, view.getVisibleSection().sectionIndex(),
                    "scrollToSectionIndex targets the duplicate by position, not the first key match");
        });
    }

    @Test
    public void visibleSectionTracksScrolling() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(10, 4);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 250);
            pump(root);
            assertEquals("s0", view.getVisibleSection().key(), "starts at the first section");
            view.scrollTo(36); // item in section s9 (9 * 4)
            pump(root);
            assertEquals("s9", view.getVisibleSection().key(), "tracks to the scrolled section");
        });
    }

    @Test
    public void scrollToItemAccountsForHeaderOffset() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(20, 6);
            view.setColumnCount(2);
            view.setCellHeight(60);
            view.setVgap(0);
            view.setSectionHeaderHeight(40);
            StackPane root = host(view, 400, 300);
            pump(root);
            view.scrollTo(100); // deep item; many section headers precede it
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertTrue(range.firstIndex() <= 100 && 100 <= range.lastIndex(),
                    "the item is visible despite the preceding header heights");
        });
    }

    @Test
    public void sectionHeaderHeightShiftsCellGeometry() throws Exception {
        onFx(() -> {
            RXTileView<String> view = grouped4(); // 2 sections x 2 items, cols 2
            view.setCellHeight(100);
            view.setVgap(0);
            view.setSectionHeaderHeight(20);
            StackPane root = host(view, 400, 600);
            pump(root);
            double yShort = cellByIndex(view, 2).getLayoutY(); // first cell of section b
            view.setSectionHeaderHeight(80);
            pump(root);
            double yTall = cellByIndex(view, 2).getLayoutY();
            // Two section headers (a's and b's) precede section b's first cell, so its
            // Y grows by twice the header-height delta — proving the height feeds geometry.
            assertEquals(2 * (80 - 20), yTall - yShort, 1.0,
                    "section-header height flows into cell geometry by the right magnitude");
        });
    }

    @Test
    public void stateCRowCountIsPerSectionNotFlat() throws Exception {
        onFx(() -> {
            // Two single-item sections, cols 3: flat would pack both in one row (ceil(2/3)=1),
            // but grouped (headers hidden) each section forces its own data row.
            RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));
            view.setColumnCount(3);
            view.setSectionKeyFactory(s -> s);
            view.setShowSectionHeaders(false);
            pump(host(view, 400, 600));
            assertEquals(2, view.getSections().size());
            assertEquals(2, view.getRowCount(),
                    "each section forces its own data row even with headers hidden (flat would be 1)");
        });
    }

    @Test
    public void visibleSectionAtExactSectionTopBoundary() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(5, 4);
            view.setColumnCount(2);
            view.setCellHeight(100);
            view.setVgap(0);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 400, 200);
            pump(root);
            ScrollBar vbar = (ScrollBar) view.lookup(".scroll-bar");
            assertNotNull(vbar);
            // Each section is 30 (header) + 2*100 (data rows) = 230 px; section 2 top = 460.
            vbar.setValue(460.0);
            pump(root);
            assertEquals(2, view.getVisibleSection().sectionIndex(),
                    "landing exactly on a section top selects that section (<= boundary), not the previous one");
        });
    }

    @Test
    public void defaultHeaderRendersEmptyForNullKey() throws Exception {
        onFx(() -> {
            RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));
            view.setColumnCount(2);
            view.setSectionKeyFactory(s -> null); // one section, null key
            pump(host(view, 400, 600));
            assertEquals(1, view.getSections().size());
            assertNull(view.getSections().get(0).key());
            List<RXTileSectionCell> headers = headers(view);
            assertEquals(1, headers.size());
            assertEquals("", headers.get(0).getText(), "a null section key renders as empty text");
        });
    }

    // ==================== Selection / keyboard / mouse (PR4) ====================

    @Test
    public void arrowKeysMoveFocusAndSelection() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireKey(view, KeyCode.DOWN, false, false); // no focus yet -> first item
            pump(root);
            assertEquals(0, sm.getSelectedIndex());
            assertTrue(isSelected(cellByIndex(view, 0)));
            assertTrue(hasFocusRing(cellByIndex(view, 0)));

            fireKey(view, KeyCode.DOWN, false, false); // + columnCount
            pump(root);
            assertEquals(3, sm.getSelectedIndex());
            assertTrue(isSelected(cellByIndex(view, 3)));
            assertFalse(isSelected(cellByIndex(view, 0)), "SINGLE selection moved off index 0");

            fireKey(view, KeyCode.RIGHT, false, false);
            pump(root);
            assertEquals(4, sm.getSelectedIndex(), "RIGHT moves by one");
        });
    }

    @Test
    public void arrowDownSkipsSectionHeaders() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(3, 4); // sections of 4, cols 2 -> 2 data rows each
            view.setColumnCount(2);
            StackPane root = host(view, 400, 600);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireMousePressed(cellByIndex(view, 3), false, false); // focus last item of section 0
            pump(root);
            fireKey(view, KeyCode.DOWN, false, false); // 3 + 2 cols = item 5 (section 1)
            pump(root);
            assertEquals(5, sm.getSelectedIndex(),
                    "DOWN lands on a section-1 item, never a header (navigation is by item index)");
            assertTrue(isSelected(cellByIndex(view, 5)));
        });
    }

    @Test
    public void homeAndEndSelectFirstAndLast() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(50);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 300);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();
            fireKey(view, KeyCode.END, false, false);
            pump(root);
            assertEquals(49, sm.getSelectedIndex());
            fireKey(view, KeyCode.HOME, false, false);
            pump(root);
            assertEquals(0, sm.getSelectedIndex());
        });
    }

    @Test
    public void shiftArrowExtendsRangeFromAnchor() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();
            fireMousePressed(cellByIndex(view, 0), false, false); // anchor + select 0
            pump(root);
            fireKey(view, KeyCode.DOWN, true, false); // shift+down -> range 0..3
            pump(root);
            assertEquals(List.of(0, 1, 2, 3), sm.getSelectedIndices(),
                    "shift extends the range from the anchor to the target inclusive");
        });
    }

    @Test
    public void shortcutArrowMovesFocusOnly() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();
            fireMousePressed(cellByIndex(view, 0), false, false); // select + focus 0
            pump(root);
            fireKey(view, KeyCode.DOWN, false, true); // ctrl/cmd+down: focus only
            pump(root);
            assertEquals(0, sm.getSelectedIndex(), "selection unchanged under Ctrl/Cmd navigation");
            assertTrue(hasFocusRing(cellByIndex(view, 3)), "focus ring moved");
            assertFalse(isSelected(cellByIndex(view, 3)), "but index 3 is not selected");
            assertTrue(isSelected(cellByIndex(view, 0)));
        });
    }

    @Test
    public void shortcutAClampedToSelectionMode() throws Exception {
        onFx(() -> {
            RXTileView<String> single = tiles(8);
            single.setColumnCount(4);
            pump(host(single, 400, 400));
            fireKey(single, KeyCode.A, false, true);
            assertTrue(single.getSelectionModel().isEmpty(), "Ctrl/Cmd+A is a no-op in SINGLE mode");

            RXTileView<String> multi = tiles(8);
            multi.setColumnCount(4);
            multi.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            pump(host(multi, 400, 400));
            fireKey(multi, KeyCode.A, false, true);
            assertEquals(8, multi.getSelectionModel().getSelectedIndices().size(), "Ctrl/Cmd+A selects all");
        });
    }

    @Test
    public void focusOffscreenScrollsIntoView() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            fireMousePressed(cellByIndex(view, 0), false, false);
            pump(root);
            fireKey(view, KeyCode.END, false, false); // focus last; should scroll in
            pump(root);
            RXTileVisibleRange range = view.getVisibleRange();
            assertEquals(399, view.getSelectionModel().getSelectedIndex());
            assertTrue(range.firstIndex() <= 399 && 399 <= range.lastIndex(),
                    "the focused item is scrolled into view");
        });
    }

    @Test
    public void selectionReAppliesToRecycledCells() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            fireMousePressed(cellByIndex(view, 0), false, false);
            pump(root);
            assertTrue(isSelected(cellByIndex(view, 0)));

            view.scrollTo(300);
            pump(root);
            assertNull(cellByIndex(view, 0), "index 0 is recycled offscreen");
            RXTileCell<?> recycled = cellByIndex(view, 300);
            assertNotNull(recycled);
            assertFalse(isSelected(recycled), "the recycled cell shows an unselected index");

            view.scrollTo(0);
            pump(root);
            assertTrue(isSelected(cellByIndex(view, 0)), ":selected is re-applied on the cell rebound to index 0");
        });
    }

    @Test
    public void onActionFiresOnEnterAndDoubleClick() throws Exception {
        AtomicReference<RXTileViewActionEvent<String>> received = new AtomicReference<>();
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.setOnAction(received::set);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 2), false, false); // focus 2
            pump(root);
            fireKey(view, KeyCode.ENTER, false, false);
            assertNotNull(received.get(), "Enter activates the focused item");
            assertEquals(2, received.get().getIndex());

            received.set(null);
            fireDoubleClick(cellByIndex(view, 5));
            assertNotNull(received.get(), "double-click activates the clicked item");
            assertEquals(5, received.get().getIndex());
        });
    }

    @Test
    public void clickSelectsAndCtrlClickToggles() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireMousePressed(cellByIndex(view, 0), false, false);
            pump(root);
            assertEquals(List.of(0), sm.getSelectedIndices());

            fireMousePressed(cellByIndex(view, 2), false, true); // ctrl+click adds 2
            pump(root);
            assertEquals(List.of(0, 2), sm.getSelectedIndices());

            fireMousePressed(cellByIndex(view, 0), false, true); // ctrl+click removes 0
            pump(root);
            assertEquals(List.of(2), sm.getSelectedIndices());
        });
    }

    @Test
    public void eventFilterCanDisableAKey() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.DOWN) {
                    e.consume();
                }
            });
            StackPane root = host(view, 400, 400);
            pump(root);
            fireMousePressed(cellByIndex(view, 0), false, false); // focus + select 0
            pump(root);
            fireKey(view, KeyCode.DOWN, false, false); // consumed by the filter
            pump(root);
            assertEquals(0, view.getSelectionModel().getSelectedIndex(),
                    "a capture-phase filter consumes the key before the skin handler runs");
        });
    }

    @Test
    public void selectionModelSwapReWiresState() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 400);
            pump(root);
            view.getSelectionModel().select(0);
            pump(root);
            assertTrue(isSelected(cellByIndex(view, 0)));

            RXTileSelectionModel<String> newModel = new RXTileSelectionModel<>(view);
            view.setSelectionModel(newModel);
            pump(root);
            assertFalse(isSelected(cellByIndex(view, 0)), "the old selection is gone after the swap");

            newModel.select(2);
            pump(root);
            assertTrue(isSelected(cellByIndex(view, 2)), "the new model's selection applies (listener re-wired)");
        });
    }

    @Test
    public void shiftClickSelectsRangeFromAnchor() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireMousePressed(cellByIndex(view, 2), false, false); // anchor at 2
            pump(root);
            fireMousePressed(cellByIndex(view, 6), true, false); // shift+click -> range 2..6
            pump(root);
            assertEquals(List.of(2, 3, 4, 5, 6), sm.getSelectedIndices());

            fireMousePressed(cellByIndex(view, 0), true, false); // shift+click from the SAME anchor
            pump(root);
            assertEquals(List.of(0, 1, 2), sm.getSelectedIndices(),
                    "the anchor is not moved by shift-click, so the range flips around index 2");
        });
    }

    @Test
    public void pageDownAndPageUpMoveByVisibleRows() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(400);
            view.setColumnCount(2);
            StackPane root = host(view, 400, 300);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireMousePressed(cellByIndex(view, 0), false, false); // focus + select 0
            pump(root);
            fireKey(view, KeyCode.PAGE_DOWN, false, false);
            pump(root);
            int afterPageDown = sm.getSelectedIndex();
            assertTrue(afterPageDown >= 2, "PageDown advances by about one viewport of rows");
            assertTrue(view.getVisibleRange().firstIndex() > 0, "PageDown scrolled the viewport");

            fireKey(view, KeyCode.PAGE_UP, false, false);
            pump(root);
            assertTrue(sm.getSelectedIndex() < afterPageDown, "PageUp moves back toward the top");
        });
    }

    @Test
    public void anchorIsResetOnItemsSwap() throws Exception {
        onFx(() -> {
            ObservableList<String> listA = FXCollections.observableArrayList();
            for (int i = 0; i < 20; i++) {
                listA.add("A" + i);
            }
            RXTileView<String> view = new RXTileView<>(listA);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 8), false, false); // anchor + focus at 8
            pump(root);

            ObservableList<String> listB = FXCollections.observableArrayList();
            for (int i = 0; i < 20; i++) {
                listB.add("B" + i); // disjoint: "A8" is absent, so focus clears and anchor must reset
            }
            view.setItems(listB);
            pump(root);

            fireMousePressed(cellByIndex(view, 3), true, false); // shift+click is the first post-swap action
            pump(root);
            // With the stale anchor 8 retained, this would extend to [3,4,5,6,7,8]. After the
            // reset the anchor falls back to the just-focused click, so it stays a single cell.
            assertEquals(List.of(3), view.getSelectionModel().getSelectedIndices(),
                    "the stale anchor was dropped on swap; shift+click does not extend from index 8");
        });
    }

    @Test
    public void anchorIsResetOnItemsContentChange() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(20);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 8), false, false); // anchor + focus at 8
            pump(root);
            items.remove(0);
            pump(root);

            fireMousePressed(cellByIndex(view, 3), true, false);
            pump(root);
            assertEquals(List.of(3), view.getSelectionModel().getSelectedIndices(),
                    "same-list mutations drop the stale anchor before the next shift gesture");
        });
    }

    @Test
    public void anchorIsResetOnSelectionModelSwap() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 8), false, false); // anchor + focus at 8
            pump(root);

            RXTileSelectionModel<String> newModel = new RXTileSelectionModel<>(view);
            newModel.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionModel(newModel);
            pump(root);

            fireMousePressed(cellByIndex(view, 3), true, false);
            pump(root);
            assertEquals(List.of(3), view.getSelectionModel().getSelectedIndices(),
                    "selection model swaps drop the stale range anchor");
        });
    }

    @Test
    public void focusRingReResolvesByItemOnSwap() throws Exception {
        onFx(() -> {
            ObservableList<String> listA = FXCollections.observableArrayList("a", "b", "c", "d");
            RXTileView<String> view = new RXTileView<>(listA);
            view.setColumnCount(4);
            StackPane root = host(view, 400, 200);
            pump(root);
            fireMousePressed(cellByIndex(view, 1), false, false); // focus "b" at index 1
            pump(root);
            assertTrue(hasFocusRing(cellByIndex(view, 1)));

            view.setItems(FXCollections.observableArrayList("x", "y", "z", "b")); // "b" now at index 3
            pump(root);
            assertTrue(hasFocusRing(cellByIndex(view, 3)), "the focus ring follows item 'b' to its new index");
            assertFalse(hasFocusRing(cellByIndex(view, 1)), "the stale numeric index no longer carries the ring");
        });
    }

    @Test
    public void removingFocusedItemReanchorsFocusToSelectionLead() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(20);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 8), false, false);
            pump(root);
            items.remove(8);
            pump(root);

            assertEquals(7, view.getSelectionModel().getSelectedIndex(),
                    "selection falls back to the prior row after deleting the lead");
            assertTrue(hasFocusRing(cellByIndex(view, 7)),
                    "keyboard focus follows the selection lead instead of clearing");

            fireKey(view, KeyCode.RIGHT, false, false);
            pump(root);
            assertEquals(8, view.getSelectionModel().getSelectedIndex(),
                    "the next arrow key continues from the reanchored focus");
        });
    }

    @Test
    public void removingFocusedMultipleSelectionLeadReanchorsFocusToSurvivorLead() throws Exception {
        onFx(() -> {
            ObservableList<String> items = items(20);
            RXTileView<String> view = new RXTileView<>(items);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view, 400, 400);
            pump(root);

            fireMousePressed(cellByIndex(view, 1), false, false);
            pump(root);
            fireMousePressed(cellByIndex(view, 5), false, true);
            pump(root);
            assertEquals(List.of(1, 5), view.getSelectionModel().getSelectedIndices());
            assertTrue(hasFocusRing(cellByIndex(view, 5)));

            items.remove(5);
            pump(root);

            assertEquals(1, view.getSelectionModel().getSelectedIndex(),
                    "multiple selection promotes the surviving selected index as lead");
            assertTrue(hasFocusRing(cellByIndex(view, 1)),
                    "focus follows the surviving selection lead instead of the prior row");

            fireKey(view, KeyCode.RIGHT, false, false);
            pump(root);
            assertEquals(2, view.getSelectionModel().getSelectedIndex(),
                    "the next arrow key continues from the survivor lead");
        });
    }

    @Test
    public void spaceTogglesSelectionWithoutActivating() throws Exception {
        AtomicReference<RXTileViewActionEvent<String>> activated = new AtomicReference<>();
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            view.setOnAction(activated::set);
            StackPane root = host(view, 400, 400);
            pump(root);
            MultipleSelectionModel<String> sm = view.getSelectionModel();

            fireMousePressed(cellByIndex(view, 0), false, false); // select + focus 0
            pump(root);
            fireKey(view, KeyCode.SPACE, false, false); // toggles 0 off
            pump(root);
            assertTrue(sm.isEmpty(), "Space toggles the focused item's selection");
            fireKey(view, KeyCode.SPACE, false, false); // toggles 0 back on
            pump(root);
            assertEquals(List.of(0), sm.getSelectedIndices());
            assertNull(activated.get(), "Space toggles selection; it does not activate (unlike ListView)");
        });
    }

    @Test
    public void doubleClickOnEmptySpaceDoesNotActivate() throws Exception {
        AtomicReference<RXTileViewActionEvent<String>> activated = new AtomicReference<>();
        onFx(() -> {
            RXTileView<String> view = tiles(3); // few items: lots of empty viewport below
            view.setColumnCount(3);
            view.setOnAction(activated::set);
            StackPane root = host(view, 400, 400);
            pump(root);
            Node viewport = view.lookup(".viewport");
            assertNotNull(viewport);
            fireDoubleClick(viewport); // target is the viewport itself, no cell underneath
            assertNull(activated.get(), "double-click on empty space neither activates nor throws");
        });
    }

    @Test
    public void tabIsNotConsumedByTheKeyHandler() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(20);
            view.setColumnCount(3);
            StackPane root = host(view, 400, 400);
            pump(root);
            KeyEvent tab = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.TAB, false, false, false, false);
            view.fireEvent(tab);
            assertFalse(tab.isConsumed(), "Tab stays available for focus traversal (the view is one Tab stop)");
        });
    }

    // ==================== Reorder animation (PR5) ====================

    @Test
    public void columnCountChangeWithAnimationEngagesGlide() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12); // 12 cells fit without scrolling
            view.setColumnCount(4);
            view.setAnimated(true);
            StackPane root = host(view, 700, 400);
            pump(root);
            assertFalse(anyCellTranslated(view, 12), "no glide before a column change");

            view.setColumnCount(6);
            pump(root); // sets the start translate synchronously; no pulse clears it
            assertTrue(anyCellTranslated(view, 12),
                    "a 4->6 reflow with animated=true sets a transient translate on moved cells");
        });
    }

    @Test
    public void glidingCellsAreNotParkedMidFlight() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(4);
            view.setAnimated(true);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            RXTileCell<?> moved = cellByIndex(view, 5);
            assertNotNull(moved, "the cell for item 5 stays realized");
            assertTrue(moved.isVisible(), "a gliding cell is pinned, not parked");
        });
    }

    @Test
    public void noGlideWhenAnimationDisabled() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12); // animated defaults to false
            view.setColumnCount(4);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            assertFalse(anyCellTranslated(view, 12), "cells snap to new slots when animation is off");
        });
    }

    @Test
    public void disablingAnimationMidGlideSnaps() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(4);
            view.setAnimated(true);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            assertTrue(anyCellTranslated(view, 12), "glide engaged");

            view.setAnimated(false); // listener snaps in-flight glides synchronously
            assertFalse(anyCellTranslated(view, 12), "disabling animation mid-glide snaps to final");
        });
    }

    @Test
    public void nonPositiveDurationDisablesGlide() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(4);
            view.setAnimated(true);
            view.setAnimationDuration(Duration.ZERO);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            assertFalse(anyCellTranslated(view, 12), "a non-positive duration disables the glide");
        });
    }

    @Test
    public void sectionHeadersGlideVerticallyOnReorder() throws Exception {
        onFx(() -> {
            RXTileView<String> view = manySections(3, 6); // 6 items/section: 2 cols -> 3 rows, 3 cols -> 2 rows
            view.setColumnCount(2);
            view.setAnimated(true);
            StackPane root = host(view, 400, 700);
            pump(root);
            view.setColumnCount(3); // shrinks section 0's rows, shifting later headers up
            pump(root);

            boolean headerGliding = false;
            for (RXTileSectionCell header : headers(view)) {
                if (Math.abs(header.getTranslateY()) > 0.5 && Math.abs(header.getTranslateX()) < 0.5) {
                    headerGliding = true;
                    break;
                }
            }
            assertTrue(headerGliding, "section headers glide vertically (not horizontally) on a reorder");
        });
    }

    @Test
    public void nonColumnChangeRelayoutDoesNotGlide() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(4);
            view.setAnimated(true);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setVgap(30); // relayout, but the column count is unchanged
            pump(root);
            assertFalse(anyCellTranslated(view, 12), "only a column-count change glides; other relayouts snap");
        });
    }

    @Test
    public void disposeSnapsInFlightGlides() throws Exception {
        onFx(() -> {
            RXTileView<String> view = tiles(12);
            view.setColumnCount(4);
            view.setAnimated(true);
            StackPane root = host(view, 700, 400);
            pump(root);
            view.setColumnCount(6);
            pump(root);
            RXTileCell<?> moved = cellByIndex(view, 5);
            assertNotNull(moved);

            view.setSkin(null); // disposes the skin -> viewport.dispose() -> snapAllGlides()
            assertEquals(0.0, moved.getTranslateX(), 0.0001, "dispose snaps in-flight glides (no pinned leak)");
            assertEquals(0.0, moved.getTranslateY(), 0.0001);
        });
    }

    // ==================== Helpers ====================

    private static boolean anyCellTranslated(RXTileView<?> view, int itemCount) {
        for (int i = 0; i < itemCount; i++) {
            RXTileCell<?> cell = cellByIndex(view, i);
            if (cell != null && (Math.abs(cell.getTranslateX()) > 0.5 || Math.abs(cell.getTranslateY()) > 0.5)) {
                return true;
            }
        }
        return false;
    }

    private static ObservableList<String> items(int count) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            items.add("Item " + i);
        }
        return items;
    }

    private static RXTileView<String> tiles(int count) {
        return new RXTileView<>(items(count));
    }

    private static RXTileView<String> grouped4() {
        RXTileView<String> view = new RXTileView<>(
                FXCollections.observableArrayList("a1", "a2", "b1", "b2"));
        view.setColumnCount(2);
        view.setSectionKeyFactory(s -> s.substring(0, 1));
        return view;
    }

    private static RXTileView<String> manySections(int sectionCount, int perSection) {
        ObservableList<String> items = FXCollections.observableArrayList();
        for (int s = 0; s < sectionCount; s++) {
            for (int i = 0; i < perSection; i++) {
                items.add("s" + s + "#" + i);
            }
        }
        RXTileView<String> view = new RXTileView<>(items);
        view.setSectionKeyFactory(item -> item.substring(0, item.indexOf('#')));
        return view;
    }

    private static List<RXTileSectionCell> headers(RXTileView<?> view) {
        List<RXTileSectionCell> result = new ArrayList<>();
        for (Node node : view.lookupAll(".rx-tile-section-header")) {
            if (node instanceof RXTileSectionCell header && !header.isEmpty()) {
                result.add(header);
            }
        }
        return result;
    }

    private static StackPane host(RXTileView<?> view, double w, double h) {
        StackPane root = new StackPane(view);
        new Scene(root, w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static void fireWheel(Node target, double deltaY) {
        ScrollEvent event = new ScrollEvent(ScrollEvent.SCROLL,
                0, 0, 0, 0, false, false, false, false, false, false,
                0, deltaY, 0, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                0, new PickResult(target, 0, 0));
        target.fireEvent(event);
    }

    private static RXTileCell<?> cellByIndex(RXTileView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-tile-cell")) {
            if (node instanceof RXTileCell<?> cell && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    private static List<RXTileCell<?>> rowCells(RXTileView<?> view, int row) {
        List<RXTileCell<?>> cells = new ArrayList<>();
        for (Node node : view.lookupAll(".rx-tile-cell")) {
            if (node instanceof RXTileCell<?> cell && !cell.isEmpty() && cell.getRowIndex() == row) {
                cells.add(cell);
            }
        }
        cells.sort(Comparator.comparingInt((RXTileCell<?> cell) -> cell.getColumnIndex()));
        return cells;
    }

    // shortcut maps to both control and meta so isShortcutDown() is true on any platform.
    private static void fireKey(Node target, KeyCode code, boolean shift, boolean shortcut) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut, false, shortcut));
    }

    private static void fireMousePressed(Node target, boolean shift, boolean shortcut) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                shift, shortcut, false, shortcut, true, false, false, false, false, true,
                new PickResult(target, 0, 0)));
    }

    private static void fireDoubleClick(Node target) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(target, 0, 0)));
    }

    private static boolean isSelected(RXTileCell<?> cell) {
        return cell != null && cell.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("selected"));
    }

    private static boolean hasFocusRing(RXTileCell<?> cell) {
        return cell != null && cell.getPseudoClassStates().stream()
                .anyMatch(pc -> pc.getPseudoClassName().equals("focused"));
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

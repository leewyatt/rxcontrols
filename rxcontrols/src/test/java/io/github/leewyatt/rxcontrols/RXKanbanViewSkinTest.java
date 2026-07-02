package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import io.github.leewyatt.rxcontrols.event.CardActionEvent;
import io.github.leewyatt.rxcontrols.event.CardMovedEvent;
import io.github.leewyatt.rxcontrols.event.ColumnMovedEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skin / virtualization tests for {@link RXKanbanView} (phase 2 scope): the static
 * board renders, each column's cards fixed-height virtualize, default headers show
 * the title and count, the board placeholder shows when empty, illegal size values
 * fall back to the default, and a board wider than its viewport grows a horizontal
 * scroll bar. Each test drives a real (headless) layout pass.
 */
public class RXKanbanViewSkinTest {

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
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void emptyBoardShowsPlaceholder() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = new RXKanbanView<>();
            Region placeholder = new Region();
            board.setPlaceholder(placeholder);
            pump(host(board, 600, 400));
            assertTrue(placeholder.isVisible(), "placeholder shows on an empty board");
            assertEquals(0, realizedCards(board), "no card cells realized on an empty board");
        });
    }

    @Test
    public void columnsRenderDefaultHeaderTitleAndCount() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 3, "DOING", 0, "DONE", 1);
            pump(host(board, 900, 400));
            Set<String> labels = labelTexts(board);
            assertTrue(labels.contains("TODO"), "TODO header title rendered");
            assertTrue(labels.contains("DOING"), "DOING header title rendered");
            assertTrue(labels.contains("DONE"), "DONE header title rendered");
            assertTrue(labels.contains("3"), "TODO count pill shows 3");
            assertTrue(labels.contains("0"), "empty column count pill shows 0");
        });
    }

    @Test
    public void largeColumnVirtualizes() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("Backlog", 10_000);
            pump(host(board, 400, 400));
            int realized = realizedCards(board);
            assertTrue(realized > 0, "some cards realized");
            assertTrue(realized < 50,
                    "only the visible window is realized, not all 10000 cards (was " + realized + ")");
        });
    }

    @Test
    public void firstCardSitsAtTopOfColumnContent() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("Backlog", 50);
            board.setPrefCardHeight(80);
            board.setCardSpacing(10);
            pump(host(board, 400, 500));
            RXKanbanCardCell<?> first = cellByText(board, "card-0");
            RXKanbanCardCell<?> second = cellByText(board, "card-1");
            assertTrue(first != null && second != null, "first two cards realized");
            double gap = second.getLayoutY() - first.getLayoutY();
            assertEquals(90.0, gap, 1.0, "row stride is cardHeight(80) + cardSpacing(10)");
        });
    }

    @Test
    public void illegalCardHeightFallsBackToDefault() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("Backlog", 200);
            board.setPrefCardHeight(0);       // non-positive -> fallback (96)
            board.setCardSpacing(-5);         // negative -> clamped to 0
            pump(host(board, 400, 400));
            assertTrue(realizedCards(board) > 0, "board still renders with fallback sizing");
            RXKanbanCardCell<?> first = cellByText(board, "card-0");
            RXKanbanCardCell<?> second = cellByText(board, "card-1");
            assertEquals(96.0, second.getLayoutY() - first.getLayoutY(), 1.0,
                    "row stride is the default height (96) with spacing clamped to 0");
        });
    }

    @Test
    public void cardHeightAndSpacingChangesRelayoutImmediately() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("Backlog", 50);
            board.setPrefCardHeight(80.0);
            board.setCardSpacing(10.0);
            pump(host(board, 400, 600));
            assertEquals(90.0, cardStride(board), 1.0, "initial stride = 80 + 10");

            // Change ONLY the height: the stride must update on the next pulse, without
            // needing some other property (column width, etc.) to nudge the viewport.
            board.setPrefCardHeight(120.0);
            pump(board);
            assertEquals(130.0, cardStride(board), 1.0, "height change relayouts the viewport immediately");

            // Likewise for spacing alone.
            board.setCardSpacing(4.0);
            pump(board);
            assertEquals(124.0, cardStride(board), 1.0, "spacing change relayouts the viewport immediately");
        });
    }

    @Test
    public void wideBoardGrowsHorizontalScrollBar() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 1, "B", 1, "C", 1, "D", 1, "E", 1);
            board.setPrefColumnWidth(280);
            pump(host(board, 400, 400));
            assertTrue(hasVisibleHorizontalScrollBar(board),
                    "five 280px columns in a 400px board overflow horizontally");
        });
    }

    @Test
    public void narrowBoardHasNoHorizontalScrollBar() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 1, "B", 1);
            board.setPrefColumnWidth(150);
            pump(host(board, 900, 400));
            assertFalse(hasVisibleHorizontalScrollBar(board),
                    "two 150px columns fit in a 900px board");
        });
    }

    // ==================== Selection / focus / keyboard ====================

    @Test
    public void clickSelectsAndFocusesCard() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 5);
            RXKanbanColumn<String> todo = board.getColumns().get(0);
            pump(host(board, 500, 500));
            RXKanbanCardCell<?> cell = cellByText(board, "card-2");
            assertNotNull(cell, "card-2 realized");
            press(cell);
            assertSame(todo, board.getSelectedColumn());
            assertEquals(2, board.getSelectedCardIndex());
            assertEquals("card-2", board.getSelectedCard());
            assertSame(todo, board.getFocusedColumn());
            assertEquals(2, board.getFocusedCardIndex());
            assertTrue(hasPseudo(cell, "selected"), "clicked card gets :selected");
            assertTrue(hasPseudo(cell, "focused"), "clicked card gets :focused");
        });
    }

    @Test
    public void arrowDownMovesFocusNotSelection() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 5);
            pump(host(board, 500, 500));
            press(cellByText(board, "card-0"));
            key(board, KeyCode.DOWN);
            assertEquals(1, board.getFocusedCardIndex(), "focus moved down");
            assertEquals("card-1", board.getFocusedCard());
            assertEquals(0, board.getSelectedCardIndex(), "selection unchanged by arrow");
        });
    }

    @Test
    public void arrowRightMovesFocusToAdjacentColumn() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 3, "B", 3);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));
            press(cellByText(board, "card-1"));       // in column A
            key(board, KeyCode.RIGHT);
            assertSame(b, board.getFocusedColumn(), "focus jumped to column B");
            assertEquals(1, board.getFocusedCardIndex(), "nearest index preserved");
        });
    }

    @Test
    public void keyboardFocusSkipsHiddenColumn() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 3, "B", 3, "C", 3);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            RXKanbanColumn<String> c = board.getColumns().get(2);
            b.setVisible(false);
            pump(host(board, 1000, 500));
            board.updateFocus(a, 1);
            key(board, KeyCode.RIGHT);
            assertSame(c, board.getFocusedColumn(), "RIGHT skips hidden B and lands on C");
            key(board, KeyCode.LEFT);
            assertSame(a, board.getFocusedColumn(), "LEFT skips hidden B back to A");
        });
    }

    @Test
    public void keyboardActionsIgnoreHiddenFocusedColumn() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 3, "B", 3);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            AtomicReference<CardActionEvent<String>> action = new AtomicReference<>();
            board.setOnCardAction(action::set);
            pump(host(board, 900, 500));
            board.updateFocus(a, 1);
            a.setVisible(false);   // the focused column becomes hidden after it was focused
            pump(board);
            key(board, KeyCode.ENTER);
            assertNull(action.get(), "Enter on a hidden focused column fires no card action");
            key(board, KeyCode.SPACE);
            assertNull(board.getSelectedCard(), "Space does not select a hidden column's card");
        });
    }

    @Test
    public void arrowKeysRecoverFromHiddenFocusedColumn() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 3, "B", 3);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));
            board.updateFocus(a, 1);
            a.setVisible(false);   // the focused column becomes hidden after it was focused
            pump(board);
            key(board, KeyCode.DOWN);
            assertSame(b, board.getFocusedColumn(), "DOWN recovers focus onto a visible column");
            assertNotSame(a, board.getFocusedColumn(), "focus does not stay inside the hidden column");
        });
    }

    @Test
    public void inPlaceCardReplaceRefreshesSelectionProjection() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();   // A = [A0, A1, A2]
            RXKanbanColumn<String> a = board.getColumns().get(0);
            pump(host(board, 900, 500));
            board.updateSelection(a, 1);
            board.updateFocus(a, 1);
            assertEquals("A1", board.getSelectedCard());
            assertEquals("A1", board.getFocusedCard());

            a.getCards().set(1, "A1-edited");   // in-place replace: size unchanged
            pump(board);
            assertEquals("A1-edited", board.getSelectedCard(),
                    "selectedCard projection tracks an in-place replace at the selected index");
            assertEquals("A1-edited", board.getFocusedCard(),
                    "focusedCard projection tracks an in-place replace at the focused index");
        });
    }

    @Test
    public void enterFiresCardActionForFocusedCard() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 4);
            RXKanbanColumn<String> todo = board.getColumns().get(0);
            AtomicReference<CardActionEvent<String>> fired = new AtomicReference<>();
            board.setOnCardAction(fired::set);
            pump(host(board, 500, 500));
            press(cellByText(board, "card-1"));
            key(board, KeyCode.ENTER);
            CardActionEvent<String> event = fired.get();
            assertNotNull(event, "card action fired");
            assertEquals("card-1", event.getCard());
            assertEquals(1, event.getIndex());
            assertSame(todo, event.getColumn());
        });
    }

    @Test
    public void doubleClickFiresCardAction() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 4);
            AtomicReference<CardActionEvent<String>> fired = new AtomicReference<>();
            board.setOnCardAction(fired::set);
            pump(host(board, 500, 500));
            doubleClick(cellByText(board, "card-3"));
            assertNotNull(fired.get(), "double-click fired card action");
            assertEquals("card-3", fired.get().getCard());
        });
    }

    @Test
    public void spaceSelectsFocusedCard() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 5);
            pump(host(board, 500, 500));
            press(cellByText(board, "card-0"));
            key(board, KeyCode.DOWN);
            key(board, KeyCode.SPACE);
            assertEquals(1, board.getSelectedCardIndex(), "space selects the focused card");
            assertEquals("card-1", board.getSelectedCard());
        });
    }

    @Test
    public void selectionClampsWhenFocusedColumnShrinks() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("TODO", 5);
            RXKanbanColumn<String> todo = board.getColumns().get(0);
            pump(host(board, 500, 500));
            press(cellByText(board, "card-4"));       // select last
            assertEquals(4, board.getSelectedCardIndex());
            todo.getCards().remove(0);                // now 4 cards, index 4 out of range
            assertEquals(3, board.getSelectedCardIndex(), "selection index clamped to new size");
            assertEquals("card-4", board.getSelectedCard(), "projection re-derived to card now at index 3");
        });
    }

    @Test
    public void selectionClearsWhenColumnRemoved() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 2, "B", 2);
            pump(host(board, 900, 500));
            press(cellByText(board, "card-0"));       // selects a card in column A
            board.getColumns().remove(0);             // remove column A
            assertEquals(-1, board.getSelectedCardIndex(), "selection cleared when its column is removed");
            assertTrue(board.getSelectedColumn() == null, "selected column cleared");
        });
    }

    // ==================== Drag and drop ====================

    @Test
    public void crossColumnMoveCommitsByIndex() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            AtomicReference<CardMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnCardMoved(fired::set);
            pump(host(board, 900, 500));

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            RXKanbanCardCell<?> targetTop = cellByText(board, "B0");
            dragTo(board, source, center(targetTop));
            pump(board);

            assertEquals(List.of("A1", "A2"), a.getCards(), "A0 removed from source column by index");
            assertEquals(List.of("A0", "B0", "B1"), b.getCards(), "A0 inserted at top of target column");
            CardMovedEvent<String> event = fired.get();
            assertNotNull(event, "CardMovedEvent fired");
            assertSame(a, event.getFromColumn());
            assertEquals(0, event.getFromIndex());
            assertSame(b, event.getToColumn());
            assertEquals(0, event.getToIndex());
            assertFalse(event.isReorder());
        });
    }

    @Test
    public void crossColumnMoveAtNonZeroIndex() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            AtomicReference<CardMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnCardMoved(fired::set);
            pump(host(board, 900, 500));

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            RXKanbanCardCell<?> b0 = cellByText(board, "B0");
            Bounds bb = b0.localToScene(b0.getBoundsInLocal());
            // Drop one stride below B0's top -> lands A0 after B0 (target index 1).
            double stride = bb.getHeight() + 8.0;
            dragTo(board, source, new double[]{(bb.getMinX() + bb.getMaxX()) / 2.0, bb.getMinY() + stride});
            pump(board);

            assertEquals(List.of("A1", "A2"), a.getCards());
            assertEquals(List.of("B0", "A0", "B1"), b.getCards(), "A0 lands between B0 and B1");
            assertEquals(1, fired.get().getToIndex(), "cross-column toIndex is 1");
        });
    }

    @Test
    public void cardDropAboveBoardDoesNotCommit() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            AtomicReference<CardMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnCardMoved(fired::set);
            pump(host(board, 900, 500));

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            double columnX = center(cellByText(board, "B0"))[0];   // x still over a column
            double aboveBoard = board.localToScene(board.getBoundsInLocal()).getMinY() - 40.0;
            dragTo(board, source, new double[]{columnX, aboveBoard});
            pump(board);

            assertNull(fired.get(), "releasing above the board (off it vertically) fires no move");
            assertEquals(List.of("A0", "A1", "A2"), a.getCards(), "source column unchanged");
            assertEquals(List.of("B0", "B1"), b.getCards(), "target column unchanged");
        });
    }

    @Test
    public void draggedGhostDoesNotExpandOverlayLayoutBounds() throws Exception {
        // Board edge hit-testing AND horizontal auto-scroll read overlay.getLayoutBounds(),
        // NOT getBoundsInLocal(): the ghost is an overlay child that follows the pointer off
        // the board and would otherwise corrupt those bounds. This locks that invariant.
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            pump(host(board, 500, 400));
            Region overlay = (Region) board.lookup(".drag-overlay");
            assertNotNull(overlay, "drag overlay present");

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            Bounds sb = source.localToScene(source.getBoundsInLocal());
            double sx = (sb.getMinX() + sb.getMaxX()) / 2.0;
            double sy = (sb.getMinY() + sb.getMaxY()) / 2.0;
            source.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, source));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy + 12.0, board));   // start drag
            double layoutW = overlay.getLayoutBounds().getWidth();

            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 4000.0, sy, board));   // fling ghost off-board
            assertEquals(layoutW, overlay.getLayoutBounds().getWidth(), 0.5,
                    "layout width is unaffected by the ghost position");
            assertTrue(overlay.getBoundsInLocal().getWidth() > layoutW + 100.0,
                    "getBoundsInLocal DID grow with the ghost — hence auto-scroll/hit-test use getLayoutBounds()");
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, sx + 4000.0, sy, board));   // cleanup
        });
    }

    @Test
    public void sameColumnReorderCommits() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            AtomicReference<CardMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnCardMoved(fired::set);
            pump(host(board, 900, 500));

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            Bounds sb = source.localToScene(source.getBoundsInLocal());
            // Drop one stride below the source top -> lands A0 after A1 (index 1 of the
            // source-removed list [A1, A2]).
            double stride = sb.getHeight() + 8.0;
            dragTo(board, source, new double[]{(sb.getMinX() + sb.getMaxX()) / 2.0, sb.getMinY() + stride});
            pump(board);

            assertEquals(List.of("A1", "A0", "A2"), a.getCards(), "same-column reorder by index");
            assertTrue(fired.get() != null && fired.get().isReorder(), "reorder event fired");
        });
    }

    @Test
    public void vetoedMoveLeavesDataUnchanged() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            board.setOnCardMoved(Event::consume);
            pump(host(board, 900, 500));

            dragTo(board, cellByText(board, "A0"), center(cellByText(board, "B0")));
            pump(board);

            assertEquals(List.of("A0", "A1", "A2"), a.getCards(), "consumed move does not mutate source");
            assertEquals(List.of("B0", "B1"), b.getCards(), "consumed move does not mutate target");
        });
    }

    @Test
    public void dropValidatorRejectsMove() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            // Reject any drop into column B.
            board.setDropValidator(ctx -> ctx.getTargetColumn() != b);
            AtomicReference<CardMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnCardMoved(fired::set);
            pump(host(board, 900, 500));

            dragTo(board, cellByText(board, "A0"), center(cellByText(board, "B0")));
            pump(board);

            assertEquals(List.of("A0", "A1", "A2"), a.getCards(), "rejected drop leaves source unchanged");
            assertEquals(List.of("B0", "B1"), b.getCards(), "rejected drop leaves target unchanged");
            assertTrue(fired.get() == null, "no move event when the drop is rejected");
        });
    }

    @Test
    public void dragDisabledWhenCardDragDisabled() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            board.setCardDragEnabled(false);
            pump(host(board, 900, 500));

            dragTo(board, cellByText(board, "A0"), center(cellByText(board, "B0")));
            pump(board);

            assertEquals(List.of("A0", "A1", "A2"), a.getCards(), "no drag when cardDragEnabled=false");
        });
    }

    @Test
    public void animatedFalseSnapsWithoutGlide() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            board.setAnimated(false);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            pump(host(board, 900, 500));

            // A card change would settle-glide when animated; with animation off every
            // cell must sit at its final layout position (no residual translate).
            a.getCards().add(0, "A-new");
            pump(board);

            for (Node node : board.lookupAll(".rx-kanban-card-cell")) {
                assertEquals(0.0, node.getTranslateY(), 0.001, "no residual glide translate when animated=false");
            }
            assertNotNull(cellByText(board, "A-new"), "inserted card rendered");
        });
    }

    // ==================== Accessibility ====================

    @Test
    public void accessibilityRolesAndProjection() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            a.getCards().setAll("A0", "A1", "A2");
            pump(host(board, 900, 500));

            assertEquals(AccessibleRole.PARENT, board.getAccessibleRole(), "board root is PARENT");

            press(cellByText(board, "A1"));
            pump(board);

            Region colA = (Region) headerOf(board, "A").getParent();
            Region colB = (Region) headerOf(board, "B").getParent();
            Node vpA = colA.lookup(".content");
            Node vpB = colB.lookup(".content");
            assertEquals(AccessibleRole.LIST_VIEW, vpA.getAccessibleRole(), "column card area is LIST_VIEW");
            assertEquals(3, vpA.queryAccessibleAttribute(AccessibleAttribute.ITEM_COUNT));

            Node focusA = (Node) vpA.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM);
            assertNotNull(focusA, "focused column reports its focus item");
            assertEquals(AccessibleRole.LIST_ITEM, focusA.getAccessibleRole(), "card is LIST_ITEM");
            assertEquals("A1", ((RXKanbanCardCell<?>) focusA).getItem());
            assertEquals(1, ((List<?>) vpA.queryAccessibleAttribute(AccessibleAttribute.SELECTED_ITEMS)).size());

            // The board model projects to the owning column only: column B answers empty.
            assertNull(vpB.queryAccessibleAttribute(AccessibleAttribute.FOCUS_ITEM), "non-focused column: no focus item");
            assertTrue(((List<?>) vpB.queryAccessibleAttribute(AccessibleAttribute.SELECTED_ITEMS)).isEmpty());
        });
    }

    @Test
    public void cardDragSurvivesSecondButtonPressThenPrimaryRelease() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));

            RXKanbanCardCell<?> source = cellByText(board, "A0");
            double[] target = center(cellByText(board, "B0"));
            Bounds sb = source.localToScene(source.getBoundsInLocal());
            double sx = (sb.getMinX() + sb.getMaxX()) / 2.0;
            double sy = (sb.getMinY() + sb.getMaxY()) / 2.0;

            source.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, source));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy + 12.0, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, target[0], target[1], board));
            // Press a SECOND button mid-drag, then release the primary button.
            secondaryPress(board, target[0], target[1]);
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, target[0], target[1], board));
            pump(board);

            assertEquals(List.of("A1", "A2"), a.getCards(), "drag still commits on primary release");
            assertEquals(List.of("A0", "B0", "B1"), b.getCards());
            Pane overlay = (Pane) board.lookup(".drag-overlay");
            assertNotNull(overlay, "drag overlay present");
            assertTrue(overlay.getChildrenUnmodifiable().isEmpty(), "no ghost stranded on the overlay");
        });
    }

    @Test
    public void cardDragSurvivesSecondaryButtonReleaseMidDrag() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));
            RXKanbanCardCell<?> source = cellByText(board, "A0");
            double[] target = center(cellByText(board, "B0"));
            Bounds sb = source.localToScene(source.getBoundsInLocal());
            double sx = (sb.getMinX() + sb.getMaxX()) / 2.0;
            double sy = (sb.getMinY() + sb.getMaxY()) / 2.0;

            source.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, source));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy + 12.0, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, target[0], target[1], board));
            // Releasing a SECONDARY button mid-drag must neither commit nor drop the gesture.
            secondaryRelease(board, target[0], target[1]);
            pump(board);
            assertEquals(List.of("A0", "A1", "A2"), a.getCards(), "secondary release does not commit");
            Pane overlay = (Pane) board.lookup(".drag-overlay");
            assertFalse(overlay.getChildrenUnmodifiable().isEmpty(), "drag is still active (ghost present)");

            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, target[0], target[1], board));
            pump(board);
            assertEquals(List.of("A1", "A2"), a.getCards(), "the primary release commits");
            assertEquals(List.of("A0", "B0", "B1"), b.getCards());
        });
    }

    @Test
    public void secondaryPressBeforeThresholdKeepsCardGestureArmed() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));
            RXKanbanCardCell<?> source = cellByText(board, "A0");
            double[] target = center(cellByText(board, "B0"));
            Bounds sb = source.localToScene(source.getBoundsInLocal());
            double sx = (sb.getMinX() + sb.getMaxX()) / 2.0;
            double sy = (sb.getMinY() + sb.getMaxY()) / 2.0;

            source.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, source));   // arm, no motion yet
            secondaryPress(board, sx, sy);   // a foreign press before the threshold must NOT disarm
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy + 12.0, board));   // now cross threshold
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, target[0], target[1], board));
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, target[0], target[1], board));
            pump(board);
            assertEquals(List.of("A1", "A2"), a.getCards(), "the gesture stayed armed through the foreign press");
            assertEquals(List.of("A0", "B0", "B1"), b.getCards());
        });
    }

    @Test
    public void columnDragSurvivesSecondButtonPressThenPrimaryRelease() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            board.setColumnReorderEnabled(true);
            pump(host(board, 1200, 500));

            Node headerA = headerOf(board, "A");
            Node headerB = headerOf(board, "B");
            Region colA = (Region) headerA.getParent();
            Bounds ha = headerA.localToScene(headerA.getBoundsInLocal());
            Bounds hb = headerB.localToScene(headerB.getBoundsInLocal());
            double sx = (ha.getMinX() + ha.getMaxX()) / 2.0;
            double sy = (ha.getMinY() + ha.getMaxY()) / 2.0;
            double pastB = (hb.getMinX() + hb.getMaxX()) / 2.0 + 6.0;

            headerA.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, headerA));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, pastB, sy, board));
            secondaryPress(board, pastB, sy);
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, pastB, sy, board));
            pump(board);

            assertEquals(List.of("B", "A", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "column reorder still commits on primary release");
            assertEquals(0.0, colA.getTranslateX(), 0.001, "dragged column not stranded at the pointer");
        });
    }

    // ==================== Column justify / responsive width ====================

    @Test
    public void stretchFillsBoardWidth() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.STRETCH);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            double content = contentWidth(board);
            double fill = (content - 24.0) / 3.0;   // 3 columns, 2 gaps of 12
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertTrue(a.getWidth() > 260.0, "columns grow past prefColumnWidth to fill: " + a.getWidth());
            assertEquals(fill, a.getWidth(), 2.0, "columns share the width equally");
            assertEquals(content, c.getLayoutX() + c.getWidth(), 3.0, "last column reaches the right edge");
            assertEquals(0.0, a.getLayoutX(), 1.0, "first column hugs the left edge when filled");
        });
    }

    @Test
    public void startLeavesTrailingSlack() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setAnimated(false);
            pump(host(board, 900, 400));   // default justify START
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertEquals(200.0, a.getWidth(), 2.0, "START keeps columns at prefColumnWidth");
            assertEquals(0.0, a.getLayoutX(), 1.0, "block hugs the left edge");
            assertTrue(c.getLayoutX() + c.getWidth() < 700.0, "trailing space stays empty on the right");
        });
    }

    @Test
    public void centerJustifyCentersBlock() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.CENTER);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            // Block width 3*200 + 2*12 = 624; the leftover is split before and after it.
            double content = contentWidth(board);
            double startX = (content - 624.0) / 2.0;
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertEquals(startX, a.getLayoutX(), 3.0, "block is centered");
            assertEquals(200.0, a.getWidth(), 2.0, "CENTER only positions, keeps prefColumnWidth");
            assertEquals(startX + 624.0, c.getLayoutX() + c.getWidth(), 3.0);
        });
    }

    @Test
    public void narrowBoardShrinksColumnsToFit() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setMinColumnWidth(120.0);
            board.setAnimated(false);
            pump(host(board, 534, 400));
            // Columns at pref overflow, so they shrink to fill; fill stays above min 120.
            double content = contentWidth(board);
            double fill = (content - 24.0) / 3.0;
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertTrue(a.getWidth() < 200.0, "columns shrink below pref to fit: " + a.getWidth());
            assertEquals(fill, a.getWidth(), 3.0, "columns shrink to the exact fit width");
            assertEquals(content, c.getLayoutX() + c.getWidth(), 3.0, "shrunk columns fill the board");
            assertFalse(horizontalScrollBarVisible(board), "no scrollbar while columns still fit by shrinking");
        });
    }

    @Test
    public void tooNarrowBoardScrollsAtMinWidth() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setMinColumnWidth(150.0);
            board.setAnimated(false);
            pump(host(board, 300, 400));
            // Even at min 150: 3*150+24 = 474 > 300 → scroll, columns pinned at min.
            Region a = boxOf(board, "A");
            assertEquals(150.0, a.getWidth(), 2.0, "columns bottom out at minColumnWidth");
            assertTrue(horizontalScrollBarVisible(board), "board scrolls once columns hit the min floor");
        });
    }

    @Test
    public void maxColumnWidthCapsStretch() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.STRETCH);
            board.setMaxColumnWidth(240.0);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            // fill (~286) exceeds the 240 cap → width pinned to 240; the block is centered.
            double content = contentWidth(board);
            double startX = (content - (240.0 * 3.0 + 24.0)) / 2.0;
            Region a = boxOf(board, "A");
            assertEquals(240.0, a.getWidth(), 2.0, "stretch is capped at maxColumnWidth");
            assertEquals(startX, a.getLayoutX(), 3.0, "the capped block is centered");
        });
    }

    @Test
    public void columnReorderCommitsUnderStretch() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setColumnsJustify(ItemsJustify.STRETCH);
            board.setColumnReorderEnabled(true);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            dragColumn(board, "A", "B");
            pump(board);
            assertEquals(List.of("B", "A", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "reorder still commits when columns are stretched to fill");
        });
    }

    @Test
    public void minZeroShrinksToNothingWithoutScrolling() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setMinColumnWidth(0.0);   // 0 is a real floor: shrink freely, never scroll
            board.setAnimated(false);
            pump(host(board, 300, 400));
            Region a = boxOf(board, "A");
            assertTrue(a.getWidth() < 150.0, "columns shrink well below pref toward 0: " + a.getWidth());
            assertFalse(horizontalScrollBarVisible(board), "min 0 never scrolls, it keeps shrinking");
        });
    }

    @Test
    public void defaultMinDoesNotShrinkAndScrolls() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setAnimated(false);
            // Default minColumnWidth is USE_COMPUTED_SIZE (negative) — no shrink.
            pump(host(board, 300, 400));
            Region a = boxOf(board, "A");
            assertEquals(200.0, a.getWidth(), 2.0, "default keeps columns at prefColumnWidth");
            assertTrue(horizontalScrollBarVisible(board), "default scrolls instead of shrinking");
        });
    }

    @Test
    public void endJustifyHugsRightEdge() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.END);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            double content = contentWidth(board);
            double startX = content - (200.0 * 3.0 + 24.0);   // block pushed to the right
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertEquals(startX, a.getLayoutX(), 3.0, "END pushes the block to the right edge");
            assertEquals(content, c.getLayoutX() + c.getWidth(), 3.0, "last column hugs the right edge");
            assertEquals(200.0, a.getWidth(), 2.0, "END only positions, keeps pref width");
        });
    }

    @Test
    public void spaceBetweenSpreadsInnerGaps() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.SPACE_BETWEEN);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            double content = contentWidth(board);
            Region a = boxOf(board, "A");
            Region b = boxOf(board, "B");
            Region c = boxOf(board, "C");
            assertEquals(0.0, a.getLayoutX(), 1.0, "first column hugs the left edge");
            assertEquals(content, c.getLayoutX() + c.getWidth(), 3.0, "last column hugs the right edge");
            double gapAB = b.getLayoutX() - (a.getLayoutX() + a.getWidth());
            double gapBC = c.getLayoutX() - (b.getLayoutX() + b.getWidth());
            assertEquals(gapAB, gapBC, 1.0, "inner gaps are equal");
            assertTrue(gapAB > 12.0, "inner gaps grew beyond the base spacing");
        });
    }

    @Test
    public void spaceEvenlyBalancesEdgeGaps() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.SPACE_EVENLY);
            board.setAnimated(false);
            pump(host(board, 900, 400));
            double content = contentWidth(board);
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            double leading = a.getLayoutX();
            double trailing = content - (c.getLayoutX() + c.getWidth());
            assertEquals(leading, trailing, 1.5, "SPACE_EVENLY balances the two edge gaps");
            assertTrue(leading > 1.0, "edge gaps are non-zero");
            assertEquals(200.0, a.getWidth(), 2.0, "SPACE_EVENLY only positions, keeps pref width");
        });
    }

    @Test
    public void hidingLastColumnUnderStretchLeavesNoTrailingGap() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.STRETCH);
            board.setAnimated(false);
            RXKanbanColumn<String> c = board.getColumns().get(2);
            pump(host(board, 900, 400));
            c.setVisible(false);
            pump(board);
            double content = contentWidth(board);
            double fill = (content - 12.0) / 2.0;   // 2 shown columns, ONE gap
            Region a = boxOf(board, "A");
            Region b = boxOf(board, "B");
            assertEquals(fill, a.getWidth(), 2.0, "survivors fill the width across a single gap");
            assertEquals(content, b.getLayoutX() + b.getWidth(), 3.0,
                    "the last visible column hugs the right edge — no phantom trailing gap");
        });
    }

    @Test
    public void hidingColumnUnderStretchRefillsSurvivors() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setPrefColumnWidth(200.0);
            board.setColumnSpacing(12.0);
            board.setColumnsJustify(ItemsJustify.STRETCH);
            board.setAnimated(false);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 400));
            double before = boxOf(board, "A").getWidth();
            b.setVisible(false);
            pump(board);
            double content = contentWidth(board);
            Region a = boxOf(board, "A");
            Region c = boxOf(board, "C");
            assertTrue(a.getWidth() > before + 100.0, "survivors grow to refill the freed space");
            assertEquals((content - 12.0) / 2.0, a.getWidth(), 2.0, "two survivors share the width");
            assertEquals(content, c.getLayoutX() + c.getWidth(), 3.0, "survivors still fill to the right edge");
        });
    }

    // ==================== Column level (hide / reorder) ====================

    @Test
    public void hiddenColumnDisappearsCompletely() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = twoColumnBoard();
            board.setAnimated(false);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            pump(host(board, 900, 500));

            Region colA = (Region) headerOf(board, "A").getParent();
            Region colB = (Region) headerOf(board, "B").getParent();
            double expandedWidth = colA.getWidth();
            assertTrue(expandedWidth > 200.0, "shown column near prefColumnWidth");
            double bShownX = colB.getLayoutX();

            a.setVisible(false);
            pump(board);

            assertTrue(colA.getWidth() < 1.0, "hidden column has zero width: " + colA.getWidth());
            assertFalse(colA.isVisible(), "column is fully hidden");
            // The neighbour closes up into the vacated space (width + gap both gone).
            assertTrue(colB.getLayoutX() < bShownX - 100.0, "sibling column slides left over the hidden one");

            a.setVisible(true);
            pump(board);
            assertTrue(colA.isVisible(), "showing restores the column");
            assertTrue(colA.getWidth() > 200.0, "shown width restored");
        });
    }

    @Test
    public void hiddenColumnSurvivesReorderAndReShows() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            pump(host(board, 1200, 500));
            RXKanbanColumn<String> a = board.getColumns().get(0);
            RXKanbanColumn<String> b = board.getColumns().get(1);
            RXKanbanColumn<String> c = board.getColumns().get(2);
            Region colC = (Region) headerOf(board, "C").getParent();

            c.setVisible(false);
            pump(board);
            assertFalse(colC.isVisible(), "C hidden");

            // Reorder so C is no longer at index 2 (what a header drag does internally).
            board.getColumns().setAll(c, a, b);
            pump(board);
            assertEquals(0, board.getColumns().indexOf(c), "C moved to the front");
            assertFalse(colC.isVisible(), "C stays hidden by identity across the reorder");

            // Re-showing the SAME column object works regardless of its new index —
            // this is why callers must toggle by identity, not by a stale index.
            c.setVisible(true);
            pump(board);
            assertTrue(colC.isVisible(), "C re-shows by identity after reorder");
            assertTrue(colC.getWidth() > 200.0, "C width restored");
        });
    }

    @Test
    public void columnReorderWithHiddenColumnCommitsCorrectOrder() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 1, "B", 1, "C", 1, "D", 1);
            board.setColumnReorderEnabled(true);
            board.setAnimated(false);
            board.getColumns().get(1).setVisible(false);   // hide B (index 1)
            pump(host(board, 1200, 400));
            dragColumn(board, "A", "C");   // drag A past visible C's center
            pump(board);
            assertEquals(List.of("C", "B", "A", "D"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "visible [A,C,D] -> [C,A,D]; hidden B stays pinned at its absolute slot (index 1)");
        });
    }

    @Test
    public void columnReorderWithLeadingHiddenColumnPinsHiddenSlot() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("H", 1, "A", 1, "B", 1);
            board.setColumnReorderEnabled(true);
            board.setAnimated(false);
            board.getColumns().get(0).setVisible(false);   // hide the LEADING column H
            pump(host(board, 1000, 400));

            // Drag B leftward, releasing left of A's centre so it lands before A.
            Node headerB = headerOf(board, "B");
            Bounds bb = headerB.localToScene(headerB.getBoundsInLocal());
            Bounds ab = headerOf(board, "A").localToScene(headerOf(board, "A").getBoundsInLocal());
            double sx = (bb.getMinX() + bb.getMaxX()) / 2.0;
            double sy = (bb.getMinY() + bb.getMaxY()) / 2.0;
            double targetX = ab.getMinX() + 5.0;   // inside A but left of its centre
            headerB.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, headerB));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx - 12.0, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, targetX, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, targetX, sy, board));
            pump(board);

            assertEquals(List.of("H", "B", "A"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "visible [A,B] -> [B,A]; leading hidden H stays pinned at index 0");
        });
    }

    @Test
    public void columnMovedEventToIndexIsFinalAbsoluteSlotWithHiddenColumn() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("A", 1, "B", 1, "C", 1, "D", 1);
            board.setColumnReorderEnabled(true);
            board.setAnimated(false);
            board.getColumns().get(1).setVisible(false);   // hide B (index 1)
            AtomicReference<ColumnMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnColumnMoved(fired::set);
            pump(host(board, 1200, 400));
            dragColumn(board, "A", "C");   // commits [C, B, A, D]
            pump(board);

            ColumnMovedEvent<String> event = fired.get();
            assertNotNull(event, "ColumnMovedEvent fired");
            assertEquals(0, event.getFromIndex());
            // toIndex is the moved column's FINAL absolute slot (A in [C,B,A,D]), NOT a plain
            // remove/add target — the contract a consumer must reproduce.
            assertEquals(2, event.getToIndex(), "toIndex is A's index in the committed order");
            assertEquals(event.getToIndex(), board.getColumns().indexOf(event.getColumn()),
                    "toIndex matches where the moved column actually landed");
        });
    }

    @Test
    public void columnReorderNoOpWithLeadingHiddenColumnDoesNotMutate() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("H", 1, "A", 1, "B", 1);
            board.setColumnReorderEnabled(true);
            board.setAnimated(false);
            board.getColumns().get(0).setVisible(false);   // hide the LEADING column H
            AtomicReference<ColumnMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnColumnMoved(fired::set);
            pump(host(board, 1000, 400));

            // Drag A a little (past the threshold) but drop it where it already sits among
            // the visible columns (left of B's centre) -> a visible no-op.
            Node headerA = headerOf(board, "A");
            Bounds hb = headerA.localToScene(headerA.getBoundsInLocal());
            double sx = (hb.getMinX() + hb.getMaxX()) / 2.0;
            double sy = (hb.getMinY() + hb.getMaxY()) / 2.0;
            headerA.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, headerA));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 20.0, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, sx + 20.0, sy, board));
            pump(board);

            assertNull(fired.get(), "a visible no-op fires no ColumnMovedEvent");
            assertEquals(List.of("H", "A", "B"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "the hidden leading column is not shuffled by a no-op drop");
        });
    }

    @Test
    public void wipOverLimitTogglesPseudoAndCountLabel() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = board("Doing", 3);
            RXKanbanColumn<String> doing = board.getColumns().get(0);
            doing.setWipLimit(2);
            pump(host(board, 400, 400));
            Region box = boxOf(board, "Doing");
            Label wip = (Label) box.lookup(".wip-indicator");
            assertNotNull(wip, "wip indicator present");
            assertTrue(hasPseudo(box, "over-limit"), "3 cards over a limit of 2 sets :over-limit");
            assertEquals("3/2", wip.getText());
            doing.getCards().remove(0);
            pump(board);
            assertFalse(hasPseudo(box, "over-limit"), "2 cards at a limit of 2 is not over the limit");
            assertEquals("2/2", wip.getText());
        });
    }

    @Test
    public void columnDragOpensLiveMakeWayGap() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            board.setColumnReorderEnabled(true);
            pump(host(board, 1200, 500));

            Node headerA = headerOf(board, "A");
            Node headerB = headerOf(board, "B");
            Region colB = (Region) headerB.getParent();
            double bNaturalX = colB.getLayoutX();

            Bounds ha = headerA.localToScene(headerA.getBoundsInLocal());
            Bounds hb = headerB.localToScene(headerB.getBoundsInLocal());
            double sx = (ha.getMinX() + ha.getMaxX()) / 2.0;
            double sy = (ha.getMinY() + ha.getMaxY()) / 2.0;
            double pastB = (hb.getMinX() + hb.getMaxX()) / 2.0 + 6.0;

            // Press A's header and hover past B WITHOUT releasing.
            headerA.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, headerA));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy, board));
            board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, pastB, sy, board));
            pump(board);

            // Mid-drag (before release) the neighbour parts to open a slot for A.
            assertTrue(colB.getLayoutX() < bNaturalX - 100.0,
                    "column B slides aside to make way mid-drag: " + colB.getLayoutX() + " vs " + bNaturalX);

            board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, pastB, sy, board));
        });
    }

    @Test
    public void columnReorderMovesByIndex() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            board.setColumnReorderEnabled(true);
            AtomicReference<ColumnMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnColumnMoved(fired::set);
            pump(host(board, 1200, 500));

            dragColumn(board, "A", "B");
            pump(board);

            assertEquals(List.of("B", "A", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "A reordered to index 1");
            ColumnMovedEvent<String> event = fired.get();
            assertNotNull(event, "ColumnMovedEvent fired");
            assertEquals(0, event.getFromIndex());
            assertEquals(1, event.getToIndex());
        });
    }

    @Test
    public void columnReorderPreservesSelection() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            board.setColumnReorderEnabled(true);
            RXKanbanColumn<String> a = board.getColumns().get(0);
            pump(host(board, 1200, 500));

            press(cellByText(board, "A1"));
            pump(board);
            assertSame(a, board.getSelectedColumn(), "column A card selected before reorder");
            assertEquals(1, board.getSelectedCardIndex());

            dragColumn(board, "A", "B");
            pump(board);

            assertEquals(List.of("B", "A", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList());
            // The atomic commit keeps A continuously present, so its selection survives.
            assertSame(a, board.getSelectedColumn(), "selection preserved across reorder");
            assertEquals(1, board.getSelectedCardIndex(), "selected index preserved across reorder");
        });
    }

    @Test
    public void columnReorderWithNullDurationDoesNotThrow() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setColumnReorderEnabled(true);
            // animated stays true (default); a null duration means "no animation" and must
            // NOT be handed to the glide Timeline.
            board.setAnimationDuration(null);
            pump(host(board, 1200, 500));

            dragColumn(board, "A", "B");
            pump(board);

            assertEquals(List.of("B", "A", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "reorder commits with a null (disabled) animation duration and no exception");
        });
    }

    @Test
    public void columnReorderVetoedLeavesOrder() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            board.setColumnReorderEnabled(true);
            board.setOnColumnMoved(Event::consume);
            pump(host(board, 1200, 500));

            dragColumn(board, "A", "B");
            pump(board);

            assertEquals(List.of("A", "B", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "consumed reorder leaves the column order unchanged");
        });
    }

    @Test
    public void columnReorderDisabledIgnoresHeaderDrag() throws Exception {
        onFx(() -> {
            RXKanbanView<String> board = threeColumnBoard();
            board.setAnimated(false);
            // columnReorderEnabled defaults false.
            AtomicReference<ColumnMovedEvent<String>> fired = new AtomicReference<>();
            board.setOnColumnMoved(fired::set);
            pump(host(board, 1200, 500));

            dragColumn(board, "A", "B");
            pump(board);

            assertEquals(List.of("A", "B", "C"),
                    board.getColumns().stream().map(RXKanbanColumn::getTitle).toList(),
                    "no reorder when columnReorderEnabled=false");
            assertTrue(fired.get() == null, "no event when reorder disabled");
        });
    }

    // ==================== Helpers ====================

    private static RXKanbanView<String> threeColumnBoard() {
        RXKanbanView<String> board = new RXKanbanView<>();
        ObservableList<RXKanbanColumn<String>> columns = FXCollections.observableArrayList();
        for (String title : List.of("A", "B", "C")) {
            RXKanbanColumn<String> column = new RXKanbanColumn<>(title);
            column.getCards().addAll(title + "0", title + "1");
            columns.add(column);
        }
        board.setColumns(columns);
        return board;
    }

    private static Region boxOf(RXKanbanView<?> board, String title) {
        return (Region) headerOf(board, title).getParent();
    }

    // The columns are laid out inside the board's padding, so column x is 0-based at the
    // left content edge and the usable width is the board width minus its insets.
    private static double contentWidth(RXKanbanView<?> board) {
        return board.getWidth() - board.getInsets().getLeft() - board.getInsets().getRight();
    }

    private static boolean horizontalScrollBarVisible(RXKanbanView<?> board) {
        for (Node n : board.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar sb && sb.getOrientation() == Orientation.HORIZONTAL && sb.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static Node headerOf(RXKanbanView<?> board, String title) {
        for (Node h : board.lookupAll(".header")) {
            for (Node l : h.lookupAll(".label")) {
                if (l instanceof Label label && title.equals(label.getText())) {
                    return h;
                }
            }
        }
        return null;
    }

    private static void dragColumn(RXKanbanView<?> board, String from, String pastCenterOf) {
        Node header = headerOf(board, from);
        Node target = headerOf(board, pastCenterOf);
        Bounds hb = header.localToScene(header.getBoundsInLocal());
        Bounds tb = target.localToScene(target.getBoundsInLocal());
        double sx = (hb.getMinX() + hb.getMaxX()) / 2.0;
        double sy = (hb.getMinY() + hb.getMaxY()) / 2.0;
        double targetX = (tb.getMinX() + tb.getMaxX()) / 2.0 + 6.0;
        header.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, header));
        board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12.0, sy, board));
        board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, targetX, sy, board));
        board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, targetX, sy, board));
    }

    private static RXKanbanView<String> twoColumnBoard() {
        RXKanbanView<String> board = new RXKanbanView<>();
        RXKanbanColumn<String> a = new RXKanbanColumn<>("A");
        a.getCards().addAll("A0", "A1", "A2");
        RXKanbanColumn<String> b = new RXKanbanColumn<>("B");
        b.getCards().addAll("B0", "B1");
        board.setColumns(FXCollections.observableArrayList(a, b));
        return board;
    }

    private static double[] center(Node node) {
        Bounds b = node.localToScene(node.getBoundsInLocal());
        return new double[]{(b.getMinX() + b.getMaxX()) / 2.0, (b.getMinY() + b.getMaxY()) / 2.0};
    }

    private static void dragTo(RXKanbanView<?> board, Node source, double[] target) {
        Bounds sb = source.localToScene(source.getBoundsInLocal());
        double sx = (sb.getMinX() + sb.getMaxX()) / 2.0;
        double sy = (sb.getMinY() + sb.getMaxY()) / 2.0;
        source.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, sx, sy, source));
        board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, sx + 12, sy + 12, board));
        board.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, target[0], target[1], board));
        board.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, target[0], target[1], board));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, double sceneX, double sceneY, Node pick) {
        return new MouseEvent(type, sceneX, sceneY, sceneX, sceneY, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true,
                new PickResult(pick, sceneX, sceneY));
    }

    // A secondary-button press while the primary is still held down (both buttons down).
    private static void secondaryPress(Node target, double sceneX, double sceneY) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, sceneX, sceneY, sceneX, sceneY,
                MouseButton.SECONDARY, 1, false, false, false, false, true, false, true, false, false, true,
                new PickResult(target, sceneX, sceneY)));
    }

    // Release the secondary button while the primary is still held down.
    private static void secondaryRelease(Node target, double sceneX, double sceneY) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_RELEASED, sceneX, sceneY, sceneX, sceneY,
                MouseButton.SECONDARY, 1, false, false, false, false, true, false, false, false, false, true,
                new PickResult(target, sceneX, sceneY)));
    }

    private static void press(Node target) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true,
                new PickResult(target, 5, 5)));
    }

    private static void doubleClick(Node target) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, false, false, true,
                new PickResult(target, 5, 5)));
    }

    private static void key(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    private static boolean hasPseudo(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(pc -> pc.getPseudoClassName().equals(name));
    }


    @SafeVarargs
    private static RXKanbanView<String> board(Object... titleThenCount) {
        RXKanbanView<String> board = new RXKanbanView<>();
        ObservableList<RXKanbanColumn<String>> columns = FXCollections.observableArrayList();
        for (int i = 0; i < titleThenCount.length; i += 2) {
            String title = (String) titleThenCount[i];
            int count = (Integer) titleThenCount[i + 1];
            RXKanbanColumn<String> column = new RXKanbanColumn<>(title);
            for (int c = 0; c < count; c++) {
                column.getCards().add("card-" + c);
            }
            columns.add(column);
        }
        board.setColumns(columns);
        return board;
    }

    private static int realizedCards(RXKanbanView<?> board) {
        return board.lookupAll(".rx-kanban-card-cell").size();
    }

    private static Set<String> labelTexts(RXKanbanView<?> board) {
        Set<String> texts = new HashSet<>();
        for (Node node : board.lookupAll(".label")) {
            if (node instanceof Label label && label.getText() != null) {
                texts.add(label.getText());
            }
        }
        return texts;
    }

    private static RXKanbanCardCell<?> cellByText(RXKanbanView<?> board, String text) {
        for (Node node : board.lookupAll(".rx-kanban-card-cell")) {
            if (node instanceof RXKanbanCardCell<?> cell && !cell.isEmpty() && text.equals(cell.getItem())) {
                return cell;
            }
        }
        return null;
    }

    // The vertical distance between two adjacent realized cards = the row stride.
    private static double cardStride(RXKanbanView<?> board) {
        return cellByText(board, "card-1").getLayoutY() - cellByText(board, "card-0").getLayoutY();
    }

    private static boolean hasVisibleHorizontalScrollBar(RXKanbanView<?> board) {
        for (Node node : board.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar bar
                    && bar.getOrientation() == Orientation.HORIZONTAL && bar.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static StackPane host(RXKanbanView<?> board, double w, double h) {
        StackPane root = new StackPane(board);
        new Scene(root, w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static void onFx(FxAction action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
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
            throw new AssertionError("FX action timed out");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError assertion) {
            throw assertion;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}

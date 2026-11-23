package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.event.RXDashboardEvent;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the gestures of {@link RXDashboardPane} while the cards pack upward ({@code autoCompact}), driven by synthetic
 * mouse events on a grid of 100 by 100 pixel cells without gaps: a drag along a row exchanges places, a card dragged
 * down one row lets the cards below take its row, a drop into empty space lands at the highest row, coming back over
 * the starting cell restores the starting layout, Esc and the release, resizing with packing, {@code BLOCK} and
 * {@link RXDashboardPane#compact()} during a drag.
 */
public class RXDashboardPaneAutoCompactGestureTest {

    private static final PseudoClass DRAGGING = PseudoClass.getPseudoClass("dragging");

    @BeforeAll
    public static void startToolkit() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        fx(Label::new);
    }

    @Test
    public void draggingAlongARowExchangesPlacesWithEachCardPassed() throws Exception {
        fx(() -> {
            Board board = rowOfFour();
            List<RXDashboardEvent> events = board.events();
            Point2D start = board.press("1");
            board.dragBy(start, 200, 0);
            assertEquals(cells("1:2,0 2:0,0 3:1,0 4:3,0 T:0,1 U:2,1"), board.preview());
            assertEquals(new Placement(2, 0, 1, 1), board.placeholder());
            board.release(start.add(200, 0));
            assertEquals(cells("1:2,0 2:0,0 3:1,0 4:3,0 T:0,1 U:2,1"), board.cells());
            assertEquals(1, events.size());
        });
    }

    @Test
    public void draggingAWideCardDownOneRowLetsTheCardsBelowTakeItsRow() throws Exception {
        fx(() -> {
            Board board = screenshotBoard();
            Point2D start = board.press("7");
            board.dragBy(start, 0, 100);
            board.release(start.add(0, 100));
            assertEquals(cells("9:0,0 2:1,0 8:3,0 4:5,0 1:0,1 7:1,1 11:0,2 5:0,3 6:0,4"), board.cells());
        });
    }

    @Test
    public void aDropIntoEmptySpaceLandsAtTheHighestRowItCanReach() throws Exception {
        fx(() -> {
            Board board = screenshotBoard();
            Point2D start = board.press("8");
            board.dragBy(start, 100, 400);
            assertEquals(new Placement(4, 1, 1, 1), board.placeholder(), "the placeholder shows where it lands");
            board.release(start.add(100, 400));
            assertEquals("4,1", board.cells().get("8"));
        });
    }

    @Test
    public void comingBackOverTheStartingCellRestoresTheStartingLayout() throws Exception {
        fx(() -> {
            Board board = rowOfFour();
            List<RXDashboardEvent> events = board.events();
            Map<String, String> before = board.cells();
            Point2D start = board.press("1");
            board.dragBy(start, 200, 0);
            board.dragFrom(start.add(200, 0), -200, 0);
            assertEquals(before, board.preview());
            board.release(start);
            assertEquals(before, board.cells());
            assertEquals(0, events.size());
        });
    }

    @Test
    public void escapeRestoresAndTheReleaseCommitsWhatIsShown() throws Exception {
        fx(() -> {
            Board board = rowOfFour();
            Map<String, String> before = board.cells();
            Point2D start = board.press("1");
            board.dragBy(start, 200, 0);
            board.key(KeyCode.ESCAPE);
            assertEquals(before, board.cells());

            Board again = rowOfFour();
            start = again.press("1");
            again.dragBy(start, 100, 0);
            Map<String, String> shown = again.preview();
            // Released further right without a drag event in between: what was shown is committed, nothing more.
            again.release(start.add(200, 0));
            assertEquals(shown, again.cells());
            assertEquals(cells("1:1,0 2:0,0 3:2,0 4:3,0 T:0,1 U:2,1"), again.cells());
        });
    }

    @Test
    public void resizingBackLetsThePushedCardsComeBack() throws Exception {
        fx(() -> {
            Board board = new Board(4, card("A", 0, 0, 1, 1), card("B", 0, 1, 1, 1));
            Region bottom = handle(board.find("A"), "bottom");
            Point2D start = board.pressOn(bottom, 40, 4);
            board.dragBy(start, 0, 100);
            assertEquals(cells("A:0,0 B:0,2"), board.preview());
            board.dragFrom(start.add(0, 100), 0, -100);
            assertEquals(cells("A:0,0 B:0,1"), board.preview());
            board.release(start);
            assertEquals(cells("A:0,0 B:0,1"), board.cells());
        });
    }

    @Test
    public void blockNeverExchangesAndStillPacks() throws Exception {
        fx(() -> {
            Board board = rowOfFour();
            board.pane.setCollisionPolicy(CollisionPolicy.BLOCK);
            Map<String, String> before = board.cells();
            Point2D start = board.press("1");
            board.dragBy(start, 100, 0);
            assertEquals(before, board.preview(), "covering 2 is rejected");
            board.release(start.add(100, 0));
            assertEquals(before, board.cells());

            Board stack = new Board(4, card("A", 0, 0, 1, 1), card("B", 0, 1, 1, 1));
            stack.pane.setCollisionPolicy(CollisionPolicy.BLOCK);
            start = stack.press("A");
            stack.dragBy(start, 100, 0);
            stack.release(start.add(100, 0));
            assertEquals(cells("A:1,0 B:0,0"), stack.cells());
        });
    }

    @Test
    public void compactDuringADragKeepsTheDragGoing() throws Exception {
        fx(() -> {
            Board board = rowOfFour();
            Point2D start = board.press("1");
            board.dragBy(start, 100, 0);
            board.pane.compact();
            assertTrue(board.find("1").getPseudoClassStates().contains(DRAGGING));
            board.release(start.add(100, 0));
            assertEquals(cells("1:1,0 2:0,0 3:2,0 4:3,0 T:0,1 U:2,1"), board.cells());
        });
    }

    @Test
    public void comingBackRestoresTheStartEvenWhereRetracingTheStepsWouldNot() throws Exception {
        fx(() -> {
            Board board = new Board(4, card("1", 0, 0, 1, 1), card("2", 0, 1, 2, 1), card("3", 1, 2, 1, 2));
            Map<String, String> before = board.cells();
            Point2D start = board.press("2");
            board.dragBy(start, 100, 100);
            assertTrue(!before.equals(board.preview()), "the drag changed the layout");
            board.dragFrom(start.add(100, 100), -100, -100);
            assertEquals(before, board.preview());
            board.release(start);
            assertEquals(before, board.cells());
        });
    }

    @Test
    public void thePlaceholderMarksWhereAResizedCardRises() throws Exception {
        fx(() -> {
            Board board = new Board(4, card("A", 1, 0, 1, 1), card("C", 0, 1, 2, 1));
            Region right = handle(board.find("C"), "right");
            Point2D start = board.pressOn(right, 4, 40);
            board.dragBy(start, -100, 0);
            assertEquals(new Placement(0, 0, 1, 1), board.placeholder());
            board.release(start.add(-100, 0));
            assertEquals(cells("A:1,0 C:0,0"), board.cells());
        });
    }

    @Test
    public void underBlockThePlaceholderMarksThePackedPlace() throws Exception {
        fx(() -> {
            Board board = new Board(4, card("A", 0, 0, 1, 1), card("B", 0, 1, 1, 1));
            board.pane.setCollisionPolicy(CollisionPolicy.BLOCK);
            Point2D start = board.press("B");
            board.dragBy(start, 200, 200);
            assertEquals(new Placement(2, 0, 1, 1), board.placeholder());
        });
    }

    @Test
    public void draggingNextToALockedCardNeverLeavesCardsOverlapping() throws Exception {
        fx(() -> {
            RXDashboardCard locked = card("L", 2, 2, 3, 1);
            locked.setLocked(true);
            Board board = new Board(5, locked, card("S", 2, 0, 1, 1), card("T", 2, 3, 2, 3));
            Point2D start = board.press("T");
            board.dragBy(start, -150, -400);
            board.release(start.add(-150, -400));
            List<RXDashboardCard> cards = board.pane.getCards();
            for (int i = 0; i < cards.size(); i++) {
                for (int j = i + 1; j < cards.size(); j++) {
                    RXDashboardCard a = cards.get(i);
                    RXDashboardCard b = cards.get(j);
                    assertTrue(a.getColumn() + a.getColumnSpan() <= b.getColumn()
                            || b.getColumn() + b.getColumnSpan() <= a.getColumn()
                            || a.getRow() + a.getRowSpan() <= b.getRow() || b.getRow() + b.getRowSpan() <= a.getRow(),
                            a.getCardId() + " overlaps " + b.getCardId());
                }
            }
        });
    }

    @Test
    public void theTargetCellLeansBackByHalfTheGapFromTheGridsInnerEdge() throws Exception {
        fx(() -> {
            // Cells every 100 pixels with a 12 pixel gap and a 30 pixel inset: a card dragged right reaches the next
            // column once past the middle by half the gap, 56 pixels, counted from the first column's edge.
            Board early = new Board(4, 12, 30, card("A", 0, 0, 1, 1), card("B", 3, 0, 1, 1));
            Point2D start = early.press("A");
            early.dragBy(start, 53, 0);
            assertEquals(new Placement(0, 0, 1, 1), early.placeholder());

            Board late = new Board(4, 12, 30, card("A", 0, 0, 1, 1), card("B", 3, 0, 1, 1));
            start = late.press("A");
            late.dragBy(start, 58, 0);
            assertEquals(new Placement(1, 0, 1, 1), late.placeholder());
        });
    }

    @Test
    public void afterComingBackTheDragGoesOnAsIfItHadJustStarted() throws Exception {
        fx(() -> {
            Board wandered = new Board(4, card("1", 0, 0, 1, 1), card("2", 0, 1, 2, 1), card("3", 1, 2, 1, 2));
            Point2D start = wandered.press("2");
            wandered.dragBy(start, 100, 100);
            wandered.dragFrom(start.add(100, 100), -100, -100);
            wandered.dragFrom(start, 100, 0);

            Board fresh = new Board(4, card("1", 0, 0, 1, 1), card("2", 0, 1, 2, 1), card("3", 1, 2, 1, 2));
            Point2D again = fresh.press("2");
            fresh.dragBy(again, 100, 0);
            assertEquals(fresh.preview(), wandered.preview());
        });
    }

    // ==================== Boards ====================

    private static Board rowOfFour() {
        return new Board(4, card("1", 0, 0, 1, 1), card("2", 1, 0, 1, 1), card("3", 2, 0, 1, 1),
                card("4", 3, 0, 1, 1), card("T", 0, 1, 2, 1), card("U", 2, 1, 2, 1));
    }

    private static Board screenshotBoard() {
        return new Board(6, card("9", 0, 0, 1, 1), card("7", 1, 0, 5, 1), card("1", 0, 1, 1, 1),
                card("2", 1, 1, 1, 1), card("8", 3, 1, 1, 1), card("4", 5, 1, 1, 1), card("11", 0, 2, 1, 1),
                card("5", 0, 3, 1, 1), card("6", 0, 4, 1, 1));
    }

    // ==================== Helpers ====================

    private static RXDashboardCard card(String id, int column, int row, int columnSpan, int rowSpan) {
        return new RXDashboardCard(id, null, column, row, columnSpan, rowSpan);
    }

    private static Region handle(RXDashboardCard card, String kind) {
        for (Node child : card.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("resize-handle") && child.getStyleClass().contains(kind)) {
                return (Region) child;
            }
        }
        throw new AssertionError("no " + kind + " handle");
    }

    // "A:0,0 B:1,0" as a map.
    private static Map<String, String> cells(String text) {
        Map<String, String> cells = new TreeMap<>();
        for (String entry : text.split(" ")) {
            int colon = entry.indexOf(':');
            cells.put(entry.substring(0, colon), entry.substring(colon + 1));
        }
        return cells;
    }

    // A pane whose cells repeat every 100 pixels both ways, the gap included, with an inset all round, in a shown-less
    // stage, laid out.
    private static final class Board {
        final RXDashboardPane pane = new RXDashboardPane();
        private final int columns;
        private final double gap;
        private final double inset;

        Board(int columns, RXDashboardCard... cards) {
            this(columns, 0.0, 0.0, cards);
        }

        Board(int columns, double gap, double inset, RXDashboardCard... cards) {
            this.columns = columns;
            this.gap = gap;
            this.inset = inset;
            pane.setColumnCount(columns);
            pane.setRowHeight(100 - gap);
            pane.setHgap(gap);
            pane.setVgap(gap);
            pane.setPadding(new Insets(inset));
            pane.setAnimationDuration(Duration.millis(200));
            pane.getCards().addAll(cards);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(pane), width(), 900));
            pane.getScene().getRoot().applyCss();
            layout();
            layout();
        }

        private double width() {
            return columns * 100.0 - gap + 2 * inset;
        }

        void layout() {
            pane.resize(width(), Math.max(300, pane.prefHeight(-1)));
            pane.layout();
        }

        RXDashboardCard find(String id) {
            for (RXDashboardCard card : pane.getCards()) {
                if (id.equals(card.getCardId())) {
                    return card;
                }
            }
            throw new AssertionError("no card " + id);
        }

        List<RXDashboardEvent> events() {
            List<RXDashboardEvent> events = new ArrayList<>();
            pane.addEventHandler(RXDashboardEvent.LAYOUT_CHANGED, events::add);
            return events;
        }

        Map<String, String> cells() {
            Map<String, String> cells = new TreeMap<>();
            for (RXDashboardCard card : pane.getCards()) {
                cells.put(card.getCardId(), card.getColumn() + "," + card.getRow());
            }
            return cells;
        }

        // Where the cards show in the preview: the placeholder's area for the dragged card is read separately.
        Map<String, String> preview() {
            layout();
            Map<String, String> cells = new TreeMap<>();
            for (RXDashboardCard card : pane.getCards()) {
                Placement shown = card.getPseudoClassStates().contains(DRAGGING) ? placeholder()
                        : new Placement((int) Math.round((card.getLayoutX() - inset) / 100),
                        (int) Math.round((card.getLayoutY() - inset) / 100), card.getColumnSpan(), card.getRowSpan());
                cells.put(card.getCardId(), shown.column() + "," + shown.row());
            }
            return cells;
        }

        Placement placeholder() {
            layout();
            Region placeholder = (Region) pane.getChildrenUnmodifiable().get(0);
            return new Placement((int) Math.round((placeholder.getLayoutX() - inset) / 100),
                    (int) Math.round((placeholder.getLayoutY() - inset) / 100),
                    (int) Math.round((placeholder.getWidth() + gap) / 100),
                    (int) Math.round((placeholder.getHeight() + gap) / 100));
        }

        Point2D press(String id) {
            return pressOn(find(id), 5, 5);
        }

        Point2D pressOn(Node target, double x, double y) {
            Point2D scene = target.localToScene(x, y);
            fire(target, MouseEvent.MOUSE_PRESSED, scene);
            return scene;
        }

        // Drags from a scene point by an offset in steps of a few pixels, laying out after each, as pulses would.
        void dragBy(Point2D from, double dx, double dy) {
            dragFrom(from, dx, dy);
        }

        void dragFrom(Point2D from, double dx, double dy) {
            int steps = (int) Math.max(1, Math.ceil(Math.hypot(dx, dy) / 5));
            for (int i = 1; i <= steps; i++) {
                Point2D at = from.add(dx * i / steps, dy * i / steps);
                fire(pane, MouseEvent.MOUSE_DRAGGED, at);
                layout();
            }
        }

        void release(Point2D at) {
            fire(pane, MouseEvent.MOUSE_RELEASED, at);
            layout();
        }

        void key(KeyCode code) {
            pane.getScene().getRoot().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false,
                    false));
            layout();
        }

        private void fire(Node target, EventType<MouseEvent> type, Point2D scene) {
            boolean down = type != MouseEvent.MOUSE_RELEASED;
            target.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                    MouseButton.PRIMARY, 1, false, false, false, false, down, false, false, false, false, true,
                    new PickResult(target, scene.getX(), scene.getY())));
        }
    }

    private static void fx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}

package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.event.RXDashboardEvent;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for dragging cards on an {@link RXDashboardPane}, driven by synthetic mouse and key events on an unshown
 * stage: the threshold, the card following the pointer, the preview and placeholder, committing with exactly one
 * event, the event's contents and what its handlers may do, cancelling (Esc, changes to the pane, leaving the scene)
 * with no event, and the switches that stop a drag from starting.
 */
public class RXDashboardPaneGestureTest {

    private static final double EPSILON = 1e-6;
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
        // Creating a control installs Modena, which the default card style looks colors up in.
        fx(Label::new);
    }

    // ==================== Dragging ====================

    @Test
    public void dragPushesNeighboursAndCommitsOnRelease() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            List<RXDashboardLayout> layouts = new ArrayList<>();
            board.pane.layoutProperty().addListener((obs, old, value) -> layouts.add(value));
            RXDashboardLayout before = board.pane.getLayout();

            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 3, start.getY());
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING), "below the threshold");
            board.drag(board.a, start.getX() + 100, start.getY());
            board.layout();
            assertTrue(board.a.getPseudoClassStates().contains(DRAGGING));
            assertEquals(100.0, board.a.getTranslateX(), EPSILON, "follows the pointer");
            assertEquals(0.0, board.a.getLayoutX(), EPSILON, "layout position stays at the start cells");
            assertTrue(board.placeholder().isVisible());
            assertEquals(100.0, board.placeholder().getLayoutX(), EPSILON);
            assertEquals(100.0, board.b.getLayoutY(), EPSILON, "B pushed in the preview");
            assertTrue(layouts.isEmpty(), "the preview stays private");
            assertEquals(before, board.pane.getLayout());

            board.release(board.a, start.getX() + 100, start.getY());
            board.layout();
            assertPlacement(board.a, 1, 0, 2, 1);
            assertPlacement(board.b, 2, 1, 1, 1);
            assertPlacement(board.c, 2, 2, 1, 1);
            assertEquals(1, events.size());
            RXDashboardEvent event = events.get(0);
            assertSame(board.a, event.getCard());
            assertSame(board.pane, event.getDashboard());
            assertEquals(before, event.getPreviousLayout());
            assertEquals(board.pane.getLayout(), event.getLayout());
            assertEquals(1, layouts.size());
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            assertFalse(board.placeholder().isVisible());
            assertEquals(0.0, board.a.getViewOrder(), EPSILON);
            assertEquals(100.0, board.a.getLayoutX(), EPSILON);
            assertEquals(100.0, board.a.getLayoutX() + board.a.getTranslateX(), 0.5,
                    "glides from where it was dropped");
        });
    }

    @Test
    public void draggingAwayAndBackCommitsNothing() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.layout();
            board.drag(board.a, start.getX() + 2, start.getY() + 1);
            board.layout();
            assertEquals(0.0, board.b.getLayoutY(), EPSILON, "B back in the preview");
            board.release(board.a, start.getX() + 2, start.getY() + 1);
            assertTrue(events.isEmpty());
            assertPlacement(board.a, 0, 0, 2, 1);
        });
    }

    @Test
    public void escapeCancelsWithoutAnEvent() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            RXDashboardLayout before = board.pane.getLayout();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.layout();
            board.pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false,
                    false));
            board.layout();
            assertEquals(before, board.pane.getLayout());
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            assertFalse(board.placeholder().isVisible());
            assertEquals(0.0, board.a.getLayoutX(), EPSILON);
            assertEquals(100.0, board.a.getLayoutX() + board.a.getTranslateX(), 0.5,
                    "glides back from where it showed");
            board.release(board.a, start.getX() + 100, start.getY());
            assertTrue(events.isEmpty());
            assertEquals(before, board.pane.getLayout());
        });
    }

    @Test
    public void blockAndLockKeepThePreviewOnTheLastAcceptedCells() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.pane.setCollisionPolicy(CollisionPolicy.BLOCK);
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX(), start.getY() + 200);
            board.drag(board.a, start.getX() + 100, start.getY() + 200);
            board.layout();
            assertEquals(new Placement(1, 2, 2, 1), board.candidate());
            board.drag(board.a, start.getX() + 100, start.getY() + 100);
            board.layout();
            assertEquals(new Placement(1, 2, 2, 1), board.candidate(), "C is in the way");
            board.release(board.a, start.getX() + 100, start.getY() + 100);
            assertPlacement(board.a, 1, 2, 2, 1);
            assertPlacement(board.c, 2, 1, 1, 1);

            Board locked = new Board();
            locked.b.setLocked(true);
            Point2D from = locked.press(locked.a, 5, 5);
            locked.drag(locked.a, from.getX() + 100, from.getY());
            locked.release(locked.a, from.getX() + 100, from.getY());
            assertPlacement(locked.a, 0, 0, 2, 1);
        });
    }

    @Test
    public void lockedDisabledOrReadOnlyCardsDoNotDrag() throws Exception {
        fx(() -> {
            Board locked = new Board();
            locked.a.setLocked(true);
            assertNoDrag(locked);
            Board fixed = new Board();
            fixed.a.setMoveEnabled(false);
            assertNoDrag(fixed);
            Board readOnly = new Board();
            readOnly.pane.setEditable(false);
            assertNoDrag(readOnly);
            Board secondary = new Board();
            Point2D start = secondary.local(secondary.a, 5, 5);
            secondary.fire(secondary.a, MouseEvent.MOUSE_PRESSED, start, MouseButton.SECONDARY);
            secondary.fire(secondary.a, MouseEvent.MOUSE_DRAGGED, start.add(100, 0), MouseButton.SECONDARY);
            assertFalse(secondary.a.getPseudoClassStates().contains(DRAGGING));
        });
    }

    @Test
    public void contentThatConsumesThePressAndDragHandlesDecideWhereADragStarts() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region consuming = new Region();
            consuming.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
            board.a.setContent(consuming);
            board.layout();
            Point2D start = board.press(consuming, 10, 10);
            board.drag(consuming, start.getX() + 100, start.getY());
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            board.release(consuming, start.getX() + 100, start.getY());

            Board handled = new Board();
            Region title = new Region();
            Region body = new Region();
            title.setPrefHeight(20);
            StackPane content = new StackPane(body, title);
            handled.a.setContent(content);
            handled.a.setDragHandle(title);
            handled.layout();
            Point2D onBody = handled.press(body, 10, 10);
            handled.drag(body, onBody.getX() + 100, onBody.getY());
            assertFalse(handled.a.getPseudoClassStates().contains(DRAGGING));
            handled.release(body, onBody.getX() + 100, onBody.getY());
            Point2D onTitle = handled.press(title, 5, 5);
            handled.drag(title, onTitle.getX() + 100, onTitle.getY());
            assertTrue(handled.a.getPseudoClassStates().contains(DRAGGING));
        });
    }

    @Test
    public void thePaneGrowsByARowWhileDragging() throws Exception {
        fx(() -> {
            Board board = new Board();
            double height = board.pane.prefHeight(-1);
            Point2D start = board.press(board.c, 5, 5);
            board.drag(board.c, start.getX(), start.getY() + 30);
            board.layout();
            assertEquals(new Placement(2, 1, 1, 1), board.candidate());
            assertEquals(height + 100.0, board.pane.prefHeight(-1), EPSILON);
            board.drag(board.c, start.getX(), start.getY() + 100);
            board.layout();
            assertEquals(new Placement(2, 2, 1, 1), board.candidate());
            board.release(board.c, start.getX(), start.getY() + 100);
            assertEquals(height + 100.0, board.pane.prefHeight(-1), EPSILON, "C now ends a row lower");
        });
    }

    @Test
    public void thePaneSettlesAfterTheDropGlide() throws Exception {
        AtomicReference<Board> holder = new AtomicReference<>();
        fx(() -> {
            Board board = new Board();
            holder.set(board);
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 130, start.getY());
            board.layout();
            board.release(board.a, start.getX() + 130, start.getY());
            board.layout();
            assertPlacement(board.a, 1, 0, 2, 1);
            assertEquals(30.0, board.a.getTranslateX(), 0.5, "glides from where it was dropped");
        });
        Thread.sleep(600);
        fx(() -> {
            Board board = holder.get();
            for (RXDashboardCard card : List.of(board.a, board.b, board.c)) {
                assertEquals(0.0, card.getTranslateX(), EPSILON);
                assertEquals(0.0, card.getTranslateY(), EPSILON);
            }
            board.pane.layout();
            board.pane.layout();
            assertFalse(board.pane.isNeedsLayout());
            board.pane.layout();
            assertFalse(board.pane.isNeedsLayout());
        });
    }

    // ==================== Resizing ====================

    @Test
    public void handlesStraddleTheEdgesAndShowOnlyWhereResizingIsAllowed() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region right = handle(board.a, "right");
            Region bottom = handle(board.a, "bottom");
            Region corner = handle(board.a, "corner");
            // No gap: the handles keep their thickness inside the card, off the neighbours.
            assertTrue(right.isVisible());
            assertEquals(192.0, right.getLayoutX(), EPSILON);
            assertEquals(8.0, right.getWidth(), EPSILON);
            assertEquals(92.0, bottom.getLayoutY(), EPSILON);
            assertEquals(8.0, bottom.getHeight(), EPSILON);
            assertEquals(184.0, corner.getLayoutX(), EPSILON);
            assertEquals(16.0, corner.getWidth(), EPSILON);
            assertEquals(84.0, right.getHeight(), EPSILON);
            // A gap of 10: they straddle the edges, half in the gap.
            board.pane.setHgap(10);
            board.pane.setVgap(10);
            board.layout();
            assertEquals(board.a.getWidth() - 4.0, right.getLayoutX(), EPSILON);
            assertEquals(board.a.getHeight() - 4.0, bottom.getLayoutY(), EPSILON);
            board.pane.setHgap(2);
            board.layout();
            assertEquals(board.a.getWidth() - 6.0, right.getLayoutX(), EPSILON, "2 px out, 6 px in");
            board.pane.setHgap(0);
            board.pane.setVgap(0);
            board.layout();
            board.a.setResizeEnabled(false);
            board.layout();
            assertFalse(right.isVisible());
            board.a.setResizeEnabled(true);
            board.a.setLocked(true);
            board.layout();
            assertFalse(corner.isVisible());
            board.a.setLocked(false);
            board.pane.setEditable(false);
            board.layout();
            assertFalse(bottom.isVisible());
            assertFalse(handle(new RXDashboardCard(), "right").isVisible(), "no handles outside a pane");
        });
    }

    @Test
    public void widenFromTheRightEdgeLive() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            Region right = handle(board.a, "right");
            Point2D start = board.press(right, 4, 40);
            board.drag(right, start.getX() + 70, start.getY());
            board.layout();
            assertTrue(board.a.getPseudoClassStates().contains(PseudoClass.getPseudoClass("resizing")));
            assertEquals(270.0, board.a.getWidth(), EPSILON, "follows the pointer");
            assertEquals(100.0, board.a.getHeight(), EPSILON);
            assertEquals(new Placement(0, 0, 3, 1), board.candidate());
            assertEquals(100.0, board.b.getLayoutY(), EPSILON, "B pushed in the preview");
            board.release(right, start.getX() + 70, start.getY());
            board.layout();
            assertPlacement(board.a, 0, 0, 3, 1);
            assertPlacement(board.b, 2, 1, 1, 1);
            assertPlacement(board.c, 2, 2, 1, 1);
            assertEquals(300.0, board.a.getWidth(), EPSILON, "snaps to its cells");
            assertEquals(1, events.size());
            assertFalse(board.a.getPseudoClassStates().contains(PseudoClass.getPseudoClass("resizing")));
        });
    }

    @Test
    public void growTheTrendChartFromTheCorner() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region corner = handle(board.a, "corner");
            Point2D start = board.press(corner, 12, 12);
            board.drag(corner, start.getX() + 100, start.getY() + 100);
            board.release(corner, start.getX() + 100, start.getY() + 100);
            assertPlacement(board.a, 0, 0, 3, 2);
            assertPlacement(board.b, 2, 2, 1, 1);
            assertPlacement(board.c, 2, 3, 1, 1);

            Region bottom = handle(board.c, "bottom");
            board.layout();
            Point2D from = board.press(bottom, 40, 4);
            board.drag(bottom, from.getX(), from.getY() + 160);
            board.release(bottom, from.getX(), from.getY() + 160);
            assertPlacement(board.c, 2, 3, 1, 3);
        });
    }

    @Test
    public void resizingStaysWithinSpanLimitsAndTheGrid() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.a.setMaxColumnSpan(2);
            board.a.setMinRowSpan(1);
            Region right = handle(board.a, "right");
            Point2D start = board.press(right, 4, 40);
            board.drag(right, start.getX() + 150, start.getY());
            board.layout();
            assertEquals(200.0, board.a.getWidth(), EPSILON);
            assertEquals(new Placement(0, 0, 2, 1), board.candidate());
            board.drag(right, start.getX() - 150, start.getY());
            board.layout();
            assertEquals(100.0, board.a.getWidth(), EPSILON, "never below one column");
            board.release(right, start.getX() - 150, start.getY());
            assertPlacement(board.a, 0, 0, 1, 1);

            Board wide = new Board();
            Region edge = handle(wide.c, "right");
            Point2D from = wide.press(edge, 4, 40);
            wide.drag(edge, from.getX() + 400, from.getY());
            wide.layout();
            assertEquals(200.0, wide.c.getWidth(), EPSILON, "stops at the grid's right edge");
            wide.release(edge, from.getX() + 400, from.getY());
            assertPlacement(wide.c, 2, 1, 2, 1);
        });
    }

    @Test
    public void resizingOntoALockedCardIsRejected() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.b.setLocked(true);
            List<RXDashboardEvent> events = board.events();
            Region right = handle(board.a, "right");
            Point2D start = board.press(right, 4, 40);
            board.drag(right, start.getX() + 100, start.getY());
            board.layout();
            assertEquals(new Placement(0, 0, 2, 1), board.candidate());
            board.release(right, start.getX() + 100, start.getY());
            assertPlacement(board.a, 0, 0, 2, 1);
            assertTrue(events.isEmpty());
        });
    }

    @Test
    public void withoutLiveResizeOnlyThePlaceholderFollows() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.pane.setLiveResize(false);
            Region right = handle(board.a, "right");
            Point2D start = board.press(right, 4, 40);
            board.drag(right, start.getX() + 100, start.getY());
            board.layout();
            assertEquals(200.0, board.a.getWidth(), EPSILON);
            assertEquals(300.0, board.placeholder().getWidth(), EPSILON);
            board.pane.setLiveResize(true);
            board.layout();
            assertEquals(300.0, board.a.getWidth(), EPSILON);
            board.release(right, start.getX() + 100, start.getY());
            assertPlacement(board.a, 0, 0, 3, 1);
        });
    }

    @Test
    public void escapeCancelsAResize() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            Region corner = handle(board.a, "corner");
            Point2D start = board.press(corner, 12, 12);
            board.drag(corner, start.getX() + 100, start.getY() + 100);
            board.layout();
            board.pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false,
                    false));
            board.layout();
            assertEquals(200.0, board.a.getWidth(), EPSILON);
            assertEquals(100.0, board.a.getHeight(), EPSILON);
            board.release(corner, start.getX() + 100, start.getY() + 100);
            assertPlacement(board.a, 0, 0, 2, 1);
            assertPlacement(board.b, 2, 0, 1, 1);
            assertTrue(events.isEmpty());
        });
    }

    // ==================== Auto-scroll ====================

    @Test
    public void restingNearTheViewportEdgeScrollsUntilRelease() throws Exception {
        RXDashboardPane pane = new RXDashboardPane();
        RXDashboardCard top = new RXDashboardCard("top", null, 0, 0, 1, 1);
        ScrollPane scroller = new ScrollPane(pane);
        AtomicReference<Board> helperRef = new AtomicReference<>();
        AtomicReference<Point2D> pressed = new AtomicReference<>();
        fx(() -> {
            Board helper = new Board();
            helperRef.set(helper);
            pane.setColumnCount(4);
            pane.setRowHeight(100);
            pane.setHgap(0);
            pane.setVgap(0);
            pane.getCards().add(top);
            for (int row = 1; row < 8; row++) {
                pane.getCards().add(new RXDashboardCard("r" + row, null, 0, row, 1, 1));
            }
            scroller.setFitToWidth(true);
            Stage stage = new Stage();
            stage.setScene(new Scene(scroller, 400, 300));
            scroller.applyCss();
            scroller.layout();
            scroller.layout();
            Point2D start = helper.press(top, 50, 50);
            pressed.set(start);
            double bottom = scroller.localToScene(0, scroller.snappedTopInset()).getY()
                    + scroller.getViewportBounds().getHeight();
            helper.drag(top, start.getX() + 100, bottom - 4);
            assertEquals(0.0, scroller.getVvalue(), EPSILON, "scrolls on the next tick");
        });
        Thread.sleep(300);
        AtomicReference<Double> value = new AtomicReference<>();
        fx(() -> {
            assertTrue(scroller.getVvalue() > 0.0, "scrolled while the pointer rested");
            assertTrue(top.getPseudoClassStates().contains(DRAGGING));
            Point2D start = pressed.get();
            helperRef.get().release(top, start.getX() + 100, start.getY());
            value.set(scroller.getVvalue());
        });
        Thread.sleep(150);
        fx(() -> assertEquals(value.get(), scroller.getVvalue(), EPSILON, "stops with the gesture"));
    }

    // ==================== Robustness ====================

    @Test
    public void contentThatConsumesTheReleaseCannotStrandAGesture() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region content = new Region();
            int[] clicks = {0};
            content.addEventHandler(MouseEvent.MOUSE_RELEASED, MouseEvent::consume);
            content.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> clicks[0]++);
            board.a.setContent(content);
            board.layout();
            List<RXDashboardEvent> events = board.events();
            Point2D start = board.press(content, 10, 10);
            board.drag(content, start.getX() + 100, start.getY());
            board.release(content, start.getX() + 100, start.getY());
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            assertEquals(1, events.size());
            assertPlacement(board.a, 1, 0, 2, 1);
            board.fire(content, MouseEvent.MOUSE_CLICKED, new Point2D(start.getX() + 100, start.getY()),
                    MouseButton.PRIMARY);
            assertEquals(0, clicks[0], "the click that ends a drag is not the content's");
            Point2D again = board.press(content, 10, 10);
            board.release(content, again.getX(), again.getY());
            board.fire(content, MouseEvent.MOUSE_CLICKED, again, MouseButton.PRIMARY);
            assertEquals(1, clicks[0], "a plain click still is");
        });
    }

    @Test
    public void aCancelledDragDoesNotClickTheContentEither() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region content = new Region();
            int[] clicks = {0};
            content.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> clicks[0]++);
            board.a.setContent(content);
            board.layout();
            Point2D start = board.press(content, 10, 10);
            board.drag(content, start.getX() + 20, start.getY());
            board.pane.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false,
                    false));
            board.release(content, start.getX() + 20, start.getY());
            board.fire(content, MouseEvent.MOUSE_CLICKED, start, MouseButton.PRIMARY);
            assertEquals(0, clicks[0]);
        });
    }

    @Test
    public void anotherButtonDuringADragDoesNotLetTheClickThrough() throws Exception {
        fx(() -> {
            Board board = new Board();
            Region content = new Region();
            List<MouseButton> clicks = new ArrayList<>();
            content.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> clicks.add(event.getButton()));
            board.a.setContent(content);
            board.layout();
            Point2D start = board.press(content, 10, 10);
            board.drag(content, start.getX() + 100, start.getY());
            board.fire(content, MouseEvent.MOUSE_PRESSED, start.add(100, 0), MouseButton.SECONDARY);
            board.fire(content, MouseEvent.MOUSE_RELEASED, start.add(100, 0), MouseButton.SECONDARY);
            board.fire(content, MouseEvent.MOUSE_CLICKED, start.add(100, 0), MouseButton.SECONDARY);
            board.release(content, start.getX() + 100, start.getY());
            board.fire(content, MouseEvent.MOUSE_CLICKED, start.add(100, 0), MouseButton.PRIMARY);
            assertPlacement(board.a, 1, 0, 2, 1);
            assertEquals(List.of(MouseButton.SECONDARY), clicks);
        });
    }

    @Test
    public void aGestureEndedFromItsOwnLayoutPassThrowsNothing() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.a.layoutYProperty().addListener((obs, old, value) -> board.a.setLocked(true));
            Point2D start = board.press(board.c, 5, 5);
            board.drag(board.c, start.getX() - 200, start.getY() - 100);
            board.layout();
            board.layout();
            assertFalse(board.c.getPseudoClassStates().contains(DRAGGING));

            Board resizing = new Board();
            Region right = handle(resizing.a, "right");
            resizing.a.widthProperty().addListener((obs, old, value) -> resizing.a.setLocked(true));
            Point2D from = resizing.press(right, 4, 40);
            resizing.drag(right, from.getX() + 60, from.getY());
            resizing.layout();
            resizing.layout();
            assertFalse(resizing.a.getPseudoClassStates().contains(PseudoClass.getPseudoClass("resizing")));
        });
    }

    @Test
    public void aGestureEndedWhileItsCardIsPlacedThrowsNothing() throws Exception {
        fx(() -> {
            // The pane narrows during each gesture, so its card is placed again; a listener on the card ends it.
            Board moved = new Board();
            moved.c.layoutXProperty().addListener((obs, old, value) -> moved.c.setLocked(true));
            Point2D start = moved.press(moved.c, 5, 5);
            moved.drag(moved.c, start.getX() - 150, start.getY() + 10);
            moved.pane.resize(300, moved.pane.getHeight());
            moved.pane.layout();
            assertFalse(moved.c.getPseudoClassStates().contains(DRAGGING));

            Board translated = new Board();
            start = translated.press(translated.c, 5, 5);
            translated.drag(translated.c, start.getX() - 150, start.getY() + 10);
            translated.c.translateXProperty().addListener((obs, old, value) -> translated.c.setLocked(true));
            translated.pane.resize(300, translated.pane.getHeight());
            translated.pane.layout();
            assertFalse(translated.c.getPseudoClassStates().contains(DRAGGING));

            Board resized = new Board();
            Region right = handle(resized.b, "right");
            resized.b.layoutXProperty().addListener((obs, old, value) -> resized.b.setLocked(true));
            start = resized.press(right, 4, 40);
            resized.drag(right, start.getX() + 60, start.getY());
            resized.pane.resize(300, resized.pane.getHeight());
            resized.pane.layout();
            assertFalse(resized.b.getPseudoClassStates().contains(PseudoClass.getPseudoClass("resizing")));
        });
    }

    @Test
    public void aGestureEndedByItsOwnCardsChangesThrowsNothing() throws Exception {
        fx(() -> {
            // Ended while following the pointer.
            Board followed = new Board();
            followed.c.translateXProperty().addListener((obs, old, value) -> followed.c.setLocked(true));
            Point2D start = followed.press(followed.c, 5, 5);
            followed.drag(followed.c, start.getX() - 150, start.getY() + 10);
            followed.drag(followed.c, start.getX() - 160, start.getY() + 10);
            assertFalse(followed.c.getPseudoClassStates().contains(DRAGGING));
            assertEquals(0.0, followed.c.getTranslateX());

            // Ended while the gesture starts: the card's stacking is given back.
            Board raised = new Board();
            double viewOrder = raised.c.getViewOrder();
            raised.c.viewOrderProperty().addListener((obs, old, value) -> raised.c.setLocked(true));
            start = raised.press(raised.c, 5, 5);
            raised.drag(raised.c, start.getX() - 150, start.getY() + 10);
            assertFalse(raised.c.getPseudoClassStates().contains(DRAGGING));
            assertEquals(viewOrder, raised.c.getViewOrder());

            // Ended by the release's last move.
            Board released = new Board();
            start = released.press(released.c, 5, 5);
            released.drag(released.c, start.getX() - 150, start.getY() + 10);
            released.c.translateXProperty().addListener((obs, old, value) -> released.pane.setEditable(false));
            released.release(released.c, start.getX() - 170, start.getY() + 10);
            assertFalse(released.c.getPseudoClassStates().contains(DRAGGING));
            assertPlacement(released.c, 2, 1, 1, 1);
        });
    }

    @Test
    public void anIdTheSnapshotDoesNotUseCanChangeDuringADrag() throws Exception {
        fx(() -> {
            Board board = new Board();
            RXDashboardCard anonymous = new RXDashboardCard(null, null, 3, 0, 1, 1);
            board.pane.getCards().add(anonymous);
            board.layout();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            anonymous.setCardId("");
            assertTrue(board.a.getPseudoClassStates().contains(DRAGGING));
            board.release(board.a, start.getX() + 100, start.getY());
            assertPlacement(board.a, 1, 0, 2, 1);
        });
    }

    @Test
    public void aPressDuringAGestureCancelsIt() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.press(board.c, 5, 5);
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            board.release(board.c, 0, 0);
            assertTrue(events.isEmpty());
            assertPlacement(board.a, 0, 0, 2, 1);
        });
    }

    @Test
    public void aCardFollowsThePointerWhenThePaneMovesUnderIt() throws Exception {
        fx(() -> {
            Board board = new Board();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 60, start.getY());
            board.layout();
            double shownBefore = board.a.localToScene(0, 0).getX();
            board.pane.setTranslateX(-100);
            assertEquals(shownBefore, board.a.localToScene(0, 0).getX(), EPSILON, "stays under the pointer");
            board.layout();
            assertEquals(new Placement(2, 0, 2, 1), board.candidate(), "the pointer is over other cells now");
            board.release(board.a, start.getX() + 60, start.getY());
            assertPlacement(board.a, 2, 0, 2, 1);
        });
    }

    @Test
    public void handlesFollowTheDashboardACardMovesTo() throws Exception {
        fx(() -> {
            Board editable = new Board();
            Board readOnly = new Board();
            readOnly.pane.setEditable(false);
            RXDashboardCard moved = new RXDashboardCard("M", null, 3, 0, 1, 1);
            editable.pane.getCards().add(moved);
            editable.layout();
            assertTrue(handle(moved, "right").isVisible());
            readOnly.pane.getCards().add(moved);
            readOnly.layout();
            assertFalse(handle(moved, "right").isVisible());
            editable.pane.getCards().add(moved);
            editable.layout();
            assertTrue(handle(moved, "right").isVisible());
        });
    }

    // ==================== Event handlers ====================

    @Test
    public void eventHandlersMayChangeTheLayoutAgain() throws Exception {
        fx(() -> {
            Board board = new Board();
            RXDashboardLayout before = board.pane.getLayout();
            List<RXDashboardLayout> layouts = new ArrayList<>();
            board.pane.layoutProperty().addListener((obs, old, value) -> layouts.add(value));
            board.pane.setOnLayoutChanged(event -> board.pane.applyLayout(event.getPreviousLayout()));
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.release(board.a, start.getX() + 100, start.getY());
            assertEquals(before, board.pane.getLayout(), "rejected by the handler");
            assertEquals(2, layouts.size());
            assertEquals(before, layouts.get(1));

            Board compacting = new Board();
            compacting.pane.addEventHandler(RXDashboardEvent.LAYOUT_CHANGED, event -> compacting.pane.compact());
            Point2D from = compacting.press(compacting.a, 5, 5);
            compacting.drag(compacting.a, from.getX(), from.getY() + 300);
            compacting.release(compacting.a, from.getX(), from.getY() + 300);
            assertPlacement(compacting.a, 0, 0, 2, 1);
        });
    }

    @Test
    public void aCardWithoutAnIdStillFiresTheEvent() throws Exception {
        fx(() -> {
            Board board = new Board();
            RXDashboardCard anonymous = new RXDashboardCard(null, null, 3, 0, 1, 1);
            board.pane.getCards().add(anonymous);
            board.layout();
            List<RXDashboardEvent> events = board.events();
            RXDashboardLayout before = board.pane.getLayout();
            Point2D start = board.press(anonymous, 5, 5);
            board.drag(anonymous, start.getX(), start.getY() + 300);
            board.release(anonymous, start.getX(), start.getY() + 300);
            assertEquals(1, events.size());
            assertEquals(before, board.pane.getLayout());
            assertPlacement(anonymous, 3, 3, 1, 1);
        });
    }

    // ==================== Cancelling ====================

    @Test
    public void changesToThePaneCancelTheDrag() throws Exception {
        fx(() -> {
            assertCancelledBy(board -> board.pane.getCards().remove(board.c));
            assertCancelledBy(board -> board.pane.setColumnCount(6));
            assertCancelledBy(board -> board.pane.setEditable(false));
            assertCancelledBy(board -> board.pane.compact());
            assertCancelledBy(board -> board.pane.setCollisionPolicy(CollisionPolicy.BLOCK));
            assertCancelledBy(board -> board.a.setLocked(true));
            assertCancelledBy(board -> board.a.setMoveEnabled(false));
            assertCancelledBy(board -> board.b.setCardId("B2"));
            assertCancelledBy(board -> ((StackPane) board.pane.getScene().getRoot()).getChildren().clear());
        });
    }

    @Test
    public void bindingsThatRecomputeTheSameValueDoNotCancel() throws Exception {
        fx(() -> {
            Board board = new Board();
            board.pane.columnCountProperty().bind(Bindings.createIntegerBinding(
                    () -> board.pane.getWidth() < 200 ? 2 : 4, board.pane.widthProperty()));
            SimpleBooleanProperty source = new SimpleBooleanProperty(false);
            board.c.lockedProperty().bind(Bindings.createBooleanBinding(() -> false, source));
            board.c.minColumnSpanProperty().bind(Bindings.createIntegerBinding(() -> 1, source));
            board.b.cardIdProperty().bind(Bindings.createStringBinding(() -> "B", source));
            board.pane.collisionPolicyProperty().bind(Bindings.createObjectBinding(() -> CollisionPolicy.PUSH, source));
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.pane.resize(396, board.pane.getHeight());
            board.pane.layout();
            source.set(true);
            source.set(false);
            assertTrue(board.a.getPseudoClassStates().contains(DRAGGING));
            board.release(board.a, start.getX() + 100, start.getY());
            assertPlacement(board.a, 1, 0, 2, 1);
        });
    }

    @Test
    public void removingTheDraggedCardCancelsAndGivesItsStateBack() throws Exception {
        fx(() -> {
            Board board = new Board();
            List<RXDashboardEvent> events = board.events();
            Point2D start = board.press(board.a, 5, 5);
            board.drag(board.a, start.getX() + 100, start.getY());
            board.pane.getCards().remove(board.a);
            board.release(board.a, start.getX() + 100, start.getY());
            assertTrue(events.isEmpty());
            assertEquals(0.0, board.a.getTranslateX(), EPSILON);
            assertEquals(0.0, board.a.getViewOrder(), EPSILON);
            assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
            assertNull(board.a.getParent());
            assertEquals(0, board.b.getRow());
        });
    }

    // ==================== Helpers ====================

    private static Region handle(RXDashboardCard card, String edge) {
        for (Node child : card.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains("resize-handle") && child.getStyleClass().contains(edge)) {
                return (Region) child;
            }
        }
        throw new AssertionError("no " + edge + " handle");
    }

    private static void assertNoDrag(Board board) {
        Point2D start = board.press(board.a, 5, 5);
        board.drag(board.a, start.getX() + 100, start.getY());
        assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
        board.release(board.a, start.getX() + 100, start.getY());
        assertPlacement(board.a, 0, 0, 2, 1);
    }

    private static void assertCancelledBy(Consumer<Board> change) {
        Board board = new Board();
        List<RXDashboardEvent> events = board.events();
        Point2D start = board.press(board.a, 5, 5);
        board.drag(board.a, start.getX() + 100, start.getY());
        assertTrue(board.a.getPseudoClassStates().contains(DRAGGING));
        change.accept(board);
        assertFalse(board.a.getPseudoClassStates().contains(DRAGGING));
        board.drag(board.a, start.getX() + 100, start.getY() + 100);
        board.release(board.a, start.getX() + 100, start.getY() + 100);
        assertTrue(events.isEmpty());
        assertEquals(0.0, board.a.getTranslateX(), 0.5);
    }

    private static void assertPlacement(RXDashboardCard card, int column, int row, int columnSpan, int rowSpan) {
        assertEquals(new Placement(column, row, columnSpan, rowSpan),
                new Placement(card.getColumn(), card.getRow(), card.getColumnSpan(), card.getRowSpan()),
                card.getCardId());
    }

    // A(0,0,2,1), B(2,0,1,1), C(2,1,1,1) on a 4-column pane with 100 px cells and no gaps, in an unshown stage.
    private static final class Board {
        final RXDashboardPane pane = new RXDashboardPane();
        final RXDashboardCard a = new RXDashboardCard("A", null, 0, 0, 2, 1);
        final RXDashboardCard b = new RXDashboardCard("B", null, 2, 0, 1, 1);
        final RXDashboardCard c = new RXDashboardCard("C", null, 2, 1, 1, 1);

        Board() {
            pane.setColumnCount(4);
            pane.setRowHeight(100);
            pane.setHgap(0);
            pane.setVgap(0);
            pane.setAnimationDuration(Duration.millis(200));
            pane.getCards().addAll(a, b, c);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(pane), 400, 600));
            pane.getScene().getRoot().applyCss();
            layout();
            layout();
        }

        void layout() {
            pane.resize(400, Math.max(300, pane.prefHeight(-1)));
            pane.layout();
        }

        Region placeholder() {
            return (Region) pane.getChildrenUnmodifiable().get(0);
        }

        Placement candidate() {
            return placementOf(placeholder());
        }

        private Placement placementOf(Region region) {
            return new Placement((int) Math.round(region.getLayoutX() / 100), (int) Math.round(region.getLayoutY() / 100),
                    (int) Math.round(region.getWidth() / 100), (int) Math.round(region.getHeight() / 100));
        }

        List<RXDashboardEvent> events() {
            List<RXDashboardEvent> events = new ArrayList<>();
            pane.addEventHandler(RXDashboardEvent.LAYOUT_CHANGED, events::add);
            return events;
        }

        Point2D local(Node target, double x, double y) {
            return target.localToScene(x, y);
        }

        Point2D press(Node target, double x, double y) {
            Point2D scene = local(target, x, y);
            fire(target, MouseEvent.MOUSE_PRESSED, scene, MouseButton.PRIMARY);
            return scene;
        }

        void drag(Node target, double sceneX, double sceneY) {
            fire(target, MouseEvent.MOUSE_DRAGGED, new Point2D(sceneX, sceneY), MouseButton.PRIMARY);
        }

        void release(Node target, double sceneX, double sceneY) {
            fire(target, MouseEvent.MOUSE_RELEASED, new Point2D(sceneX, sceneY), MouseButton.PRIMARY);
        }

        void fire(Node target, EventType<MouseEvent> type, Point2D scene, MouseButton button) {
            boolean down = type != MouseEvent.MOUSE_RELEASED;
            target.fireEvent(new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(), button, 1,
                    false, false, false, false, down && button == MouseButton.PRIMARY, false,
                    down && button == MouseButton.SECONDARY, false, false, true,
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

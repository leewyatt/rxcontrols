package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogActionsLayout;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link RXDialog}: property defaults, the show / attach +
 * detach lifecycle, and the close gate's pure logic — result conversion, the
 * {@code CLOSE_REQUEST} veto, candidate derivation per {@link CloseReason}, and
 * ordered lifecycle events. Animation is turned off so the gate runs synchronously;
 * the animated transitions, focus trap, scrim, and stacking are real-device checks.
 */
public class RXDialogTest {

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
        // Pin Modena so the dialog card's -rx-surface -> -fx-background alias resolves
        // deterministically regardless of a prior test class's user-agent stylesheet.
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    // ==================== Defaults ====================

    @Test
    public void defaultsAreSensible() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            assertFalse(dialog.isShowing(), "a fresh dialog is hidden");
            assertEquals(RXDialogTransition.CENTER, dialog.getTransition());
            assertTrue(dialog.isAnimated());
            assertTrue(dialog.isModal());
            assertTrue(dialog.isCloseOnEsc());
            assertTrue(dialog.isCloseOnScrimClick());
            assertEquals(RXDialogActionsLayout.BOX, dialog.getActionsLayout());
            assertNull(dialog.getResult());
            assertNull(dialog.getContent());
            assertTrue(dialog.getButtonTypes().isEmpty());
        });
    }

    // ==================== Lifecycle ====================

    @Test
    public void showAttachesAndActionButtonClosesWithResult() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.CANCEL, ButtonType.OK);
            List<RXDialogEvent> events = recordEvents(dialog);
            AtomicReference<ButtonType> delivered = new AtomicReference<>();
            dialog.setOnResult(delivered::set);

            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            assertTrue(dialog.isShowing(), "shown after show()");
            assertNotNull(dialog.getScene(), "attached to the owner's scene");
            assertSame(scene, dialog.getScene());

            // The skin fires this exact call when an action button is clicked.
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);

            assertFalse(dialog.isShowing(), "hidden after close");
            assertNull(dialog.getScene(), "detached from the scene after hide");
            assertEquals(ButtonType.OK, dialog.getResult());
            assertEquals(ButtonType.OK, delivered.get(), "onResult received the result");

            assertEquals(
                    List.of(RXDialogEvent.SHOWING, RXDialogEvent.SHOWN,
                            RXDialogEvent.CLOSE_REQUEST, RXDialogEvent.HIDING, RXDialogEvent.HIDDEN),
                    events.stream().map(e -> e.getEventType()).toList(),
                    "lifecycle events fire in order");
        });
    }

    @Test
    public void closeRequestVetoKeepsDialogOpen() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            AtomicReference<ButtonType> delivered = new AtomicReference<>();
            dialog.setOnResult(delivered::set);
            EventHandler<RXDialogEvent> veto = Event::consume;
            dialog.addEventHandler(RXDialogEvent.CLOSE_REQUEST, veto);

            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);

            assertTrue(dialog.isShowing(), "veto keeps the dialog open");
            assertNull(dialog.getResult(), "no result is set on a vetoed close");
            assertNull(delivered.get(), "onResult is not called on a vetoed close");

            // Lift the veto and close so no shown dialog leaks into a later test.
            dialog.removeEventHandler(RXDialogEvent.CLOSE_REQUEST, veto);
            dialog.close();
            assertFalse(dialog.isShowing(), "closes once the veto is lifted");
        });
    }

    @Test
    public void dismissReasonsDeriveTheCancelButtonTypeAsCandidate() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.CANCEL, ButtonType.OK);
            List<RXDialogEvent> events = recordEvents(dialog);

            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            // ESC / scrim / close-button dismissals carry no explicit candidate;
            // the gate derives the cancel-type button.
            dialog.requestClose(null, CloseReason.ESC);

            assertEquals(ButtonType.CANCEL, dialog.getResult(),
                    "ESC derives the cancel-type button as the candidate");
            RXDialogEvent hidden = events.stream()
                    .filter(e -> e.getEventType() == RXDialogEvent.HIDDEN).findFirst().orElseThrow();
            assertEquals(ButtonType.CANCEL, hidden.getButtonType());
            assertEquals(CloseReason.ESC, hidden.getCloseReason());
        });
    }

    @Test
    public void nullResultConverterCastsTheCandidate() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            dialog.setAnimated(false);
            dialog.getButtonTypes().setAll(ButtonType.OK);
            // No resultConverter set.

            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);
            dialog.close(ButtonType.OK);

            assertEquals(ButtonType.OK, dialog.getResult(),
                    "with no converter the candidate button type is the result");
        });
    }

    @Test
    public void setResultDoesNotClose() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            dialog.setResult(ButtonType.OK);

            assertTrue(dialog.isShowing(), "setResult must not close the dialog");
            dialog.close(); // cleanup: do not leak a shown dialog into a later test
        });
    }

    @Test
    public void reshowFromOnResultKeepsDialogAttached() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);

            AtomicInteger reshows = new AtomicInteger();
            dialog.setOnResult(result -> {
                if (reshows.getAndIncrement() == 0) {
                    dialog.show(owner); // chain a new dialog from the result callback
                }
            });

            dialog.show(owner);
            dialog.close(ButtonType.OK); // -> hide completes -> onResult re-shows once

            assertTrue(dialog.isShowing(), "a re-show from onResult must leave the dialog shown");
            assertNotNull(dialog.getScene(), "the re-shown dialog must stay attached (not torn out by a trailing detach)");

            dialog.close(ButtonType.OK); // cleanup: second result does not re-show
            assertFalse(dialog.isShowing());
        });
    }

    @Test
    public void showOverNonPaneRootWrapsAndRestores() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            Region owner = new Region();
            Group root = new Group(owner); // a Parent that is NOT a Pane -> WRAP install
            Scene scene = new Scene(root, 400, 300);

            dialog.show(owner);
            assertTrue(dialog.isShowing());
            assertNotSame(root, scene.getRoot(), "a non-Pane root is wrapped while a dialog shows");

            dialog.close();
            assertFalse(dialog.isShowing());
            assertSame(root, scene.getRoot(), "the original root is restored after the last dialog hides");
        });
    }

    @Test
    public void reentrantCloseFromHandlerDoesNotDoubleClose() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            List<RXDialogEvent> events = recordEvents(dialog);
            // A handler that itself requests a close must not recurse or emit a second
            // HIDING after HIDDEN: the re-entrant close is swallowed by the close gate.
            dialog.addEventHandler(RXDialogEvent.CLOSE_REQUEST, e -> dialog.close());

            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);

            assertFalse(dialog.isShowing(), "dialog closes exactly once");
            List<EventType<? extends Event>> types =
                    events.stream().map(RXDialogEvent::getEventType).toList();
            assertEquals(1, types.stream().filter(t -> t == RXDialogEvent.HIDING).count(),
                    "exactly one HIDING despite the re-entrant close");
            assertEquals(1, types.stream().filter(t -> t == RXDialogEvent.HIDDEN).count(),
                    "exactly one HIDDEN");
            assertSame(RXDialogEvent.HIDDEN, types.get(types.size() - 1),
                    "HIDDEN is terminal (no spurious HIDING fires after it)");
        });
    }

    @Test
    public void stackedModalSuppressesTheLowerScrim() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);

            RXDialog<ButtonType> lower = newDialog(ButtonType.OK);
            RXDialog<ButtonType> upper = newDialog(ButtonType.OK);
            lower.show(owner);
            upper.show(owner);

            Node lowerScrim = lower.lookup(".overlay");
            Node upperScrim = upper.lookup(".overlay");
            assertNotNull(lowerScrim, "the lower dialog has a scrim node");
            assertNotNull(upperScrim, "the upper dialog has a scrim node");
            assertFalse(lowerScrim.isVisible(),
                    "a dialog covered by another suppresses its own scrim (one merged scrim)");
            assertTrue(upperScrim.isVisible(), "the top-most dialog shows its scrim");

            upper.close();
            assertTrue(lowerScrim.isVisible(),
                    "closing the top dialog restores the now-top-most dialog's scrim");

            lower.close();
        });
    }

    @Test
    public void awareContentReceivesDialogInjection() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            assertNull(layout.getDialog(), "unhosted content has no dialog");

            dialog.setContent(layout);
            assertSame(dialog, layout.getDialog(), "content is injected with its hosting dialog");

            RXDialogContent other = new RXDialogContent("Other", "Body");
            dialog.setContent(other);
            assertNull(layout.getDialog(), "the replaced content's dialog ref is cleared");
            assertSame(dialog, other.getDialog(), "the new content is injected");

            dialog.setContent(null);
            assertNull(other.getDialog(), "clearing the content clears the injection");
        });
    }

    @Test
    public void plainAndGeneralContentInjectionContract() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();

            // A plain (non-aware) Node is accepted and receives no injection (no exception).
            Region plain = new Region();
            dialog.setContent(plain);
            assertSame(plain, dialog.getContent(), "a plain node is accepted as content");

            // A bare RXDialogContentBase (not an RXDialogContent) gets the general injection.
            RXDialogContentBase aware = new RXDialogContentBase();
            dialog.setContent(aware);
            assertSame(dialog, aware.getDialog(), "the general RXDialogContentBase channel is injected");

            dialog.setContent(null);
            assertNull(aware.getDialog(), "clearing the content clears the general injection");
        });
    }

    @Test
    public void movingAwareContentToAnotherDialogReleasesTheFirst() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> first = new RXDialog<>();
            RXDialog<ButtonType> second = new RXDialog<>();
            RXDialogContentBase content = new RXDialogContentBase();

            first.setContent(content);
            second.setContent(content); // move the same node to a second dialog
            assertSame(second, content.getDialog(), "the new dialog owns the moved content");

            // The first dialog, no longer tracking the node, must not clobber the second's injection.
            first.setContent(null);
            assertSame(second, content.getDialog(), "the prior dialog does not null the new owner's injection");

            second.setContent(null);
            assertNull(content.getDialog());
        });
    }

    @Test
    public void dialogPropertyFiresOnAttachAndDetach() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            RXDialogContentBase content = new RXDialogContentBase();
            AtomicInteger fires = new AtomicInteger();
            AtomicReference<RXDialog<?>> last = new AtomicReference<>();
            content.dialogProperty().addListener((obs, old, now) -> {
                fires.incrementAndGet();
                last.set(now);
            });

            dialog.setContent(content);
            assertEquals(1, fires.get(), "attach fires dialogProperty once");
            assertSame(dialog, last.get(), "attach reports the hosting dialog");

            dialog.setContent(null);
            assertEquals(2, fires.get(), "detach fires dialogProperty");
            assertNull(last.get(), "detach reports null");
        });
    }

    @Test
    public void headerCloseButtonClosesHostingDialog() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.OK);
            List<RXDialogEvent> events = recordEvents(dialog);
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            layout.setShowClose(true);
            dialog.setContent(layout);

            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            Node closeButton = layout.lookup(".close-button");
            assertNotNull(closeButton, "showClose adds an in-header close button");
            closeButton.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                    false, false, false, null));

            assertFalse(dialog.isShowing(), "clicking the header X closes the hosting dialog");
            RXDialogEvent hidden = events.stream()
                    .filter(e -> e.getEventType() == RXDialogEvent.HIDDEN).findFirst().orElseThrow();
            assertEquals(CloseReason.CLOSE_BUTTON, hidden.getCloseReason(),
                    "the header X closes with reason CLOSE_BUTTON");
        });
    }

    @Test
    public void conflictingContainerOnAnOverlappingDialogThrows() throws Exception {
        runOnFx(() -> {
            StackPane paneA = new StackPane();
            StackPane paneB = new StackPane();
            new Scene(new StackPane(paneA, paneB), 400, 300);

            RXDialog<ButtonType> first = newDialog(ButtonType.OK);
            first.showIn(paneA); // installs the scene's single overlay layer in paneA

            RXDialog<ButtonType> second = newDialog(ButtonType.OK);
            // A still-overlapping second dialog asking for a different container cannot be
            // honored (one overlay per scene) -> fail loudly instead of mounting into paneA.
            assertThrows(IllegalStateException.class, () -> second.showIn(paneB));
            assertFalse(second.isShowing(), "the rejected dialog did not open");

            first.close();
            // Once the first closes the layer uninstalls, so a fresh container is honored.
            second.showIn(paneB);
            assertTrue(second.isShowing(), "after the layer frees up, the container is honored");
            second.close();
        });
    }

    @Test
    public void actionsLayoutSelectsTheContainer() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = newDialog(ButtonType.CANCEL, ButtonType.OK);
            Region owner = new Region();
            new Scene(new StackPane(owner), 400, 300);
            dialog.show(owner);

            // BOX (default) renders a plain RXBox row in buttonTypes order (styled by CSS).
            Node defaultActions = dialog.lookup(".actions");
            assertTrue(defaultActions instanceof RXBox, "BOX (default) uses an RXBox");
            RXBox row = (RXBox) defaultActions;
            assertEquals(2, row.getChildren().size());
            assertEquals("Cancel", ((RXButton) row.getChildren().get(0)).getText(),
                    "BOX keeps buttonTypes insertion order");

            // PLATFORM switches to the native ButtonBar.
            dialog.setActionsLayout(RXDialogActionsLayout.PLATFORM);
            assertTrue(dialog.lookup(".actions") instanceof ButtonBar, "PLATFORM uses a ButtonBar");

            dialog.close();
        });
    }

    @Test
    public void showWithoutOwnerOrContainerThrows() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            assertThrows(IllegalStateException.class, dialog::show,
                    "show() with no owner / container should fail clearly");
        });
    }

    // ==================== Drag / resize geometry ====================

    // These drive the skin's gesture handlers through real, picked mouse events and assert the
    // laid-out card geometry (lookup + fireEvent + applyCss/layout; no production back door).
    // The animated transitions, the eight resize cursors, and touch are real-device checks.

    @Test
    public void enableResizableAndEnableDraggableDefaultFalse() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            assertFalse(dialog.isEnableResizable(), "enableResizable defaults to false");
            assertFalse(dialog.isEnableDraggable(), "enableDraggable defaults to false");
        });
    }

    @Test
    public void dragMovesCardByTheDelta() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double x0 = card.getLayoutX();
            double y0 = card.getLayoutY();

            pressDragRelease(card, card.getWidth() / 2, 24, 120, 60);

            assertEquals(x0 + 120, card.getLayoutX(), 2.0, "drag moves the card right by the delta");
            assertEquals(y0 + 60, card.getLayoutY(), 2.0, "drag moves the card down by the delta");
            dialog.close();
        });
    }

    @Test
    public void dragClampsCardWithinTheScene() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);

            // Fling far past the edges; the per-frame clamp must keep the card fully visible.
            pressDragRelease(card, card.getWidth() / 2, 24, 100_000, 100_000);

            assertTrue(card.getLayoutX() >= -0.5, "not off the left edge");
            assertTrue(card.getLayoutY() >= -0.5, "not off the top edge");
            assertTrue(card.getLayoutX() + card.getWidth() <= 800.5, "not off the right edge");
            assertTrue(card.getLayoutY() + card.getHeight() <= 600.5, "not off the bottom edge");
            dialog.close();
        });
    }

    @Test
    public void sceneShrinkReclampsADraggedCard() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableDraggable(true);
            StackPane root = showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            pressDragRelease(card, card.getWidth() / 2, 24, 300, 200);

            // Shrink the scene: the card must be pulled back into view (raw offset preserved).
            layoutTree(root, 360, 300);

            assertTrue(card.getLayoutX() >= -0.5 && card.getLayoutX() + card.getWidth() <= 360.5,
                    "card stays horizontally visible after the scene shrinks");
            assertTrue(card.getLayoutY() >= -0.5 && card.getLayoutY() + card.getHeight() <= 300.5,
                    "card stays vertically visible after the scene shrinks");
            dialog.close();
        });
    }

    @Test
    public void resizeEastGrowsWidthKeepingTheLeftEdgeFixed() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double x0 = card.getLayoutX();
            double w0 = card.getWidth();

            pressDragRelease(card, w0 - 3, card.getHeight() / 2, 100, 0);

            assertEquals(x0, card.getLayoutX(), 2.0, "east resize keeps the left edge fixed");
            assertEquals(w0 + 100, card.getWidth(), 2.0, "east resize grows the width by the delta");
            dialog.close();
        });
    }

    @Test
    public void resizeWestGrowsWidthKeepingTheRightEdgeFixed() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double right0 = card.getLayoutX() + card.getWidth();
            double w0 = card.getWidth();

            // Drag the west edge left: the opposite (right) edge must not move (spec §3.4 #2).
            pressDragRelease(card, 3, card.getHeight() / 2, -100, 0);

            assertEquals(w0 + 100, card.getWidth(), 2.0, "west resize grows the width by the delta");
            assertEquals(right0, card.getLayoutX() + card.getWidth(), 2.0,
                    "west resize keeps the right edge fixed");
            dialog.close();
        });
    }

    @Test
    public void resizeNorthGrowsHeightKeepingTheBottomEdgeFixed() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double bottom0 = card.getLayoutY() + card.getHeight();
            double h0 = card.getHeight();

            // Drag the north edge up: the opposite (bottom) edge must not move (spec §3.4 #2).
            // y=3 is in both the north resize band and the drag band, so this also covers the
            // resize-wins-over-drag priority (§3.4 #3).
            pressDragRelease(card, card.getWidth() / 2, 3, 0, -100);

            assertEquals(h0 + 100, card.getHeight(), 2.0, "north resize grows the height");
            assertEquals(bottom0, card.getLayoutY() + card.getHeight(), 2.0,
                    "north resize keeps the bottom edge fixed");
            dialog.close();
        });
    }

    @Test
    public void resizeSouthGrowsHeightKeepingTheTopEdgeFixed() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double y0 = card.getLayoutY();
            double h0 = card.getHeight();

            pressDragRelease(card, card.getWidth() / 2, card.getHeight() - 3, 0, 100);

            assertEquals(h0 + 100, card.getHeight(), 2.0, "south resize grows the height");
            assertEquals(y0, card.getLayoutY(), 2.0, "south resize keeps the top edge fixed");
            dialog.close();
        });
    }

    @Test
    public void resizeSouthEastCornerGrowsBothKeepingTopLeftFixed() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double x0 = card.getLayoutX();
            double y0 = card.getLayoutY();
            double w0 = card.getWidth();
            double h0 = card.getHeight();

            // A corner grows both axes; the opposite (top-left) corner must stay fixed.
            pressDragRelease(card, card.getWidth() - 3, card.getHeight() - 3, 100, 100);

            assertEquals(x0, card.getLayoutX(), 2.0, "SE-corner resize keeps the left edge fixed");
            assertEquals(y0, card.getLayoutY(), 2.0, "SE-corner resize keeps the top edge fixed");
            assertEquals(w0 + 100, card.getWidth(), 2.0, "SE-corner resize grows the width");
            assertEquals(h0 + 100, card.getHeight(), 2.0, "SE-corner resize grows the height");
            dialog.close();
        });
    }

    @Test
    public void dragAfterResizeMovesAndDoesNotResize() throws Exception {
        // Integration: drag and resize are independent — after an edge resize, a title-band drag
        // moves the card (it does not keep resizing). The release-time gesture reset itself is
        // guarded by bodyDragAfterResizeIsInert (a band press calls beginDrag, which defensively
        // re-clears, so it would not by itself catch a missing release-time reset).
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double w0 = card.getWidth();

            pressDragRelease(card, w0 - 3, card.getHeight() / 2, 100, 0);
            double w1 = card.getWidth();
            double x1 = card.getLayoutX();
            assertEquals(w0 + 100, w1, 2.0, "the east resize grew the width");

            pressDragRelease(card, card.getWidth() / 2, 24, 80, 0);

            assertEquals(w1, card.getWidth(), 2.0, "a drag after a resize must not change the width");
            assertEquals(x1 + 80, card.getLayoutX(), 2.0, "a drag after a resize moves the card");
            dialog.close();
        });
    }

    @Test
    public void bodyDragAfterResizeIsInert() throws Exception {
        // Regression for the release-time gesture reset (onCardMouseReleased -> clearGestureFlags):
        // a press on a non-interactive body region starts no gesture (no begin*, so nothing
        // defensively re-clears), so a drag from it must be a no-op. If the resize release failed
        // to clear resizeActive, this body drag would run updateResize from stale anchors.
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);

            pressDragRelease(card, card.getWidth() - 3, card.getHeight() / 2, 100, 0); // resize east
            double w1 = card.getWidth();
            double x1 = card.getLayoutX();

            // Press well below the drag band and away from any edge, then drag.
            pressDragRelease(card, card.getWidth() / 2, card.getHeight() - 30, 80, 40);

            assertEquals(w1, card.getWidth(), 2.0, "a body drag after a resize must not resize the card");
            assertEquals(x1, card.getLayoutX(), 2.0, "a body drag after a resize must not move the card");
            dialog.close();
        });
    }

    @Test
    public void tinySceneResizeNeverThrows() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            // A scene smaller than the card's CSS min is a degenerate case: layoutInArea keeps
            // the card at its min so it overflows (centered), but layout + a resize gesture must
            // stay well-defined and never throw (spec §3.4 #1 uses boundedSize + Math.min/max,
            // never RXMath.clamp which throws when min > max).
            showAndLayout(dialog, 120, 120);
            StackPane card = card(dialog);
            assertNotNull(card, "card lays out in a tiny scene");
            double w0 = card.getWidth();
            assertTrue(w0 > 0 && Double.isFinite(w0), "card keeps a sane size in a tiny scene");

            pressDragRelease(card, card.getWidth() - 3, card.getHeight() / 2, -50, 0);

            assertTrue(card.getWidth() > 0 && Double.isFinite(card.getWidth()),
                    "a resize gesture in a tiny scene stays well-defined (no clamp throw)");
            dialog.close();
        });
    }

    @Test
    public void pressOnCloseButtonDoesNotDragTheCard() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            RXDialogContent content = (RXDialogContent) dialog.getContent();
            content.setShowClose(true);
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            Node closeButton = dialog.lookup(".close-button");
            assertNotNull(closeButton, "showClose adds the close button");
            double x0 = card.getLayoutX();

            // A press-drag starting on the close (X) button must be excluded from dragging.
            Point2D press = closeButton.localToScene(4, 4);
            fire(closeButton, MouseEvent.MOUSE_PRESSED, press.getX(), press.getY(), MouseButton.PRIMARY, true);
            fire(closeButton, MouseEvent.MOUSE_DRAGGED, press.getX() + 120, press.getY() + 120, MouseButton.NONE, true);
            fire(closeButton, MouseEvent.MOUSE_RELEASED, press.getX() + 120, press.getY() + 120, MouseButton.PRIMARY, false);
            layoutTree((StackPane) card.getScene().getRoot(), 800, 600);

            assertEquals(x0, card.getLayoutX(), 0.5, "a press on the close button does not drag the card");
            assertTrue(dialog.isShowing(), "the excluded press-drag did not close the dialog");
            dialog.close();
        });
    }

    @Test
    public void disablingDraggableMidDragCancelsButKeepsPosition() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableDraggable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double x0 = card.getLayoutX();

            Point2D press = card.localToScene(card.getWidth() / 2, 24);
            fire(card, MouseEvent.MOUSE_PRESSED, press.getX(), press.getY(), MouseButton.PRIMARY, true);
            fire(card, MouseEvent.MOUSE_DRAGGED, press.getX() + 100, press.getY(), MouseButton.NONE, true);
            layoutTree((StackPane) card.getScene().getRoot(), 800, 600);
            assertEquals(x0 + 100, card.getLayoutX(), 2.0, "card follows the drag");

            // Disabling mid-drag cancels the gesture but keeps the current position (§3.4 #10).
            dialog.setEnableDraggable(false);
            fire(card, MouseEvent.MOUSE_DRAGGED, press.getX() + 200, press.getY(), MouseButton.NONE, true);
            layoutTree((StackPane) card.getScene().getRoot(), 800, 600);
            assertEquals(x0 + 100, card.getLayoutX(), 2.0,
                    "disabling cancels further dragging and keeps the position (no recenter)");

            fire(card, MouseEvent.MOUSE_RELEASED, press.getX() + 200, press.getY(), MouseButton.PRIMARY, false);
            dialog.close();
        });
    }

    @Test
    public void disablingResizableMidResizeCancelsButKeepsSize() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = gestureDialog();
            dialog.setEnableResizable(true);
            showAndLayout(dialog, 800, 600);
            StackPane card = card(dialog);
            double w0 = card.getWidth();

            Point2D press = card.localToScene(card.getWidth() - 3, card.getHeight() / 2);
            fire(card, MouseEvent.MOUSE_PRESSED, press.getX(), press.getY(), MouseButton.PRIMARY, true);
            fire(card, MouseEvent.MOUSE_DRAGGED, press.getX() + 100, press.getY(), MouseButton.NONE, true);
            layoutTree((StackPane) card.getScene().getRoot(), 800, 600);
            assertEquals(w0 + 100, card.getWidth(), 2.0, "card grows during the resize");

            // Disabling mid-resize cancels the gesture but keeps the current size (§3.4 #10).
            dialog.setEnableResizable(false);
            fire(card, MouseEvent.MOUSE_DRAGGED, press.getX() + 200, press.getY(), MouseButton.NONE, true);
            layoutTree((StackPane) card.getScene().getRoot(), 800, 600);
            assertEquals(w0 + 100, card.getWidth(), 2.0,
                    "disabling cancels further resizing and keeps the size (no reset to auto)");

            fire(card, MouseEvent.MOUSE_RELEASED, press.getX() + 200, press.getY(), MouseButton.PRIMARY, false);
            dialog.close();
        });
    }

    // ==================== Helpers ====================

    private static RXDialog<ButtonType> newDialog(ButtonType... types) {
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setAnimated(false);
        dialog.setResultConverter(buttonType -> buttonType);
        dialog.getButtonTypes().setAll(types);
        return dialog;
    }

    // A non-animated dialog with real content, so its card has a genuine pref size to drag/resize.
    private static RXDialog<ButtonType> gestureDialog() {
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setAnimated(false);
        dialog.setContent(new RXDialogContent("Title",
                "Body text that is long enough to give the card a real preferred size."));
        dialog.getButtonTypes().setAll(ButtonType.OK);
        return dialog;
    }

    // Shows the dialog over a fresh sized scene and runs a deterministic layout pass.
    private static StackPane showAndLayout(RXDialog<?> dialog, double width, double height) {
        Region owner = new Region();
        StackPane root = new StackPane(owner);
        new Scene(root, width, height);
        dialog.show(owner);
        return layoutTree(root, width, height);
    }

    // Forces the whole scene-graph (layer, dialog control, card) to lay out at a known size.
    private static StackPane layoutTree(StackPane root, double width, double height) {
        root.resize(width, height);
        root.applyCss();
        root.layout();
        return root;
    }

    private static StackPane card(RXDialog<?> dialog) {
        return (StackPane) dialog.lookup(".dialog-card");
    }

    // Fires a full press -> drag -> release on the card (local press point, scene-space delta),
    // then re-lays-out so the gesture's requestLayout is applied before assertions.
    private static void pressDragRelease(Node card, double localX, double localY, double dx, double dy) {
        Point2D press = card.localToScene(localX, localY);
        fire(card, MouseEvent.MOUSE_PRESSED, press.getX(), press.getY(), MouseButton.PRIMARY, true);
        fire(card, MouseEvent.MOUSE_DRAGGED, press.getX() + dx, press.getY() + dy, MouseButton.NONE, true);
        fire(card, MouseEvent.MOUSE_RELEASED, press.getX() + dx, press.getY() + dy, MouseButton.PRIMARY, false);
        ((Parent) card.getScene().getRoot()).layout();
    }

    private static void fire(Node node, EventType<MouseEvent> type, double sceneX, double sceneY,
                             MouseButton button, boolean primaryDown) {
        node.fireEvent(new MouseEvent(type, sceneX, sceneY, sceneX, sceneY, button,
                button == MouseButton.NONE ? 0 : 1,
                false, false, false, false, primaryDown, false, false, false, false, false, null));
    }

    private static List<RXDialogEvent> recordEvents(RXDialog<ButtonType> dialog) {
        List<RXDialogEvent> events = new ArrayList<>();
        dialog.addEventHandler(RXDialogEvent.ANY, events::add);
        return events;
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
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
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}

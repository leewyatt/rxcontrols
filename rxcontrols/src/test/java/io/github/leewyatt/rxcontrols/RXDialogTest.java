package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogTransition;
import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
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
            assertFalse(dialog.isShowCloseButton());
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
    public void showWithoutOwnerOrHostThrows() throws Exception {
        runOnFx(() -> {
            RXDialog<ButtonType> dialog = new RXDialog<>();
            assertThrows(IllegalStateException.class, dialog::show,
                    "show() with no owner / host should fail clearly");
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

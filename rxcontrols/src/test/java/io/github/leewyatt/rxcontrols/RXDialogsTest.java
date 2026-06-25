package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.CloseReason;
import io.github.leewyatt.rxcontrols.enums.RXDialogActionsLayout;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the {@link RXDialogs} facade: it builds a plain
 * {@link RXDialog} with the right buttons / style class / content and delivers the
 * result through the returned future. Animation is switched off on the looked-up
 * dialog so the close gate (and the result) runs synchronously.
 */
public class RXDialogsTest {

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
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void confirmOffersCancelAndOkAndCompletesWithTheChoice() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            CompletableFuture<ButtonType> result = RXDialogs.confirm(owner, "Delete?", "Cannot be undone.");
            RXDialog<?> dialog = shownDialog(scene);

            assertNotNull(dialog, "the facade attached a dialog to the scene");
            assertEquals(List.of(ButtonType.CANCEL, ButtonType.OK), dialog.getButtonTypes());
            assertTrue(dialog.getStyleClass().contains("rx-dialog-confirmation"),
                    "confirmation carries its type style class");

            dialog.setAnimated(false);
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);

            assertTrue(result.isDone(), "the future completes when the dialog closes");
            assertEquals(ButtonType.OK, result.getNow(null), "completed with the chosen button");
        });
    }

    @Test
    public void messageFactoriesUseOneOkButtonAndTheTypeStyleClass() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            RXDialogs.error(owner, "Upload failed", "Network unreachable.");
            RXDialog<?> dialog = shownDialog(scene);

            assertEquals(List.of(ButtonType.OK), dialog.getButtonTypes(), "a message has a single OK button");
            assertTrue(dialog.getStyleClass().contains("rx-dialog-error"), "error carries its type style class");

            dialog.setAnimated(false);
            dialog.close();
        });
    }

    @Test
    public void messageFactoriesAcceptCustomActionButtons() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            ButtonType report = new ButtonType("Report");
            CompletableFuture<ButtonType> result =
                    RXDialogs.error(owner, "Crashed", "Unexpected error.", ButtonType.OK, report);
            RXDialog<?> dialog = shownDialog(scene);

            assertEquals(List.of(ButtonType.OK, report), dialog.getButtonTypes(),
                    "a typed message can still offer custom action buttons");
            assertTrue(dialog.getStyleClass().contains("rx-dialog-error"), "and keeps its type styling");

            dialog.setAnimated(false);
            dialog.requestClose(report, CloseReason.ACTION_BUTTON);
            assertEquals(report, result.getNow(null), "the future reports the custom button");
        });
    }

    @Test
    public void confirmDismissalYieldsTheCancelButton() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            CompletableFuture<ButtonType> result = RXDialogs.confirm(owner, "Delete?", "Cannot be undone.");
            RXDialog<?> dialog = shownDialog(scene);
            dialog.setAnimated(false);

            // ESC carries no explicit candidate; a confirmation resolves it to its Cancel button.
            dialog.requestClose(null, CloseReason.ESC);
            assertEquals(ButtonType.CANCEL, result.getNow(null),
                    "dismissing a confirmation yields Cancel, not OK");
        });
    }

    @Test
    public void messageDismissalNeverActivatesAnAffirmativeButton() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            ButtonType report = new ButtonType("Report");
            CompletableFuture<ButtonType> result =
                    RXDialogs.error(owner, "Crashed", "Unexpected error.", ButtonType.OK, report);
            RXDialog<?> dialog = shownDialog(scene);
            dialog.setAnimated(false);

            // No cancel-type button exists, so a dismissal must yield null — never OK / Report,
            // so a stray ESC / scrim can't trigger an affirmative action.
            dialog.requestClose(null, CloseReason.SCRIM);
            assertTrue(result.isDone(), "the dismissal still completes the future");
            assertNull(result.getNow(report), "dismissal yields null, not an affirmative button");
        });
    }

    @Test
    public void inputReturnsTheFieldTextOnOk() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            CompletableFuture<String> result = RXDialogs.input(owner, "Rename", "New name:", "old-name");
            RXDialog<?> dialog = shownDialog(scene);

            dialog.setAnimated(false);
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);

            assertEquals("old-name", result.getNow(null), "OK yields the field text");
        });
    }

    @Test
    public void inputYieldsNullOnCancel() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            CompletableFuture<String> result = RXDialogs.input(owner, "Rename", "New name:", "old-name");
            RXDialog<?> dialog = shownDialog(scene);

            dialog.setAnimated(false);
            dialog.requestClose(ButtonType.CANCEL, CloseReason.ACTION_BUTTON);

            assertTrue(result.isDone(), "cancel still completes the future");
            assertNull(result.getNow("sentinel"), "cancel yields null, not the text");
        });
    }

    @Test
    public void busyIgnoresEscAndScrimAndCloseHidesIt() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            RXDialogs.Busy busy = RXDialogs.busy(owner, "Loading…");
            RXDialog<?> dialog = shownDialog(scene);

            assertTrue(busy.isShowing(), "the busy dialog is up");
            assertFalse(dialog.isCloseOnEsc(), "busy ignores ESC");
            assertFalse(dialog.isCloseOnScrimClick(), "busy ignores scrim clicks");

            dialog.setAnimated(false);
            busy.close();
            assertFalse(busy.isShowing(), "close() dismisses the busy dialog");
        });
    }

    @Test
    public void validWhenVetoesTheAffirmativeButtonUntilValid() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            BooleanProperty valid = new SimpleBooleanProperty(false);
            CompletableFuture<ButtonType> result = RXDialogs.create(owner)
                    .type(RXDialogs.Type.CONFIRMATION).title("Rename").message("Pick a name.")
                    .buttons(ButtonType.CANCEL, ButtonType.OK).validWhen(valid).show();
            RXDialog<?> dialog = shownDialog(scene);
            dialog.setAnimated(false);

            // Invalid: clicking the affirmative (OK) is vetoed, the dialog stays open.
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);
            assertFalse(result.isDone(), "OK is vetoed while invalid");
            assertTrue(dialog.isShowing(), "the dialog stays open");

            // Cancel is never gated.
            valid.set(true);
            dialog.requestClose(ButtonType.OK, CloseReason.ACTION_BUTTON);
            assertTrue(result.isDone(), "OK closes once valid");
            assertEquals(ButtonType.OK, result.getNow(null));
        });
    }

    @Test
    public void validWhenStillAllowsEscWhileInvalid() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            BooleanProperty valid = new SimpleBooleanProperty(false);
            CompletableFuture<ButtonType> result = RXDialogs.create(owner)
                    .title("T").message("M").buttons(ButtonType.OK).validWhen(valid).show();
            RXDialog<?> dialog = shownDialog(scene);
            dialog.setAnimated(false);

            // Even with only OK + invalid, a dismissal (ESC) is never trapped by the gate.
            dialog.requestClose(null, CloseReason.ESC);
            assertTrue(result.isDone(), "ESC dismisses a validation-gated dialog");
            assertFalse(dialog.isShowing(), "the dialog closed on ESC");
        });
    }

    @Test
    public void builderHonoursBehaviourOverrides() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            RXDialogs.create(owner).title("T").message("M")
                    .modal(false).closeOnEsc(false).draggable(true).resizable(true).show();
            RXDialog<?> dialog = shownDialog(scene);

            assertFalse(dialog.isModal(), "modal override applied");
            assertFalse(dialog.isCloseOnEsc(), "closeOnEsc override applied");
            assertTrue(dialog.isEnableDraggable(), "draggable override applied");
            assertTrue(dialog.isEnableResizable(), "resizable override applied");

            dialog.setAnimated(false);
            dialog.close();
        });
    }

    @Test
    public void builderAppliesActionsLayoutAndCloseButton() throws Exception {
        runOnFx(() -> {
            Region owner = new Region();
            Scene scene = new Scene(new StackPane(owner), 400, 300);

            // Extra knobs configured by chaining — no new factory overload needed.
            RXDialogs.create(owner).type(RXDialogs.Type.INFORMATION).title("T").message("M")
                    .actionsLayout(RXDialogActionsLayout.PLATFORM).closeButton(true).show();
            RXDialog<?> dialog = shownDialog(scene);

            assertEquals(RXDialogActionsLayout.PLATFORM, dialog.getActionsLayout(),
                    "actionsLayout flows to the dialog");
            assertTrue(dialog.isShowCloseButton(),
                    "closeButton(true) shows the dialog's close (X)");

            dialog.setAnimated(false);
            dialog.close();
        });
    }

    // ==================== Helpers ====================

    private static RXDialog<?> shownDialog(Scene scene) {
        scene.getRoot().applyCss();
        return (RXDialog<?>) scene.getRoot().lookup(".rx-dialog");
    }

    private static void runOnFx(ThrowingRunnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action did not finish in time");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

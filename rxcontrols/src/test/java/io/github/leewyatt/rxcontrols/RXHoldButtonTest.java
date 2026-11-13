package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.FeedbackSignals;
import io.github.leewyatt.rxcontrols.skins.RXHoldButtonSkin;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXHoldButton control contract, the three-caption skin structure
 * and its sizing, the press-and-hold state machine, the completed feedback
 * with its lock and reset, and the progress fill. Hold tests use a short
 * hold duration and wait off the FX thread, so the timelines can run.
 */
public class RXHoldButtonTest {

    private static final String LONG_TEXT = "A considerably longer caption";
    private static final boolean MAC = RXOS.isMacOS();
    private static final PseudoClass HOLDING = PseudoClass.getPseudoClass("holding");
    private static final PseudoClass COMPLETED = PseudoClass.getPseudoClass("completed");
    private static final Duration SHORT_HOLD = Duration.millis(80.0);
    // Comfortably past SHORT_HOLD, well short of the 1500 ms default.
    private static final long PAST_HOLD_MS = 400L;

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
        Platform.setImplicitExit(false);
    }

    // ==================== Control API ====================

    @Test
    public void defaultsMatchContract() {
        RXHoldButton button = new RXHoldButton("Hold to delete");

        assertEquals(List.of("rx-hold-button"), button.getStyleClass());
        assertEquals(Duration.millis(1500.0), button.getHoldDuration());
        assertEquals("Keep holding…", button.getHoldingText());
        assertEquals("Done", button.getCompletedText());
        assertNull(button.getHoldingGraphic());
        assertNull(button.getCompletedGraphic());
        assertEquals(Duration.INDEFINITE, button.getFeedbackDuration());
        assertNull(button.getAnimation());
        assertEquals(Duration.millis(200.0), button.getAnimationDuration());
        assertEquals(FillAnimation.LEFT_TO_RIGHT, button.getFillAnimation());
        assertFalse(button.isMnemonicParsing());
        assertEquals(AccessibleRole.BUTTON, button.getAccessibleRole());
        assertNull(button.getAccessibleRoleDescription());
    }

    @Test
    public void cssMetadataCoversStyleablePropertiesOnly() {
        RXHoldButton button = new RXHoldButton();
        List<CssMetaData<? extends Styleable, ?>> metaData = button.getCssMetaData();

        assertTrue(hasProperty(metaData, "-rx-feedback-duration"));
        assertTrue(hasProperty(metaData, "-rx-animation-duration"));
        assertTrue(hasProperty(metaData, "-rx-fill-animation"));
        assertFalse(metaData.stream().anyMatch(item -> item.getProperty().contains("hold-duration")));

        CssMetaData<RXHoldButton, ?> feedback = metaData(button, "-rx-feedback-duration");
        assertTrue(feedback.isSettable(button));
        button.feedbackDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        assertFalse(feedback.isSettable(button));
    }

    @Test
    public void fireDispatchesUnlessDisabled() {
        RXHoldButton button = new RXHoldButton("Hold");
        AtomicInteger fired = countActions(button);

        button.fire();
        assertEquals(1, fired.get());
        button.executeAccessibleAction(AccessibleAction.FIRE);
        assertEquals(2, fired.get());

        button.setDisable(true);
        button.fire();
        assertEquals(2, fired.get());
    }

    // ==================== Skin Structure ====================

    @Test
    public void captionsAreThreeUnlabeledNodesWithOnlyTheIdleOneShowing() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));

        Label idle = caption(button, ".caption");
        Label holding = caption(button, ".holding-caption");
        Label completed = caption(button, ".completed-caption");
        for (Label caption : List.of(idle, holding, completed)) {
            assertFalse(caption.getStyleClass().contains("label"));
            assertFalse(caption.isMnemonicParsing());
        }
        assertTrue(idle.isVisible() && idle.getParent().isVisible());
        assertFalse(holding.isVisible());
        assertFalse(completed.getParent().isVisible());
        assertEquals("Hold", idle.getText());
        assertEquals("Keep holding…", holding.getText());
        assertEquals("Done", completed.getText());

        button.setHoldingText(null);
        button.setCompletedText(null);
        assertNull(holding.getText());
        assertNull(completed.getText());
        button.getSkin().dispose();
    }

    @Test
    public void accessibleTextPrefersTheAccessibleTextOverTheFace() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));

        assertEquals("Hold", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.setAccessibleText("Delete the file");
        assertEquals("Delete the file", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
        button.getSkin().dispose();
    }

    @Test
    public void resetBeforeTheSkinLeavesNoStaleSignal() {
        RXHoldButton button = new RXHoldButton("Hold");
        button.resetFeedback();
        assertTrue(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));

        laidOut(button);
        assertFalse(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));

        button.resetFeedback();
        assertFalse(button.getProperties().containsKey(FeedbackSignals.HOLD_RESET));
        button.getSkin().dispose();
        button.getSkin().dispose();
        assertTrue(button.getChildrenUnmodifiable().isEmpty());
    }

    // ==================== Sizing ====================

    @Test
    public void everyFaceCountsTowardTheSize() {
        assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT, button -> {
        });
    }

    @Test
    public void wrappedTextCountsUnderAWidthConstraint() {
        RXHoldButton wrapped = laidOut(withContentOn(new RXHoldButton(), Face.IDLE,
                LONG_TEXT + " " + LONG_TEXT));
        wrapped.setWrapText(true);
        assertTrue(wrapped.prefHeight(80.0) > wrapped.prefHeight(160.0),
                "a narrower width wraps into more lines");
        wrapped.getSkin().dispose();

        assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT + " " + LONG_TEXT,
                button -> button.setWrapText(true));
    }

    @Test
    public void everyContentDisplayKeepsTheFacesEquivalent() {
        for (ContentDisplay display : List.of(ContentDisplay.LEFT, ContentDisplay.TOP,
                ContentDisplay.GRAPHIC_ONLY)) {
            assertSameSizeWhicheverFaceHoldsTheContent(LONG_TEXT, button -> button.setContentDisplay(display));
        }
    }

    @Test
    public void labelPaddingPadsEveryFace() {
        for (Face face : Face.values()) {
            RXHoldButton plain = laidOut(withContentOn(new RXHoldButton(), face));
            RXHoldButton padded = withContentOn(new RXHoldButton(), face);
            padded.setStyle("-fx-label-padding: 10;");
            laidOut(padded);

            assertEquals(plain.prefWidth(-1) + 20.0, padded.prefWidth(-1), 0.01, face.name());
            assertEquals(plain.prefHeight(-1) + 20.0, padded.prefHeight(-1), 0.01, face.name());
            plain.getSkin().dispose();
            padded.getSkin().dispose();
        }
    }

    @Test
    public void graphicChangesResizeTheButtonAtOnce() {
        RXHoldButton button = laidOut(new RXHoldButton(null));
        button.setHoldingText(null);
        button.setCompletedText(null);
        double empty = button.prefWidth(-1);

        Region icon = sizedRegion(40.0);
        button.setCompletedGraphic(icon);
        assertEquals(empty + 40.0, button.prefWidth(-1), 0.01);

        icon.setPrefWidth(90.0);
        assertEquals(empty + 90.0, button.prefWidth(-1), 0.01);

        Region holdingIcon = sizedRegion(120.0);
        button.setHoldingGraphic(holdingIcon);
        assertEquals(empty + 120.0, button.prefWidth(-1), 0.01);

        button.setHoldingGraphic(null);
        assertEquals(empty + 90.0, button.prefWidth(-1), 0.01);
        button.getSkin().dispose();
    }

    @Test
    public void iconOnlyButtonIsSizedByItsIdleFace() {
        RXHoldButton iconOnly = new RXHoldButton(null, sizedRegion(24.0));
        iconOnly.setHoldingText(null);
        iconOnly.setCompletedText(null);
        laidOut(iconOnly);
        RXHoldButton withDefaults = laidOut(new RXHoldButton(null, sizedRegion(24.0)));

        assertTrue(withDefaults.prefWidth(-1) > iconOnly.prefWidth(-1),
                "the default texts widen a button that keeps them");
        Region idle = caption(iconOnly, ".caption");
        assertEquals(iconOnly.snappedLeftInset() + idle.prefWidth(-1) + iconOnly.snappedRightInset(),
                iconOnly.prefWidth(-1), 0.01);
        iconOnly.getSkin().dispose();
        withDefaults.getSkin().dispose();
    }

    // ==================== Mouse Hold ====================

    @Test
    public void holdingToTheThresholdFiresOnce() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            double prefWidth = button.prefWidth(-1);
            double prefHeight = button.prefHeight(-1);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);

            assertHolding(button, true);
            assertTrue(button.isArmed());
            assertEquals(prefWidth, button.prefWidth(-1), 0.01);
            assertEquals(prefHeight, button.prefHeight(-1), 0.01);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(1, fired.get());
            assertHolding(button, false);
            assertFalse(button.isArmed());
            assertTrue(button.getPseudoClassStates().contains(COMPLETED));

            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals(1, fired.get());
            assertFalse(button.getPseudoClassStates().contains(COMPLETED));
            button.getSkin().dispose();
        });
    }

    @Test
    public void releasingEarlyCancelsWithoutFiring() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            double prefWidth = button.prefWidth(-1);
            double prefHeight = button.prefHeight(-1);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);

            assertHolding(button, false);
            assertFalse(button.isArmed());
            assertEquals(prefWidth, button.prefWidth(-1), 0.01);
            assertEquals(prefHeight, button.prefHeight(-1), 0.01);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void onlyAPlainPrimaryPressStartsAndOnlyItsReleaseEnds() throws InterruptedException {
        onFx(() -> {
            RXHoldButton button = holdButton();

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY);
            assertHolding(button, false);
            button.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0.0, 0.0, 0.0, 0.0,
                    MouseButton.PRIMARY, 1, true, false, false, false,
                    true, false, false, false, false, false, null));
            assertHolding(button, false);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.SECONDARY);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.SECONDARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void movingOutCancelsAndMovingBackDoesNotResume() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_EXITED, MouseButton.NONE);
            assertHolding(button, false);
            mouse(button, MouseEvent.MOUSE_ENTERED, MouseButton.NONE);
            assertHolding(button, false);
            // The primary button is still down: SPACE does not start a hold.
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals(0, fired.get());
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void aCompletedHoldSurvivesDraggingOutAndBack() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_EXITED, MouseButton.NONE);
            mouse(button, MouseEvent.MOUSE_ENTERED, MouseButton.NONE);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals(1, fired.get());
            button.getSkin().dispose();
        });
    }

    // ==================== Keyboard Hold ====================

    @Test
    public void spaceHoldFiresOnceAndIgnoresAutoRepeat() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            assertTrue(key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertHolding(button, true);
            // Auto-repeat mid-hold is swallowed and changes nothing.
            assertTrue(key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertHolding(button, true);
            assertTrue(button.isArmed());
            // A release of another activation key is not the owner's.
            key(button, KeyEvent.KEY_RELEASED, KeyCode.ENTER);
            assertHolding(button, true);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(1, fired.get());
            // Auto-repeat after completion starts nothing.
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, fired.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void earlyKeyUpCancelsAndEnterFollowsThePlatform() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertHolding(button, false);

            assertEquals(!MAC, key(button, KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            assertHolding(button, !MAC);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.ENTER);

            assertFalse(key(button, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE,
                    false, true, false, false)), "a modified press stays unconsumed");
            assertHolding(button, false);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void mouseAndKeyboardHoldsDoNotInterfere() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertHolding(button, true);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(1, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    // ==================== Accessibility ====================

    @Test
    public void theHoldingFaceNamesTheButtonWhileHeld() throws InterruptedException {
        onFx(() -> {
            RXHoldButton button = holdButton();

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertEquals("Keep holding\u2026", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.setHoldingText(null);
            assertEquals("Hold", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals("Hold", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.getSkin().dispose();
        });
    }

    // ==================== Untimed Activation ====================

    @Test
    public void fireDuringAHoldFiresOnceAndCancelsTheHold() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            button.fire();
            assertEquals(1, fired.get());
            assertHolding(button, false);
            // The owner key is still down: its auto-repeat does not restart.
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(1, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void aConsumedActionEventFromTheGraphicLeavesTheHoldRunning() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            Region graphic = sizedRegion(16.0);
            graphic.addEventHandler(ActionEvent.ACTION, ActionEvent::consume);
            RXHoldButton button = holdButton();
            button.setGraphic(graphic);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            graphic.fireEvent(new ActionEvent());
            assertEquals(0, fired.get());
            assertHolding(button, true);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(1, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void anActionEventBubblingFromTheGraphicCancelsTheHold() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        AtomicBoolean armedInOnAction = new AtomicBoolean(true);
        onFx(() -> {
            Region graphic = sizedRegion(16.0);
            RXHoldButton button = holdButton();
            button.setGraphic(graphic);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            button.setOnAction(event -> armedInOnAction.set(button.isArmed()));
            ref.set(button);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            graphic.fireEvent(new ActionEvent());
            assertEquals(1, fired.get());
            assertFalse(armedInOnAction.get());
            assertHolding(button, false);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(1, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    @Test
    public void anOutsideDisarmCancelsAndAnOutsideArmStartsNothing() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);

            button.arm();
            assertHolding(button, false);
            button.disarm();

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            button.disarm();
            assertHolding(button, false);
            // Still down: a new press of the owner cannot arrive, and SPACE
            // does not start a second hold.
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    // ==================== Structural Interruptions ====================

    @Test
    public void disablingRemovingOrDisposingCancelsWithoutFiring() throws InterruptedException {
        AtomicInteger fired = new AtomicInteger();
        AtomicReference<List<RXHoldButton>> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton disabled = holdButton();
            RXHoldButton removed = holdButton();
            RXHoldButton disposed = holdButton();
            for (RXHoldButton button : List.of(disabled, removed, disposed)) {
                button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
                key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            }
            disabled.setDisable(true);
            ((StackPane) removed.getParent()).getChildren().clear();
            disposed.getSkin().dispose();
            for (RXHoldButton button : List.of(disabled, removed)) {
                assertHolding(button, false);
            }
            for (RXHoldButton button : List.of(disabled, removed, disposed)) {
                assertFalse(button.getPseudoClassStates().contains(HOLDING));
                assertFalse(button.isArmed());
            }
            ref.set(List.of(disabled, removed));
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get());
            RXHoldButton disabled = ref.get().get(0);
            disabled.setDisable(false);
            // Structural: the key's release is not awaited.
            key(disabled, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(disabled, true);
            for (RXHoldButton button : ref.get()) {
                button.getSkin().dispose();
            }
        });
    }

    @Test
    public void hidingAnAncestorCancelsAHold() throws InterruptedException {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = new RXHoldButton("Hold");
            button.setHoldDuration(SHORT_HOLD);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            StackPane holder = new StackPane(button);
            Stage stage = new Stage();
            stage.setScene(new Scene(holder, 300.0, 200.0));
            stage.show();
            stageRef.set(stage);
            ref.set(button);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            holder.setVisible(false);
            assertHolding(button, false);
            assertFalse(button.isArmed());
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get());
            ref.get().getSkin().dispose();
            stageRef.get().hide();
        });
    }

    @Test
    public void losingFocusCancelsAKeyHoldButNotAMouseHold() throws InterruptedException {
        onFx(() -> {
            FocusableHoldButton button = new FocusableHoldButton();
            button.setHoldDuration(SHORT_HOLD);
            laidOut(button);

            button.focus(true);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, true);
            button.focus(false);
            assertHolding(button, false);
            // Structural: the lost key's release is not awaited.
            button.focus(true);
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, true);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            button.focus(false);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    // Without a focused window Node.focused never turns true, so the test
    // drives it through the protected setter.
    private static final class FocusableHoldButton extends RXHoldButton {

        FocusableHoldButton() {
            super("Hold");
        }

        void focus(boolean value) {
            setFocused(value);
        }
    }

    // ==================== Re-entrancy ====================

    @Test
    public void onActionMayDisableRemoveOrReskinTheButton() throws InterruptedException {
        AtomicReference<List<RXHoldButton>> ref = new AtomicReference<>();
        AtomicReference<Skin<?>> originalSkin = new AtomicReference<>();
        AtomicInteger actions = new AtomicInteger();
        // Timeline handlers report exceptions to the uncaught handler instead
        // of throwing, so the test records them there.
        List<Throwable> uncaught = new ArrayList<>();
        AtomicReference<Thread.UncaughtExceptionHandler> previous = new AtomicReference<>();
        onFx(() -> {
            previous.set(Thread.currentThread().getUncaughtExceptionHandler());
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
        });
        try {
            runReentrantHolds(ref, originalSkin, actions, uncaught);
        } finally {
            onFx(() -> Thread.currentThread().setUncaughtExceptionHandler(previous.get()));
        }
    }

    private static void runReentrantHolds(AtomicReference<List<RXHoldButton>> ref,
                                          AtomicReference<Skin<?>> originalSkin,
                                          AtomicInteger actions,
                                          List<Throwable> uncaught) throws InterruptedException {
        onFx(() -> {
            RXHoldButton disabling = holdButton();
            disabling.setOnAction(event -> disabling.setDisable(true));
            RXHoldButton removing = holdButton();
            removing.setOnAction(event -> ((StackPane) removing.getParent()).getChildren().clear());
            RXHoldButton reskinning = holdButton();
            // JavaFX 17 ignores setSkin for a skin of the installed skin's
            // class, so the replacement is an anonymous subclass.
            reskinning.setOnAction(event -> reskinning.setSkin(new RXHoldButtonSkin(reskinning) {
            }));
            originalSkin.set(reskinning.getSkin());
            List<RXHoldButton> buttons = List.of(disabling, removing, reskinning);
            for (RXHoldButton button : buttons) {
                button.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());
                mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            }
            ref.set(buttons);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(List.of(), uncaught);
            assertEquals(3, actions.get());
            List<RXHoldButton> buttons = ref.get();
            assertTrue(buttons.get(0).isDisabled());
            assertNull(buttons.get(1).getParent());
            assertNotSame(originalSkin.get(), buttons.get(2).getSkin());
            for (RXHoldButton button : buttons) {
                mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
                button.setDisable(false);
                // Back to idle with no stale owner: a new press holds again.
                mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
                assertTrue(button.getPseudoClassStates().contains(HOLDING));
                button.getSkin().dispose();
            }
        });
    }

    // ==================== Hold Duration ====================

    @Test
    public void unusableHoldDurationsFallBackToTheDefault() throws InterruptedException {
        AtomicReference<List<RXHoldButton>> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            List<Duration> unusable = Arrays.asList(null, Duration.ZERO, Duration.millis(-5.0),
                    Duration.UNKNOWN, Duration.INDEFINITE);
            List<RXHoldButton> buttons = new ArrayList<>();
            for (Duration duration : unusable) {
                RXHoldButton button = holdButton();
                button.setHoldDuration(duration);
                button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
                mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
                buttons.add(button);
            }
            ref.set(buttons);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(0, fired.get(), "the default hold is far longer than the wait");
            for (RXHoldButton button : ref.get()) {
                assertHolding(button, true);
                button.getSkin().dispose();
            }
        });
    }

    @Test
    public void durationChangesAndResetsDuringAHoldDoNotAffectIt() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            button.setHoldDuration(Duration.seconds(30.0));
            button.resetFeedback();
            assertHolding(button, true);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            assertEquals(1, fired.get());
            ref.get().getSkin().dispose();
        });
    }

    // ==================== Completed Feedback ====================

    @Test
    public void theDefaultFeedbackStaysAndLocksUntilReset() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = feedbackButton(RXHoldButton.DEFAULT_FEEDBACK_DURATION);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertCompletedFace(button, true);
            assertEquals("Done", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            // Locked: neither a press nor its release does anything.
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertFalse(button.isArmed());
            assertFalse(button.getPseudoClassStates().contains(HOLDING));
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertCompletedFace(button, true);
            assertEquals(1, fired.get());

            button.resetFeedback();
            assertCompletedFace(button, false);
            assertHolding(button, false);
            assertEquals(1, fired.get(), "a reset fires nothing");
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void anEmptyCompletedFaceStillNamesTheButton() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setCompletedText(null);
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertCompletedFace(button, true);
            Label completed = caption(button, ".completed-caption");
            assertNull(completed.getText());
            assertNull(completed.getGraphic());
            assertEquals("Hold", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.getSkin().dispose();
        });
    }

    @Test
    public void aFiniteFeedbackEndsOnItsOwn() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicReference<Double> prefWidth = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.millis(600.0));
            button.setAnimation(new AnimFade());
            button.setAnimationDuration(Duration.millis(60.0));
            ref.set(button);
            prefWidth.set(button.prefWidth(-1));
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertEquals(prefWidth.get(), button.prefWidth(-1), 0.01);
        });
        Thread.sleep(350L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertCompletedFace(button, true);
            assertEquals(prefWidth.get(), button.prefWidth(-1), 0.01);
        });
        Thread.sleep(900L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertCompletedFace(button, false);
            assertHolding(button, false);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void theFeedbackTimerDoesNotWaitForTheSwap() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        RecordingAnimation recording = new RecordingAnimation();
        onFx(() -> {
            // The swap never finishes on its own: only a timer counting from
            // the threshold can end the feedback.
            RXHoldButton button = feedbackButton(Duration.millis(150.0));
            button.setAnimation(recording);
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(700L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertFalse(button.getPseudoClassStates().contains(COMPLETED));
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
            button.getSkin().dispose();
        });
    }

    @Test
    public void disposingDuringTheFeedbackLeavesNothingBehind() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.millis(300.0));
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS / 2);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertTrue(button.getPseudoClassStates().contains(COMPLETED));
            button.getSkin().dispose();
            assertFalse(button.getPseudoClassStates().contains(COMPLETED));
            assertTrue(button.getChildrenUnmodifiable().isEmpty());
        });
    }

    @Test
    public void aVetoedCompletionShowsNoCompletedFace() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        RecordingAnimation recording = new RecordingAnimation();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setAnimation(recording);
            button.armedProperty().addListener((observable, was, armed) -> {
                if (!armed) {
                    button.setDisable(true);
                }
            });
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(0, fired.get());
            assertCompletedFace(button, false);
            assertEquals(1, recording.contexts.size(), "cut back, with no swap back to play");
            // Back to idle with no stale owner: a new press holds again.
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            button.setDisable(false);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void aFeedbackEndingBeforeTheReleaseStillWaitsForIt() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.millis(100.0));
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertCompletedFace(button, false);
            // The owner is still down: another input cannot start a hold.
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void aZeroFeedbackShowsNoCompletedFace() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.ZERO);
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertTrue(button.getPseudoClassStates().contains(COMPLETED));
            assertFalse(caption(button, ".completed-caption").getParent().isVisible());
            assertTrue(caption(button, ".caption").isVisible());
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertFalse(button.getPseudoClassStates().contains(COMPLETED));
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void theCompletedSwapStartsFromTheHoldingFaceAndReturnsToTheIdleFace()
            throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        RecordingAnimation recording = new RecordingAnimation();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setAnimation(recording);
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(1, recording.contexts.size());
            Node outgoing = recording.contexts.get(0).getCurrentPage();
            assertSame(caption(button, ".holding-caption").getParent(), outgoing);
            assertTrue(recording.holdingShownOnStart.get(0), "the outgoing page shows the holding face");
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);

            // A reset mid-swap ends it, then swaps back to the idle face.
            button.resetFeedback();
            assertFalse(button.getPseudoClassStates().contains(COMPLETED));
            assertTrue(caption(button, ".caption").isVisible());
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertSame(outgoing, recording.contexts.get(1).getNextPage());
            assertFalse(recording.holdingShownOnStart.get(1), "the incoming page shows the idle face");

            // A press while the swap back plays ends it at once.
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertEquals(2, recording.jumpToEndCalls);
            assertHolding(button, true);
            assertTrue(caption(button, ".holding-caption").getParent().isVisible());
            button.getSkin().dispose();
        });
    }

    @Test
    public void aZeroAnimationDurationCutsDirectly() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        RecordingAnimation recording = new RecordingAnimation();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setAnimation(recording);
            button.setAnimationDuration(Duration.ZERO);
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertCompletedFace(button, true);
            button.resetFeedback();
            assertCompletedFace(button, false);
            assertTrue(recording.contexts.isEmpty());
            button.getSkin().dispose();
        });
    }

    @Test
    public void aKeyHeldAcrossTheUnlockDoesNotStartAHold() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);

            assertTrue(key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE), "a locked press is consumed");
            assertFalse(button.isArmed());
            button.resetFeedback();
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, false);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, fired.get());
            key(button, KeyEvent.KEY_PRESSED, KeyCode.SPACE);
            assertHolding(button, true);
            key(button, KeyEvent.KEY_RELEASED, KeyCode.SPACE);
            assertEquals(1, fired.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void aLockedPressReleasedAfterTheResetFiresNothing() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            button.resetFeedback();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals(1, fired.get(), "the locked press is swallowed");

            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            assertHolding(button, true);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(1, fired.get(), "released before the threshold");
            // Complete again, then fire while the feedback locks the button.
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertEquals(2, fired.get());
            button.fire();
            assertEquals(3, fired.get());
            assertCompletedFace(button, true);
            button.getSkin().dispose();
        });
    }

    @Test
    public void disablingAfterCompletionKeepsTheCompletedFace() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setOnAction(event -> button.setDisable(true));
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertTrue(button.isDisabled());
            assertCompletedFace(button, true);
            // A reset works on a disabled button too.
            button.resetFeedback();
            assertCompletedFace(button, false);
            button.getSkin().dispose();
        });
    }

    @Test
    public void feedbackDurationIsStyleable() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));

        button.setStyle("-rx-feedback-duration: 2s;");
        button.getParent().applyCss();
        assertEquals(Duration.seconds(2.0), button.getFeedbackDuration());
        // A value set from code first: a failed parse would leave it in
        // place, instead of falling back to the INDEFINITE default.
        button.setStyle(null);
        button.setFeedbackDuration(Duration.ZERO);
        button.setStyle("-rx-feedback-duration: indefinite;");
        button.getParent().applyCss();
        assertEquals(Duration.INDEFINITE, button.getFeedbackDuration());
        button.getSkin().dispose();
    }

    // ==================== Progress Fill ====================

    @Test
    public void theFillSweepsWithTheHoldAndStaysFullOnCompletion() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        AtomicReference<Double> prefWidth = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = feedbackButton(Duration.INDEFINITE);
            button.setHoldDuration(Duration.millis(600.0));
            ref.set(button);
            assertFalse(fillLayer(button).isVisible(), "hidden at rest");
            prefWidth.set(button.prefWidth(-1));
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(150L);
        // The press swapped captions; settle that layout, so what follows
        // shows whether the fill itself asks for one.
        onFx(() -> ref.get().getParent().layout());
        Thread.sleep(150L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertTrue(fillLayer(button).isVisible());
            assertFalse(button.isNeedsLayout(), "progress reshapes the clip without a layout pass");
            assertEquals(prefWidth.get(), button.prefWidth(-1), 0.01, "the fill never sizes the button");
            double clipWidth = fillClip(button).getWidth();
            double areaWidth = fillRegion(button).getWidth();
            assertTrue(clipWidth > 0.0 && clipWidth < areaWidth,
                    "mid-hold the fill is partial, was " + clipWidth + " of " + areaWidth);
        });
        Thread.sleep(700L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertEquals(fillRegion(button).getWidth(), fillClip(button).getWidth(), 0.01);
            assertEquals(prefWidth.get(), button.prefWidth(-1), 0.01);
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertTrue(fillLayer(button).isVisible(), "the completed face keeps the fill");
            button.resetFeedback();
            assertFalse(fillLayer(button).isVisible());
            button.getSkin().dispose();
        });
    }

    @Test
    public void aCancelledHoldRewindsTheFill() throws InterruptedException {
        AtomicReference<RXHoldButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton button = holdButton();
            button.setHoldDuration(Duration.millis(600.0));
            ref.set(button);
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(300L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            mouse(button, MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY);
            assertTrue(fillLayer(button).isVisible(), "the fill rewinds rather than vanishing");
        });
        Thread.sleep(PAST_HOLD_MS);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertFalse(fillLayer(button).isVisible());
            mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
        });
        Thread.sleep(300L);
        onFx(() -> {
            RXHoldButton button = ref.get();
            assertTrue(fillLayer(button).isVisible());
            // A structural interruption clears the fill at once, no rewind.
            button.setDisable(true);
            assertFalse(fillLayer(button).isVisible());
            button.getSkin().dispose();
        });
    }

    @Test
    public void changingTheFillAnimationRebuildsTheClip() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));
        Region region = fillRegion(button);
        Node defaultClip = region.getClip();
        assertInstanceOf(Rectangle.class, defaultClip);

        button.setFillAnimation(FillAnimation.CIRCLE);
        Node circleClip = fillRegion(button).getClip();
        assertNotSame(defaultClip, circleClip);

        button.setFillAnimation(null);
        assertNotSame(circleClip, fillRegion(button).getClip());
        // Left to right, not another rectangle preset: at rest it sits on
        // the left edge (right to left would sit on the right one).
        assertEquals(0.0, fillClip(button).getX(), 0.01);
        Region layer = fillLayer(button);
        button.getSkin().dispose();
        assertNull(region.getClip());
        assertNull(layer.getClip());
    }

    @Test
    public void fillAnimationIsStyleableByKeyword() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));
        button.setStyle("-rx-fill-animation: right-to-left;");
        button.getParent().applyCss();
        assertSame(FillAnimation.RIGHT_TO_LEFT, button.getFillAnimation());
        button.getSkin().dispose();
    }

    @Test
    public void theFillMirrorsInRightToLeft() throws InterruptedException {
        AtomicReference<List<RXHoldButton>> ref = new AtomicReference<>();
        onFx(() -> {
            RXHoldButton ltr = feedbackButton(Duration.INDEFINITE);
            RXHoldButton rtl = feedbackButton(Duration.INDEFINITE);
            rtl.getParent().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            rtl.getParent().layout();
            for (RXHoldButton button : List.of(ltr, rtl)) {
                button.setHoldDuration(Duration.millis(600.0));
                mouse(button, MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY);
            }
            ref.set(List.of(ltr, rtl));
        });
        Thread.sleep(300L);
        onFx(() -> {
            RXHoldButton ltr = ref.get().get(0);
            RXHoldButton rtl = ref.get().get(1);
            // A clip is not in the scene graph: its geometry is in the
            // clipped node's local coordinates.
            Bounds ltrFill = fillRegion(ltr).localToScene(fillClip(ltr).getBoundsInLocal());
            Bounds ltrArea = fillRegion(ltr).localToScene(fillRegion(ltr).getLayoutBounds());
            assertEquals(ltrArea.getMinX(), ltrFill.getMinX(), 0.5, "left to right starts on the left");
            Bounds rtlFill = fillRegion(rtl).localToScene(fillClip(rtl).getBoundsInLocal());
            Bounds rtlArea = fillRegion(rtl).localToScene(fillRegion(rtl).getLayoutBounds());
            assertEquals(rtlArea.getMaxX(), rtlFill.getMaxX(), 0.5, "right to left starts on the right");
            assertTrue(rtlFill.getWidth() < rtlArea.getWidth());
            ltr.getSkin().dispose();
            rtl.getSkin().dispose();
        });
    }

    // ==================== Theme ====================

    @Test
    public void theThemeCentersTheContent() {
        RXHoldButton button = laidOut(new RXHoldButton("Hold"));
        assertEquals(Pos.CENTER, button.getAlignment());
        button.getSkin().dispose();
    }

    // ==================== Helpers ====================

    private enum Face {
        IDLE, HOLDING, COMPLETED
    }

    // The same content placed on each face in turn must size the button
    // identically: every face is measured, visible or not.
    private static void assertSameSizeWhicheverFaceHoldsTheContent(String text,
                                                                   Consumer<RXHoldButton> setup) {
        RXHoldButton[] buttons = new RXHoldButton[Face.values().length];
        for (Face face : Face.values()) {
            RXHoldButton button = withContentOn(new RXHoldButton(), face, text);
            setup.accept(button);
            buttons[face.ordinal()] = laidOut(button);
        }
        RXHoldButton reference = buttons[0];
        Label idle = caption(reference, ".caption");
        assertEquals(reference.snappedLeftInset() + reference.snapSizeX(idle.prefWidth(-1))
                + reference.snappedRightInset(), reference.prefWidth(-1), 0.01);
        assertEquals(reference.snappedTopInset() + reference.snapSizeY(idle.prefHeight(-1))
                + reference.snappedBottomInset(), reference.prefHeight(-1), 0.01);
        for (RXHoldButton button : buttons) {
            assertEquals(reference.prefWidth(-1), button.prefWidth(-1), 0.01);
            assertEquals(reference.prefHeight(-1), button.prefHeight(-1), 0.01);
            assertEquals(reference.minWidth(-1), button.minWidth(-1), 0.01);
            assertEquals(reference.minHeight(-1), button.minHeight(-1), 0.01);
            for (double width : new double[]{80.0, 160.0}) {
                assertEquals(reference.prefHeight(width), button.prefHeight(width), 0.01);
            }
        }
        for (RXHoldButton button : buttons) {
            button.getSkin().dispose();
        }
    }

    private static RXHoldButton withContentOn(RXHoldButton button, Face face) {
        return withContentOn(button, face, LONG_TEXT);
    }

    private static RXHoldButton withContentOn(RXHoldButton button, Face face, String text) {
        button.setText(null);
        button.setHoldingText(null);
        button.setCompletedText(null);
        Region icon = sizedRegion(30.0);
        switch (face) {
            case IDLE:
                button.setText(text);
                button.setGraphic(icon);
                break;
            case HOLDING:
                button.setHoldingText(text);
                button.setHoldingGraphic(icon);
                break;
            default:
                button.setCompletedText(text);
                button.setCompletedGraphic(icon);
                break;
        }
        return button;
    }

    private static Region fillLayer(RXHoldButton button) {
        return assertInstanceOf(Region.class, button.lookup(".fill-layer"));
    }

    private static Region fillRegion(RXHoldButton button) {
        return assertInstanceOf(Region.class, button.lookup(".fill-region"));
    }

    private static Rectangle fillClip(RXHoldButton button) {
        return assertInstanceOf(Rectangle.class, fillRegion(button).getClip());
    }

    private static RXHoldButton feedbackButton(Duration feedback) {
        RXHoldButton button = new RXHoldButton("Hold");
        button.setHoldDuration(SHORT_HOLD);
        button.setFeedbackDuration(feedback);
        return laidOut(button);
    }

    private static void assertCompletedFace(RXHoldButton button, boolean completed) {
        assertEquals(completed, button.getPseudoClassStates().contains(COMPLETED));
        assertEquals(completed, caption(button, ".completed-caption").getParent().isVisible());
        assertEquals(!completed, caption(button, ".caption").getParent().isVisible());
    }

    private static RXHoldButton holdButton() {
        RXHoldButton button = new RXHoldButton("Hold");
        button.setHoldDuration(SHORT_HOLD);
        // Leaves the completed feedback as the owner is released.
        button.setFeedbackDuration(Duration.ZERO);
        return laidOut(button);
    }

    private static void assertHolding(RXHoldButton button, boolean holding) {
        assertEquals(holding, button.getPseudoClassStates().contains(HOLDING));
        assertEquals(holding, caption(button, ".holding-caption").isVisible());
        assertEquals(!holding, caption(button, ".caption").isVisible());
    }

    private static void mouse(RXHoldButton button, EventType<MouseEvent> type, MouseButton which) {
        boolean pressed = type == MouseEvent.MOUSE_PRESSED;
        MouseEvent event = new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, which, 1,
                false, false, false, false,
                pressed && which == MouseButton.PRIMARY, false,
                pressed && which == MouseButton.SECONDARY,
                false, false, false, null);
        button.fireEvent(event);
    }

    // Returns whether the button consumed the event: a consumed event never
    // bubbles up to the parent.
    private static boolean key(RXHoldButton button, EventType<KeyEvent> type, KeyCode code) {
        return key(button, new KeyEvent(type, "", "", code, false, false, false, false));
    }

    private static boolean key(RXHoldButton button, KeyEvent event) {
        AtomicBoolean bubbled = new AtomicBoolean();
        Node parent = button.getParent();
        EventHandler<KeyEvent> probe = seen -> bubbled.set(true);
        if (parent != null) {
            parent.addEventHandler(event.getEventType(), probe);
        }
        button.fireEvent(event);
        if (parent != null) {
            parent.removeEventHandler(event.getEventType(), probe);
        }
        return !bubbled.get();
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    private static Region sizedRegion(double width) {
        Region region = new Region();
        region.setPrefSize(width, 20.0);
        region.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
    }

    private static RXHoldButton laidOut(RXHoldButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 400.0, 200.0);
        root.resize(400.0, 200.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static Label caption(RXHoldButton button, String selector) {
        Node caption = button.lookup(selector);
        assertNotNull(caption, selector);
        return assertInstanceOf(Label.class, caption);
    }

    private static AtomicInteger countActions(RXHoldButton button) {
        AtomicInteger fired = new AtomicInteger();
        button.addEventHandler(ActionEvent.ACTION, event -> fired.incrementAndGet());
        return fired;
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metaData, String property) {
        return metaData.stream().anyMatch(item -> property.equals(item.getProperty()));
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<RXHoldButton, ?> metaData(RXHoldButton button, String property) {
        for (CssMetaData<? extends Styleable, ?> item : button.getCssMetaData()) {
            if (property.equals(item.getProperty())) {
                return (CssMetaData<RXHoldButton, ?>) item;
            }
        }
        throw new AssertionError(property + " is not styleable");
    }

    // ==================== Recording animation ====================

    // Never finishes on its own: a swap only ends through jumpToEnd or dispose.
    // Records, as each swap starts, whether the holding caption shows.
    private static class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        final List<Boolean> holdingShownOnStart = new ArrayList<>();
        int jumpToEndCalls;

        private Animation running;
        private Runnable finishAction;

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
            StackPane firstPage = context.getCurrentPage().lookup(".holding-caption") != null
                    ? (StackPane) context.getCurrentPage()
                    : (StackPane) context.getNextPage();
            holdingShownOnStart.add(firstPage.lookup(".holding-caption").isVisible());
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            incoming.setVisible(true);
            finishAction = () -> {
                outgoing.setVisible(false);
                incoming.setVisible(true);
            };
            running = new PauseTransition(Duration.seconds(30.0));
            return running;
        }

        @Override
        public void jumpToEnd() {
            jumpToEndCalls++;
            stop();
            if (finishAction != null) {
                finishAction.run();
                finishAction = null;
            }
        }

        @Override
        public void clearEffects(TransitionContext context) {
        }

        @Override
        public void dispose() {
            stop();
        }

        private void stop() {
            if (running != null) {
                running.stop();
                running = null;
            }
        }
    }
}

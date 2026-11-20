package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTaskButton.Run;
import io.github.leewyatt.rxcontrols.RXTaskButton.State;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.feedback.RingIndicator;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXTaskButtonSkin;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.text.Font;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTaskButton skin: the four faces and the size they give the
 * button, the face shown in every state, animated swaps and direct cuts, the
 * built-in indicators, the hand-wired button behavior with its arming rule,
 * the accessible name and the skin lifecycle.
 */
public class RXTaskButtonSkinTest {

    private static final Duration LONG = Duration.seconds(30.0);
    private static final KeyCodeCombination ENTER = new KeyCodeCombination(KeyCode.ENTER);
    private static final KeyCodeCombination ESCAPE = new KeyCodeCombination(KeyCode.ESCAPE);
    private static final List<String> FACES =
            List.of(".idle-face", ".pending-face", ".succeeded-face", ".failed-face");

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
        // Hiding the last test stage must not shut the toolkit down.
        Platform.setImplicitExit(false);
    }

    // ==================== Structure ====================

    @Test
    public void defaultSkinBuildsARippleLayerBelowFourFaces() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());

            assertInstanceOf(RXTaskButtonSkin.class, button.getSkin());
            assertEquals(2, button.getChildrenUnmodifiable().size());
            assertInstanceOf(RippleLayer.class, button.getChildrenUnmodifiable().get(0));
            Node contentPane = button.getChildrenUnmodifiable().get(1);
            assertTrue(contentPane.getStyleClass().contains("content-pane"));
            for (String selector : FACES) {
                Region face = face(button, selector);
                assertTrue(face.getParent().getStyleClass().contains("page"), selector);
                assertSame(contentPane, face.getParent().getParent(), selector);
                assertNotNull(face.lookup(".indicator"), selector);
                assertFalse(caption(button, selector).getStyleClass().contains("label"), selector);
            }
            assertFalse(button.isMnemonicParsing());
            button.getSkin().dispose();
        });
    }

    @Test
    public void captionsShowTheirOwnTextAndGraphicInTheButtonsAppearance() throws InterruptedException {
        onFx(() -> {
            Label graphic = new Label("g");
            RXTaskButton button = laidOut(new RXTaskButton("Save", graphic));
            Label idle = caption(button, ".idle-face");
            Label pending = caption(button, ".pending-face");
            Label succeeded = caption(button, ".succeeded-face");
            Label failed = caption(button, ".failed-face");

            assertEquals("Save", idle.getText());
            assertSame(graphic, idle.getGraphic());
            // Nothing is borrowed from the button text.
            assertNull(pending.getText());
            assertNull(succeeded.getText());
            assertNull(failed.getText());
            assertNull(pending.getGraphic());

            button.setPendingText("Saving");
            button.setSucceededText("Saved");
            button.setFailedText("Failed");
            Label pendingGraphic = new Label("p");
            Label succeededGraphic = new Label("s");
            Label failedGraphic = new Label("f");
            button.setPendingGraphic(pendingGraphic);
            button.setSucceededGraphic(succeededGraphic);
            button.setFailedGraphic(failedGraphic);
            assertEquals("Saving", pending.getText());
            assertEquals("Saved", succeeded.getText());
            assertEquals("Failed", failed.getText());
            assertSame(pendingGraphic, pending.getGraphic());
            assertSame(succeededGraphic, succeeded.getGraphic());
            assertSame(failedGraphic, failed.getGraphic());
            assertSame(graphic, idle.getGraphic());

            Font font = Font.font(20.0);
            button.setFont(font);
            button.setTextFill(Color.RED);
            for (Label caption : List.of(idle, pending, succeeded, failed)) {
                assertEquals(font, caption.getFont());
                assertEquals(Color.RED, caption.getTextFill());
                assertFalse(caption.isMnemonicParsing());
            }
            button.getSkin().dispose();
        });
    }

    // ==================== Size ====================

    @Test
    public void sizeCoversTheLargestFaceAndStaysThroughEveryState() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingText("Saving the document");
            button.setSucceededText("Saved");
            button.setFailedText("Failed");
            button.setFont(Font.font(13.0));
            double insets = button.snappedLeftInset() + button.snappedRightInset();
            double widest = face(button, ".pending-face").prefWidth(-1);
            assertTrue(widest > face(button, ".idle-face").prefWidth(-1));

            double prefWidth = button.prefWidth(-1);
            double prefHeight = button.prefHeight(-1);
            double minWidth = button.minWidth(-1);
            double minHeight = button.minHeight(-1);
            assertEquals(insets + widest, prefWidth, 1.0e-6);
            assertEquals(insets + face(button, ".pending-face").minWidth(-1), minWidth, 1.0e-6);
            assertEquals(prefWidth, button.maxWidth(-1), 1.0e-6);
            assertEquals(prefHeight, button.maxHeight(-1), 1.0e-6);

            Run first = button.start();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            first.succeed();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            button.resetFeedback();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            button.start().fail();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            button.start();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            button.cancel();
            assertSizes(button, prefWidth, prefHeight, minWidth, minHeight);
            button.getSkin().dispose();
        });
    }

    @Test
    public void iconOnlyButtonIsNotWidenedByTheOtherFaces() throws InterruptedException {
        onFx(() -> {
            Region icon = new Region();
            icon.setPrefSize(16.0, 16.0);
            icon.setMinSize(16.0, 16.0);
            RXTaskButton button = laidOut(new RXTaskButton(null, icon));
            double insets = button.snappedLeftInset() + button.snappedRightInset();
            setIndicatorSizes(button, 12.0);

            assertEquals(insets + 16.0, button.prefWidth(-1), 1.0e-6);

            button.setPendingText("Saving");
            assertTrue(button.prefWidth(-1) > insets + 16.0);
            button.getSkin().dispose();
        });
    }

    @Test
    public void labelPaddingPadsEveryFace() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingText("Saving");
            double width = button.prefWidth(-1);
            double height = button.prefHeight(-1);

            // labelPadding has no public setter: it is styled from CSS only.
            button.setStyle("-fx-label-padding: 3 4 5 6;");
            button.applyCss();

            assertEquals(width + 10.0, button.prefWidth(-1), 1.0e-6);
            assertEquals(height + 8.0, button.prefHeight(-1), 1.0e-6);
            for (String selector : FACES) {
                assertEquals(new Insets(3.0, 4.0, 5.0, 6.0), face(button, selector).getPadding(), selector);
            }
            button.getSkin().dispose();
        });
    }

    @Test
    public void wrappedCaptionGrowsTallerInNarrowerWidth() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingText("A pending caption long enough to wrap onto several lines");
            button.setWrapText(true);

            assertTrue(button.prefHeight(100.0) > button.prefHeight(-1));
            button.getSkin().dispose();
        });
    }

    @Test
    public void gapBetweenIndicatorAndCaptionFollowsTheButton() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingText("Saving");
            button.setMaxWidth(Double.MAX_VALUE);
            setIndicatorSizes(button, 12.0);
            button.setGraphicTextGap(6.0);
            button.getParent().layout();
            button.start();
            button.getParent().layout();
            Node indicator = face(button, ".pending-face").lookup(".indicator");
            Label caption = caption(button, ".pending-face");
            assertEquals(indicator.getLayoutX() + 12.0 + 6.0, caption.getLayoutX(), 1.0e-6);

            button.setGraphicTextGap(20.0);
            button.getParent().layout();

            assertEquals(indicator.getLayoutX() + 12.0 + 20.0, caption.getLayoutX(), 1.0e-6);
            button.getSkin().dispose();
        });
    }

    // ==================== Faces ====================

    @Test
    public void exactlyOneFaceShowsInEveryState() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            assertShowing(button, ".idle-face");

            Run first = button.start();
            assertShowing(button, ".pending-face");
            first.succeed();
            assertShowing(button, ".succeeded-face");
            button.resetFeedback();
            assertShowing(button, ".idle-face");

            Run second = button.start();
            assertShowing(button, ".pending-face");
            second.fail();
            assertShowing(button, ".failed-face");
            // A retry from the failed face.
            Run third = button.start();
            assertShowing(button, ".pending-face");
            third.succeed();
            assertShowing(button, ".succeeded-face");
            button.start().fail();
            assertShowing(button, ".failed-face");
            button.resetFeedback();
            assertShowing(button, ".idle-face");

            button.start();
            button.cancel();
            assertShowing(button, ".idle-face");
            button.getSkin().dispose();
        });
    }

    @Test
    public void delayWindowKeepsTheIdleFace() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingDelay(LONG);

            Run run = button.start();
            assertTrue(button.isRunning());
            assertShowing(button, ".idle-face");

            run.succeed();
            assertShowing(button, ".succeeded-face");
            button.getSkin().dispose();
        });
    }

    @Test
    public void swapsPlayForwardToPendingAndResultsAndBackwardToIdle() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);
            button.setAnimationDuration(Duration.millis(120.0));

            Run run = button.start();
            assertEquals(1, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(0).getDirection());
            assertEquals(Duration.millis(120.0), recording.contexts.get(0).getDuration());
            assertSame(face(button, ".pending-face").getParent(), recording.contexts.get(0).getNextPage());

            // Still running: the next swap jumps it to its end first.
            run.fail();
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(1).getDirection());
            assertSame(face(button, ".failed-face").getParent(), recording.contexts.get(1).getNextPage());

            button.resetFeedback();
            assertEquals(2, recording.jumpToEndCalls);
            assertEquals(3, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(2).getDirection());
            assertSame(face(button, ".idle-face").getParent(), recording.contexts.get(2).getNextPage());
            button.getSkin().dispose();
        });
    }

    @Test
    public void zeroAnimationDurationCutsAndInvalidOneFallsBack() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);

            button.setAnimationDuration(Duration.ZERO);
            Run run = button.start();
            assertEquals(0, recording.contexts.size());
            assertShowing(button, ".pending-face");

            button.setAnimationDuration(Duration.INDEFINITE);
            run.succeed();
            assertEquals(1, recording.contexts.size());
            assertEquals(RXTaskButton.DEFAULT_ANIMATION_DURATION, recording.contexts.get(0).getDuration());

            button.setAnimationDuration(null);
            button.resetFeedback();
            assertEquals(2, recording.contexts.size());
            assertEquals(RXTaskButton.DEFAULT_ANIMATION_DURATION, recording.contexts.get(1).getDuration());
            button.getSkin().dispose();
        });
    }

    @Test
    public void zeroResultDurationNeverShowsTheResultFace() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setSucceededDuration(Duration.ZERO);
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);
            List<String> shown = new ArrayList<>();
            face(button, ".succeeded-face").visibleProperty().addListener((o, was, is) -> shown.add("succeeded=" + is));

            button.start().succeed();

            // One swap to the pending face and one straight back.
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(0).getDirection());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
            assertSame(face(button, ".idle-face").getParent(), recording.contexts.get(1).getNextPage());
            assertEquals(List.of(), shown);
            button.getSkin().dispose();
        });
    }

    @Test
    public void faceFollowsWhereTheButtonIsNotWhatAListenerWasTold() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            // An invalidation listener registered before the skin exists runs
            // before the skin's own: the result is dismissed while it is being
            // reported, and the skin is told about SUCCEEDED after IDLE.
            button.stateProperty().addListener((Observable observable) -> {
                if (button.getState() == State.SUCCEEDED) {
                    button.resetFeedback();
                }
            });
            laidOut(button);
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);

            button.start().succeed();

            assertEquals(State.IDLE, button.getState());
            // The succeeded face is never swapped in.
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
            assertSame(face(button, ".idle-face").getParent(), recording.contexts.get(1).getNextPage());
            recording.jumpToEnd();
            assertShowing(button, ".idle-face");
            button.getSkin().dispose();
        });
    }

    // ==================== Indicators ====================

    @Test
    public void pendingFaceHoldsARingAndTheResultFacesHoldShapes() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());

            assertInstanceOf(RingIndicator.class, indicator(button, ".pending-face"));
            for (String selector : List.of(".idle-face", ".succeeded-face", ".failed-face")) {
                Node indicator = indicator(button, selector);
                assertFalse(indicator instanceof RingIndicator, selector);
                assertTrue(indicator.getStyleClass().contains("indicator"), selector);
            }
            button.getSkin().dispose();
        });
    }

    @Test
    public void builtInIndicatorsArePaintedInTheTextFill() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RingIndicator ring = ring(button);
            Region check = assertInstanceOf(Region.class, indicator(button, ".succeeded-face"));
            Region cross = assertInstanceOf(Region.class, indicator(button, ".failed-face"));

            button.setTextFill(Color.CRIMSON);
            assertEquals(Color.CRIMSON, ring.getPaint());
            for (Region shape : List.of(check, cross)) {
                assertEquals(1, shape.getBackground().getFills().size());
                assertEquals(Color.CRIMSON, shape.getBackground().getFills().get(0).getFill());
            }

            // A style sheet cannot paint them another color.
            check.setStyle("-fx-background-color: blue;");
            button.applyCss();
            assertEquals(Color.CRIMSON, check.getBackground().getFills().get(0).getFill());

            button.setTextFill(null);
            assertNull(ring.getPaint());
            assertNull(check.getBackground());
            assertNull(cross.getBackground());

            button.getSkin().dispose();
            assertFalse(ring.paintProperty().isBound());
            assertFalse(check.backgroundProperty().isBound());
            assertFalse(cross.backgroundProperty().isBound());
        });
    }

    @Test
    public void ringShowsAStopMarkOnlyWhileAClickWouldCancel() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RingIndicator ring = ring(button);
            button.setCancellable(true);
            // Cancellable, but nothing to cancel.
            assertFalse(ring.isStopMarkVisible());

            // No minimum: a click cancels as soon as the pending face is up.
            Run run = button.start();
            assertTrue(ring.isStopMarkVisible());
            button.setCancellable(false);
            assertFalse(ring.isStopMarkVisible());
            button.setCancellable(true);
            assertTrue(ring.isStopMarkVisible());
            run.succeed();
            assertFalse(ring.isStopMarkVisible());

            // A custom indicator gets no mark.
            button.setPendingIndicator(new Region());
            button.start();
            assertNull(face(button, ".pending-face").lookup(".stop"));

            button.getSkin().dispose();
            assertFalse(ring.stopMarkVisibleProperty().isBound());
        });
    }

    @Test
    public void runInFlightBeforeTheSkinIsInPlaceShowsTheStopMarkFromTheStart() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            button.setCancellable(true);
            button.start();

            laidOut(button);

            assertTrue(ring(button).isStopMarkVisible());
            button.getSkin().dispose();
        });
    }

    @Test
    public void ringDrawsTheProgressOfTheButton() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            Arc arc = arc(ring(button));
            // Not in a showing window, so the arc takes every length at once.
            assertEquals(-12.0, arc.getLength());

            button.setProgress(0.25);
            assertEquals(-90.0, arc.getLength());
            button.setProgress(3.0);
            assertEquals(-360.0, arc.getLength());

            // A run starts indeterminate.
            Run run = button.start();
            assertEquals(-12.0, arc.getLength());
            run.updateProgress(0.5);
            assertEquals(-180.0, arc.getLength());
            button.getSkin().dispose();
        });
    }

    @Test
    public void pendingIndicatorTakesThePlaceOfTheRingAndNullBringsItBack() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RingIndicator ring = ring(button);
            Region pendingFace = face(button, ".pending-face");
            Region custom = new Region();
            double width = pendingFace.prefWidth(-1);
            // The face with an indicator that takes no room, to measure from.
            button.setPendingIndicator(custom);
            double withoutIndicator = pendingFace.prefWidth(-1);

            custom.setPrefSize(30.0, 10.0);
            assertSame(custom, pendingFace.getChildrenUnmodifiable().get(0));
            assertNull(ring.getParent());
            assertEquals(2, pendingFace.getChildrenUnmodifiable().size());
            // It counts toward the size of the face, at its own preferred size.
            assertEquals(withoutIndicator + 30.0, pendingFace.prefWidth(-1), 1.0e-6);
            button.start();
            relayout(button);
            assertEquals(30.0, custom.getWidth());
            assertEquals(10.0, custom.getHeight());
            // The button does not paint an indicator of the application.
            assertNull(custom.getBackground());

            button.setPendingIndicator(null);
            assertSame(ring, pendingFace.getChildrenUnmodifiable().get(0));
            assertNull(custom.getParent());
            assertEquals(width, pendingFace.prefWidth(-1), 1.0e-6);
            button.getSkin().dispose();
        });
    }

    @Test
    public void pendingIndicatorSetBeforeTheSkinIsInPlaceFromTheStart() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            Region custom = new Region();
            button.setPendingIndicator(custom);

            laidOut(button);

            assertSame(custom, face(button, ".pending-face").getChildrenUnmodifiable().get(0));
            button.getSkin().dispose();
        });
    }

    @Test
    public void progressSetBeforeTheSkinIsDrawnFromTheStart() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            button.setProgress(0.5);

            laidOut(button);

            assertEquals(-180.0, arc(ring(button)).getLength());
            button.getSkin().dispose();
        });
    }

    // The ring only animates in a showing window, so this test needs a real
    // stage. "Moves" is polled for; "rests" compares two samples taken apart.
    @Test
    public void ringTravelsOnlyWhileThePendingFaceShows() throws InterruptedException {
        AtomicReference<RXTaskButton> buttonRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<Run> runRef = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button();
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(button), 200.0, 100.0));
            stage.show();
            buttonRef.set(button);
            stageRef.set(stage);
        });
        RXTaskButton button = buttonRef.get();
        try {
            RingIndicator ring = onFxGet(() -> ring(button));
            assertRests(ring);

            onFx(() -> runRef.set(button.start()));
            assertMoves(ring);

            // A determinate progress: the arc settles on its length.
            onFx(() -> button.setProgress(0.5));
            awaitLength(ring, -180.0);
            assertRests(ring);
            onFx(() -> button.setProgress(-1.0));
            assertMoves(ring);

            // Hidden through an ancestor of the button.
            onFx(() -> button.getParent().setVisible(false));
            assertRests(ring);
            onFx(() -> button.getParent().setVisible(true));
            assertMoves(ring);

            onFx(() -> runRef.get().succeed());
            assertRests(ring);

            // A replaced skin lets go of its ring; the new one has its own.
            onFx(() -> button.start());
            assertMoves(ring);
            onFx(() -> replaceSkin(button));
            assertRests(ring);
            assertMoves(onFxGet(() -> ring(button)));
        } finally {
            onFx(() -> stageRef.get().hide());
        }
    }

    // ==================== Skin lifecycle ====================

    @Test
    public void newSkinStartsOnTheFaceThatIsDueWithoutAnAnimation() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);
            Run run = button.start();
            recording.jumpToEnd();
            int swaps = recording.contexts.size();

            replaceSkin(button);
            relayout(button);
            assertEquals(swaps, recording.contexts.size());
            assertEquals(2, button.getChildrenUnmodifiable().size());
            assertShowing(button, ".pending-face");

            // The new skin follows the button from here on.
            run.succeed();
            assertEquals(swaps + 1, recording.contexts.size());
            recording.jumpToEnd();
            assertShowing(button, ".succeeded-face");

            replaceSkin(button);
            relayout(button);
            assertEquals(swaps + 1, recording.contexts.size());
            assertShowing(button, ".succeeded-face");
            button.getSkin().dispose();
        });
    }

    @Test
    public void disposeDuringASwapStartsNoAnimationAndStopsFollowing() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            RecordingAnimation recording = new RecordingAnimation();
            button.setAnimation(recording);
            Run run = button.start();
            Region pendingFace = face(button, ".pending-face");
            Region idleFace = face(button, ".idle-face");

            button.getSkin().dispose();

            assertEquals(1, recording.contexts.size());
            assertEquals(1, recording.disposeCalls);
            assertTrue(button.getChildrenUnmodifiable().isEmpty());
            assertTrue(idleFace.isVisible() && idleFace.getParent().isVisible());
            assertFalse(pendingFace.getParent().isVisible());
            // The state belongs to the button and is untouched.
            assertEquals(State.PENDING, button.getState());
            assertTrue(button.getPseudoClassStates().stream()
                    .anyMatch(pseudoClass -> "pending".equals(pseudoClass.getPseudoClassName())));

            run.succeed();
            assertEquals(State.SUCCEEDED, button.getState());
            assertEquals(1, recording.contexts.size());
            assertTrue(button.getChildrenUnmodifiable().isEmpty());
            assertTrue(idleFace.isVisible() && idleFace.getParent().isVisible());
        });
    }

    @Test
    public void skinReplacedWhileTheStateIsReportedLeavesQuietly() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            // Registered before the first skin, so it runs before that skin's
            // listener, which is then told about a state it can no longer show.
            button.stateProperty().addListener((Observable observable) -> {
                if (button.getState() == State.PENDING) {
                    replaceSkin(button);
                }
            });
            laidOut(button);
            List<Throwable> uncaught = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((failed, error) -> uncaught.add(error));
            try {
                button.start();
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }

            assertEquals(List.of(), uncaught);
            relayout(button);
            assertEquals(2, button.getChildrenUnmodifiable().size());
            assertShowing(button, ".pending-face");
            button.getSkin().dispose();
        });
    }

    @Test
    public void skinReplacedWhileThePendingIndicatorIsReportedLeavesItToTheNewSkin() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            // Registered before the first skin, so that skin is told about an
            // indicator after it was disposed.
            button.pendingIndicatorProperty().addListener((Observable observable) -> {
                if (button.getPendingIndicator() != null) {
                    replaceSkin(button);
                }
            });
            laidOut(button);
            Region custom = new Region();
            List<Throwable> uncaught = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((failed, error) -> uncaught.add(error));
            try {
                button.setPendingIndicator(custom);
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }

            assertEquals(List.of(), uncaught);
            relayout(button);
            assertSame(custom, indicator(button, ".pending-face"));
            assertSame(button, custom.getParent().getParent().getParent().getParent());
            button.getSkin().dispose();
        });
    }

    @Test
    public void disposedSkinNoLongerArmsTheButton() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());

            button.getSkin().dispose();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));

            assertFalse(button.isArmed());
        });
    }

    // ==================== Button behavior ====================

    @Test
    public void primaryPressArmsAndReleaseFiresOnce() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger actions = countActions(button);
            RippleLayer layer = rippleLayer(button);
            int ripples = layer.getChildrenUnmodifiable().size();

            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            assertTrue(button.isArmed());
            assertTrue(layer.getChildrenUnmodifiable().size() > ripples);
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertFalse(button.isArmed());
            assertEquals(1, actions.get());

            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(button.isArmed());
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(button.isArmed());
            assertEquals(2, actions.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void otherKeysStayUnconsumed() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger arrow = new AtomicInteger();
            AtomicInteger space = new AtomicInteger();
            // Dispatch works on event copies, so consumption is asserted through
            // the scene accelerators, which only run for unconsumed events.
            button.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT), arrow::incrementAndGet);
            button.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE), space::incrementAndGet);

            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.RIGHT));
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertEquals(1, arrow.get());
            assertEquals(0, space.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void focusLossWhileKeyDownDisarmsWithoutFiring() throws InterruptedException {
        onFx(() -> {
            FocusableTaskButton button = new FocusableTaskButton();
            laidOut(button);
            AtomicInteger actions = countActions(button);
            button.focus(true);

            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(button.isArmed());
            button.focus(false);
            assertFalse(button.isArmed());
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertEquals(0, actions.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void pressDoesNotArmWhileTheActivationWouldBeSwallowed() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger actions = countActions(button);
            RippleLayer layer = rippleLayer(button);
            int ripples = layer.getChildrenUnmodifiable().size();

            // Inside the delay window: running, still on the idle face.
            button.setPendingDelay(LONG);
            button.setCancellable(true);
            Run delayed = button.start();
            assertFalse(press(button));
            assertFalse(pressKey(button));
            delayed.cancel();

            // Pending and not cancellable.
            button.setPendingDelay(Duration.ZERO);
            button.setCancellable(false);
            Run pending = button.start();
            assertEquals(State.PENDING, button.getState());
            assertFalse(press(button));
            assertFalse(pressKey(button));
            pending.cancel();

            // Cancellable, but the pending face has not shown for its minimum yet.
            button.setCancellable(true);
            button.setPendingMinDuration(LONG);
            Run young = button.start();
            assertFalse(press(button));

            // The outcome is in and waits for the minimum: nothing left to cancel.
            young.succeed();
            assertEquals(State.PENDING, button.getState());
            assertFalse(button.isRunning());
            assertFalse(press(button));
            button.cancel();
            button.resetFeedback();

            assertEquals(0, actions.get());
            assertEquals(ripples, layer.getChildrenUnmodifiable().size());
            button.getSkin().dispose();
        });
    }

    @Test
    public void pressArmsWhenTheActivationWouldCancelOrRetry() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger actions = countActions(button);
            button.setCancellable(true);

            // Pending, cancellable, minimum display over: the click cancels.
            button.start();
            assertEquals(State.PENDING, button.getState());
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            assertTrue(button.isArmed());
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
            assertEquals(0, actions.get());

            // A result face: the click reaches onAction, which is how a run is retried.
            button.start().fail();
            assertEquals(State.FAILED, button.getState());
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            assertTrue(button.isArmed());
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertEquals(1, actions.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void pressDoesOnlyWhatItMeantWhenItBegan() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger actions = countActions(button);
            button.setCancellable(true);

            // Armed to cancel, the run ends first: the release is no action.
            Run run = button.start();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            assertTrue(button.isArmed());
            run.succeed();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertFalse(button.isArmed());
            assertEquals(0, actions.get());
            assertEquals(State.SUCCEEDED, button.getState());

            // The same for a key stroke.
            run = button.start();
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(button.isArmed());
            run.fail();
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertEquals(0, actions.get());
            assertEquals(State.FAILED, button.getState());

            // Armed to act, a run starts meanwhile: the release does not cancel it.
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            assertTrue(button.isArmed());
            button.start();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertTrue(button.isRunning());
            assertEquals(0, actions.get());
            button.getSkin().dispose();
        });
    }

    @Test
    public void pressStillFiresWhenItMeansTheSameOnAnotherFaceOrRun() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            AtomicInteger actions = countActions(button);
            button.setCancellable(true);

            // A result face that gives way to the normal face: an action on both.
            button.start().fail();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            button.resetFeedback();
            assertEquals(State.IDLE, button.getState());
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertEquals(1, actions.get());

            // A run replaced while the pending face stays up: a cancel of
            // whichever run the ring stands for at the release.
            button.start();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
            Run second = button.start();
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
            assertFalse(second.isCurrent());
            assertFalse(button.isRunning());
            assertEquals(1, actions.get());
            button.getSkin().dispose();
        });
    }

    // The accelerators only fire for a button in a showing window, so this
    // test needs a real stage.
    @Test
    public void defaultAndCancelAcceleratorsFollowTheSceneAndTheGuard() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button();
            AtomicInteger actions = countActions(button);
            Scene first = new Scene(new StackPane(button), 200.0, 100.0);
            Stage stage = new Stage();
            stage.setScene(first);
            stage.show();
            try {
                button.setDefaultButton(true);
                button.setCancelButton(true);
                first.getAccelerators().get(ENTER).run();
                first.getAccelerators().get(ESCAPE).run();
                assertEquals(2, actions.get());

                // Busy: the activation is swallowed, whatever fired it.
                Run run = button.start();
                first.getAccelerators().get(ENTER).run();
                assertEquals(2, actions.get());
                assertTrue(button.isRunning());
                // Cancellable: ESCAPE on a cancel button cancels the run.
                button.setCancellable(true);
                first.getAccelerators().get(ESCAPE).run();
                assertFalse(run.isCurrent());
                assertEquals(2, actions.get());

                ((StackPane) first.getRoot()).getChildren().clear();
                Scene second = new Scene(new StackPane(button), 200.0, 100.0);
                assertFalse(first.getAccelerators().containsKey(ENTER));
                assertTrue(second.getAccelerators().containsKey(ENTER));

                button.getSkin().dispose();
                assertFalse(second.getAccelerators().containsKey(ENTER));
                assertFalse(second.getAccelerators().containsKey(ESCAPE));
            } finally {
                stage.hide();
            }
        });
    }

    // ==================== Accessibility ====================

    @Test
    public void accessibleNameFollowsTheFaceShown() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = laidOut(button());
            button.setPendingText("Saving");
            button.setSucceededText("Saved");
            button.setFailedText("Could not save");

            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            Run run = button.start();
            assertEquals("Saving", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            // A face without a text is a visual choice; the button keeps a name.
            button.setPendingText(null);
            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.setPendingText("");
            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            run.succeed();
            assertEquals("Saved", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.start().fail();
            assertEquals("Could not save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));

            button.setAccessibleText("Save the document");
            assertEquals("Save the document", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.getSkin().dispose();
        });
    }

    @Test
    public void iconOnlyNameFallsBackToTheGraphicInEveryState() throws InterruptedException {
        onFx(() -> {
            Region icon = new Region();
            icon.setAccessibleText("Save");
            RXTaskButton button = laidOut(new RXTaskButton(null, icon));
            button.setPendingDelay(Duration.ZERO);
            button.setPendingMinDuration(Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);

            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            Run run = button.start();
            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            run.succeed();
            assertEquals("Save", button.queryAccessibleAttribute(AccessibleAttribute.TEXT));
            button.getSkin().dispose();
        });
    }

    // ==================== Helpers ====================

    // No delay, no minimum display, results stay: every face change is the
    // direct consequence of a call.
    private static RXTaskButton button() {
        RXTaskButton button = new RXTaskButton("Save");
        button.setPendingDelay(Duration.ZERO);
        button.setPendingMinDuration(Duration.ZERO);
        button.setSucceededDuration(Duration.INDEFINITE);
        button.setFailedDuration(Duration.INDEFINITE);
        return button;
    }

    private static RXTaskButton laidOut(RXTaskButton button) {
        StackPane root = new StackPane(button);
        new Scene(root, 300.0, 100.0);
        root.resize(300.0, 100.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static void relayout(RXTaskButton button) {
        button.getParent().applyCss();
        button.getParent().layout();
    }

    // JavaFX 17 ignores setSkin for a skin of the installed skin's class, so
    // each replacement is a new anonymous subclass of a different class.
    private static void replaceSkin(RXTaskButton button) {
        button.setSkin(button.getSkin() instanceof FirstSkin ? new SecondSkin(button) : new FirstSkin(button));
    }

    private static AtomicInteger countActions(RXTaskButton button) {
        AtomicInteger actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        return actions;
    }

    private static RippleLayer rippleLayer(RXTaskButton button) {
        return assertInstanceOf(RippleLayer.class, button.getChildrenUnmodifiable().get(0));
    }

    // Presses the primary button, reports whether that armed the button, and
    // lets go without firing.
    private static boolean press(RXTaskButton button) {
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED, MouseButton.PRIMARY));
        boolean armed = button.isArmed();
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_EXITED, MouseButton.PRIMARY));
        Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED, MouseButton.PRIMARY));
        return armed;
    }

    private static boolean pressKey(RXTaskButton button) {
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
        boolean armed = button.isArmed();
        Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
        return armed;
    }

    private static void assertSizes(RXTaskButton button, double prefWidth, double prefHeight,
                                    double minWidth, double minHeight) {
        assertEquals(prefWidth, button.prefWidth(-1), 1.0e-6);
        assertEquals(prefHeight, button.prefHeight(-1), 1.0e-6);
        assertEquals(minWidth, button.minWidth(-1), 1.0e-6);
        assertEquals(minHeight, button.minHeight(-1), 1.0e-6);
    }

    // Exactly the given face is visible on a visible page.
    private static void assertShowing(RXTaskButton button, String expected) {
        List<String> showing = new ArrayList<>();
        for (String selector : FACES) {
            Region face = face(button, selector);
            if (face.isVisible() && face.getParent().isVisible()) {
                showing.add(selector);
            }
        }
        assertEquals(List.of(expected), showing);
    }

    private static Region face(RXTaskButton button, String selector) {
        Node face = button.lookup(selector);
        assertNotNull(face, selector);
        return assertInstanceOf(Region.class, face);
    }

    private static Label caption(RXTaskButton button, String faceSelector) {
        return assertInstanceOf(Label.class, face(button, faceSelector).lookup(".caption"));
    }

    private static Node indicator(RXTaskButton button, String faceSelector) {
        return face(button, faceSelector).getChildrenUnmodifiable().get(0);
    }

    private static RingIndicator ring(RXTaskButton button) {
        return assertInstanceOf(RingIndicator.class, indicator(button, ".pending-face"));
    }

    private static Arc arc(RingIndicator ring) {
        return (Arc) ring.getChildrenUnmodifiable().get(1);
    }

    // Everything the animations of the ring change.
    private static List<Double> pose(RingIndicator ring) throws InterruptedException {
        Arc arc = arc(ring);
        return onFxGet(() -> List.of(arc.getLength(), arc.getStartAngle(),
                ((Rotate) arc.getTransforms().get(0)).getAngle()));
    }

    private static void assertMoves(RingIndicator ring) throws InterruptedException {
        List<Double> before = pose(ring);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.sleep(30L);
            if (!before.equals(pose(ring))) {
                return;
            }
        }
        throw new AssertionError("the ring does not move: " + before);
    }

    private static void assertRests(RingIndicator ring) throws InterruptedException {
        List<Double> before = pose(ring);
        Thread.sleep(200L);
        assertEquals(before, pose(ring));
    }

    private static void awaitLength(RingIndicator ring, double length) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        double last = Double.NaN;
        while (System.nanoTime() < deadline) {
            last = onFxGet(() -> arc(ring).getLength());
            if (last == length) {
                return;
            }
            Thread.sleep(30L);
        }
        throw new AssertionError("arc length " + last + " never reached " + length);
    }

    private static void setIndicatorSizes(RXTaskButton button, double size) {
        for (String selector : FACES) {
            if (!".idle-face".equals(selector)) {
                assertInstanceOf(Region.class, face(button, selector).lookup(".indicator"))
                        .setPrefSize(size, size);
            }
        }
    }

    private static void onFx(Runnable body) throws InterruptedException {
        onFxGet(() -> {
            body.run();
            return null;
        });
    }

    private static <T> T onFxGet(Supplier<T> body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(body.get());
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
        return result.get();
    }

    private static MouseEvent mouseEvent(EventType<MouseEvent> type, MouseButton button) {
        boolean pressed = type == MouseEvent.MOUSE_PRESSED;
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, button, 1,
                false, false, false, false,
                pressed && button == MouseButton.PRIMARY, false,
                pressed && button == MouseButton.SECONDARY,
                false, false, false, null);
    }

    private static KeyEvent keyEvent(EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }

    private static final class FirstSkin extends RXTaskButtonSkin {

        FirstSkin(RXTaskButton button) {
            super(button);
        }
    }

    private static final class SecondSkin extends RXTaskButtonSkin {

        SecondSkin(RXTaskButton button) {
            super(button);
        }
    }

    // Without a focused window Node.focused never turns true, so the test
    // drives it through the protected setter.
    private static final class FocusableTaskButton extends RXTaskButton {

        FocusableTaskButton() {
            super("Save");
            setPendingDelay(Duration.ZERO);
        }

        void focus(boolean value) {
            setFocused(value);
        }
    }

    // ==================== Recording animation ====================

    // Never finishes on its own: a swap only ends through jumpToEnd or dispose.
    private static final class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int disposeCalls;

        private Animation running;
        private Runnable finishAction;

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
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
            disposeCalls++;
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

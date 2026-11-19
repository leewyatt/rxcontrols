package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTaskButton.Run;
import io.github.leewyatt.rxcontrols.RXTaskButton.State;
import io.github.leewyatt.rxcontrols.skins.RXButtonSkin;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTaskButton state machine on the control alone: the running and
 * state properties, the pending delay and minimum, the result durations, the
 * run handle, the activation guard and re-entrant calls from listeners. Runs
 * are driven through the handle only.
 */
public class RXTaskButtonTest {

    private static final PseudoClass RUNNING = PseudoClass.getPseudoClass("running");
    private static final PseudoClass PENDING = PseudoClass.getPseudoClass("pending");
    private static final PseudoClass SUCCEEDED = PseudoClass.getPseudoClass("succeeded");
    private static final PseudoClass FAILED = PseudoClass.getPseudoClass("failed");

    // Long enough that a window never ends during a test body.
    private static final Duration LONG = Duration.seconds(30.0);

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

    // ==================== Defaults and CSS ====================

    @Test
    public void defaultsAndCssMetadataMatchContract() {
        RXTaskButton button = new RXTaskButton("Save");

        assertTrue(button.getStyleClass().contains("rx-task-button"));
        assertTrue(button.getStyleClass().contains("rx-button"));
        assertFalse(button.isRunning());
        assertEquals(State.IDLE, button.getState());
        assertEquals(-1.0, button.getProgress());
        assertNull(button.getPendingText());
        assertNull(button.getSucceededText());
        assertNull(button.getFailedText());
        assertNull(button.getPendingGraphic());
        assertNull(button.getSucceededGraphic());
        assertNull(button.getFailedGraphic());
        assertNull(button.getPendingIndicator());
        assertFalse(button.isCancellable());
        assertNull(button.getAnimation());
        assertEquals(Duration.millis(2000.0), button.getSucceededDuration());
        assertEquals(Duration.millis(3000.0), button.getFailedDuration());
        assertEquals(Duration.millis(200.0), button.getPendingDelay());
        assertEquals(Duration.millis(400.0), button.getPendingMinDuration());
        assertEquals(Duration.millis(200.0), button.getAnimationDuration());
        assertEquals(button.getSucceededDuration(), RXTaskButton.DEFAULT_SUCCEEDED_DURATION);
        assertEquals(button.getFailedDuration(), RXTaskButton.DEFAULT_FAILED_DURATION);
        assertEquals(button.getPendingDelay(), RXTaskButton.DEFAULT_PENDING_DELAY);
        assertEquals(button.getPendingMinDuration(), RXTaskButton.DEFAULT_PENDING_MIN_DURATION);
        assertEquals(button.getAnimationDuration(), RXTaskButton.DEFAULT_ANIMATION_DURATION);
        assertPseudoClasses(button, false, State.IDLE);

        assertTrue(button.getCssMetaData().containsAll(RXButton.getClassCssMetaData()));
        for (String name : List.of("-rx-succeeded-duration", "-rx-failed-duration", "-rx-pending-delay",
                "-rx-pending-min-duration", "-rx-animation-duration")) {
            assertTrue(durationMetaData(button, name).isSettable(button), name);
        }
        button.succeededDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        button.failedDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        button.pendingDelayProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        button.pendingMinDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        button.animationDurationProperty().bind(new SimpleObjectProperty<>(Duration.ONE));
        for (String name : List.of("-rx-succeeded-duration", "-rx-failed-duration", "-rx-pending-delay",
                "-rx-pending-min-duration", "-rx-animation-duration")) {
            assertFalse(durationMetaData(button, name).isSettable(button), name);
        }
    }

    @Test
    public void durationsParseFromCss() {
        RXTaskButton button = new RXTaskButton("Save");
        StackPane root = new StackPane(button);
        new Scene(root, 300.0, 100.0);

        button.setStyle("-rx-succeeded-duration: indefinite; -rx-failed-duration: indefinite;"
                + " -rx-pending-delay: 50ms; -rx-pending-min-duration: 70ms; -rx-animation-duration: 90ms;");
        root.applyCss();

        assertEquals(Duration.INDEFINITE, button.getSucceededDuration());
        assertEquals(Duration.INDEFINITE, button.getFailedDuration());
        assertEquals(Duration.millis(50.0), button.getPendingDelay());
        assertEquals(Duration.millis(70.0), button.getPendingMinDuration());
        assertEquals(Duration.millis(90.0), button.getAnimationDuration());
        button.getSkin().dispose();
    }

    // ==================== Running and state ====================

    @Test
    public void runningFollowsTheWorkWhileTheIdleFaceWaitsForTheDelay() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            Probe probe = new Probe(button);

            Run run = button.start();

            assertTrue(button.isRunning());
            assertEquals(State.IDLE, button.getState());
            assertPseudoClasses(button, true, State.IDLE);

            run.succeed();

            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());
            assertPseudoClasses(button, false, State.SUCCEEDED);
            assertEquals(List.of("running=true", "running=false", "state=SUCCEEDED"), probe.events);
        });
    }

    @Test
    public void zeroDelayShowsThePendingFaceAtOnce() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Probe probe = new Probe(button);

            button.start();

            assertEquals(State.PENDING, button.getState());
            assertPseudoClasses(button, true, State.PENDING);
            assertEquals(List.of("running=true", "state=PENDING"), probe.events);
        });
    }

    @Test
    public void pendingFaceAppearsWhenTheDelayEndsWithTheRunInFlight() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.millis(80.0), LONG);
            ref.set(button);
            button.start();
            assertEquals(State.IDLE, button.getState());
        });
        Thread.sleep(400L);
        onFx(() -> {
            assertTrue(ref.get().isRunning());
            assertEquals(State.PENDING, ref.get().getState());
        });
    }

    @Test
    public void outcomeInsideTheMinimumTurnsRunningOffButIsShownLater() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(150.0));
            ref.set(button);
            probe.set(new Probe(button));

            Run run = button.start();
            run.succeed();

            assertFalse(button.isRunning());
            assertFalse(run.isCurrent());
            assertEquals(State.PENDING, button.getState());
            assertPseudoClasses(button, false, State.PENDING);
        });
        Thread.sleep(500L);
        onFx(() -> {
            assertEquals(State.SUCCEEDED, ref.get().getState());
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=SUCCEEDED"),
                    probe.get().events);
        });
    }

    @Test
    public void outcomeAfterTheMinimumShowsAtOnce() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<Run> run = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(80.0));
            ref.set(button);
            run.set(button.start());
        });
        Thread.sleep(400L);
        onFx(() -> {
            run.get().fail();

            assertFalse(ref.get().isRunning());
            assertEquals(State.FAILED, ref.get().getState());
            assertPseudoClasses(ref.get(), false, State.FAILED);
        });
    }

    // ==================== Result durations ====================

    @Test
    public void resultFaceReturnsToIdleWhenItsDurationEnds() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.millis(80.0));
            ref.set(button);
            button.start().succeed();
            assertEquals(State.SUCCEEDED, button.getState());
        });
        Thread.sleep(400L);
        onFx(() -> assertEquals(State.IDLE, ref.get().getState()));
    }

    @Test
    public void zeroResultDurationNeverShowsTheResultFace() throws InterruptedException {
        AtomicReference<RXTaskButton> delayed = new AtomicReference<>();
        AtomicReference<Probe> delayedProbe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.ZERO);
            button.setFailedDuration(Duration.ZERO);
            Probe probe = new Probe(button);

            button.start().succeed();
            button.start().fail();

            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE",
                    "running=true", "state=PENDING", "running=false", "state=IDLE"), probe.events);

            // Inside the delay window the state does not change at all.
            RXTaskButton quiet = button(Duration.millis(80.0), Duration.ZERO);
            quiet.setSucceededDuration(Duration.ZERO);
            delayed.set(quiet);
            delayedProbe.set(new Probe(quiet));
            quiet.start().succeed();
        });
        Thread.sleep(400L);
        // The stopped delay timer brings no pending face up afterwards.
        onFx(() -> assertEquals(List.of("running=true", "running=false"), delayedProbe.get().events));
    }

    @Test
    public void indefiniteResultDurationHoldsTheFaceUntilResetOrTheNextRun() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.setFailedDuration(Duration.INDEFINITE);
            ref.set(button);
            button.start().succeed();
        });
        Thread.sleep(300L);
        onFx(() -> {
            RXTaskButton button = ref.get();
            assertEquals(State.SUCCEEDED, button.getState());

            button.resetFeedback();
            assertEquals(State.IDLE, button.getState());

            button.start().fail();
            assertEquals(State.FAILED, button.getState());
            button.start();
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void unusableResultDurationsFallBackToTheDefault() throws InterruptedException {
        onFx(() -> {
            for (Duration unusable : new Duration[] {null, Duration.millis(-5.0), Duration.UNKNOWN}) {
                RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
                button.setSucceededDuration(unusable);
                button.setFailedDuration(unusable);

                button.start().succeed();
                assertEquals(State.SUCCEEDED, button.getState(), String.valueOf(unusable));
                button.start().fail();
                assertEquals(State.FAILED, button.getState(), String.valueOf(unusable));
                button.resetFeedback();
            }
        });
    }

    @Test
    public void unusablePendingDelayFallsBackToTheDefault() throws InterruptedException {
        List<RXTaskButton> buttons = new ArrayList<>();
        onFx(() -> {
            for (Duration unusable : new Duration[] {
                    null, Duration.millis(-5.0), Duration.UNKNOWN, Duration.INDEFINITE}) {
                RXTaskButton button = button(unusable, LONG);
                buttons.add(button);
                button.start();
                // Neither "at once" nor "never": the default delay is running.
                assertEquals(State.IDLE, button.getState(), String.valueOf(unusable));
            }
        });
        Thread.sleep(700L);
        onFx(() -> {
            for (RXTaskButton button : buttons) {
                assertEquals(State.PENDING, button.getState());
            }
        });
    }

    @Test
    public void unusablePendingMinDurationFallsBackToTheDefault() throws InterruptedException {
        List<RXTaskButton> buttons = new ArrayList<>();
        onFx(() -> {
            for (Duration unusable : new Duration[] {
                    null, Duration.millis(-5.0), Duration.UNKNOWN, Duration.INDEFINITE}) {
                RXTaskButton button = button(Duration.ZERO, unusable);
                button.setSucceededDuration(Duration.INDEFINITE);
                buttons.add(button);
                button.start().succeed();
                // Neither "no minimum" nor "forever": the default minimum is running.
                assertEquals(State.PENDING, button.getState(), String.valueOf(unusable));
            }
        });
        Thread.sleep(900L);
        onFx(() -> {
            for (RXTaskButton button : buttons) {
                assertEquals(State.SUCCEEDED, button.getState());
            }
        });
    }

    @Test
    public void resultDurationIsReadWhenTheResultShows() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setFailedDuration(Duration.INDEFINITE);
            ref.set(button);

            Run run = button.start();
            button.setFailedDuration(Duration.millis(80.0));
            run.fail();
            // Too late for the face already showing.
            button.setFailedDuration(Duration.INDEFINITE);

            assertEquals(State.FAILED, button.getState());
        });
        Thread.sleep(400L);
        onFx(() -> assertEquals(State.IDLE, ref.get().getState()));
    }

    @Test
    public void durationsBelowOneMillisecondCountAsZero() throws InterruptedException {
        onFx(() -> {
            Duration tiny = Duration.millis(0.05);
            RXTaskButton button = button(tiny, tiny);
            button.setSucceededDuration(tiny);
            button.setFailedDuration(Duration.INDEFINITE);
            Probe probe = new Probe(button);

            Run first = button.start();
            assertEquals(State.PENDING, button.getState());
            first.succeed();
            assertEquals(State.IDLE, button.getState());

            Run second = button.start();
            second.fail();
            assertEquals(State.FAILED, button.getState());

            // No timer fired inside play(): every change is in its place.
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE",
                    "running=true", "state=PENDING", "running=false", "state=FAILED"), probe.events);
        });
    }

    // ==================== Anti-flicker ====================

    @Test
    public void startFromResultFaceGoesStraightToPending() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            button.setFailedDuration(Duration.INDEFINITE);
            button.start().fail();
            Probe probe = new Probe(button);

            button.start();

            assertEquals(List.of("running=true", "state=PENDING"), probe.events);
        });
    }

    @Test
    public void startWhilePendingKeepsTheFaceAndResetsProgress() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run first = button.start();
            first.updateProgress(0.5);
            Probe probe = new Probe(button);

            Run second = button.start();

            assertNotSame(first, second);
            assertTrue(probe.events.isEmpty(), probe.events.toString());
            assertEquals(-1.0, button.getProgress());
            assertFalse(first.isCurrent());
            assertTrue(second.isCurrent());
        });
    }

    @Test
    public void startInsideTheDelayDoesNotRestartTheDelay() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.millis(600.0), LONG);
            ref.set(button);
            button.start();
        });
        Thread.sleep(300L);
        onFx(() -> {
            ref.get().start();
            assertEquals(State.IDLE, ref.get().getState());
        });
        Thread.sleep(450L);
        // 750 ms in: a restarted delay would run until 900 ms.
        onFx(() -> assertEquals(State.PENDING, ref.get().getState()));
    }

    @Test
    public void outcomeInsideTheDelayStopsTheDelay() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.millis(80.0), LONG);
            button.setSucceededDuration(Duration.ZERO);
            ref.set(button);
            button.start().succeed();

            // Only a delay left over from the first run could end during the test.
            button.setPendingDelay(LONG);
            button.start();
        });
        Thread.sleep(400L);
        onFx(() -> assertEquals(State.IDLE, ref.get().getState()));
    }

    @Test
    public void heldOutcomeWithZeroResultDurationGoesStraightToIdle() throws InterruptedException {
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(100.0));
            button.setSucceededDuration(Duration.ZERO);
            probe.set(new Probe(button));
            button.start().succeed();
        });
        Thread.sleep(500L);
        onFx(() -> assertEquals(
                List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                probe.get().events));
    }

    @Test
    public void synchronousOutcomeFromResultFaceStillShowsPendingForTheMinimum() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(LONG, Duration.millis(150.0));
            button.setSucceededDuration(Duration.INDEFINITE);
            button.setFailedDuration(Duration.INDEFINITE);
            ref.set(button);
            button.start().succeed();
            assertEquals(State.SUCCEEDED, button.getState());

            button.start().fail();

            assertEquals(State.PENDING, button.getState());
        });
        Thread.sleep(500L);
        onFx(() -> assertEquals(State.FAILED, ref.get().getState()));
    }

    // ==================== Stale outcomes ====================

    @Test
    public void onlyTheFirstOutcomeOfTheCurrentRunCounts() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.setFailedDuration(Duration.INDEFINITE);

            Run cancelled = button.start();
            button.cancel();
            cancelled.succeed();
            assertEquals(State.IDLE, button.getState());

            Run replaced = button.start();
            Run current = button.start();
            replaced.fail();
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());

            current.succeed();
            current.fail();
            assertEquals(State.SUCCEEDED, button.getState());
        });
    }

    @Test
    public void startDuringTheHeldOutcomeDiscardsIt() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(150.0));
            ref.set(button);
            button.start().succeed();
            probe.set(new Probe(button));

            button.start();

            assertTrue(button.isRunning());
        });
        Thread.sleep(500L);
        onFx(() -> {
            assertEquals(State.PENDING, ref.get().getState());
            assertEquals(List.of("running=true"), probe.get().events);
        });
    }

    // ==================== Cancel and reset ====================

    @Test
    public void cancelReturnsToIdleAtOnceWhateverTheMinimum() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run run = button.start();
            Probe probe = new Probe(button);

            button.cancel();

            assertFalse(run.isCurrent());
            assertEquals(List.of("running=false", "state=IDLE"), probe.events);

            button.cancel();
            assertEquals(2, probe.events.size());
        });
    }

    @Test
    public void cancelInsideTheDelayKeepsThePendingFaceAway() throws InterruptedException {
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.millis(80.0), LONG);
            probe.set(new Probe(button));
            button.start();
            button.cancel();
        });
        Thread.sleep(400L);
        onFx(() -> assertEquals(List.of("running=true", "running=false"), probe.get().events));
    }

    @Test
    public void cancelDuringTheHeldOutcomeDoesNothing() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(150.0));
            ref.set(button);
            button.start().succeed();

            button.cancel();

            assertEquals(State.PENDING, button.getState());
        });
        Thread.sleep(500L);
        onFx(() -> assertEquals(State.SUCCEEDED, ref.get().getState()));
    }

    @Test
    public void handleCancelsItsOwnRunOnly() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run stale = button.start();
            Run current = button.start();

            stale.cancel();
            assertTrue(button.isRunning());

            current.cancel();
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void resetFeedbackEndsAResultFaceOnly() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);

            button.resetFeedback();
            assertEquals(State.IDLE, button.getState());

            Run run = button.start();
            button.resetFeedback();
            assertEquals(State.PENDING, button.getState());
            assertTrue(button.isRunning());

            run.succeed();
            assertEquals(State.SUCCEEDED, button.getState());
            button.setDisable(true);
            button.resetFeedback();
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void startStopsTheResultTimerOfThePreviousRun() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.millis(150.0));
            ref.set(button);
            button.start().succeed();

            // The next result has no timer of its own, so only a leftover one could end it.
            button.setSucceededDuration(Duration.INDEFINITE);
            button.start().succeed();
        });
        Thread.sleep(500L);
        onFx(() -> assertEquals(State.SUCCEEDED, ref.get().getState()));
    }

    @Test
    public void disablingDoesNotTouchARunInFlight() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            Run run = button.start();

            button.setDisable(true);
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());

            run.succeed();
            assertEquals(State.SUCCEEDED, button.getState());
        });
    }

    // ==================== Run handle ====================

    @Test
    public void isCurrentLastsUntilTheRunEnds() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);

            Run settled = button.start();
            assertTrue(settled.isCurrent());
            settled.succeed();
            assertFalse(settled.isCurrent());

            Run cancelled = button.start();
            button.cancel();
            assertFalse(cancelled.isCurrent());

            Run replaced = button.start();
            Run current = button.start();
            assertFalse(replaced.isCurrent());
            assertTrue(current.isCurrent());
        });
    }

    @Test
    public void staleHandleDoesNothing() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run stale = button.start();
            Run current = button.start();
            Probe probe = new Probe(button);

            stale.updateProgress(0.7);
            stale.succeed();
            stale.fail();
            stale.cancel();

            assertEquals(-1.0, button.getProgress());
            assertTrue(probe.events.isEmpty(), probe.events.toString());
            assertTrue(current.isCurrent());
        });
    }

    @Test
    public void handleCalledFromAnotherThreadActsOnTheFxThread() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<Run> run = new AtomicReference<>();
        List<Boolean> onFxThread = new ArrayList<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            ref.set(button);
            run.set(button.start());
            button.runningProperty().addListener(o -> onFxThread.add(Platform.isFxApplicationThread()));
            button.stateProperty().addListener(o -> onFxThread.add(Platform.isFxApplicationThread()));
            button.progressProperty().addListener(o -> onFxThread.add(Platform.isFxApplicationThread()));
        });

        // From this thread: progress first, then the outcome.
        run.get().updateProgress(0.9);
        run.get().succeed();

        onFx(() -> {
            assertEquals(0.9, ref.get().getProgress());
            assertEquals(State.SUCCEEDED, ref.get().getState());
            assertEquals(List.of(true, true, true), onFxThread);
        });
    }

    @Test
    public void outcomeAndProgressOfAReplacedRunArrivingLateAreDropped() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            ref.set(button);
            Run first = button.start();
            // Queued behind this block: by the time they run, the run is stale.
            callOffFxThread(() -> {
                first.updateProgress(0.9);
                first.fail();
            });

            button.start();
        });
        onFx(() -> {
            assertTrue(ref.get().isRunning());
            assertEquals(State.PENDING, ref.get().getState());
            assertEquals(-1.0, ref.get().getProgress());
        });
    }

    @Test
    public void outcomeArrivingAfterCancelOnTheFxThreadIsDropped() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            button.setSucceededDuration(Duration.INDEFINITE);
            ref.set(button);
            Run run = button.start();
            callOffFxThread(run::succeed);

            button.cancel();
        });
        onFx(() -> assertEquals(State.IDLE, ref.get().getState()));
    }

    @Test
    public void progressUpdatesFromAnotherThreadAreCoalesced() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicInteger notifications = new AtomicInteger();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            ref.set(button);
            Run run = button.start();
            button.progressProperty().addListener((o, was, is) -> notifications.incrementAndGet());
            // The FX thread is busy here, so every update lands before any is applied.
            callOffFxThread(() -> {
                for (int i = 0; i < 1000; i++) {
                    run.updateProgress(i / 2000.0);
                }
                run.updateProgress(0.75);
            });
        });
        onFx(() -> {
            assertEquals(0.75, ref.get().getProgress());
            assertEquals(1, notifications.get());
        });
    }

    @Test
    public void queuedProgressOfAStaleHandleDoesNotSwallowTheCurrentOne() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            ref.set(button);
            Run first = button.start();
            callOffFxThread(() -> first.updateProgress(0.3));
            Run second = button.start();
            callOffFxThread(() -> second.updateProgress(0.6));
        });
        onFx(() -> assertEquals(0.6, ref.get().getProgress()));
    }

    @Test
    public void boundProgressIsLeftAlone() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            SimpleDoubleProperty source = new SimpleDoubleProperty(0.4);
            button.progressProperty().bind(source);

            Run run = button.start();
            run.updateProgress(0.8);

            assertEquals(0.4, button.getProgress());
            assertTrue(button.isRunning());
        });
    }

    @Test
    public void buttonMethodsRejectOtherThreads() {
        RXTaskButton button = new RXTaskButton("Save");

        assertThrows(IllegalStateException.class, button::start);
        assertThrows(IllegalStateException.class, button::cancel);
        assertThrows(IllegalStateException.class, button::resetFeedback);
        assertFalse(button.isRunning());
    }

    // ==================== Activation guard ====================

    @Test
    public void busyButtonSwallowsEveryActionEvent() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            AtomicInteger actions = countActions(button);

            button.fire();
            assertEquals(1, actions.get());

            // Running, the idle face still up.
            Run run = button.start();
            button.fire();
            button.fireEvent(new ActionEvent());
            assertEquals(1, actions.get());
            assertTrue(button.isRunning());

            // The pending face up.
            run.cancel();
            button.setPendingDelay(Duration.ZERO);
            button.start();
            button.fire();
            button.fireEvent(new ActionEvent());
            assertEquals(1, actions.get());
        });
    }

    @Test
    public void activationInsideTheDelayNeverCancels() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, Duration.ZERO);
            button.setCancellable(true);
            AtomicInteger actions = countActions(button);
            Run run = button.start();

            button.fire();

            assertTrue(run.isCurrent());
            assertEquals(0, actions.get());
        });
    }

    @Test
    public void activationCancelsOnlyOnceThePendingFaceHasShownForTheMinimum() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<AtomicInteger> actions = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(150.0));
            button.setCancellable(true);
            ref.set(button);
            actions.set(countActions(button));
            button.start();

            button.fire();

            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
        Thread.sleep(500L);
        onFx(() -> {
            RXTaskButton button = ref.get();
            // Not cancellable: swallowed, nothing else.
            button.setCancellable(false);
            button.fire();
            assertTrue(button.isRunning());

            button.setCancellable(true);
            button.fire();

            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
            assertEquals(0, actions.get().get());
        });
    }

    @Test
    public void secondClickOfADoubleClickRetryDoesNotCancelIt() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, Duration.millis(400.0));
            button.setCancellable(true);
            button.setFailedDuration(Duration.INDEFINITE);
            button.start().fail();
            AtomicInteger actions = new AtomicInteger();
            button.setOnAction(event -> {
                actions.incrementAndGet();
                button.start();
            });

            button.fire();
            button.fire();

            assertEquals(1, actions.get());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void activationDuringTheHeldOutcomeIsSwallowed() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<AtomicInteger> actions = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.millis(150.0));
            button.setCancellable(true);
            button.setSucceededDuration(Duration.INDEFINITE);
            ref.set(button);
            actions.set(countActions(button));
            button.start().succeed();

            button.fire();

            assertEquals(0, actions.get().get());
            assertEquals(State.PENDING, button.getState());
        });
        Thread.sleep(500L);
        onFx(() -> {
            assertEquals(State.SUCCEEDED, ref.get().getState());

            // On a result face an activation reaches onAction again.
            ref.get().fire();
            assertEquals(1, actions.get().get());
        });
    }

    @Test
    public void actionEventAimedAtAnInnerNodeIsInterceptedWhileBusy() throws InterruptedException {
        onFx(() -> {
            Button inner = new Button("inner");
            AtomicInteger innerActions = new AtomicInteger();
            inner.setOnAction(event -> innerActions.incrementAndGet());
            RXTaskButton button = button(LONG, LONG);
            button.setGraphic(inner);
            StackPane root = new StackPane(button);
            new Scene(root, 300.0, 100.0);
            root.applyCss();
            root.layout();

            Event.fireEvent(inner, new ActionEvent());
            assertEquals(1, innerActions.get());

            button.start();
            Event.fireEvent(inner, new ActionEvent());
            assertEquals(1, innerActions.get());
            button.getSkin().dispose();
        });
    }

    // ==================== Pseudo-classes ====================

    @Test
    public void pseudoClassesMirrorRunningAndStateAcrossASkinReplacement() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.setFailedDuration(Duration.INDEFINITE);
            StackPane root = new StackPane(button);
            new Scene(root, 300.0, 100.0);
            root.applyCss();

            Run run = button.start();
            assertPseudoClasses(button, true, State.PENDING);

            replaceSkin(button);
            assertTrue(button.isRunning());
            assertPseudoClasses(button, true, State.PENDING);

            run.fail();
            assertPseudoClasses(button, false, State.FAILED);

            replaceSkin(button);
            assertPseudoClasses(button, false, State.FAILED);

            button.start().succeed();
            assertPseudoClasses(button, false, State.SUCCEEDED);
            button.resetFeedback();
            assertPseudoClasses(button, false, State.IDLE);
            button.getSkin().dispose();
        });
    }

    // ==================== Re-entrant calls ====================

    @Test
    public void resetFeedbackFromTheStateListenerEndsTheResultForGood() throws InterruptedException {
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.millis(80.0));
            // Recorded ahead of the resetting listener, so each change is seen as it happens.
            probe.set(new Probe(button));
            button.stateProperty().addListener((o, was, is) -> {
                if (is == State.SUCCEEDED) {
                    button.resetFeedback();
                }
            });

            button.start().succeed();

            assertEquals(State.IDLE, button.getState());
        });
        Thread.sleep(400L);
        // The result timer was stopped: nothing changes after the reset.
        onFx(() -> assertEquals(List.of("running=true", "state=PENDING", "running=false",
                "state=SUCCEEDED", "state=IDLE"), probe.get().events));
    }

    @Test
    public void succeedFromTheStateListenerOnPendingIsHeldForTheMinimum() throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        AtomicReference<Probe> probe = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.millis(60.0), Duration.millis(200.0));
            button.setSucceededDuration(Duration.INDEFINITE);
            ref.set(button);
            Run run = button.start();
            button.stateProperty().addListener((o, was, is) -> {
                if (is == State.PENDING) {
                    run.succeed();
                }
            });
            probe.set(new Probe(button));
            button.runningProperty().addListener((o, was, is) ->
                    probe.get().events.add("while " + button.getState()));
        });
        Thread.sleep(800L);
        onFx(() -> {
            assertEquals(State.SUCCEEDED, ref.get().getState());
            assertEquals(List.of("running=false", "while PENDING", "state=PENDING", "state=SUCCEEDED"),
                    probe.get().events);
        });
    }

    @Test
    public void staleHandleCalledWhileCancellingDoesNotStopTheCancellation() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run run = button.start();
            button.runningProperty().addListener((o, was, is) -> {
                if (!is) {
                    // What an aborted upload does: its callback reports a failure.
                    run.fail();
                }
            });

            button.cancel();

            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void ineffectiveCallsWhileAnOutcomeArrivesDoNotStopIt() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.runningProperty().addListener((o, was, is) -> {
                if (!is) {
                    button.resetFeedback();
                    button.cancel();
                }
            });

            button.start().succeed();

            assertEquals(State.SUCCEEDED, button.getState());
        });
    }

    @Test
    public void resetFeedbackWhileRestartingFromAResultFaceStillShowsPending() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.start().succeed();
            button.runningProperty().addListener((o, was, is) -> {
                if (is) {
                    button.resetFeedback();
                }
            });

            Run run = button.start();

            assertTrue(run.isCurrent());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void cancelFromTheProgressListenerOfAStartLeavesNoPendingFaceBehind() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            button.setProgress(0.5);
            button.progressProperty().addListener((o, was, is) -> {
                if (is.doubleValue() < 0.0) {
                    button.cancel();
                }
            });

            Run run = button.start();

            assertFalse(run.isCurrent());
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void cancelWhileRestartingFromAResultFaceLeavesNoResultFaceBehind() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            button.start().succeed();
            button.runningProperty().addListener((o, was, is) -> {
                if (is) {
                    button.cancel();
                    button.setProgress(0.5);
                }
            });

            Run run = button.start();

            assertFalse(run.isCurrent());
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
            // The cancelled start writes nothing more, not even the progress reset.
            assertEquals(0.5, button.getProgress());
        });
    }

    @Test
    public void startWhileCancellingKeepsThePendingFaceForTheNewRun() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            button.start();
            // Recorded ahead of the restarting listener, so each change is seen as it happens.
            Probe probe = new Probe(button);
            AtomicReference<Run> restarted = new AtomicReference<>();
            button.runningProperty().addListener((o, was, is) -> {
                if (!is && restarted.get() == null) {
                    restarted.set(button.start());
                }
            });

            button.cancel();

            assertTrue(restarted.get().isCurrent());
            assertTrue(button.isRunning());
            // The pending face never left: no state change at all.
            assertEquals(State.PENDING, button.getState());
            assertEquals(List.of("running=false", "running=true"), probe.events);
        });
    }

    @Test
    public void startWhileAnOutcomeArrivesReplacesTheResult() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.INDEFINITE);
            Run first = button.start();
            AtomicReference<Run> second = new AtomicReference<>();
            button.runningProperty().addListener((o, was, is) -> {
                if (!is && second.get() == null) {
                    second.set(button.start());
                }
            });

            first.succeed();

            // The first run's result face is not written over the new run.
            assertTrue(second.get().isCurrent());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void startThenCancelWhileAnOutcomeArrivesLeavesNoResultFaceWithoutATimer()
            throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.millis(80.0));
            Run first = button.start();
            boolean[] done = new boolean[1];
            button.runningProperty().addListener((o, was, is) -> {
                if (!is && !done[0]) {
                    done[0] = true;
                    button.start();
                    button.cancel();
                }
            });

            first.succeed();

            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void disablingOrReplacingTheSkinFromAListenerDoesNotStallTheStateMachine()
            throws InterruptedException {
        AtomicReference<RXTaskButton> ref = new AtomicReference<>();
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setSucceededDuration(Duration.millis(80.0));
            StackPane root = new StackPane(button);
            new Scene(root, 300.0, 100.0);
            root.applyCss();
            ref.set(button);
            button.stateProperty().addListener((o, was, is) -> {
                button.setDisable(is != State.IDLE);
                replaceSkin(button);
            });

            Run run = button.start();
            assertEquals(State.PENDING, button.getState());
            run.succeed();
            assertEquals(State.SUCCEEDED, button.getState());
        });
        Thread.sleep(400L);
        onFx(() -> {
            assertEquals(State.IDLE, ref.get().getState());
            assertFalse(ref.get().isDisabled());
            ref.get().getSkin().dispose();
        });
    }

    // ==================== Helpers ====================

    private static RXTaskButton button(Duration pendingDelay, Duration pendingMinDuration) {
        RXTaskButton button = new RXTaskButton("Save");
        button.setPendingDelay(pendingDelay);
        button.setPendingMinDuration(pendingMinDuration);
        return button;
    }

    private static AtomicInteger countActions(RXTaskButton button) {
        AtomicInteger actions = new AtomicInteger();
        button.setOnAction(event -> actions.incrementAndGet());
        return actions;
    }

    // JavaFX 17 ignores setSkin for a skin of the installed skin's class, so
    // the replacement alternates between two subclasses.
    private static void replaceSkin(RXTaskButton button) {
        button.setSkin(button.getSkin() instanceof FirstSkin ? new SecondSkin(button) : new FirstSkin(button));
    }

    private static final class FirstSkin extends RXButtonSkin {

        FirstSkin(RXTaskButton button) {
            super(button);
        }
    }

    private static final class SecondSkin extends RXButtonSkin {

        SecondSkin(RXTaskButton button) {
            super(button);
        }
    }

    private static void assertPseudoClasses(RXTaskButton button, boolean running, State state) {
        assertEquals(running, button.getPseudoClassStates().contains(RUNNING));
        assertEquals(state == State.PENDING, button.getPseudoClassStates().contains(PENDING));
        assertEquals(state == State.SUCCEEDED, button.getPseudoClassStates().contains(SUCCEEDED));
        assertEquals(state == State.FAILED, button.getPseudoClassStates().contains(FAILED));
    }

    @SuppressWarnings("unchecked")
    private static CssMetaData<RXTaskButton, Duration> durationMetaData(RXTaskButton button, String name) {
        for (CssMetaData<? extends Styleable, ?> metaData : button.getCssMetaData()) {
            if (name.equals(metaData.getProperty())) {
                return (CssMetaData<RXTaskButton, Duration>) metaData;
            }
        }
        throw new AssertionError(name + " is not styleable");
    }

    // Runs the body on another thread and waits for it, so whatever it hands
    // over to the FX thread is queued behind the caller's own FX block.
    private static void callOffFxThread(Runnable body) {
        Thread thread = new Thread(body, "task-button-test-worker");
        thread.start();
        try {
            thread.join(10_000L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
        assertFalse(thread.isAlive());
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

    // Records every change of running and state, in order.
    private static final class Probe {

        final List<String> events = new ArrayList<>();

        Probe(RXTaskButton button) {
            button.runningProperty().addListener((o, was, is) -> events.add("running=" + is));
            button.stateProperty().addListener((o, was, is) -> events.add("state=" + is));
        }
    }
}

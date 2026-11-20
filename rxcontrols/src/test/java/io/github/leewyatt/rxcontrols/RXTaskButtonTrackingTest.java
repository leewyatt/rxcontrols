package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTaskButton.Run;
import io.github.leewyatt.rxcontrols.RXTaskButton.State;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how RXTaskButton follows a Worker or a CompletionStage: the state
 * mapping, progress, cancellation in both directions, sources that have
 * already finished, and sources swapped from listeners.
 */
public class RXTaskButtonTrackingTest {

    // Long enough that a window never ends during a test body.
    private static final Duration LONG = Duration.seconds(30.0);

    private static final String FAILURE = "failed on purpose";

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

    // ==================== Worker mapping ====================

    @Test
    public void taskIsFollowedFromReadyToSucceeded() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        GatedTask task = new GatedTask();
        onFx(() -> {
            Run run = button.track(task);

            assertEquals(Worker.State.READY, task.getState());
            assertTrue(run.isCurrent());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });

        Thread worker = new Thread(task, "task-button-test-task");
        worker.setDaemon(true);
        worker.start();
        assertTrue(task.entered.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            assertEquals(Worker.State.RUNNING, task.getState());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });

        task.release.countDown();
        worker.join(10_000L);
        onFx(() -> {
            assertEquals(Worker.State.SUCCEEDED, task.getState());
            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());
        });
    }

    @Test
    public void failedTaskShowsTheFailedFace() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        onFx(() -> {
            Task<Void> task = failingTask();
            button.track(task);
            task.run();
        });
        onFx(() -> {
            assertFalse(button.isRunning());
            assertEquals(State.FAILED, button.getState());
        });
    }

    @Test
    public void cancelledWorkerReturnsToIdleWithoutBeingCancelledAgain() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            Probe probe = new Probe(button);
            ReportingTask task = new ReportingTask();
            Run run = button.track(task);

            task.cancel();

            assertFalse(run.isCurrent());
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                    probe.events);
            assertEquals(1, task.cancelCalls);
        });
    }

    @Test
    public void finishedWorkerGivesItsOutcomeBeforeTrackReturns() throws InterruptedException {
        Task<Void> succeeded = succeedingTask();
        Task<Void> failed = failingTask();
        ReportingTask cancelled = new ReportingTask();
        onFx(() -> {
            succeeded.run();
            failed.run();
            cancelled.cancel();
        });
        onFx(() -> {
            RXTaskButton first = button(LONG, LONG);
            Run run = first.track(succeeded);
            assertFalse(run.isCurrent());
            assertFalse(first.isRunning());
            assertEquals(State.SUCCEEDED, first.getState());

            RXTaskButton second = button(LONG, LONG);
            second.track(failed);
            assertFalse(second.isRunning());
            assertEquals(State.FAILED, second.getState());

            RXTaskButton third = button(LONG, LONG);
            Probe probe = new Probe(third);
            third.track(cancelled);
            assertEquals(List.of("running=true", "running=false"), probe.events);
            assertEquals(1, cancelled.cancelCalls);
        });
    }

    @Test
    public void finishedWorkerWithoutADelayStillShowsPendingForTheMinimum() throws InterruptedException {
        Task<Void> succeeded = succeedingTask();
        onFx(succeeded::run);
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);

            button.track(succeeded);

            assertFalse(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void progressFollowsTheWorkerStartingWithItsCurrentValue() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            ReportingTask task = new ReportingTask();
            task.report(1.0, 4.0);

            button.track(task);
            assertEquals(0.25, button.getProgress());

            task.report(2.0, 4.0);
            assertEquals(0.5, button.getProgress());

            // A new run releases the task; its progress is no longer followed.
            button.start();
            task.report(3.0, 4.0);
            assertEquals(-1.0, button.getProgress());
        });
    }

    @Test
    public void boundProgressIsNotWrittenWhileFollowing() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            button.progressProperty().bind(new SimpleDoubleProperty(0.9));
            ReportingTask task = new ReportingTask();
            task.report(1.0, 4.0);

            button.track(task);
            task.report(2.0, 4.0);

            assertEquals(0.9, button.getProgress());
            assertTrue(button.isRunning());
        });
    }

    @Test
    public void buttonCancelCancelsTheWorkerOnce() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            ReportingTask task = new ReportingTask();
            button.track(task);

            button.cancel();

            assertTrue(task.isCancelled());
            assertEquals(1, task.cancelCalls);
            assertFalse(button.isRunning());
        });
    }

    @Test
    public void workerIsCancelledAfterTheButtonHasReturnedToIdle() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            ReportingTask task = new ReportingTask();
            List<String> seen = new ArrayList<>();
            task.beforeCancel = () -> seen.add(button.isRunning() + " " + button.getState());
            button.track(task);

            button.cancel();

            assertEquals(List.of("false IDLE"), seen);
        });
    }

    @Test
    public void activationOnACancellableButtonCancelsTheWorker() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.setCancellable(true);
            ReportingTask task = new ReportingTask();
            button.track(task);

            button.fire();

            assertTrue(task.isCancelled());
            assertFalse(button.isRunning());
        });
    }

    @Test
    public void handleCancelCancelsTheWorker() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            ReportingTask task = new ReportingTask();
            Run run = button.track(task);

            run.cancel();

            assertTrue(task.isCancelled());
            assertFalse(button.isRunning());
        });
    }

    @Test
    public void startReleasesTheWorkerWithoutCancellingIt() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Task<Void> task = succeedingTask();
        AtomicReference<Run> second = new AtomicReference<>();
        onFx(() -> {
            button.track(task);
            second.set(button.start());

            assertFalse(task.isCancelled());
            task.run();
        });
        onFx(() -> {
            assertEquals(Worker.State.SUCCEEDED, task.getState());
            assertTrue(second.get().isCurrent());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void trackWhilePendingKeepsTheFace() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, LONG);
            Run first = button.track(new CompletableFuture<String>());
            Probe probe = new Probe(button);

            Run second = button.track(new ReportingTask());

            assertFalse(first.isCurrent());
            assertTrue(second.isCurrent());
            assertEquals(List.of(), probe.events);
        });
    }

    // ==================== Service ====================

    @Test
    public void scheduledHandlerFollowsEveryRunOfAService() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        ManualService service = new ManualService();
        onFx(() -> {
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_SCHEDULED,
                    event -> button.track(service));
            service.start();

            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
            service.runNext();
        });
        onFx(() -> {
            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());

            // Restarted after it has finished: straight from the result face.
            probe.events.clear();
            service.restart();
            assertEquals(List.of("running=true", "state=PENDING"), probe.events);

            // Restarted while it runs: the cancelled run takes the button to idle first.
            probe.events.clear();
            service.restart();
            assertEquals(List.of("running=false", "state=IDLE", "running=true", "state=PENDING"),
                    probe.events);

            // The cancelled task does nothing; the last one succeeds.
            service.runNext();
            service.runNext();
        });
        onFx(() -> {
            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());
        });
    }

    @Test
    public void startBeforeARestartKeepsThePendingFace() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        ManualService service = new ManualService();
        Probe probe = new Probe(button);
        onFx(() -> {
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_SCHEDULED,
                    event -> button.track(service));
            service.start();
            probe.events.clear();

            button.start();
            service.restart();

            assertEquals(List.of(), probe.events);
            assertTrue(button.isRunning());
            // The cancelled task does nothing; the new one succeeds.
            service.runNext();
            service.runNext();
        });
        onFx(() -> assertEquals(List.of("running=false", "state=SUCCEEDED"), probe.events));
    }

    @Test
    public void restartThenTrackFollowsTheNewRun() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        ManualService service = finishedService();
        onFx(() -> {
            service.restart();
            Run run = button.track(service);

            assertTrue(run.isCurrent());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void trackThenRestartShowsTheLeftoverOutcomeAndMissesTheNewRun() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        ManualService service = finishedService();
        onFx(() -> {
            Run run = button.track(service);
            service.restart();

            assertFalse(run.isCurrent());
            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());
            assertEquals(Worker.State.SCHEDULED, service.getState());
        });
    }

    @Test
    public void serviceRestartedFromItsOwnFailedHandlerLooksCancelled() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        ManualService service = new ManualService();
        service.failing = true;
        onFx(() -> {
            service.setOnFailed(event -> {
                service.failing = false;
                service.restart();
            });
            service.start();
            button.track(service);
            service.runNext();
        });
        onFx(() -> {
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                    probe.events);
            assertEquals(Worker.State.SCHEDULED, service.getState());
        });
    }

    @Test
    public void inFlightSuccessOfThePreviousRunIsNotCountedForTheNextOne() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        ManualService service = new ManualService();
        AtomicReference<Run> secondStep = new AtomicReference<>();
        onFx(() -> {
            // The handler runs before the button's own listener hears of the success.
            service.setOnSucceeded(event -> secondStep.set(button.start()));
            service.start();
            button.track(service);
            service.runNext();
        });
        onFx(() -> {
            assertTrue(secondStep.get().isCurrent());
            assertTrue(button.isRunning());
            assertEquals(List.of("running=true", "state=PENDING"), probe.events);
        });
    }

    // ==================== ScheduledService ====================

    @Test
    public void scheduledServiceIsRejected() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        ScheduledService<Void> service = new ScheduledService<>() {
            @Override
            protected Task<Void> createTask() {
                return succeedingTask();
            }
        };
        onFx(() -> {
            IllegalArgumentException thrown =
                    assertThrows(IllegalArgumentException.class, () -> button.track(service));

            assertTrue(thrown.getMessage().contains("WORKER_STATE_RUNNING"));
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void scheduledServiceCanDriveTheButtonThroughTheHandle() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Run> current = new AtomicReference<>();
        AtomicInteger rounds = new AtomicInteger();
        ScheduledService<Void> service = new ScheduledService<>() {
            @Override
            protected Task<Void> createTask() {
                return rounds.incrementAndGet() == 2 ? failingTask() : succeedingTask();
            }
        };
        onFx(() -> {
            service.setExecutor(Runnable::run);
            service.setPeriod(Duration.millis(20.0));
            service.setRestartOnFailure(false);
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_RUNNING,
                    event -> current.set(button.start()));
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                    event -> current.get().succeed());
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> {
                current.get().fail();
                finished.countDown();
            });
            service.start();
        });

        assertTrue(finished.await(10, TimeUnit.SECONDS));
        onFx(() -> assertEquals(List.of(
                "running=true", "state=PENDING", "running=false", "state=SUCCEEDED",
                "running=true", "state=PENDING", "running=false", "state=FAILED"), probe.events));
    }

    @Test
    public void scheduledServiceCancelledInARoundCancelsTheRunThroughTheHandle()
            throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        GatedTask task = new GatedTask();
        AtomicReference<Run> current = new AtomicReference<>();
        ScheduledService<Void> service = new ScheduledService<>() {
            @Override
            protected Task<Void> createTask() {
                return task;
            }
        };
        onFx(() -> {
            service.setExecutor(command -> {
                Thread worker = new Thread(command, "task-button-test-round");
                worker.setDaemon(true);
                worker.start();
            });
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_RUNNING,
                    event -> current.set(button.start()));
            service.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED,
                    event -> current.get().cancel());
            service.start();
        });
        assertTrue(task.entered.await(10, TimeUnit.SECONDS));
        onFx(() -> {
            assertTrue(button.isRunning());

            service.cancel();

            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                    probe.events);
        });
        task.release.countDown();
    }

    // ==================== CompletionStage ====================

    @Test
    public void stageCompletedOnAnotherThreadChangesTheButtonOnTheFxThread() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        CompletableFuture<String> future = new CompletableFuture<>();
        List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
        button.runningProperty().addListener(
                (o, was, is) -> onFxThread.add(Platform.isFxApplicationThread()));
        button.stateProperty().addListener(
                (o, was, is) -> onFxThread.add(Platform.isFxApplicationThread()));
        onFx(() -> button.track(future));

        callOffFxThread(() -> future.complete("done"));

        onFx(() -> {
            assertFalse(button.isRunning());
            assertEquals(State.SUCCEEDED, button.getState());
        });
        assertEquals(List.of(true, true, true, true), onFxThread);
    }

    @Test
    public void stageCancelledOnAnotherThreadChangesTheButtonOnTheFxThread() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        CompletableFuture<String> future = new CompletableFuture<>();
        List<Boolean> onFxThread = new CopyOnWriteArrayList<>();
        button.runningProperty().addListener(
                (o, was, is) -> onFxThread.add(Platform.isFxApplicationThread()));
        button.stateProperty().addListener(
                (o, was, is) -> onFxThread.add(Platform.isFxApplicationThread()));
        onFx(() -> button.track(future));

        callOffFxThread(() -> future.cancel(false));

        onFx(() -> {
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
        assertEquals(List.of(true, true, true, true), onFxThread);
    }

    @Test
    public void completedStageGivesItsOutcomeBeforeTrackReturns() throws InterruptedException {
        onFx(() -> {
            RXTaskButton first = button(LONG, LONG);
            Run run = first.track(CompletableFuture.completedFuture("done"));
            assertFalse(run.isCurrent());
            assertFalse(first.isRunning());
            assertEquals(State.SUCCEEDED, first.getState());

            RXTaskButton second = button(LONG, LONG);
            second.track(CompletableFuture.failedFuture(new IllegalStateException(FAILURE)));
            assertFalse(second.isRunning());
            assertEquals(State.FAILED, second.getState());
        });
    }

    @Test
    public void stageFailureShowsTheFailedFace() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> future = new CompletableFuture<>();
            button.track(future.thenApply(String::trim));

            future.completeExceptionally(new IllegalStateException(FAILURE));

            assertEquals(State.FAILED, button.getState());
        });
    }

    @Test
    public void cancelledStageCountsAsCancelNotFailure() throws InterruptedException {
        onFx(() -> {
            RXTaskButton direct = button(Duration.ZERO, Duration.ZERO);
            Probe directProbe = new Probe(direct);
            CompletableFuture<String> cancelled = new CompletableFuture<>();
            direct.track(cancelled);
            cancelled.cancel(false);
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                    directProbe.events);

            // A dependent stage reports the cancellation wrapped in a CompletionException.
            RXTaskButton wrapped = button(Duration.ZERO, Duration.ZERO);
            Probe wrappedProbe = new Probe(wrapped);
            CompletableFuture<String> upstream = new CompletableFuture<>();
            wrapped.track(upstream.thenApply(String::trim));
            upstream.cancel(false);
            assertEquals(List.of("running=true", "state=PENDING", "running=false", "state=IDLE"),
                    wrappedProbe.events);

            RXTaskButton explicit = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> future = new CompletableFuture<>();
            explicit.track(future);
            future.completeExceptionally(new CompletionException(new CancellationException()));
            assertEquals(State.IDLE, explicit.getState());
            assertFalse(explicit.isRunning());
        });
    }

    @Test
    public void cancelCancelsTheStage() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            CompletableFuture<String> future = new CompletableFuture<>();
            button.track(future);

            button.cancel();

            assertTrue(future.isCancelled());
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void stageIsCancelledWithInterruption() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(LONG, LONG);
            List<Boolean> interrupts = new ArrayList<>();
            CompletableFuture<String> future = new CompletableFuture<>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    interrupts.add(mayInterruptIfRunning);
                    return super.cancel(mayInterruptIfRunning);
                }
            };
            button.track(future);

            button.cancel();

            assertEquals(List.of(true), interrupts);
        });
    }

    @Test
    public void cancelToleratesAStageThatIsNotAFuture() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> delegate = new CompletableFuture<>();
            AtomicInteger conversions = new AtomicInteger();
            button.track(opaqueStage(delegate, conversions));

            button.cancel();

            assertEquals(1, conversions.get());
            assertFalse(button.isRunning());
            assertEquals(State.IDLE, button.getState());
        });
    }

    @Test
    public void stageThatReportsItsOwnCancellationIsNotCancelledBack() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> delegate = new CompletableFuture<>();
            AtomicInteger conversions = new AtomicInteger();
            button.track(opaqueStage(delegate, conversions));

            delegate.cancel(false);

            assertFalse(button.isRunning());
            assertEquals(0, conversions.get());
        });
    }

    @Test
    public void outcomeCompletedOffThreadIsDroppedByACancelOnTheFxThread() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        Probe probe = new Probe(button);
        onFx(() -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            button.track(future);
            // The outcome is now queued for the FX thread, behind this block.
            callOffFxThread(() -> future.complete("done"));

            button.cancel();
        });
        onFx(() -> assertEquals(
                List.of("running=true", "state=PENDING", "running=false", "state=IDLE"), probe.events));
    }

    // ==================== Handle and source together ====================

    @Test
    public void handleFromTrackCanReportBeforeTheSource() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> future = new CompletableFuture<>();
            Run run = button.track(future);

            run.succeed();
            future.completeExceptionally(new IllegalStateException(FAILURE));

            assertEquals(State.SUCCEEDED, button.getState());
            assertFalse(future.isCancelled());
        });
    }

    @Test
    public void staleSourceOutcomeAfterCancelOrRestartIsIgnored() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            AtomicInteger conversions = new AtomicInteger();
            CompletableFuture<String> cancelled = new CompletableFuture<>();
            button.track(opaqueStage(cancelled, conversions));
            button.cancel();
            cancelled.complete("done");
            assertEquals(State.IDLE, button.getState());

            CompletableFuture<String> replaced = new CompletableFuture<>();
            button.track(replaced);
            Run next = button.start();
            replaced.completeExceptionally(new IllegalStateException(FAILURE));
            assertTrue(next.isCurrent());
            assertEquals(State.PENDING, button.getState());
        });
    }

    // ==================== Re-entrant calls ====================

    @Test
    public void trackingAnotherSourceFromTheCancelListenerStillCancelsTheOldOne()
            throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            CompletableFuture<String> first = new CompletableFuture<>();
            CompletableFuture<String> second = new CompletableFuture<>();
            AtomicReference<Run> next = new AtomicReference<>();
            button.track(first);
            button.runningProperty().addListener((o, was, is) -> {
                if (!is && next.get() == null) {
                    next.set(button.track(second));
                }
            });

            button.cancel();

            assertTrue(first.isCancelled());
            assertFalse(second.isCancelled());
            assertTrue(next.get().isCurrent());
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());
        });
    }

    @Test
    public void retrackingTheSameServiceFromTheCancelListenerDoesNotCancelTheNewRun()
            throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
        ManualService service = new ManualService();
        onFx(() -> {
            service.start();
            button.track(service);
            AtomicReference<Run> next = new AtomicReference<>();
            button.runningProperty().addListener((o, was, is) -> {
                if (!is && next.get() == null) {
                    service.restart();
                    next.set(button.track(service));
                }
            });

            button.cancel();

            assertEquals(Worker.State.SCHEDULED, service.getState());
            assertTrue(next.get().isCurrent());
            assertTrue(button.isRunning());
        });
    }

    @Test
    public void cancelFromTheStartListenerCancelsTheSource() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            Probe probe = new Probe(button);
            ReportingTask task = new ReportingTask();
            button.runningProperty().addListener((o, was, is) -> {
                if (is) {
                    button.cancel();
                }
            });

            Run run = button.track(task);

            assertTrue(task.isCancelled());
            assertEquals(1, task.cancelCalls);
            assertFalse(run.isCurrent());
            assertEquals(List.of("running=true", "running=false"), probe.events);
        });
    }

    @Test
    public void resetFeedbackFromTheStartListenerStillAttachesTheSource() throws InterruptedException {
        onFx(() -> {
            RXTaskButton button = button(Duration.ZERO, Duration.ZERO);
            button.start().succeed();
            button.runningProperty().addListener((o, was, is) -> {
                if (is) {
                    button.resetFeedback();
                }
            });
            CompletableFuture<String> future = new CompletableFuture<>();

            button.track(future);
            assertTrue(button.isRunning());
            assertEquals(State.PENDING, button.getState());

            future.completeExceptionally(new IllegalStateException(FAILURE));
            assertEquals(State.FAILED, button.getState());
        });
    }

    // ==================== Arguments and threads ====================

    @Test
    public void trackRejectsNullAndOtherThreads() throws InterruptedException {
        RXTaskButton button = button(Duration.ZERO, Duration.ZERO);

        assertThrows(IllegalStateException.class, () -> button.track(new ReportingTask()));
        assertThrows(IllegalStateException.class, () -> button.track(new CompletableFuture<String>()));
        onFx(() -> {
            assertThrows(NullPointerException.class, () -> button.track((Worker<?>) null));
            assertThrows(NullPointerException.class, () -> button.track((CompletionStage<?>) null));
            assertFalse(button.isRunning());
            assertNotEquals(State.PENDING, button.getState());
        });
    }

    // ==================== Helpers ====================

    // Result faces stay until the next run, so no test depends on when they end.
    private static RXTaskButton button(Duration pendingDelay, Duration pendingMinDuration) {
        RXTaskButton button = new RXTaskButton("Save");
        button.setPendingDelay(pendingDelay);
        button.setPendingMinDuration(pendingMinDuration);
        button.setSucceededDuration(Duration.INDEFINITE);
        button.setFailedDuration(Duration.INDEFINITE);
        return button;
    }

    private static Task<Void> succeedingTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                return null;
            }
        };
    }

    private static Task<Void> failingTask() {
        return new Task<>() {
            @Override
            protected Void call() {
                throw new IllegalStateException(FAILURE);
            }
        };
    }

    private static ManualService finishedService() throws InterruptedException {
        ManualService service = new ManualService();
        onFx(() -> {
            service.start();
            service.runNext();
        });
        onFx(() -> assertEquals(Worker.State.SUCCEEDED, service.getState()));
        return service;
    }

    // A stage that completes like the delegate but cannot be turned into a future.
    @SuppressWarnings("unchecked")
    private static CompletionStage<String> opaqueStage(CompletableFuture<String> delegate,
                                                        AtomicInteger conversions) {
        return (CompletionStage<String>) Proxy.newProxyInstance(
                RXTaskButtonTrackingTest.class.getClassLoader(),
                new Class<?>[] {CompletionStage.class},
                (proxy, method, args) -> {
                    if ("toCompletableFuture".equals(method.getName())) {
                        conversions.incrementAndGet();
                        throw new UnsupportedOperationException();
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
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

    // A task the test can cancel and move forward by hand on the FX thread.
    private static final class ReportingTask extends Task<Void> {

        int cancelCalls;
        Runnable beforeCancel = () -> { };

        @Override
        protected Void call() {
            return null;
        }

        void report(double workDone, double total) {
            updateProgress(workDone, total);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelCalls++;
            beforeCancel.run();
            return super.cancel(mayInterruptIfRunning);
        }
    }

    // A task that stays in its body until the test lets it go.
    private static final class GatedTask extends Task<Void> {

        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override
        protected Void call() throws InterruptedException {
            entered.countDown();
            release.await();
            return null;
        }
    }

    // A service whose tasks wait in a queue until the test runs them. A task
    // run on the FX thread reports its state changes in later FX runnables.
    private static final class ManualService extends Service<Void> {

        final Deque<Runnable> queued = new ArrayDeque<>();
        boolean failing;

        ManualService() {
            setExecutor(queued::add);
        }

        @Override
        protected Task<Void> createTask() {
            return failing ? failingTask() : succeedingTask();
        }

        void runNext() {
            queued.remove().run();
        }
    }
}

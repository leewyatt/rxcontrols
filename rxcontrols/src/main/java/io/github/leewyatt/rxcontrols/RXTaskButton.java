package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.TaskButtonAccess;
import io.github.leewyatt.rxcontrols.internal.TaskButtonAccess.Activation;
import io.github.leewyatt.rxcontrols.internal.feedback.FeedbackDurations;
import io.github.leewyatt.rxcontrols.skins.RXTaskButtonSkin;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Worker;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Button that shows the life of one piece of work: a pending face while the
 * work is in flight, then a succeeded or failed face, then the normal face
 * again. The work can be anything, asynchronous or synchronous: a callback
 * API, a {@code CompletionStage}, a {@code Service}; a JavaFX {@code Task} is
 * only one of the possibilities. The button does not run the work, it is only
 * told when it starts and how it ends.
 *
 * <p>The button publishes two read-only properties, and they answer different
 * questions:</p>
 * <ul>
 * <li>{@link #runningProperty() running} is whether the work is in flight. It
 * turns {@code true} the moment a run starts and {@code false} the moment an
 * outcome arrives or the run is cancelled. Use it to ask "is the button
 * busy", for example
 * {@code close.disableProperty().bind(save.runningProperty())}.</li>
 * <li>{@link #stateProperty() state} is <b>the face the button shows, not the
 * outcome of the work</b>. The pending face waits for the
 * {@link #pendingDelayProperty() pendingDelay} and then stays for at least
 * the {@link #pendingMinDurationProperty() pendingMinDuration}, so an outcome
 * can be shown later than it arrived; with a result duration of
 * {@code Duration.ZERO} the state never becomes {@link State#SUCCEEDED} or
 * {@link State#FAILED} at all. Take the outcome from the callback that
 * reported it, never from {@code state}.</li>
 * </ul>
 *
 * <p>A run starts when the application calls {@link #start()}, normally from
 * {@code onAction}; the button never starts one on its own, so the handler
 * can decide not to (a form that failed validation). {@code start()} returns
 * the {@link Run} handle of that run, and the outcome is reported through
 * it:</p>
 *
 * <pre>{@code
 * // A callback API: the callbacks may run on any thread
 * upload.setOnAction(event -> {
 *     RXTaskButton.Run run = upload.start();
 *     uploader.upload(file, new UploadListener() {
 *         public void onProgress(double fraction) {
 *             run.updateProgress(fraction);
 *         }
 *
 *         public void onDone(boolean ok) {
 *             if (ok) {
 *                 run.succeed();
 *             } else {
 *                 run.fail();
 *             }
 *         }
 *     });
 * });
 *
 * // A quick synchronous operation: from the normal face, Save -> Saved
 * // with no pending face
 * save.setOnAction(event -> {
 *     RXTaskButton.Run run = save.start();
 *     if (store.save(document)) {
 *         run.succeed();
 *     } else {
 *         run.fail();
 *     }
 * });
 *
 * // Only the pending state, no result faces
 * button.setSucceededDuration(Duration.ZERO);
 * button.setFailedDuration(Duration.ZERO);
 * }</pre>
 *
 * <p>A handle is good for its own run only. Once that run has an outcome, was
 * cancelled or was replaced by a newer run, every method of the handle does
 * nothing, so a late callback can never report into a newer run. Always
 * report both outcomes: a run that never gets one keeps the button busy.</p>
 *
 * <p>For work that already is a {@code CompletionStage} or a JavaFX
 * {@code Worker}, prefer {@link #track(CompletionStage) track}: it starts a
 * run and reports its outcome for you. The verb is deliberate: the button
 * follows the work, it never starts it.</p>
 *
 * <pre>{@code
 * // A CompletionStage, completed on any thread
 * send.setOnAction(event -> send.track(client.sendAsync(request, handler)));
 *
 * // A Task: the application still executes it
 * export.setOnAction(event -> {
 *     Task<Void> task = new ExportTask(file);
 *     export.track(task);
 *     executor.execute(task);
 * });
 *
 * // A Service: wired once, the button then follows every run of it
 * service.addEventHandler(WorkerStateEvent.WORKER_STATE_SCHEDULED, event -> sync.track(service));
 * sync.setOnAction(event -> service.restart());
 * }</pre>
 *
 * <p>When a tracked worker or stage fails, the button does not read its
 * exception: nothing of it goes into a text, an accessible text, a tooltip
 * or a log. The exception stays where it was, for the application to
 * handle.</p>
 *
 * <p>Two delays keep quick work from flashing. The pending face only appears
 * when the run is still in flight after the {@code pendingDelay}; work that
 * ends inside that window goes straight from the normal face to its result.
 * Once the pending face shows, it stays for at least the
 * {@code pendingMinDuration}; an outcome arriving earlier is held back until
 * then. The delay only applies while the normal face shows: a run started
 * from a result face (a retry) or while the pending face is up shows the
 * pending face at once.</p>
 *
 * <p>While the button is busy, that is while it is running or shows the
 * pending face, it swallows every {@code ActionEvent}: {@code onAction} does
 * not run, whether the activation came from the mouse, the keyboard, an
 * accelerator or application code. The guard is an event filter, so it also
 * intercepts an {@code ActionEvent} aimed at a control placed inside the
 * button. With {@link #cancellableProperty() cancellable} set, an activation
 * cancels the run instead, but only once the pending face has shown for the
 * {@code pendingMinDuration}: the second click of a double click never
 * cancels a run that has just started. Activating the button on a result
 * face reaches {@code onAction} again, which is how a failed run is
 * retried. A mouse press or key stroke does only what it would have done
 * when it {@linkplain #armedProperty() armed} the button: if the run ends
 * while the button is held down to cancel it, the release does nothing,
 * rather than reach {@code onAction} and start the work again; and a press
 * that armed the button for its action does not cancel a run started
 * meanwhile.</p>
 *
 * <p>The run belongs to the application, not to the button: disabling the
 * button, hiding it or removing it from the scene changes nothing about a run
 * in flight, and its outcome is shown as usual. The state lives in the
 * control, so it also survives a skin replacement; only what a mouse press
 * under way armed the button for is forgotten with the old skin: its
 * release is then an ordinary activation.</p>
 *
 * <p>The accessible name of the button follows the face shown: the text of
 * that face when it has one, the button's own text otherwise, and the
 * accessible text of its graphic when it has no text either, unless an
 * {@code accessibleText} is set, which always wins. A screen reader is not
 * guaranteed to announce the change on its own, so a failure that matters
 * should also be conveyed another way, for example with a dialog or by
 * moving the focus. Mnemonics are not supported: {@code mnemonicParsing}
 * starts {@code false} and has no effect.</p>
 *
 * <p>{@link #start()}, the {@code track} methods, {@link #cancel()} and
 * {@link #resetFeedback()} must be called on the JavaFX application thread and
 * throw an
 * {@code IllegalStateException} elsewhere. The methods of a {@link Run} can be
 * called from any thread. Do not open a nested event loop (a
 * {@code showAndWait} dialog) from a listener of {@code running} or
 * {@code state}: the button's timers keep firing inside it, in the middle of
 * the change being reported.</p>
 *
 * <p>The button never shows why a run failed: {@link Run#fail()} takes no
 * argument. An application that wants to show a reason sets the
 * {@link #failedTextProperty() failedText} before reporting the failure.</p>
 */
public class RXTaskButton extends RXButton {

    /**
     * The face a task button shows.
     */
    public enum State {
        /** The normal face: the button's own text and graphic. */
        IDLE,
        /** The pending face, shown while a run is in flight. */
        PENDING,
        /** The face shown after a run succeeded. */
        SUCCEEDED,
        /** The face shown after a run failed. */
        FAILED
    }

    private static final String DEFAULT_STYLE_CLASS = "rx-task-button";

    static {
        // The skin is in another package and has to know what an activation
        // would do and when it turns into a cancel; both depend on a private
        // timer.
        TaskButtonAccess.setActivationProbe(RXTaskButton::activation);
        TaskButtonAccess.setCancelReadyProbe(button -> button.cancelReady.getReadOnlyProperty());
    }

    private static final PseudoClass RUNNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("running");
    private static final PseudoClass PENDING_PSEUDO_CLASS = PseudoClass.getPseudoClass("pending");
    private static final PseudoClass SUCCEEDED_PSEUDO_CLASS = PseudoClass.getPseudoClass("succeeded");
    private static final PseudoClass FAILED_PSEUDO_CLASS = PseudoClass.getPseudoClass("failed");

    /**
     * Default {@link #succeededDurationProperty() succeededDuration}, and the
     * value used in place of a {@code null}, negative or unknown one.
     */
    public static final Duration DEFAULT_SUCCEEDED_DURATION = Duration.millis(2000.0);

    /**
     * Default {@link #failedDurationProperty() failedDuration}, and the value
     * used in place of a {@code null}, negative or unknown one.
     */
    public static final Duration DEFAULT_FAILED_DURATION = Duration.millis(3000.0);

    /**
     * Default {@link #pendingDelayProperty() pendingDelay}, and the value used
     * in place of a {@code null}, negative, unknown or indefinite one.
     */
    public static final Duration DEFAULT_PENDING_DELAY = Duration.millis(200.0);

    /**
     * Default {@link #pendingMinDurationProperty() pendingMinDuration}, and
     * the value used in place of a {@code null}, negative, unknown or
     * indefinite one.
     */
    public static final Duration DEFAULT_PENDING_MIN_DURATION = Duration.millis(400.0);

    /**
     * Default {@link #animationDurationProperty() animationDuration}, and the
     * value used in place of a {@code null}, negative or otherwise unusable one.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    // A pause shorter than one animation tick finishes inside play(): its
    // callback would run in the middle of the transition that started it.
    // Anything below a millisecond therefore counts as no time at all.
    private static final double SHORTEST_TIMED_MILLIS = 1.0;

    // ==================== Run state ====================

    // Every start and every cancellation is a new generation. A handle from
    // an older one is ignored, and a transition that notified a listener
    // sees from it whether that listener started or cancelled a run.
    private int generation;

    // The handle the button is waiting on, null while not running. Volatile
    // for Run.isCurrent(), which worker threads poll.
    private volatile Run currentRun;

    // What the current run follows; null when the run is reported through
    // its handle only, and while not running. A followed Worker refers to
    // its listeners weakly, so this field is what keeps them alive, and a
    // button nothing else refers to can be collected while it still follows.
    private Source source;

    // An outcome that arrived before the pending face had shown long enough.
    private State deferredOutcome;

    private final PauseTransition pendingDelayTimer = new PauseTransition();
    private final PauseTransition pendingMinTimer = new PauseTransition();
    private final PauseTransition resultTimer = new PauseTransition();

    // Registered first in the constructor, so it is the first of the button's
    // own filters. A filter, not a handler: a handler on the same node cannot
    // keep onAction from running.
    private final EventHandler<ActionEvent> activationGuard = this::guardActivation;

    // ==================== Constructors ====================

    /**
     * Creates a task button with an empty text caption.
     */
    public RXTaskButton() {
        initialize();
    }

    /**
     * Creates a task button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXTaskButton(String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a task button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXTaskButton(String text, Node graphic) {
        super(text, graphic);
        initialize();
    }

    /**
     * {@inheritDoc}
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTaskButtonSkin(this);
    }

    private void initialize() {
        addEventFilter(ActionEvent.ACTION, activationGuard);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMnemonicParsing(false);
        pendingDelayTimer.setOnFinished(event -> pendingDelayElapsed());
        pendingMinTimer.setOnFinished(event -> pendingMinElapsed());
        resultTimer.setOnFinished(event -> resultElapsed());
    }

    // ==================== Runs ====================

    // Every transition below follows the same order, because each write to
    // running, progress or state runs application listeners that may call
    // back into the button:
    //   1. a transition whose precondition fails changes nothing at all;
    //   2. fields and timers reach their final values before the first
    //      public write;
    //   3. after each public write the transition goes on only while the run
    //      it handles is still the one that counts.

    /**
     * Starts a run and returns its handle. Can be called in any state: a run
     * still in flight is replaced without being cancelled, its handle goes
     * stale and its outcome will be ignored; a result face still showing
     * gives way to the pending face at once.
     *
     * <p>{@code running} turns {@code true} and {@code progress} is reset to
     * indeterminate (unless it is bound). The pending face appears after the
     * {@link #pendingDelayProperty() pendingDelay} when the normal face
     * shows, and at once otherwise.</p>
     *
     * @return the handle of the new run
     * @throws IllegalStateException if not called on the JavaFX application
     *                               thread
     */
    public final Run start() {
        checkFxThread();
        return begin(null);
    }

    /**
     * Starts a run that follows the given worker and returns its handle.
     * The button does not run the worker: the application executes the
     * {@code Task} or starts the {@code Service} itself, before or after
     * this call. Like {@link #start()}, this can be called in any state.
     *
     * <p>The button is running while the worker is ready, scheduled or
     * running. A worker that succeeds or fails ends the run with that
     * outcome; a worker that is cancelled cancels the run. The worker's
     * progress is copied to {@link #progressProperty() progress}, unless
     * that property is bound. The state and the progress the worker already
     * has are taken over at once, so a worker that has finished gives its
     * outcome before this method returns. Cancelling the run from the button
     * or the handle calls {@code worker.cancel()}. The returned handle can
     * still report an outcome first; whatever the worker reports afterwards
     * is ignored.</p>
     *
     * <p>A {@code Service} runs many times. The simplest way to follow it is
     * to track it whenever it is scheduled, wired once:</p>
     *
     * <pre>{@code
     * service.addEventHandler(WorkerStateEvent.WORKER_STATE_SCHEDULED, event -> button.track(service));
     * }</pre>
     *
     * <p>The button then follows every run, whoever started it. That handler
     * keeps the button alive for as long as the service lives: remove it, or
     * wrap it in a {@code WeakEventHandler}, when the service outlives the
     * button. To track a service from {@code onAction} instead, call
     * {@code service.restart()} first and {@code track(service)} second; the
     * other way round the button takes over the outcome left from the
     * previous run and never sees the new one.</p>
     *
     * <p>A service that is restarted while the button follows it reports the
     * old run as cancelled first, so the button returns to the normal face
     * and the pending face of the new run waits for the
     * {@link #pendingDelayProperty() pendingDelay} again. This includes a
     * service restarted from its own {@code onFailed} handler: the button
     * sees the cancellation, not the failure. Call {@link #start()} right
     * before the restart to keep the pending face up.</p>
     *
     * <p>A {@code ScheduledService} cannot be tracked. Drive the button from
     * its events instead: call {@link #start()} from a
     * {@code WORKER_STATE_RUNNING} handler and keep the returned handle, then
     * call {@link Run#succeed()}, {@link Run#fail()} and {@link Run#cancel()}
     * on it from the {@code WORKER_STATE_SUCCEEDED},
     * {@code WORKER_STATE_FAILED} and {@code WORKER_STATE_CANCELLED}
     * handlers. A service cancelled before its first round runs has not
     * started the button yet, so there is no handle to call then.</p>
     *
     * @param worker the worker to follow
     * @return the handle of the new run
     * @throws NullPointerException     if {@code worker} is {@code null}
     * @throws IllegalArgumentException if {@code worker} is a
     *                                  {@code ScheduledService}: it stays
     *                                  scheduled between two rounds and
     *                                  restarts itself before a listener can
     *                                  see a round succeed, so the button
     *                                  would stay pending forever
     * @throws IllegalStateException    if not called on the JavaFX
     *                                  application thread
     */
    public final Run track(Worker<?> worker) {
        checkFxThread();
        Objects.requireNonNull(worker, "worker");
        if (worker instanceof ScheduledService) {
            throw new IllegalArgumentException("A ScheduledService cannot be tracked: it stays scheduled"
                    + " between two rounds and restarts itself before a listener can see a round"
                    + " succeed. Call start() from a WORKER_STATE_RUNNING handler of the service and"
                    + " call succeed(), fail() and cancel() on the returned Run from its"
                    + " WORKER_STATE_SUCCEEDED, WORKER_STATE_FAILED and WORKER_STATE_CANCELLED"
                    + " handlers instead.");
        }
        return begin(new WorkerSource(worker));
    }

    /**
     * Starts a run that follows the given stage and returns its handle. The
     * stage may complete on any thread, and may be complete already: its
     * outcome is then taken over before this method returns. Like
     * {@link #start()}, this can be called in any state.
     *
     * <p>A stage that completes normally ends the run as succeeded, one that
     * completes exceptionally as failed. A stage completed with a
     * {@code CancellationException}, also one wrapped in a
     * {@code CompletionException}, cancels the run instead. A stage has no
     * progress; {@link #progressProperty() progress} stays indeterminate
     * unless the application sets it. The returned handle can still report
     * an outcome first; whatever the stage reports afterwards is
     * ignored.</p>
     *
     * <p>Cancelling the run from the button or the handle makes a
     * best-effort attempt to cancel the stage through
     * {@code toCompletableFuture().cancel(true)}. For most stages this does
     * not interrupt the work, and cancelling a dependent stage does not
     * reach the stage it was derived from: the only guarantee is that the
     * button no longer cares about the outcome.</p>
     *
     * @param stage the stage to follow
     * @return the handle of the new run
     * @throws NullPointerException  if {@code stage} is {@code null}
     * @throws IllegalStateException if not called on the JavaFX application
     *                               thread
     */
    public final Run track(CompletionStage<?> stage) {
        checkFxThread();
        Objects.requireNonNull(stage, "stage");
        return begin(new StageSource(stage));
    }

    private Run begin(Source next) {
        // The previous source is let go, not cancelled: the application
        // started a new run on purpose, the old work is its business.
        releaseSource();
        // The newest run wins: an outcome held back for the previous one is
        // never shown.
        deferredOutcome = null;
        resultTimer.stop();
        Run run = new Run(this, ++generation);
        currentRun = run;
        // Recorded before the first public write, so that a cancel() from a
        // listener of running finds the source to cancel.
        source = next;
        boolean showPending = schedulePendingFace();

        running.set(true);
        if (!isAwaiting(run)) {
            return run;
        }
        if (!progress.isBound()) {
            progress.set(ProgressIndicator.INDETERMINATE_PROGRESS);
            if (!isAwaiting(run)) {
                return run;
            }
        }
        if (showPending) {
            state.set(State.PENDING);
            if (!isAwaiting(run)) {
                return run;
            }
        }
        // Last, because a source that has already finished reports its
        // outcome from inside attach.
        if (next != null) {
            next.attach(run);
        }
        return run;
    }

    // Returns whether the pending face appears now; otherwise it is already
    // showing or a delay timer brings it up.
    private boolean schedulePendingFace() {
        State shown = getState();
        if (shown == State.PENDING) {
            return false;
        }
        if (shown == State.IDLE) {
            // A delay left running by the previous run is not restarted:
            // a chain of short runs would push the pending face back forever.
            if (pendingDelayTimer.getStatus() == Animation.Status.RUNNING) {
                return false;
            }
            Duration delay = FeedbackDurations.finiteOrDefault(getPendingDelay(), DEFAULT_PENDING_DELAY);
            if (isTimed(delay)) {
                pendingDelayTimer.setDuration(delay);
                pendingDelayTimer.playFromStart();
                return false;
            }
        }
        startPendingMinTimer();
        return true;
    }

    private void startPendingMinTimer() {
        Duration minimum = FeedbackDurations.finiteOrDefault(
                getPendingMinDuration(), DEFAULT_PENDING_MIN_DURATION);
        if (isTimed(minimum)) {
            pendingMinTimer.setDuration(minimum);
            pendingMinTimer.playFromStart();
        }
    }

    private void pendingDelayElapsed() {
        if (!isRunning() || getState() != State.IDLE) {
            return;
        }
        startPendingMinTimer();
        state.set(State.PENDING);
    }

    private void settle(Run run, State outcome) {
        if (!isAwaiting(run)) {
            return;
        }
        currentRun = null;
        releaseSource();
        pendingDelayTimer.stop();
        if (getState() == State.PENDING && pendingMinTimer.getStatus() == Animation.Status.RUNNING) {
            deferredOutcome = outcome;
            running.set(false);
            return;
        }
        State face = resultFace(outcome);
        running.set(false);
        if (run.generation == generation) {
            state.set(face);
        }
    }

    // The result duration is read here, when the result is about to show, so
    // a value set right before the outcome is reported applies to it.
    private State resultFace(State outcome) {
        Duration duration = outcome == State.SUCCEEDED
                ? FeedbackDurations.orDefault(getSucceededDuration(), DEFAULT_SUCCEEDED_DURATION)
                : FeedbackDurations.orDefault(getFailedDuration(), DEFAULT_FAILED_DURATION);
        if (!isTimed(duration)) {
            return State.IDLE;
        }
        // An indefinite duration keeps the face until the next run or reset.
        if (!duration.isIndefinite()) {
            resultTimer.setDuration(duration);
            resultTimer.playFromStart();
        }
        return outcome;
    }

    private void pendingMinElapsed() {
        // The timer has stopped, so an activation may cancel from now on.
        updateCancelReady();
        State outcome = deferredOutcome;
        if (outcome == null) {
            return;
        }
        deferredOutcome = null;
        state.set(resultFace(outcome));
    }

    private void resultElapsed() {
        if (isResultFace(getState())) {
            state.set(State.IDLE);
        }
    }

    /**
     * Cancels the run in flight: {@code running} turns {@code false}, the
     * handle of the run goes stale and the button returns to the normal face
     * at once, whatever the {@link #pendingMinDurationProperty()
     * pendingMinDuration}. Does nothing while the button is not running,
     * which includes the time an arrived outcome is held back behind the
     * pending face.
     *
     * <p>A tracked source is cancelled as well: a worker through
     * {@code Worker.cancel()}, a stage on a best-effort basis (see
     * {@link #track(CompletionStage)}).</p>
     *
     * @throws IllegalStateException if not called on the JavaFX application
     *                               thread
     */
    public final void cancel() {
        checkFxThread();
        cancelRun(false);
    }

    private void cancelRun(boolean reportedBySource) {
        if (!isRunning()) {
            return;
        }
        int cancelled = ++generation;
        currentRun = null;
        Source cancelledSource = releaseSource();
        pendingDelayTimer.stop();
        pendingMinTimer.stop();

        running.set(false);
        // A run cancelled while it was replacing a result face must not leave
        // that face behind: its timer is already stopped.
        if (cancelled == generation && getState() != State.IDLE) {
            state.set(State.IDLE);
        }
        // Last, because it runs code of the source, and whatever the
        // listeners above did: work the user cancelled must not go on. A
        // source that reported its own cancellation is not cancelled back,
        // and neither is one a listener is already tracking again, which
        // would cancel its new run.
        if (cancelledSource != null && !reportedBySource
                && (source == null || source.target() != cancelledSource.target())) {
            cancelledSource.cancel();
        }
    }

    // Stops following the source of the current run and returns it.
    private Source releaseSource() {
        Source released = source;
        source = null;
        if (released != null) {
            released.detach();
        }
        return released;
    }

    /**
     * Ends a succeeded or failed face and returns to the normal face. Does
     * nothing in any other state; works on a disabled button too.
     *
     * @throws IllegalStateException if not called on the JavaFX application
     *                               thread
     */
    public final void resetFeedback() {
        checkFxThread();
        if (!isResultFace(getState())) {
            return;
        }
        resultTimer.stop();
        state.set(State.IDLE);
    }

    private boolean isAwaiting(Run run) {
        return run.generation == generation && isRunning();
    }

    private static boolean isResultFace(State state) {
        return state == State.SUCCEEDED || state == State.FAILED;
    }

    private static boolean isTimed(Duration duration) {
        return duration.toMillis() >= SHORTEST_TIMED_MILLIS;
    }

    private static void checkFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("RXTaskButton must be used on the JavaFX application thread;"
                    + " report an outcome from another thread through the Run handle");
        }
    }

    // ==================== Activation ====================

    private void guardActivation(ActionEvent event) {
        if (!isBusy()) {
            return;
        }
        event.consume();
        if (isActivationCancelling()) {
            cancelRun(false);
        }
    }

    private boolean isBusy() {
        return isRunning() || getState() == State.PENDING;
    }

    // What an activation would do right now, null while it would be
    // swallowed. The skin arms the button only for an activation that does
    // something, and lets a press fire only while it would still do what it
    // would have done when the press began.
    private Activation activation() {
        if (!isBusy()) {
            return Activation.ACTION;
        }
        return isActivationCancelling() ? Activation.CANCEL : null;
    }

    // The pending face has to have shown for its minimum duration before a
    // click can mean "cancel": the user must have seen it, and the second
    // click of a double click must not cancel a run that just started.
    private boolean isActivationCancelling() {
        return isCancellable()
                && isRunning()
                && getState() == State.PENDING
                && pendingMinTimer.getStatus() != Animation.Status.RUNNING;
    }

    // isActivationCancelling() as a property, for the skin, which shows the
    // stop mark exactly while it holds. Updated by everything it reads: the
    // cancellable, running and state properties, and the end of the timer.
    private final ReadOnlyBooleanWrapper cancelReady = new ReadOnlyBooleanWrapper(this, "cancelReady");

    private void updateCancelReady() {
        cancelReady.set(isActivationCancelling());
    }

    // ==================== Running ====================

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(this, "running") {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(RUNNING_PSEUDO_CLASS, get());
            updateCancelReady();
        }
    };

    /**
     * Whether a run is in flight: {@code true} from {@link #start()} until an
     * outcome arrives or the run is cancelled. No timer affects it, so it can
     * differ from the face shown: it is already {@code true} while the
     * normal face still waits for the
     * {@link #pendingDelayProperty() pendingDelay}, and already {@code false}
     * while the pending face finishes its
     * {@link #pendingMinDurationProperty() pendingMinDuration}. Mirrored by
     * the {@code running} pseudo-class.
     *
     * @return the read-only running property
     */
    public final ReadOnlyBooleanProperty runningProperty() {
        return running.getReadOnlyProperty();
    }

    /**
     * Returns whether a run is in flight.
     *
     * @return whether a run is in flight
     */
    public final boolean isRunning() {
        return running.get();
    }

    // ==================== State ====================

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.IDLE) {
        @Override
        protected void invalidated() {
            State current = get();
            pseudoClassStateChanged(PENDING_PSEUDO_CLASS, current == State.PENDING);
            pseudoClassStateChanged(SUCCEEDED_PSEUDO_CLASS, current == State.SUCCEEDED);
            pseudoClassStateChanged(FAILED_PSEUDO_CLASS, current == State.FAILED);
            updateCancelReady();
        }
    };

    /**
     * The face the button shows. This is not the outcome of the work: the
     * pending face is delayed and held by the
     * {@link #pendingDelayProperty() pendingDelay} and the
     * {@link #pendingMinDurationProperty() pendingMinDuration}, and a result
     * face is skipped altogether when its duration is {@code Duration.ZERO}.
     * Mirrored by the {@code pending}, {@code succeeded} and {@code failed}
     * pseudo-classes.
     *
     * @return the read-only state property
     */
    public final ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /**
     * Returns the face the button shows.
     *
     * @return the state, never {@code null}
     */
    public final State getState() {
        return state.get();
    }

    // ==================== Progress ====================

    private final DoubleProperty progress =
            new SimpleDoubleProperty(this, "progress", ProgressIndicator.INDETERMINATE_PROGRESS);

    /**
     * Progress of the run in flight, drawn by the pending indicator. A
     * negative value or {@code NaN} is indeterminate; a value above {@code 1}
     * is drawn as {@code 1}. Starting a run resets it to
     * {@link ProgressIndicator#INDETERMINATE_PROGRESS}. Report progress from
     * a callback with {@link Run#updateProgress(double)} rather than by
     * setting this property: only the handle keeps a late callback of an
     * older run out. While the property is bound, neither starting a run nor
     * a handle writes it.
     *
     * @return the progress property
     */
    public final DoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Returns the progress of the run in flight.
     *
     * @return the progress, negative or {@code NaN} for indeterminate
     */
    public final double getProgress() {
        return progress.get();
    }

    /**
     * Sets the progress of the run in flight.
     *
     * @param value progress in {@code [0, 1]}, or a negative value for
     *              indeterminate
     */
    public final void setProgress(double value) {
        progress.set(value);
    }

    // ==================== Pending Text ====================

    private final StringProperty pendingText = new SimpleStringProperty(this, "pendingText");

    /**
     * Text of the pending face. The default {@code null} and an empty string
     * show no text, which leaves the pending indicator alone and centered;
     * nothing is borrowed from the button text.
     *
     * @return the pending text property
     */
    public final StringProperty pendingTextProperty() {
        return pendingText;
    }

    /**
     * Returns the text of the pending face.
     *
     * @return the pending text, or {@code null}
     */
    public final String getPendingText() {
        return pendingText.get();
    }

    /**
     * Sets the text of the pending face.
     *
     * @param value the pending text, or {@code null} for none
     */
    public final void setPendingText(String value) {
        pendingText.set(value);
    }

    // ==================== Succeeded Text ====================

    private final StringProperty succeededText = new SimpleStringProperty(this, "succeededText");

    /**
     * Text of the succeeded face. The default {@code null} and an empty
     * string show no text; nothing is borrowed from the button text.
     *
     * @return the succeeded text property
     */
    public final StringProperty succeededTextProperty() {
        return succeededText;
    }

    /**
     * Returns the text of the succeeded face.
     *
     * @return the succeeded text, or {@code null}
     */
    public final String getSucceededText() {
        return succeededText.get();
    }

    /**
     * Sets the text of the succeeded face.
     *
     * @param value the succeeded text, or {@code null} for none
     */
    public final void setSucceededText(String value) {
        succeededText.set(value);
    }

    // ==================== Failed Text ====================

    private final StringProperty failedText = new SimpleStringProperty(this, "failedText");

    /**
     * Text of the failed face. The default {@code null} and an empty string
     * show no text; nothing is borrowed from the button text. A value set
     * right before {@link Run#fail()} shows with that failure.
     *
     * @return the failed text property
     */
    public final StringProperty failedTextProperty() {
        return failedText;
    }

    /**
     * Returns the text of the failed face.
     *
     * @return the failed text, or {@code null}
     */
    public final String getFailedText() {
        return failedText.get();
    }

    /**
     * Sets the text of the failed face.
     *
     * @param value the failed text, or {@code null} for none
     */
    public final void setFailedText(String value) {
        failedText.set(value);
    }

    // ==================== Pending Graphic ====================

    private final ObjectProperty<Node> pendingGraphic = new SimpleObjectProperty<>(this, "pendingGraphic");

    /**
     * Graphic of the pending face, placed next to the pending text like the
     * button graphic. The default {@code null} shows none. The three face
     * graphics and the button graphic must all be different nodes: a node
     * cannot be shown on two faces.
     *
     * @return the pending graphic property
     */
    public final ObjectProperty<Node> pendingGraphicProperty() {
        return pendingGraphic;
    }

    /**
     * Returns the graphic of the pending face.
     *
     * @return the pending graphic, or {@code null}
     */
    public final Node getPendingGraphic() {
        return pendingGraphic.get();
    }

    /**
     * Sets the graphic of the pending face.
     *
     * @param value the pending graphic, or {@code null} for none
     */
    public final void setPendingGraphic(Node value) {
        pendingGraphic.set(value);
    }

    // ==================== Succeeded Graphic ====================

    private final ObjectProperty<Node> succeededGraphic = new SimpleObjectProperty<>(this, "succeededGraphic");

    /**
     * Graphic of the succeeded face, placed next to the succeeded text like
     * the button graphic. The default {@code null} shows none. Must be a
     * different node than the other face graphics and the button graphic.
     *
     * @return the succeeded graphic property
     */
    public final ObjectProperty<Node> succeededGraphicProperty() {
        return succeededGraphic;
    }

    /**
     * Returns the graphic of the succeeded face.
     *
     * @return the succeeded graphic, or {@code null}
     */
    public final Node getSucceededGraphic() {
        return succeededGraphic.get();
    }

    /**
     * Sets the graphic of the succeeded face.
     *
     * @param value the succeeded graphic, or {@code null} for none
     */
    public final void setSucceededGraphic(Node value) {
        succeededGraphic.set(value);
    }

    // ==================== Failed Graphic ====================

    private final ObjectProperty<Node> failedGraphic = new SimpleObjectProperty<>(this, "failedGraphic");

    /**
     * Graphic of the failed face, placed next to the failed text like the
     * button graphic. The default {@code null} shows none. Must be a
     * different node than the other face graphics and the button graphic.
     *
     * @return the failed graphic property
     */
    public final ObjectProperty<Node> failedGraphicProperty() {
        return failedGraphic;
    }

    /**
     * Returns the graphic of the failed face.
     *
     * @return the failed graphic, or {@code null}
     */
    public final Node getFailedGraphic() {
        return failedGraphic.get();
    }

    /**
     * Sets the graphic of the failed face.
     *
     * @param value the failed graphic, or {@code null} for none
     */
    public final void setFailedGraphic(Node value) {
        failedGraphic.set(value);
    }

    // ==================== Pending Indicator ====================

    private final ObjectProperty<Node> pendingIndicator = new SimpleObjectProperty<>(this, "pendingIndicator");

    /**
     * Node shown in place of the built-in progress ring of the pending face.
     * The default {@code null} shows the ring. A custom indicator is laid
     * out at its preferred size and is not driven by the button: it gets
     * neither the progress nor the text color. One that draws progress binds
     * it itself:
     *
     * <pre>{@code
     * indicator.progressProperty().bind(button.progressProperty());
     * button.setPendingIndicator(indicator);
     * }</pre>
     *
     * <p>This is a different slot than the
     * {@link #pendingGraphicProperty() pendingGraphic}, which sits inside the
     * caption next to the pending text. A focus traversable indicator should
     * have its {@code focusTraversable} turned off, or the Tab key stops on
     * it, and an indicator that is a control should be mouse transparent, or
     * a click that lands on it does not reach the button. Like a graphic,
     * the indicator cannot be shown anywhere else at the same time.</p>
     *
     * @return the pending indicator property
     */
    public final ObjectProperty<Node> pendingIndicatorProperty() {
        return pendingIndicator;
    }

    /**
     * Returns the node shown in place of the built-in progress ring.
     *
     * @return the pending indicator, or {@code null} for the built-in ring
     */
    public final Node getPendingIndicator() {
        return pendingIndicator.get();
    }

    /**
     * Sets the node shown in place of the built-in progress ring.
     *
     * @param value the pending indicator, or {@code null} for the built-in
     *              ring
     */
    public final void setPendingIndicator(Node value) {
        pendingIndicator.set(value);
    }

    // ==================== Succeeded Duration ====================

    private final ObjectProperty<Duration> succeededDuration =
            new StyleableObjectProperty<>(DEFAULT_SUCCEEDED_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.SUCCEEDED_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXTaskButton.this;
                }

                @Override
                public String getName() {
                    return "succeededDuration";
                }
            };

    /**
     * How long the succeeded face stays. {@code Duration.ZERO}, like any
     * value below one millisecond, skips the face: the button goes straight
     * back to the normal face and {@code state} never becomes
     * {@link State#SUCCEEDED}. {@code Duration.INDEFINITE} keeps the face
     * until the next run or {@link #resetFeedback()}. {@code null}, negative
     * and unknown values fall back to {@link #DEFAULT_SUCCEEDED_DURATION}.
     * The value is read when the face is about to show; a change made while
     * it shows applies from the next run. From CSS:
     * {@code -rx-succeeded-duration}.
     *
     * @return the succeeded duration property
     */
    public final ObjectProperty<Duration> succeededDurationProperty() {
        return succeededDuration;
    }

    /**
     * Returns how long the succeeded face stays.
     *
     * @return the succeeded duration, or {@code null}
     */
    public final Duration getSucceededDuration() {
        return succeededDuration.get();
    }

    /**
     * Sets how long the succeeded face stays.
     *
     * @param value the succeeded duration
     */
    public final void setSucceededDuration(Duration value) {
        succeededDuration.set(value);
    }

    // ==================== Failed Duration ====================

    private final ObjectProperty<Duration> failedDuration =
            new StyleableObjectProperty<>(DEFAULT_FAILED_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.FAILED_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXTaskButton.this;
                }

                @Override
                public String getName() {
                    return "failedDuration";
                }
            };

    /**
     * How long the failed face stays. {@code Duration.ZERO}, like any value
     * below one millisecond, skips the face: the button goes straight back
     * to the normal face and {@code state} never becomes
     * {@link State#FAILED}. {@code Duration.INDEFINITE} keeps the face until
     * the next run or {@link #resetFeedback()}. {@code null}, negative and
     * unknown values fall back to {@link #DEFAULT_FAILED_DURATION}. The
     * value is read when the face is about to show; a change made while it
     * shows applies from the next run. From CSS:
     * {@code -rx-failed-duration}.
     *
     * @return the failed duration property
     */
    public final ObjectProperty<Duration> failedDurationProperty() {
        return failedDuration;
    }

    /**
     * Returns how long the failed face stays.
     *
     * @return the failed duration, or {@code null}
     */
    public final Duration getFailedDuration() {
        return failedDuration.get();
    }

    /**
     * Sets how long the failed face stays.
     *
     * @param value the failed duration
     */
    public final void setFailedDuration(Duration value) {
        failedDuration.set(value);
    }

    // ==================== Pending Delay ====================

    private final ObjectProperty<Duration> pendingDelay =
            new StyleableObjectProperty<>(DEFAULT_PENDING_DELAY) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.PENDING_DELAY;
                }

                @Override
                public Object getBean() {
                    return RXTaskButton.this;
                }

                @Override
                public String getName() {
                    return "pendingDelay";
                }
            };

    /**
     * How long a run has to be in flight before the pending face appears. A
     * run that ends inside this window never shows the pending face, and the
     * button looks unchanged during it. {@code Duration.ZERO}, like any
     * value below one millisecond, shows the pending face at once.
     * {@code null}, negative, unknown and indefinite values fall back to
     * {@link #DEFAULT_PENDING_DELAY}. The delay only applies to a run
     * started while the normal face shows, and is read when that run starts;
     * a run started while the delay of the previous run is still counting
     * shares what is left of it.
     * From CSS: {@code -rx-pending-delay}.
     *
     * @return the pending delay property
     */
    public final ObjectProperty<Duration> pendingDelayProperty() {
        return pendingDelay;
    }

    /**
     * Returns how long a run is in flight before the pending face appears.
     *
     * @return the pending delay, or {@code null}
     */
    public final Duration getPendingDelay() {
        return pendingDelay.get();
    }

    /**
     * Sets how long a run is in flight before the pending face appears.
     *
     * @param value the pending delay
     */
    public final void setPendingDelay(Duration value) {
        pendingDelay.set(value);
    }

    // ==================== Pending Min Duration ====================

    private final ObjectProperty<Duration> pendingMinDuration =
            new StyleableObjectProperty<>(DEFAULT_PENDING_MIN_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.PENDING_MIN_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXTaskButton.this;
                }

                @Override
                public String getName() {
                    return "pendingMinDuration";
                }
            };

    /**
     * The shortest time the pending face stays once it has appeared. An
     * outcome that arrives earlier turns {@code running} off at once but is
     * shown only when this time is over. {@code Duration.ZERO}, like any
     * value below one millisecond, sets no minimum. {@code null}, negative,
     * unknown and indefinite values fall back to
     * {@link #DEFAULT_PENDING_MIN_DURATION}. The value is read when the
     * pending face appears. From CSS: {@code -rx-pending-min-duration}.
     *
     * <p>This is also how long a {@link #cancellableProperty() cancellable}
     * button ignores activations after the pending face appeared. With
     * {@code Duration.ZERO} that protection is gone: the second click of a
     * double click can cancel the run the first click started.</p>
     *
     * @return the pending min duration property
     */
    public final ObjectProperty<Duration> pendingMinDurationProperty() {
        return pendingMinDuration;
    }

    /**
     * Returns the shortest time the pending face stays.
     *
     * @return the pending min duration, or {@code null}
     */
    public final Duration getPendingMinDuration() {
        return pendingMinDuration.get();
    }

    /**
     * Sets the shortest time the pending face stays.
     *
     * @param value the pending min duration
     */
    public final void setPendingMinDuration(Duration value) {
        pendingMinDuration.set(value);
    }

    // ==================== Cancellable ====================

    private final BooleanProperty cancellable = new SimpleBooleanProperty(this, "cancellable") {
        @Override
        protected void invalidated() {
            updateCancelReady();
        }
    };

    /**
     * Whether activating the button cancels the run in flight. The default
     * {@code false} swallows activations while the button is busy. When
     * {@code true}, an activation calls {@link #cancel()}, but only while a
     * run is in flight and the pending face has shown for the
     * {@link #pendingMinDurationProperty() pendingMinDuration}; earlier
     * activations, including every one during the
     * {@link #pendingDelayProperty() pendingDelay}, are still swallowed, so
     * a double click does not cancel what it started. A
     * {@code pendingMinDuration} of {@code Duration.ZERO} gives that
     * protection up. A press or key stroke that armed the button to cancel
     * does nothing when the run has ended by the time it is released.
     * {@link #cancel()} itself works at any time, whatever this property
     * says.
     *
     * <p>While an activation would cancel the run, the built-in ring of the
     * pending face shows a stop mark, a small square at its centre, so the
     * user can tell that a click now stops the work. The mark appears once
     * the pending face has shown for the {@code pendingMinDuration}, and goes
     * as soon as an activation would not cancel any more: when the run ends
     * or is cancelled, or when this property turns {@code false}. It is red
     * ({@code -rx-danger}), and the text color on a filled button; from CSS
     * it is {@code .pending-face > .indicator > .stop}. A custom
     * {@link #pendingIndicatorProperty() pendingIndicator} gets no mark: an
     * application that wants one draws it into its own indicator, or puts it
     * in the {@link #pendingGraphicProperty() pendingGraphic}. The mark is
     * not announced to a screen reader; a pending text that says the work
     * can be cancelled, such as "Cancel upload", tells everyone.</p>
     *
     * @return the cancellable property
     */
    public final BooleanProperty cancellableProperty() {
        return cancellable;
    }

    /**
     * Returns whether activating the button cancels the run in flight.
     *
     * @return whether activating the button cancels the run in flight
     */
    public final boolean isCancellable() {
        return cancellable.get();
    }

    /**
     * Sets whether activating the button cancels the run in flight.
     *
     * @param value {@code true} to cancel on activation
     */
    public final void setCancellable(boolean value) {
        cancellable.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation");

    /**
     * The animation played when the faces swap. The default {@code null}
     * swaps with a direct cut, and so do multi-page display animations and
     * animations requiring more than two pages. A swap requested while one is
     * playing jumps the running one to its end first.
     * A {@code PageAnimation} instance may be reused sequentially, but must
     * not drive concurrent transitions or be shared by multiple live hosts.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation played when the faces swap.
     *
     * @return the animation, or {@code null} for a direct cut
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation played when the faces swap.
     *
     * @param value the animation, or {@code null} for a direct cut
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXTaskButton.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of one face swap when an {@link #animationProperty() animation}
     * is set. {@code Duration.ZERO} swaps with a direct cut; {@code null},
     * negative and otherwise unusable values fall back to
     * {@link #DEFAULT_ANIMATION_DURATION}. The swap to a face counts toward
     * the time that face stays. From CSS: {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of one face swap.
     *
     * @return the animation duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of one face swap.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXTaskButton, Duration> SUCCEEDED_DURATION =
                new CssMetaData<>("-rx-succeeded-duration",
                        DurationConverter.getInstance(), DEFAULT_SUCCEEDED_DURATION) {
                    @Override
                    public boolean isSettable(RXTaskButton button) {
                        return !button.succeededDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTaskButton button) {
                        return (StyleableProperty<Duration>) button.succeededDurationProperty();
                    }
                };

        private static final CssMetaData<RXTaskButton, Duration> FAILED_DURATION =
                new CssMetaData<>("-rx-failed-duration",
                        DurationConverter.getInstance(), DEFAULT_FAILED_DURATION) {
                    @Override
                    public boolean isSettable(RXTaskButton button) {
                        return !button.failedDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTaskButton button) {
                        return (StyleableProperty<Duration>) button.failedDurationProperty();
                    }
                };

        private static final CssMetaData<RXTaskButton, Duration> PENDING_DELAY =
                new CssMetaData<>("-rx-pending-delay",
                        DurationConverter.getInstance(), DEFAULT_PENDING_DELAY) {
                    @Override
                    public boolean isSettable(RXTaskButton button) {
                        return !button.pendingDelay.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTaskButton button) {
                        return (StyleableProperty<Duration>) button.pendingDelayProperty();
                    }
                };

        private static final CssMetaData<RXTaskButton, Duration> PENDING_MIN_DURATION =
                new CssMetaData<>("-rx-pending-min-duration",
                        DurationConverter.getInstance(), DEFAULT_PENDING_MIN_DURATION) {
                    @Override
                    public boolean isSettable(RXTaskButton button) {
                        return !button.pendingMinDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTaskButton button) {
                        return (StyleableProperty<Duration>) button.pendingMinDurationProperty();
                    }
                };

        private static final CssMetaData<RXTaskButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXTaskButton button) {
                        return !button.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTaskButton button) {
                        return (StyleableProperty<Duration>) button.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(SUCCEEDED_DURATION);
            styleables.add(FAILED_DURATION);
            styleables.add(PENDING_DELAY);
            styleables.add(PENDING_MIN_DURATION);
            styleables.add(ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Sources ====================

    // What a run started by track follows.
    private abstract static class Source {

        // The followed object; two sources with the same one follow the same work.
        abstract Object target();

        // Starts listening and takes over what the target has already reached.
        abstract void attach(Run run);

        // Stops listening; does nothing when attach was never called.
        abstract void detach();

        // Asks the target to stop its work.
        abstract void cancel();
    }

    private static final class WorkerSource extends Source {

        private final Worker<?> worker;
        private ChangeListener<Worker.State> stateListener;
        private ChangeListener<Number> progressListener;
        private WeakChangeListener<Worker.State> weakStateListener;
        private WeakChangeListener<Number> weakProgressListener;

        WorkerSource(Worker<?> worker) {
            this.worker = worker;
        }

        @Override
        Object target() {
            return worker;
        }

        @Override
        void attach(Run run) {
            // The listeners belong to one run. A listener that has been
            // removed can still get a notification that was already under
            // way, and it gets the worker's value of that moment, not the
            // one that set the notification off; its stale run drops it.
            stateListener = (observable, was, is) -> run.workerReached(is);
            progressListener = (observable, was, is) -> run.applyProgress(is.doubleValue());
            weakStateListener = new WeakChangeListener<>(stateListener);
            weakProgressListener = new WeakChangeListener<>(progressListener);
            worker.stateProperty().addListener(weakStateListener);
            worker.progressProperty().addListener(weakProgressListener);
            // A listener is not called for the value the worker already has.
            run.applyProgress(worker.getProgress());
            run.workerReached(worker.getState());
        }

        @Override
        void detach() {
            if (weakStateListener != null) {
                worker.stateProperty().removeListener(weakStateListener);
                worker.progressProperty().removeListener(weakProgressListener);
            }
        }

        @Override
        void cancel() {
            worker.cancel();
        }
    }

    private static final class StageSource extends Source {

        private final CompletionStage<?> stage;

        StageSource(CompletionStage<?> stage) {
            this.stage = stage;
        }

        @Override
        Object target() {
            return stage;
        }

        @Override
        void attach(Run run) {
            // The callback cannot be removed again and a stage that never
            // completes keeps it forever, so it holds the handle only: the
            // handle refers to the button weakly and drops a late outcome.
            stage.whenComplete((value, error) -> run.stageCompleted(error));
        }

        @Override
        void detach() {
            // Nothing to remove; the stale handle ignores the callback.
        }

        @Override
        void cancel() {
            try {
                // true: some futures only abort their work when asked to interrupt.
                stage.toCompletableFuture().cancel(true);
            } catch (UnsupportedOperationException ex) {
                // A stage need not be convertible to a future; it then cannot be cancelled.
            }
        }
    }

    // ==================== Run ====================

    /**
     * Handle of one run of a task button, returned by
     * {@link RXTaskButton#start()} and the {@code track} methods. The
     * outcome and the progress of the run are reported through it.
     *
     * <p>A handle is good for its own run only: once the run has an outcome,
     * was cancelled or was replaced by a newer run, every method does
     * nothing. All methods can be called from any thread. On the JavaFX
     * application thread they act at once; on any other thread they hand over
     * to it and act there, if the handle is still good by then. Calls made
     * from one thread take effect in the order they were made, so progress
     * reported before an outcome never lands after it.</p>
     *
     * <p>The handle does not keep the button alive: a button nothing else
     * refers to can be collected while a callback still holds its handle,
     * and the handle then does nothing. Only a delay or duration of the
     * button that is still counting keeps it until it has run out.</p>
     */
    public static final class Run {

        private final WeakReference<RXTaskButton> button;
        private final int generation;

        // The latest progress waiting to be applied on the JavaFX application
        // thread; null when no hand-over is queued. One per handle: a shared
        // slot would let the queued update of a stale handle take the value a
        // newer handle just wrote and throw it away.
        private final AtomicReference<Double> queuedProgress = new AtomicReference<>();

        private Run(RXTaskButton button, int generation) {
            this.button = new WeakReference<>(button);
            this.generation = generation;
        }

        /**
         * Reports that the run succeeded. The first outcome of a run counts;
         * later ones are ignored.
         */
        public void succeed() {
            onFxThread(() -> settle(State.SUCCEEDED));
        }

        /**
         * Reports that the run failed. The first outcome of a run counts;
         * later ones are ignored. The button shows no reason: set its
         * {@link RXTaskButton#failedTextProperty() failedText} first to show
         * one.
         */
        public void fail() {
            onFxThread(() -> settle(State.FAILED));
        }

        /**
         * Cancels this run, like {@link RXTaskButton#cancel()}, but only if
         * the button is still waiting on it: a stale handle cannot cancel a
         * newer run.
         */
        public void cancel() {
            onFxThread(() -> {
                RXTaskButton owner = button.get();
                if (owner != null && owner.isAwaiting(this)) {
                    owner.cancelRun(false);
                }
            });
        }

        /**
         * Reports the progress of this run by writing the button's
         * {@link RXTaskButton#progressProperty() progress}, unless that
         * property is bound. Calls from other threads are coalesced: when
         * several arrive before the JavaFX application thread gets to them,
         * only the latest value is applied.
         *
         * @param value progress in {@code [0, 1]}, or a negative value for
         *              indeterminate
         */
        public void updateProgress(double value) {
            if (Platform.isFxApplicationThread()) {
                applyProgress(value);
            } else if (isCurrent() && queuedProgress.getAndSet(value) == null) {
                Platform.runLater(() -> applyProgress(queuedProgress.getAndSet(null)));
            }
        }

        /**
         * Returns whether the button is still waiting for the outcome of
         * this run. Background work can poll it to stop early, much like
         * {@code Task.isCancelled()}; it is also how such work learns that
         * its run was cancelled. Read from another thread, the answer may be
         * out of date the moment it is returned.
         *
         * @return {@code true} until the run has an outcome, is cancelled or
         *         is replaced by a newer run
         */
        public boolean isCurrent() {
            RXTaskButton owner = button.get();
            return owner != null && owner.currentRun == this;
        }

        private void settle(State outcome) {
            RXTaskButton owner = button.get();
            if (owner != null) {
                owner.settle(this, outcome);
            }
        }

        private void applyProgress(double value) {
            RXTaskButton owner = button.get();
            if (owner != null && owner.isAwaiting(this) && !owner.progress.isBound()) {
                owner.progress.set(value);
            }
        }

        // Ready, scheduled and running all mean "in flight" and change nothing.
        private void workerReached(Worker.State workerState) {
            if (workerState == Worker.State.SUCCEEDED) {
                settle(State.SUCCEEDED);
            } else if (workerState == Worker.State.FAILED) {
                settle(State.FAILED);
            } else if (workerState == Worker.State.CANCELLED) {
                sourceCancelled();
            }
        }

        private void stageCompleted(Throwable error) {
            if (error == null) {
                succeed();
            } else if (isCancellation(error)) {
                onFxThread(this::sourceCancelled);
            } else {
                fail();
            }
        }

        // The source ended as cancelled; it is not asked to cancel again.
        private void sourceCancelled() {
            RXTaskButton owner = button.get();
            if (owner != null && owner.isAwaiting(this)) {
                owner.cancelRun(true);
            }
        }

        // A dependent stage reports the cancellation of the stage it was
        // derived from wrapped in a CompletionException.
        private static boolean isCancellation(Throwable error) {
            return error instanceof CancellationException
                    || (error instanceof CompletionException
                    && error.getCause() instanceof CancellationException);
        }

        private static void onFxThread(Runnable action) {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        }
    }
}

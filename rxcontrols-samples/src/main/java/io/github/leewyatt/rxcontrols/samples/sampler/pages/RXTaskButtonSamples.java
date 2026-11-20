package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXSnackbars;
import io.github.leewyatt.rxcontrols.RXTaskButton;
import io.github.leewyatt.rxcontrols.animation.page.AnimBounce;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * The RXTaskButton page: the four faces side by side, work of different lengths, a
 * progress ring that fills, texts and icons, retry, cancelling, the pending state
 * on its own, custom indicators, colored buttons and swap animations.
 * All work is a timer or a sleeping thread; nothing is sent anywhere.
 */
public final class RXTaskButtonSamples {

    private static final long WORK_MILLIS = 1500L;
    private static final int PROGRESS_STEPS = 10;
    private static final long STEP_MILLIS = 220L;

    private RXTaskButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A button that follows one piece of work: a ring while it runs, then a check or a cross.",
                List.of("Save, send, upload, export: actions that take a moment and can fail.",
                        "The button does not run the work; the application tells it when the work starts"
                                + " and how it ends."),
                List.of(
                        SampleCard.wide("States",
                                "The four faces, with the state under each button. Every one can be clicked:"
                                        + " the first runs once; the second takes five seconds and ends either"
                                        + " way, by chance; the third fails every time and the fourth succeeds"
                                        + " every time, and both keep their result.",
                                states()),
                        SampleCard.medium("Fast and slow",
                                "Started from the normal face, work that ends within 200 ms never shows the ring,"
                                        + " and a ring that shows stays at least 400 ms, so nothing flashes.",
                                row(timed("50 ms", 50.0), timed("220 ms", 220.0), timed("300 ms", 300.0),
                                        timed("2 s", 2000.0))),
                        SampleCard.medium("Progress",
                                "Upload follows a Task and draws its progress; Export reports through the handle"
                                        + " and does not know its progress at first.",
                                row(upload(), export())),
                        SampleCard.medium("Text and icons",
                                "Each face has its own text; without one the indicator stands alone, which keeps"
                                        + " an icon button its size.",
                                row(withTexts(), iconOnly(), iconAndText())),
                        SampleCard.medium("Fail and retry",
                                "The button follows a CompletableFuture that always fails. Clicking the failed"
                                        + " face starts the work again.",
                                retry()),
                        SampleCard.medium("Cancellable",
                                "Both downloads take five seconds. Only the first can be cancelled: its ring"
                                        + " carries a stop mark, and a click stops the run once the ring has"
                                        + " shown for a moment. The second ignores clicks until it is done.",
                                cancellable()),
                        SampleCard.medium("Pending state only",
                                "With both result durations at zero the button only shows the ring, and a"
                                        + " snackbar reports the result.",
                                pendingOnly()),
                        SampleCard.medium("Custom indicator",
                                "Any node can stand in for the ring; a control used this way should be taken"
                                        + " out of the Tab order and made mouse transparent.",
                                row(dots(), bars())),
                        SampleCard.medium("Colors",
                                "The ring, the check and the cross take the text color of the button.",
                                row(solid("Publish", "Published", "primary"),
                                        solid("Approve", "Approved", "success"),
                                        solid("Delete", "Deleted", "danger"))),
                        SampleCard.wide("Animated swap",
                                "A page animation can carry the swap between the faces; click each one.",
                                animated("Slide", new AnimSlide(Orientation.VERTICAL)),
                                animated("Flip", new AnimFlip(Orientation.HORIZONTAL)),
                                animated("Cube", new AnimCube(Orientation.VERTICAL)),
                                animated("Bounce", new AnimBounce(Orientation.VERTICAL)),
                                animated("Fade", new AnimFade()),
                                animated("Wipe", new AnimWipe()))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... nodes) {
        HBox row = new HBox(10.0, nodes);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // Stands in for the callback of real work.
    private static void after(double millis, Runnable callback) {
        PauseTransition pause = new PauseTransition(Duration.millis(millis));
        pause.setOnFinished(event -> callback.run());
        pause.play();
    }

    private static RXTaskButton succeeding(String text, double millis) {
        RXTaskButton button = new RXTaskButton(text);
        button.setOnAction(event -> {
            RXTaskButton.Run run = button.start();
            after(millis, run::succeed);
        });
        return button;
    }

    // ==================== States ====================

    private static Node states() {
        RXTaskButton idle = saveButton();
        runOnClick(idle, WORK_MILLIS, () -> true);

        // Longer work, so the pending face can be looked at; chance decides
        // how it ends. One run is under way when the page opens.
        RXTaskButton pending = saveButton();
        runOnClick(pending, 5000.0, () -> ThreadLocalRandom.current().nextBoolean());
        pending.fire();

        // An indefinite duration keeps a result face until the next run.
        RXTaskButton failed = saveButton();
        failed.setFailedDuration(Duration.INDEFINITE);
        runOnClick(failed, WORK_MILLIS, () -> false);
        failed.start().fail();

        RXTaskButton succeeded = saveButton();
        succeeded.setSucceededDuration(Duration.INDEFINITE);
        runOnClick(succeeded, WORK_MILLIS, () -> true);
        succeeded.start().succeed();

        HBox row = new HBox(32.0, withState(idle), withState(pending), withState(failed), withState(succeeded));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXTaskButton saveButton() {
        RXTaskButton button = new RXTaskButton("Save");
        button.setPendingText("Saving…");
        button.setSucceededText("Saved");
        button.setFailedText("Not saved");
        return button;
    }

    // Every click runs the work once more; the supplier says how that run ends.
    private static void runOnClick(RXTaskButton button, double millis, BooleanSupplier succeeds) {
        button.setOnAction(event -> {
            RXTaskButton.Run run = button.start();
            after(millis, succeeds.getAsBoolean() ? run::succeed : run::fail);
        });
    }

    private static Node withState(RXTaskButton button) {
        Label state = new Label();
        state.getStyleClass().add("caption");
        state.textProperty().bind(Bindings.createStringBinding(() -> title(button.getState()),
                button.stateProperty()));
        VBox box = new VBox(8.0, button, state);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // PENDING -> "Pending"
    private static String title(RXTaskButton.State state) {
        String name = state.name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    // ==================== Fast and slow ====================

    private static Node timed(String text, double millis) {
        RXTaskButton button = succeeding(text, millis);
        button.setSucceededText("Done");
        return button;
    }

    // ==================== Progress ====================

    private static Node upload() {
        RXTaskButton button = new RXTaskButton("Upload", ButtonIcons.icon("upload-icon"));
        button.setPendingText("Uploading");
        button.setSucceededText("Uploaded");
        button.setOnAction(event -> {
            Task<Void> upload = new Task<>() {
                @Override
                protected Void call() throws InterruptedException {
                    for (int step = 0; step <= PROGRESS_STEPS; step++) {
                        updateProgress(step, PROGRESS_STEPS);
                        Thread.sleep(STEP_MILLIS);
                    }
                    return null;
                }
            };
            // The button follows the task; running it stays with the application.
            button.track(upload);
            Thread thread = new Thread(upload, "upload-sample");
            thread.setDaemon(true);
            thread.start();
        });
        return button;
    }

    private static Node export() {
        RXTaskButton button = new RXTaskButton("Export", ButtonIcons.icon("download-icon"));
        button.setPendingText("Exporting");
        button.setSucceededText("Exported");
        button.setOnAction(event -> {
            RXTaskButton.Run run = button.start();
            after(1200.0, () -> reportInSteps(run));
        });
        return button;
    }

    private static void reportInSteps(RXTaskButton.Run run) {
        int[] step = {0};
        Timeline work = new Timeline(new KeyFrame(Duration.millis(STEP_MILLIS), event -> {
            step[0]++;
            run.updateProgress(step[0] / (double) PROGRESS_STEPS);
        }));
        work.setCycleCount(PROGRESS_STEPS);
        work.setOnFinished(event -> after(350.0, run::succeed));
        work.play();
    }

    // ==================== Text and icons ====================

    private static Node withTexts() {
        RXTaskButton button = succeeding("Save", WORK_MILLIS);
        button.setPendingText("Saving…");
        button.setSucceededText("Saved");
        return button;
    }

    private static Node iconOnly() {
        RXTaskButton button = succeeding(null, WORK_MILLIS);
        button.setGraphic(ButtonIcons.icon("share-icon"));
        button.setAccessibleText("Share");
        return button;
    }

    private static Node iconAndText() {
        RXTaskButton button = succeeding("Share", WORK_MILLIS);
        button.setGraphic(ButtonIcons.icon("share-icon"));
        button.setPendingText("Sharing…");
        button.setSucceededText("Shared");
        return button;
    }

    // ==================== Fail and retry ====================

    private static Node retry() {
        RXTaskButton button = new RXTaskButton("Connect");
        button.setPendingText("Connecting…");
        button.setFailedText("Retry");
        button.setOnAction(event -> button.track(failingRequest()));
        return button;
    }

    // Completes exceptionally on a background thread, like a request that fails.
    private static CompletableFuture<Void> failingRequest() {
        return CompletableFuture.runAsync(() -> {
            throw new IllegalStateException("No connection");
        }, CompletableFuture.delayedExecutor(WORK_MILLIS, TimeUnit.MILLISECONDS));
    }

    // ==================== Cancellable ====================

    private static Node cancellable() {
        HBox row = new HBox(32.0, labelled(download(true), "Cancellable"),
                labelled(download(false), "Not cancellable"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXTaskButton download(boolean cancellable) {
        RXTaskButton button = succeeding("Download", 5000.0);
        button.setGraphic(ButtonIcons.icon("download-icon"));
        button.setCancellable(cancellable);
        button.setPendingText("Downloading…");
        button.setSucceededText("Downloaded");
        return button;
    }

    private static Node labelled(Node node, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("caption");
        VBox box = new VBox(8.0, node, label);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Pending state only ====================

    private static Node pendingOnly() {
        RXTaskButton button = new RXTaskButton("Apply settings");
        button.setSucceededDuration(Duration.ZERO);
        button.setFailedDuration(Duration.ZERO);
        button.setOnAction(event -> {
            RXTaskButton.Run run = button.start();
            after(WORK_MILLIS, () -> {
                run.succeed();
                RXSnackbars.success(button, "Settings applied");
            });
        });
        return button;
    }

    // ==================== Custom indicator ====================

    private static Node dots() {
        RXDotPulse indicator = new RXDotPulse();
        indicator.setDotSize(4.0);
        indicator.setDotGap(3.0);
        RXTaskButton button = succeeding("Send", 2500.0);
        button.setPendingIndicator(decorative(indicator));
        button.setSucceededText("Sent");
        return button;
    }

    private static Node bars() {
        RXBarSpinner indicator = new RXBarSpinner();
        indicator.setBarWidth(3.0);
        indicator.setBarHeight(14.0);
        indicator.setBarGap(2.0);
        RXTaskButton button = succeeding("Record", 2500.0);
        button.setPendingIndicator(decorative(indicator));
        button.setPendingText("Listening");
        button.setSucceededText("Recorded");
        return button;
    }

    // A control inside the button would otherwise take the Tab key and the
    // clicks that land on it.
    private static Node decorative(Node indicator) {
        indicator.setFocusTraversable(false);
        indicator.setMouseTransparent(true);
        return indicator;
    }

    // ==================== Colors ====================

    private static Node solid(String text, String succeededText, String color) {
        RXTaskButton button = succeeding(text, WORK_MILLIS);
        button.setSucceededText(succeededText);
        button.getStyleClass().addAll("solid", color);
        return button;
    }

    // ==================== Animated swap ====================

    private static Node animated(String name, PageAnimation animation) {
        RXTaskButton button = succeeding(name, WORK_MILLIS);
        button.setSucceededText("Done");
        button.setAnimation(animation);
        button.setPrefWidth(110.0);
        return button;
    }
}

package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ImageFit;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXSegmentedProgressBar page: a stories player, a password strength meter, course
 * progress across lessons, a download on many thin segments and indeterminate bars.
 */
public final class RXSegmentedProgressBarSamples {

    private static final int STORY_COUNT = 4;
    private static final double STORY_SECONDS = 3.0;
    private static final int LESSON_COUNT = 8;
    private static final List<String> STRENGTHS = List.of("Too weak", "Weak", "Fair", "Good", "Strong");
    private static final List<String> STRENGTH_COLORS = List.of("rose", "rose", "amber", "lime", "teal");

    private RXSegmentedProgressBarSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A horizontal progress bar split into equal segments that fill from left to right.",
                List.of("Progress that reads better in steps: stories, lessons, strength meters, multi-part tasks.",
                        "When the user moves between steps, use RXSegmentedStepIndicator; for a round shape, RXCircularProgressIndicator."),
                List.of(
                        SampleCard.wide("Stories",
                                "One segment per story; Play runs through the rest and the arrows jump between stories.",
                                stories()),
                        SampleCard.large("Password strength",
                                "Four segments for four rules; a color class per level replaces the default fill.",
                                strength()),
                        SampleCard.large("Course progress",
                                "The segment of the current lesson fills partly; at 100% the bar turns green through :completed.",
                                course()),
                        SampleCard.large("Download",
                                "Twenty short segments with a 2 px gap read like a classic segmented meter.",
                                download()),
                        SampleCard.large("Indeterminate",
                                "Without a progress value a band sweeps across; one segment makes a plain linear bar.",
                                indeterminate())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Stories ====================

    private static Node stories() {
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(1.5 / STORY_COUNT);
        bar.getStyleClass().add("on-image");
        bar.setSegmentCount(STORY_COUNT);
        bar.setSegmentHeight(4.0);
        bar.setProgressTransitionDuration(Duration.ZERO);
        bar.setMaxWidth(Double.MAX_VALUE);
        StackPane.setMargin(bar, new Insets(12.0));
        StackPane.setAlignment(bar, Pos.TOP_CENTER);

        Image[] images = new Image[STORY_COUNT];
        for (int i = 0; i < STORY_COUNT; i++) {
            images[i] = SampleImages.scenery(i + 1);
        }
        RXImageView view = new RXImageView(images[1]);
        view.setImageFit(ImageFit.COVER);
        view.setImageRadius(12.0);
        Label caption = text("Story 2 of 4", "on-image-text");
        StackPane.setAlignment(caption, Pos.BOTTOM_LEFT);
        StackPane.setMargin(caption, new Insets(14.0));

        StackPane frame = new StackPane(view, bar, caption);
        frame.getStyleClass().add("story-frame");
        frame.setPrefSize(420.0, 230.0);
        frame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Timeline[] running = new Timeline[1];
        bar.progressProperty().addListener((obs, oldValue, newValue) -> {
            int index = Math.min(STORY_COUNT - 1, (int) (newValue.doubleValue() * STORY_COUNT));
            view.setImage(images[index]);
            caption.setText("Story " + (index + 1) + " of " + STORY_COUNT);
        });

        RXButton previous = plain("Previous");
        previous.setOnAction(event -> {
            stop(running);
            int index = Math.min(STORY_COUNT - 1, (int) (bar.getProgress() * STORY_COUNT));
            bar.setProgress(Math.max(0, index - 1) / (double) STORY_COUNT);
        });
        RXButton next = plain("Next");
        next.setOnAction(event -> {
            stop(running);
            int index = (int) (bar.getProgress() * STORY_COUNT);
            bar.setProgress(Math.min(STORY_COUNT, index + 1) / (double) STORY_COUNT);
        });
        RXButton play = solid("Play", "pink");
        play.setGraphic(icon("play-icon"));
        play.setOnAction(event -> {
            stop(running);
            if (bar.getProgress() >= 1.0) {
                bar.setProgress(0.0);
            }
            double rest = (1.0 - bar.getProgress()) * STORY_COUNT * STORY_SECONDS;
            running[0] = new Timeline(new KeyFrame(Duration.seconds(rest),
                    new KeyValue(bar.progressProperty(), 1.0, Interpolator.LINEAR)));
            running[0].play();
        });
        HBox controls = new HBox(10.0, previous, play, next);
        controls.setAlignment(Pos.CENTER);
        VBox box = new VBox(16.0, frame, controls);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static void stop(Timeline[] running) {
        if (running[0] != null) {
            running[0].stop();
            running[0] = null;
        }
    }

    // ==================== Password strength ====================

    private static Node strength() {
        RXPasswordField field = new RXPasswordField();
        field.setText("sunnyday42");
        field.setPrefColumnCount(18);
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(0.0);
        bar.setSegmentCount(4);
        bar.setSegmentHeight(6.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label verdict = text("", "muted");
        Runnable update = () -> {
            int score = score(field.getText());
            bar.setProgress(score / 4.0);
            bar.getStyleClass().removeAll(STRENGTH_COLORS);
            bar.getStyleClass().add(STRENGTH_COLORS.get(score));
            verdict.setText(STRENGTHS.get(score));
        };
        field.textProperty().addListener((obs, oldValue, newValue) -> update.run());
        update.run();

        HBox header = new HBox(text("New password", "muted"), spacer(), verdict);
        VBox form = new VBox(8.0, header, field, bar,
                text("Use 8+ characters with upper case, digits and symbols.", "muted"));
        form.setPrefWidth(320.0);
        form.setMaxWidth(Region.USE_PREF_SIZE);
        return form;
    }

    private static int score(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (password.length() >= 8) {
            score++;
        }
        if (!password.equals(password.toLowerCase()) && !password.equals(password.toUpperCase())) {
            score++;
        }
        if (password.chars().anyMatch(Character::isDigit)) {
            score++;
        }
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            score++;
        }
        return score;
    }

    // ==================== Course progress ====================

    private static Node course() {
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(2.5 / LESSON_COUNT);
        bar.setSegmentCount(LESSON_COUNT);
        bar.setSegmentHeight(8.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label lesson = text("", "muted");
        lesson.textProperty().bind(Bindings.createStringBinding(() -> {
            double done = bar.getProgress() * LESSON_COUNT;
            if (done >= LESSON_COUNT) {
                return "Course complete";
            }
            return "Lesson " + ((int) done + 1) + " of " + LESSON_COUNT + ", "
                    + Math.round(bar.getProgress() * 100) + "% done";
        }, bar.progressProperty()));
        RXButton watch = solid("Watch 10 minutes", "violet");
        watch.setGraphic(icon("play-icon"));
        watch.setOnAction(event -> bar.setProgress(Math.min(1.0, bar.getProgress() + 0.5 / LESSON_COUNT)));
        RXButton restart = plain("Restart");
        restart.setOnAction(event -> bar.setProgress(0.0));
        VBox box = new VBox(10.0, text("JavaFX layout basics", "headline"), lesson, bar,
                new HBox(8.0, watch, restart));
        box.setPrefWidth(340.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(bar, new Insets(4.0, 0, 8.0, 0));
        return box;
    }

    // ==================== Download ====================

    private static Node download() {
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(0.35);
        bar.getStyleClass().add("sky");
        bar.setSegmentCount(20);
        bar.setSegmentGap(2.0);
        bar.setSegmentHeight(6.0);
        bar.setProgressTransitionDuration(Duration.ZERO);
        bar.setMaxWidth(Double.MAX_VALUE);
        Label state = text("", "muted");
        state.textProperty().bind(Bindings.createStringBinding(
                () -> Math.round(bar.getProgress() * 640) + " of 640 MB", bar.progressProperty()));
        RXButton start = solid("Download", "sky");
        start.setGraphic(icon("download-icon"));
        start.setOnAction(event -> {
            start.setDisable(true);
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(bar.progressProperty(), 0.0)),
                    new KeyFrame(Duration.seconds(4.0), new KeyValue(bar.progressProperty(), 1.0, Interpolator.LINEAR)));
            timeline.setOnFinished(done -> start.setDisable(false));
            timeline.play();
        });
        HBox header = new HBox(10.0, icon("file-icon"), text("design-assets.zip", "copy"), spacer(), state);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(12.0, header, bar, start);
        FocusHandOff.onDisable(start, box);
        box.setPrefWidth(340.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Indeterminate ====================

    private static Node indeterminate() {
        RXSegmentedProgressBar single = new RXSegmentedProgressBar();
        single.getStyleClass().add("indigo");
        single.setSegmentCount(1);
        single.setSegmentHeight(6.0);
        single.setMaxWidth(Double.MAX_VALUE);

        RXSegmentedProgressBar three = new RXSegmentedProgressBar();
        three.getStyleClass().add("orange");
        three.setSegmentCount(3);
        three.setSegmentHeight(6.0);
        three.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(10.0, text("Checking for updates", "copy"), single,
                text("Preparing three reports", "copy"), three);
        box.setPrefWidth(320.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(single, new Insets(0, 0, 14.0, 0));
        return box;
    }
}

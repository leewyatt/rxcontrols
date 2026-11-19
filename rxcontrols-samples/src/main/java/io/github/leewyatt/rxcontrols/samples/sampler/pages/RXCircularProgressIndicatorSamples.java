package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXCircularProgressIndicator page: a file upload list, activity rings with icons,
 * a storage meter that changes color, a spinner inside a save button, indeterminate
 * spinners in three sizes and a counter-clockwise resend countdown.
 */
public final class RXCircularProgressIndicatorSamples {

    private static final int RESEND_SECONDS = 30;

    private RXCircularProgressIndicatorSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A ring that fills as progress grows, with a spinning arc while the progress is unknown.",
                List.of("Progress of one task where a compact, round shape fits: uploads, quotas, goals, countdowns.",
                        "For a long horizontal bar or several stages use RXSegmentedProgressBar."),
                List.of(
                        SampleCard.large("File upload",
                                "One small ring per file; the text is hidden at this size and the ring turns green through :completed.",
                                uploads()),
                        SampleCard.large("Activity rings",
                                "A graphic sits above the percentage; thick strokes and one color class per goal.",
                                goals()),
                        SampleCard.large("Storage",
                                "A textFactory shows gigabytes instead of a percentage; the ring turns red above 90%.",
                                storage()),
                        SampleCard.large("Saving",
                                "An indeterminate ring as a button graphic while the request runs, then a check mark.",
                                saving()),
                        SampleCard.large("Indeterminate",
                                "Without a progress value the arc spins; three sizes and stroke widths.",
                                spinners()),
                        SampleCard.large("Resend countdown",
                                "Resend starts a 30 second countdown; clockwise false drains the ring the other way and the button unlocks when it is empty.",
                                countdown())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== File upload ====================

    private static Node uploads() {
        List<RXCircularProgressIndicator> rings = new ArrayList<>();
        VBox list = new VBox(0.0,
                fileRow("holiday-video.mp4", "84 MB", 1.0, rings),
                fileRow("site-photos.zip", "36 MB", 0.64, rings),
                fileRow("contract.pdf", "2 MB", 0.0, rings));
        list.getStyleClass().add("panel");
        list.setPrefWidth(380.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);

        RXButton upload = solid("Upload all", "teal");
        upload.setGraphic(icon("upload-icon"));
        upload.setOnAction(event -> {
            Timeline timeline = new Timeline();
            for (int i = 0; i < rings.size(); i++) {
                RXCircularProgressIndicator ring = rings.get(i);
                ring.setProgress(0.0);
                timeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1.4 + i * 0.9),
                        new KeyValue(ring.progressProperty(), 1.0, Interpolator.EASE_OUT)));
            }
            upload.setDisable(true);
            timeline.setOnFinished(done -> upload.setDisable(false));
            timeline.play();
        });
        VBox box = new VBox(16.0, list, upload);
        FocusHandOff.onDisable(upload, box);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node fileRow(String name, String size, double progress, List<RXCircularProgressIndicator> rings) {
        RXCircularProgressIndicator ring = new RXCircularProgressIndicator(progress);
        ring.setPrefSize(28.0, 28.0);
        ring.setTrackStrokeWidth(3.0);
        ring.setProgressStrokeWidth(3.0);
        ring.setProgressTransitionDuration(Duration.ZERO);
        ring.setTextFactory(value -> "");
        rings.add(ring);

        Label state = text("", "muted");
        state.textProperty().bind(Bindings.createStringBinding(() -> {
            double value = ring.getProgress();
            if (value >= 1.0) {
                return "Done";
            }
            return value <= 0.0 ? "Waiting" : Math.round(value * 100) + "%";
        }, ring.progressProperty()));
        state.setMinWidth(54.0);
        state.setAlignment(Pos.CENTER_RIGHT);

        VBox names = new VBox(2.0, text(name, "copy"), text(size, "muted"));
        HBox row = new HBox(12.0, icon("file-icon"), names, spacer(), state, ring);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Activity rings ====================

    private static Node goals() {
        HBox row = new HBox(30.0,
                goal("walk-icon", "Steps", "7,240 of 10,000", 0.72, "rose"),
                goal("drop-icon", "Water", "1.2 of 2 L", 0.6, "sky"),
                goal("moon-icon", "Sleep", "7 h of 8 h", 0.88, "violet"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node goal(String iconClass, String name, String detail, double progress, String color) {
        RXCircularProgressIndicator ring = new RXCircularProgressIndicator(progress);
        ring.getStyleClass().addAll(color, "large-text");
        ring.setPrefSize(96.0, 96.0);
        ring.setTrackStrokeWidth(9.0);
        ring.setProgressStrokeWidth(9.0);
        ring.setGraphic(icon(iconClass));
        VBox box = new VBox(4.0, ring, text(name, "copy"), text(detail, "muted"));
        box.setAlignment(Pos.CENTER);
        VBox.setMargin(ring, new Insets(0, 0, 6.0, 0));
        return box;
    }

    // ==================== Storage ====================

    private static Node storage() {
        RXCircularProgressIndicator ring = new RXCircularProgressIndicator(0.68);
        ring.getStyleClass().addAll("amber", "large-text");
        ring.setPrefSize(132.0, 132.0);
        ring.setTrackStrokeWidth(12.0);
        ring.setProgressStrokeWidth(12.0);
        ring.setProgressTransitionDuration(Duration.millis(600.0));
        ring.setTextFactory(value -> Math.round(value * 100) + " GB");
        ring.progressProperty().addListener((obs, oldValue, newValue) -> {
            boolean full = newValue.doubleValue() > 0.9;
            ring.getStyleClass().removeAll("amber", "rose");
            ring.getStyleClass().add(full ? "rose" : "amber");
        });

        Label used = text("", "copy");
        used.textProperty().bind(Bindings.createStringBinding(
                () -> Math.round(ring.getProgress() * 100) + " GB of 100 GB used", ring.progressProperty()));
        RXButton add = plain("Add videos");
        add.setOnAction(event -> ring.setProgress(Math.min(0.97, ring.getProgress() + 0.12)));
        RXButton clean = plain("Free up space");
        clean.setOnAction(event -> ring.setProgress(Math.max(0.2, ring.getProgress() - 0.25)));
        VBox side = new VBox(10.0, text("Cloud storage", "headline"), used, new HBox(8.0, add, clean));
        side.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(28.0, ring, side);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ==================== Saving ====================

    private static Node saving() {
        TextField name = new TextField("Weekly planning");
        name.setPrefColumnCount(16);
        VBox field = new VBox(6.0, text("Board name", "muted"), name);

        RXButton save = solid("Save changes", "indigo");
        save.setPrefWidth(150.0);
        save.setOnAction(event -> {
            RXCircularProgressIndicator spinner = new RXCircularProgressIndicator();
            spinner.getStyleClass().add("on-solid");
            spinner.setMinSize(16.0, 16.0);
            spinner.setPrefSize(16.0, 16.0);
            spinner.setTrackStrokeWidth(2.0);
            spinner.setProgressStrokeWidth(2.0);
            save.setGraphic(spinner);
            save.setText("Saving");
            save.setMouseTransparent(true);
            PauseTransition request = new PauseTransition(Duration.seconds(1.6));
            request.setOnFinished(done -> {
                save.setGraphic(icon("check-icon"));
                save.setText("Saved");
                PauseTransition rest = new PauseTransition(Duration.seconds(1.4));
                rest.setOnFinished(after -> {
                    save.setGraphic(null);
                    save.setText("Save changes");
                    save.setMouseTransparent(false);
                });
                rest.play();
            });
            request.play();
        });
        VBox form = new VBox(16.0, field, save);
        form.getStyleClass().addAll("panel", "padded");
        form.setMaxWidth(Region.USE_PREF_SIZE);
        return form;
    }

    // ==================== Indeterminate ====================

    private static Node spinners() {
        HBox row = new HBox(40.0,
                spinner(24.0, 3.0, "teal", "24 px"),
                spinner(40.0, 4.0, "indigo", "40 px"),
                spinner(64.0, 6.0, "pink", "64 px"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node spinner(double size, double stroke, String color, String caption) {
        RXCircularProgressIndicator ring = new RXCircularProgressIndicator();
        ring.getStyleClass().add(color);
        ring.setPrefSize(size, size);
        ring.setTrackStrokeWidth(stroke);
        ring.setProgressStrokeWidth(stroke);
        VBox box = new VBox(10.0, ring, text(caption, "muted"));
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }

    // ==================== Resend countdown ====================

    private static Node countdown() {
        RXCircularProgressIndicator ring = new RXCircularProgressIndicator(0.0);
        ring.getStyleClass().add("orange");
        ring.setPrefSize(60.0, 60.0);
        ring.setClockwise(false);
        ring.setProgressTransitionDuration(Duration.ZERO);
        ring.setTextFactory(value -> value <= 0.0 ? "Ready" : (int) Math.ceil(value * RESEND_SECONDS) + " s");

        TextField code = new TextField();
        code.setPromptText("6-digit code");
        code.setPrefColumnCount(10);
        RXButton resend = plain("Resend code");
        FocusHandOff.onDisable(resend, code);
        resend.setOnAction(event -> {
            resend.setDisable(true);
            Timeline timeline = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(ring.progressProperty(), 1.0)),
                    new KeyFrame(Duration.seconds(RESEND_SECONDS),
                            new KeyValue(ring.progressProperty(), 0.0, Interpolator.LINEAR)));
            timeline.setOnFinished(done -> resend.setDisable(false));
            timeline.play();
        });
        HBox actions = new HBox(10.0, code, resend);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox texts = new VBox(8.0, text("Check your email", "headline"),
                text("We sent a code to lee@example.com.", "muted"), actions);

        HBox row = new HBox(22.0, ring, texts);
        row.setAlignment(Pos.CENTER);
        return row;
    }
}

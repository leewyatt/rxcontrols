package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXBarSpinner.AnimationMode;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXTaskButton;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXBarSpinner page: a "now playing" marker in a playlist, the four animation modes,
 * a voice input that stops its bars, the busy indicator of a task button and two bar
 * shapes.
 */
public final class RXBarSpinnerSamples {

    private static final Duration DEFAULT_CYCLE = Duration.millis(1000.0);

    private RXBarSpinnerSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A decorative loading mark: a row of bars that rise and fall like an equalizer.",
                List.of("Mark something as playing, listening or busy, especially around audio and media.",
                        "It shows no amount of progress; for a known amount use RXCircularProgressIndicator."),
                List.of(
                        SampleCard.large("Now playing",
                                "The playing track shows small bars instead of its number; click a track to play it, click again to pause.",
                                playlist()),
                        SampleCard.large("Voice input",
                                "While listening the bars use RANDOM; setting cycleDuration to null stops them at their resting height.",
                                voice()),
                        SampleCard.wide("Animation modes",
                                "WAVE (the default), BOUNCE, PULSE and RANDOM.",
                                mode(AnimationMode.WAVE, "teal"), mode(AnimationMode.BOUNCE, "orange"),
                                mode(AnimationMode.PULSE, "violet"), mode(AnimationMode.RANDOM, "pink")),
                        SampleCard.large("In a button",
                                "An RXTaskButton with white bars as its pendingIndicator while a summary is written.",
                                summary()),
                        SampleCard.large("Bar shapes",
                                "barCount, barWidth and barGap give three thick bars, squared off by a .bar radius in CSS, or nine thin ones; minBarHeightRatio sets how low they go.",
                                shapes())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Now playing ====================

    private static Node playlist() {
        String[][] tracks = {
                {"Morning Light", "3:42"},
                {"City After Rain", "4:05"},
                {"Paper Boats", "2:58"},
                {"Slow River", "5:11"}};
        List<RXBarSpinner> spinners = new ArrayList<>();
        List<HBox> rows = new ArrayList<>();
        VBox list = new VBox(0.0);
        for (int i = 0; i < tracks.length; i++) {
            RXBarSpinner spinner = new RXBarSpinner();
            spinner.getStyleClass().add("pink");
            spinner.setBarCount(4);
            spinner.setBarWidth(3.0);
            spinner.setBarHeight(14.0);
            spinner.setBarGap(2.0);
            spinner.setVisible(false);
            Label number = text(String.valueOf(i + 1), "muted");
            number.visibleProperty().bind(spinner.visibleProperty().not());
            StackPane slot = new StackPane(number, spinner);
            slot.setPrefWidth(24.0);
            slot.setMinWidth(24.0);

            HBox row = new HBox(12.0, slot, text(tracks[i][0], "copy"), spacer(), text(tracks[i][1], "muted"));
            row.getStyleClass().addAll("panel-row", "song");
            row.setAlignment(Pos.CENTER_LEFT);
            spinners.add(spinner);
            rows.add(row);
            list.getChildren().add(row);
        }
        for (int i = 0; i < rows.size(); i++) {
            RXBarSpinner own = spinners.get(i);
            HBox row = rows.get(i);
            row.setOnMouseClicked(event -> {
                boolean play = !own.isVisible();
                for (int j = 0; j < spinners.size(); j++) {
                    spinners.get(j).setVisible(false);
                    rows.get(j).getStyleClass().remove("playing");
                }
                own.setVisible(play);
                if (play) {
                    row.getStyleClass().add("playing");
                }
            });
        }
        spinners.get(1).setVisible(true);
        rows.get(1).getStyleClass().add("playing");
        list.getStyleClass().add("panel");
        list.setPrefWidth(340.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    // ==================== Voice input ====================

    private static Node voice() {
        RXBarSpinner bars = new RXBarSpinner(AnimationMode.RANDOM);
        bars.getStyleClass().add("rose");
        bars.setBarCount(9);
        bars.setBarWidth(5.0);
        bars.setBarHeight(40.0);
        bars.setBarGap(4.0);
        bars.setCycleDuration(null);

        Label state = text("Tap the microphone and speak", "muted");
        RXButton mic = new RXButton(null, icon("mic-icon"));
        mic.getStyleClass().addAll("round-action", "rose");
        mic.setOnAction(event -> {
            boolean listening = bars.getCycleDuration() == null;
            bars.setCycleDuration(listening ? DEFAULT_CYCLE : null);
            state.setText(listening ? "Listening..." : "Tap the microphone and speak");
            mic.getStyleClass().remove("active");
            if (listening) {
                mic.getStyleClass().add("active");
            }
        });
        HBox row = new HBox(24.0, mic, bars);
        row.setAlignment(Pos.CENTER);
        VBox box = new VBox(16.0, row, state);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Animation modes ====================

    private static Node mode(AnimationMode mode, String color) {
        RXBarSpinner spinner = new RXBarSpinner(mode);
        spinner.getStyleClass().add(color);
        spinner.setBarWidth(6.0);
        spinner.setBarHeight(40.0);
        spinner.setBarGap(5.0);
        VBox box = new VBox(12.0, spinner, text(mode.name(), "muted"));
        box.setAlignment(Pos.BOTTOM_CENTER);
        box.setMinWidth(110.0);
        return box;
    }

    // ==================== In a button ====================

    private static Node summary() {
        Label result = text("Summarize the meeting notes in three lines.", "copy");
        result.setWrapText(true);
        result.setPrefWidth(300.0);
        RXBarSpinner bars = new RXBarSpinner(AnimationMode.BOUNCE);
        bars.getStyleClass().add("on-solid");
        bars.setBarCount(4);
        bars.setBarWidth(3.0);
        bars.setBarHeight(14.0);
        bars.setBarGap(2.0);
        // A control inside the button would otherwise take the Tab key and the
        // clicks that land on it.
        bars.setFocusTraversable(false);
        bars.setMouseTransparent(true);

        RXTaskButton generate = new RXTaskButton("Write summary", icon("spark-icon"));
        generate.getStyleClass().addAll("solid", "violet");
        generate.setPendingIndicator(bars);
        generate.setPendingText("Writing");
        // The new summary below is the result; the button goes straight back.
        generate.setSucceededDuration(Duration.ZERO);
        generate.setOnAction(event -> {
            RXTaskButton.Run run = generate.start();
            PauseTransition work = new PauseTransition(Duration.seconds(2.0));
            work.setOnFinished(done -> {
                run.succeed();
                generate.setText("Write again");
                result.setText("The release moves to Friday. Lee owns the docs. Tests must pass before merge.");
            });
            work.play();
        });
        VBox box = new VBox(14.0, result, generate);
        box.getStyleClass().addAll("panel", "padded");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    // ==================== Bar shapes ====================

    private static Node shapes() {
        RXBarSpinner thick = new RXBarSpinner();
        thick.getStyleClass().addAll("sky", "square-bars");
        thick.setBarCount(3);
        thick.setBarWidth(12.0);
        thick.setBarHeight(40.0);
        thick.setBarGap(6.0);

        RXBarSpinner thin = new RXBarSpinner(AnimationMode.BOUNCE);
        thin.getStyleClass().add("amber");
        thin.setBarCount(9);
        thin.setBarWidth(3.0);
        thin.setBarHeight(40.0);
        thin.setBarGap(3.0);
        thin.setMinBarHeightRatio(0.05);

        HBox row = new HBox(56.0, captioned(thick, "3 bars, 12 px wide"), captioned(thin, "9 bars, ratio 0.05"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node captioned(Node node, String caption) {
        VBox box = new VBox(12.0, node, text(caption, "muted"));
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }
}

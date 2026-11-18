package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDigit;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.playWhileShowing;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXDigit page: a live LED clock, a stopwatch, a scoreboard, an LCD readout with
 * unlit segments and a countdown timer, each built from single digits in a row.
 */
public final class RXDigitSamples {

    private static final Duration STOPWATCH_TICK = Duration.millis(100.0);
    private static final int COUNTDOWN_SECONDS = 5 * 60;

    private RXDigitSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "One seven-segment digit; put several in a row for clocks, timers and counters.",
                List.of("Retro or hardware-style readouts: clocks, scoreboards, timers, meters and dashboards.",
                        "For plain changing numbers use a Label, or RXTransitionLabel to animate the change."),
                List.of(
                        SampleCard.wide("Digital clock",
                                "Six digits and two colons show the current time; a timeline updates them every second.",
                                clock()),
                        SampleCard.medium("Stopwatch",
                                "Minutes, seconds and tenths; start, stop and reset with the buttons.",
                                stopwatch()),
                        SampleCard.medium("Scoreboard",
                                "Click a team's +1 to add a point; litFill colors each side.",
                                scoreboard()),
                        SampleCard.medium("LCD readout",
                                "unlitFill shows the dark segments too, like a calculator or a meter display.",
                                lcd()),
                        SampleCard.medium("Countdown",
                                "A five-minute timer counting down in amber; it starts over at zero.",
                                countdown())),
                MediaKit.STYLESHEET);
    }

    // ==================== Digital clock ====================

    private static Node clock() {
        List<RXDigit> digits = digits(6, 44.0, "red");
        HBox row = new HBox(8.0,
                digits.get(0), digits.get(1), colon("red", 88.0),
                digits.get(2), digits.get(3), colon("red", 88.0),
                digits.get(4), digits.get(5));
        row.setAlignment(Pos.CENTER);
        Runnable update = () -> {
            LocalTime now = LocalTime.now();
            show(digits, 0, now.getHour(), 2);
            show(digits, 2, now.getMinute(), 2);
            show(digits, 4, now.getSecond(), 2);
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> update.run()));
        timeline.setCycleCount(Animation.INDEFINITE);
        playWhileShowing(row, timeline);
        return led(row);
    }

    // ==================== Stopwatch ====================

    private static Node stopwatch() {
        List<RXDigit> digits = digits(5, 26.0, "teal");
        HBox row = new HBox(5.0, digits.get(0), digits.get(1), colon("teal", 52.0),
                digits.get(2), digits.get(3), dot("teal"), digits.get(4));
        row.setAlignment(Pos.CENTER);
        int[] tenths = {0};
        Runnable update = () -> {
            int total = tenths[0];
            show(digits, 0, total / 600 % 100, 2);
            show(digits, 2, total / 10 % 60, 2);
            digits.get(4).setDigit(total % 10);
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(STOPWATCH_TICK, event -> {
            tenths[0]++;
            update.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);

        RXButton start = solid("Start", "teal");
        start.setOnAction(event -> {
            if (timeline.getStatus() == Animation.Status.RUNNING) {
                timeline.pause();
                start.setText("Start");
            } else {
                timeline.play();
                start.setText("Stop");
            }
        });
        RXButton reset = new RXButton("Reset");
        reset.setOnAction(event -> {
            tenths[0] = 0;
            update.run();
        });
        row.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                timeline.pause();
                start.setText("Start");
            }
        });
        HBox buttons = new HBox(10.0, reset, start);
        buttons.setAlignment(Pos.CENTER);
        VBox box = new VBox(14.0, led(row), buttons);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Scoreboard ====================

    private static Node scoreboard() {
        HBox teams = new HBox(28.0, team("HOME", "orange", 2), team("AWAY", "sky", 1));
        teams.setAlignment(Pos.CENTER);
        return led(teams);
    }

    private static Node team(String name, String color, int start) {
        List<RXDigit> digits = digits(2, 26.0, color);
        HBox score = new HBox(5.0, digits.get(0), digits.get(1));
        score.setAlignment(Pos.CENTER);
        int[] points = {start};
        show(digits, 0, start, 2);
        RXButton add = solid("+1", color);
        add.setOnAction(event -> {
            points[0] = (points[0] + 1) % 100;
            show(digits, 0, points[0], 2);
        });
        VBox box = new VBox(8.0, text(name, "screen-text"), score, add);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== LCD readout ====================

    private static Node lcd() {
        List<RXDigit> digits = digits(4, 28.0, "lcd");
        HBox row = new HBox(5.0, digits.get(0), digits.get(1), dot("lcd"), digits.get(2), digits.get(3));
        row.setAlignment(Pos.BOTTOM_CENTER);
        show(digits, 0, 2186, 4);
        HBox reading = new HBox(10.0, row, text("V", "lcd-unit"));
        reading.setAlignment(Pos.BOTTOM_CENTER);
        StackPane panel = new StackPane(reading);
        panel.getStyleClass().add("lcd-panel");
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return panel;
    }

    // ==================== Countdown ====================

    private static Node countdown() {
        List<RXDigit> digits = digits(4, 30.0, "amber");
        HBox row = new HBox(5.0, digits.get(0), digits.get(1), colon("amber", 60.0), digits.get(2), digits.get(3));
        row.setAlignment(Pos.CENTER);
        int[] left = {COUNTDOWN_SECONDS};
        Runnable update = () -> {
            show(digits, 0, left[0] / 60, 2);
            show(digits, 2, left[0] % 60, 2);
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> {
            left[0] = left[0] == 0 ? COUNTDOWN_SECONDS : left[0] - 1;
            update.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        playWhileShowing(row, timeline);
        VBox box = new VBox(8.0, led(row), text("Break ends in", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Helpers ====================

    private static List<RXDigit> digits(int count, double width, String color) {
        List<RXDigit> digits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RXDigit digit = new RXDigit();
            digit.setPrefSize(width, width * 2.0);
            digit.getStyleClass().add(color);
            digits.add(digit);
        }
        return digits;
    }

    private static void show(List<RXDigit> digits, int from, int value, int width) {
        int rest = value;
        for (int i = from + width - 1; i >= from; i--) {
            digits.get(i).setDigit(rest % 10);
            rest /= 10;
        }
    }

    private static Node colon(String color, double height) {
        Region top = new Region();
        top.getStyleClass().addAll("segment-dot", color);
        Region bottom = new Region();
        bottom.getStyleClass().addAll("segment-dot", color);
        VBox colon = new VBox(height * 0.25, top, bottom);
        colon.setAlignment(Pos.CENTER);
        return colon;
    }

    private static Node dot(String color) {
        Region dot = new Region();
        dot.getStyleClass().addAll("segment-dot", color);
        VBox box = new VBox(dot);
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }

    private static Node led(Node content) {
        StackPane panel = new StackPane(content);
        panel.getStyleClass().add("screen");
        panel.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return panel;
    }
}

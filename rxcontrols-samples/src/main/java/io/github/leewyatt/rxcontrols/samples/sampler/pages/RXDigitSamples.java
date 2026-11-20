package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDigit;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXDigit page: a live LCD desk clock, a stopwatch, a scoreboard, an LCD readout with
 * unlit segments, a countdown timer and every digit from 0 to 9, each built from single
 * digits in a row.
 */
public final class RXDigitSamples {

    private static final Duration STOPWATCH_TICK = Duration.millis(100.0);
    private static final int COUNTDOWN_SECONDS = 5 * 60;
    private static final double CLOCK_DIGIT_WIDTH = 34.0;
    private static final String[] WEEKDAYS = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
    private static final PseudoClass TODAY = PseudoClass.getPseudoClass("today");

    private RXDigitSamples() {
    }

    /**
     * Builds the page content.
     *
     * @param life the lifecycle of the page
     * @return the content
     */
    public static SampleContent create(SampleLifecycle life) {
        return new SampleContent(
                "One seven-segment digit; put several in a row for clocks, timers and counters.",
                List.of("Retro or hardware-style readouts: clocks, scoreboards, timers, meters and dashboards.",
                        "For plain changing numbers use a Label, or RXTransitionLabel to animate the change."),
                List.of(
                        SampleCard.wide("Desk clock",
                                "An LCD clock: large hours and minutes, small seconds, AM or PM and today's weekday; "
                                        + "unlitFill leaves the dark segments faintly visible, and a timeline ticks every second.",
                                clock(life)),
                        SampleCard.medium("Stopwatch",
                                "Minutes, seconds and tenths; start, stop and reset with the buttons.",
                                stopwatch(life)),
                        SampleCard.medium("Scoreboard",
                                "Click a team's +1 to add a point; litFill colors each side.",
                                scoreboard()),
                        SampleCard.medium("LCD readout",
                                "unlitFill shows the dark segments too, like a calculator or a meter display.",
                                lcd()),
                        SampleCard.medium("Countdown",
                                "A five-minute timer counting down in amber; it starts over at zero.",
                                countdown(life)),
                        SampleCard.wide("All ten digits",
                                "setDigit takes 0 to 9; each value lights its own set of the seven segments.",
                                allDigits())),
                MediaKit.STYLESHEET);
    }

    // ==================== Digital clock ====================

    private static Node clock(SampleLifecycle life) {
        List<Label> days = new ArrayList<>();
        HBox week = new HBox(20.0);
        week.setAlignment(Pos.CENTER);
        for (String day : WEEKDAYS) {
            Label label = text(day, "lcd-day");
            days.add(label);
            week.getChildren().add(label);
        }
        Label half = text("", "lcd-half");
        List<RXDigit> digits = digits(4, CLOCK_DIGIT_WIDTH, "lcd-clock");
        List<RXDigit> seconds = digits(2, CLOCK_DIGIT_WIDTH / 2.0, "lcd-clock");
        // The small seconds sit on the baseline of the large digits.
        HBox small = new HBox(4.0, seconds.get(0), seconds.get(1));
        small.setAlignment(Pos.BOTTOM_LEFT);
        HBox time = new HBox(8.0, digits.get(0), digits.get(1), colon("lcd-clock", CLOCK_DIGIT_WIDTH * 2.0),
                digits.get(2), digits.get(3), small);
        time.setAlignment(Pos.CENTER_LEFT);
        HBox reading = new HBox(18.0, half, time);
        reading.setAlignment(Pos.BOTTOM_CENTER);

        Runnable update = () -> {
            LocalDateTime now = LocalDateTime.now();
            int hour = now.getHour() % 12 == 0 ? 12 : now.getHour() % 12;
            show(digits, 0, hour, 2);
            show(digits, 2, now.getMinute(), 2);
            show(seconds, 0, now.getSecond(), 2);
            half.setText(now.getHour() < 12 ? "AM" : "PM");
            int today = now.getDayOfWeek().getValue() - 1;
            for (int i = 0; i < days.size(); i++) {
                days.get(i).pseudoClassStateChanged(TODAY, i == today);
            }
        };
        update.run();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.0), event -> update.run()));
        timeline.setCycleCount(Animation.INDEFINITE);
        life.playWhileShown(timeline);

        VBox screen = new VBox(10.0, week, reading);
        screen.getStyleClass().add("lcd-screen");
        StackPane clock = new StackPane(screen);
        clock.getStyleClass().add("lcd-bezel");
        clock.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return clock;
    }

    // ==================== Stopwatch ====================

    private static Node stopwatch(SampleLifecycle life) {
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
        life.whenHidden(() -> {
            timeline.pause();
            start.setText("Start");
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

    private static Node countdown(SampleLifecycle life) {
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
        life.playWhileShown(timeline);
        VBox box = new VBox(8.0, led(row), text("Break ends in", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== All ten digits ====================

    private static Node allDigits() {
        List<RXDigit> digits = digits(10, 30.0, "teal");
        HBox row = new HBox(12.0);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < digits.size(); i++) {
            digits.get(i).setDigit(i);
            row.getChildren().add(digits.get(i));
        }
        return led(row);
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

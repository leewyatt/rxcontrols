package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXWaveProgressIndicator page: a charging battery, a water intake tracker, a row of
 * colored tanks with a back wave, an indeterminate sync and calm versus choppy water.
 */
public final class RXWaveProgressIndicatorSamples {

    private static final double WATER_GOAL_ML = 2000.0;
    private static final double GLASS_ML = 250.0;

    private RXWaveProgressIndicatorSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A round tank whose water level follows the progress, with waves moving on the surface.",
                List.of("A playful, eye-catching level: charging, a daily goal, a tank or a quota on a dashboard.",
                        "For a plain, compact ring use RXCircularProgressIndicator."),
                List.of(
                        SampleCard.large("Battery charging",
                                "Plug in to fill the tank; the percentage turns white where the water covers it.",
                                battery()),
                        SampleCard.large("Water intake",
                                "Each glass adds 250 ml of a 2 L goal; a textFactory shows milliliters.",
                                water()),
                        SampleCard.wide("Colors",
                                "-rx-front-wave-fill colors the water; the back wave, transparent by default, adds depth on the right.",
                                tank(0.3, "teal", null), tank(0.7, "violet", null),
                                tank(0.85, "rose", null), tank(0.38, "sky", Color.web("#0ea5e9", 0.35))),
                        SampleCard.large("Indeterminate",
                                "With an unknown progress the water level rises and falls.",
                                syncing()),
                        SampleCard.large("Calm and choppy",
                                "waveAmplitude and waveLength shape the surface: low and long on the left, high and short on the right.",
                                surfaces())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Battery charging ====================

    private static Node battery() {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator(0.24);
        tank.getStyleClass().add("lime");
        tank.setPrefSize(130.0, 130.0);
        tank.setProgressTransitionDuration(Duration.ZERO);

        Label state = text("Unplugged", "copy");
        RXButton charge = solid("Plug in", "lime");
        charge.setGraphic(icon("bolt-icon"));
        charge.setOnAction(event -> {
            charge.setDisable(true);
            state.setText("Charging");
            Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(5.0 * (1.0 - tank.getProgress())),
                    new KeyValue(tank.progressProperty(), 1.0, Interpolator.LINEAR)));
            timeline.setOnFinished(done -> state.setText("Fully charged"));
            timeline.play();
        });
        RXButton reset = plain("Use it");
        reset.setOnAction(event -> {
            tank.setProgress(0.24);
            state.setText("Unplugged");
            charge.setDisable(false);
        });
        reset.disableProperty().bind(tank.progressProperty().lessThan(1.0));
        VBox side = new VBox(12.0, text("Phone battery", "headline"), state, new HBox(8.0, charge, reset));
        side.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(28.0, tank, side);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ==================== Water intake ====================

    private static Node water() {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator(0.375);
        tank.getStyleClass().add("sky");
        tank.setPrefSize(130.0, 130.0);
        tank.setProgressTransitionDuration(Duration.millis(700.0));
        tank.setTextFactory(value -> Math.round(value * WATER_GOAL_ML) + " ml");

        Label left = text("", "copy");
        left.textProperty().bind(Bindings.createStringBinding(() -> {
            long rest = Math.round((1.0 - tank.getProgress()) * WATER_GOAL_ML);
            return rest <= 0 ? "Goal reached" : rest + " ml to go";
        }, tank.progressProperty()));
        RXButton glass = solid("+ 1 glass", "sky");
        glass.setGraphic(icon("drop-icon"));
        glass.setOnAction(event -> tank.setProgress(Math.min(1.0, tank.getProgress() + GLASS_ML / WATER_GOAL_ML)));
        RXButton reset = plain("New day");
        reset.setOnAction(event -> tank.setProgress(0.0));
        VBox side = new VBox(12.0, text("Today", "headline"), left, new HBox(8.0, glass, reset));
        side.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(28.0, tank, side);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ==================== Colors ====================

    private static Node tank(double progress, String color, Color backWave) {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator(progress);
        tank.getStyleClass().add(color);
        tank.setPrefSize(110.0, 110.0);
        if (backWave != null) {
            tank.setBackWaveFill(backWave);
        }
        return tank;
    }

    // ==================== Indeterminate ====================

    private static Node syncing() {
        RXWaveProgressIndicator tank = new RXWaveProgressIndicator();
        tank.getStyleClass().add("indigo");
        tank.setPrefSize(120.0, 120.0);
        VBox side = new VBox(6.0, text("Syncing photos", "headline"),
                text("Counting files on the server", "muted"));
        side.setAlignment(Pos.CENTER_LEFT);
        HBox row = new HBox(28.0, tank, side);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    // ==================== Calm and choppy ====================

    private static Node surfaces() {
        RXWaveProgressIndicator calm = new RXWaveProgressIndicator(0.72);
        calm.getStyleClass().add("teal");
        calm.setPrefSize(110.0, 110.0);
        calm.setWaveAmplitude(2.0);
        calm.setWaveLength(220.0);
        calm.setWaveCycleDuration(Duration.seconds(4.0));

        RXWaveProgressIndicator choppy = new RXWaveProgressIndicator(0.72);
        choppy.getStyleClass().add("orange");
        choppy.setPrefSize(110.0, 110.0);
        choppy.setWaveAmplitude(10.0);
        choppy.setWaveLength(50.0);
        choppy.setWaveCycleDuration(Duration.seconds(1.2));

        HBox row = new HBox(40.0, captioned(calm, "Amplitude 2, length 220"),
                captioned(choppy, "Amplitude 10, length 50"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node captioned(Node node, String caption) {
        VBox box = new VBox(10.0, node, text(caption, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }
}

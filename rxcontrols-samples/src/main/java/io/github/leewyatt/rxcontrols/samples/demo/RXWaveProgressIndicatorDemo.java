package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWaveProgressIndicatorShowcase;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Out-of-the-box demo for {@link RXWaveProgressIndicator}. Shows two
 * indicators side by side: an indeterminate one on the left (breathing
 * forever) and a determinate one on the right that ping-pongs progress
 * {@code 0 → 1 → 0} in a continuous loop.
 *
 * <p>For a full property explorer (sliders, color pickers, every styleable
 * knob) see {@link RXWaveProgressIndicatorShowcase}.
 */
public class RXWaveProgressIndicatorDemo extends Application {

    private static final Duration RAMP_DURATION = Duration.seconds(10);

    @Override
    public void start(Stage primaryStage) {
        RXWaveProgressIndicator left = new RXWaveProgressIndicator(-1);
        left.setPrefSize(160, 160);
        left.setTextFactory(progress -> "Loading…");

        RXWaveProgressIndicator middle = new RXWaveProgressIndicator(0.3);
        middle.setPrefSize(160, 160);
        // show back wave
        middle.setBackWaveFill(Color.web("#1E90FF", 0.4));
        // hide the text
        middle.setTextFactory(progress -> null);

        RXWaveProgressIndicator right = new RXWaveProgressIndicator(0.0);
        right.setPrefSize(160, 160);

        HBox root = new HBox(40, left, middle, right);
        root.setPadding(new Insets(40));
        root.setAlignment(Pos.CENTER);

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXWaveProgressIndicator Demo");
        primaryStage.show();

        startPingPong(right);
    }

    private static void startPingPong(RXWaveProgressIndicator indicator) {
        Timeline pingPong = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(indicator.progressProperty(), 0.0)),
                new KeyFrame(RAMP_DURATION,
                        new KeyValue(indicator.progressProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        pingPong.setAutoReverse(true);
        pingPong.setCycleCount(Animation.INDEFINITE);
        pingPong.play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

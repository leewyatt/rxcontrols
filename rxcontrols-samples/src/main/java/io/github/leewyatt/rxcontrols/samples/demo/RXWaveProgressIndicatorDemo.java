package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWaveProgressIndicatorShowcase;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Out-of-the-box demo for {@link RXWaveProgressIndicator} that cycles through
 * every wave state so each is visible without any user interaction:
 * indeterminate breathing → determinate {@code 0 → 1} ramp → brief hold at
 * full → back to indeterminate, looping forever.
 *
 * <p>For a full property explorer (sliders, color pickers, every styleable
 * knob) see {@link RXWaveProgressIndicatorShowcase}.
 */
public class RXWaveProgressIndicatorDemo extends Application {

    private static final Duration INDETERMINATE_DWELL = Duration.seconds(3);
    private static final Duration RAMP_DURATION = Duration.seconds(6);
    private static final Duration FULL_DWELL = Duration.seconds(2);

    @Override
    public void start(Stage primaryStage) {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(-1);
        indicator.setPrefSize(160, 160);

        StackPane root = new StackPane(indicator);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXWaveProgressIndicator Demo");
        primaryStage.show();

        startShowcaseCycle(indicator);
    }

    /**
     * Chains the cycle phases manually via {@code setOnFinished} callbacks
     * instead of a {@code SequentialTransition}: the latter propagates a
     * cycle-restart {@code jumpTo(0)} to every child, and the ramp Timeline's
     * captured start value (progress = 0) then snaps the indicator out of
     * indeterminate at the beginning of the second loop.
     */
    private static void startShowcaseCycle(RXWaveProgressIndicator indicator) {
        PauseTransition indeterminateDwell = new PauseTransition(INDETERMINATE_DWELL);
        Timeline ramp = new Timeline(
                new KeyFrame(RAMP_DURATION,
                        new KeyValue(indicator.progressProperty(), 1.0, Interpolator.EASE_BOTH))
        );
        PauseTransition fullDwell = new PauseTransition(FULL_DWELL);

        indeterminateDwell.setOnFinished(e -> {
            indicator.setProgress(0.0);
            ramp.playFromStart();
        });
        ramp.setOnFinished(e -> fullDwell.playFromStart());
        fullDwell.setOnFinished(e -> {
            indicator.setProgress(-1.0);
            indeterminateDwell.playFromStart();
        });

        indeterminateDwell.playFromStart();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

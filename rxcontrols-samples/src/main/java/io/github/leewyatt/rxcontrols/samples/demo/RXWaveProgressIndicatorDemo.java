package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWaveProgressIndicatorShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXWaveProgressIndicator}.
 *
 * <p>Demonstrates the few lines required to drop the control into a scene
 * with its default appearance. For a full property explorer (sliders, color
 * pickers, every styleable knob) see {@link RXWaveProgressIndicatorShowcase}.
 */
public class RXWaveProgressIndicatorDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXWaveProgressIndicator indicator = new RXWaveProgressIndicator(-1);
        indicator.setPrefSize(160, 160);

        StackPane root = new StackPane(indicator);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXWaveProgressIndicator Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

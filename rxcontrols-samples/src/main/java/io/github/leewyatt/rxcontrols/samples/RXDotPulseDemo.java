package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXDotPulse;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXDotPulse}.
 *
 * <p>Demonstrates the single line required to drop the indicator into a scene
 * with its default appearance. For a full property explorer (every styleable
 * knob, plus inline-with-text and button-graphic compositions) see
 * {@link RXDotPulseShowcase}.
 */
public class RXDotPulseDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXDotPulse indicator = new RXDotPulse();

        StackPane root = new StackPane(indicator);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXDotPulse Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXSegmentedProgressBar}.
 *
 * <p>Demonstrates the single line required to drop the bar into a scene with
 * its default appearance and an initial half-filled state. For a full property
 * explorer (every styleable knob plus inline / Stories compositions) see
 * {@link RXSegmentedProgressBarShowcase}.
 */
public class RXSegmentedProgressBarDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXSegmentedProgressBar bar = new RXSegmentedProgressBar(0.5);
        bar.setPrefWidth(280.0);

        StackPane root = new StackPane(bar);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXSegmentedProgressBar Demo");
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

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBarSpinnerShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXBarSpinner}.
 *
 * <p>Demonstrates the single line required to drop the indicator into a scene
 * with its default appearance. For a full property explorer (every styleable
 * knob, plus inline-with-text and button-graphic compositions) see
 * {@link RXBarSpinnerShowcase}.
 */
public class RXBarSpinnerDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXBarSpinner indicator = new RXBarSpinner();

        StackPane root = new StackPane(indicator);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXBarSpinner Demo");
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

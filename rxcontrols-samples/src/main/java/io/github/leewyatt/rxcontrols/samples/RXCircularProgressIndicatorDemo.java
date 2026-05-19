package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXCircularProgressIndicator}.
 *
 * <p>Demonstrates the few lines required to drop the control into a scene
 * with its default appearance. For a full property explorer (sliders, color
 * pickers, every styleable knob) see {@link RXCircularProgressIndicatorShowcase}.
 */
public class RXCircularProgressIndicatorDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXCircularProgressIndicator indicator = new RXCircularProgressIndicator(0.6);
        indicator.setPrefSize(120, 120);

        StackPane root = new StackPane(indicator);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXCircularProgressIndicator Demo");
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

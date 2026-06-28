package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSliderShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXSlider}.
 *
 * <p>Shows the default Material appearance with a value bubble while dragging and
 * a tick scale. For the property explorer see {@link RXSliderShowcase}.</p>
 */
public class RXSliderDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXSlider slider = new RXSlider(0.0, 100.0, 60.0);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(25.0);
        slider.setMinorTickCount(4);
        slider.setPrefWidth(360.0);

        StackPane root = new StackPane(slider);
        root.setPadding(new Insets(48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXSlider Demo");
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

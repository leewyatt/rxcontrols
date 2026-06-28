package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRangeSliderShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXRangeSlider}.
 *
 * <p>Shows the two-thumb range selection with the fill drawn between the thumbs
 * and a draggable band. For the property explorer see
 * {@link RXRangeSliderShowcase}.</p>
 */
public class RXRangeSliderDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXRangeSlider slider = new RXRangeSlider(0.0, 100.0, 30.0, 70.0);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(25.0);
        slider.setMinorTickCount(4);
        slider.setPrefWidth(360.0);

        StackPane root = new StackPane(slider);
        root.setPadding(new Insets(48.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXRangeSlider Demo");
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

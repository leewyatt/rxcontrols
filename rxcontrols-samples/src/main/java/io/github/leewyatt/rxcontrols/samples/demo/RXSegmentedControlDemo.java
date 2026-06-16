package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXSegmentedControl} demo: one sliding selector with a live
 * value readout. Click a segment and watch the white pill slide and stretch to
 * it. The full property panel lives in
 * {@link io.github.leewyatt.rxcontrols.samples.showcase.RXSegmentedControlShowcase}.
 */
public class RXSegmentedControlDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXSegmentedControl<String> segmented = new RXSegmentedControl<>(
                RXSegmentedItem.of("daily", "Daily"),
                RXSegmentedItem.of("weekly", "Weekly"),
                RXSegmentedItem.of("monthly", "Monthly"),
                RXSegmentedItem.of("quarterly", "Quarterly"),
                RXSegmentedItem.of("yearly", "Yearly"));

        Label value = new Label();
        value.textProperty().bind(segmented.valueProperty().asString("value = %s"));

        VBox root = new VBox(24.0, segmented, value);
        root.setAlignment(Pos.CENTER);
        primaryStage.setScene(new Scene(root, 560.0, 240.0));
        primaryStage.setTitle("RXSegmentedControl Demo");
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

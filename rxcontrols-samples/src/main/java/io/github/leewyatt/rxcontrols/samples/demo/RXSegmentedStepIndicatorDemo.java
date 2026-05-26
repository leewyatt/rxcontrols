package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSegmentedStepIndicatorShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXSegmentedStepIndicator}.
 *
 * <p>Demonstrates segment click handling without automatic state changes from
 * the control itself. For a full property explorer see
 * {@link RXSegmentedStepIndicatorShowcase}.</p>
 */
public class RXSegmentedStepIndicatorDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        RXSegmentedStepIndicator indicator = new RXSegmentedStepIndicator(5);
        indicator.setPrefWidth(300.0);
        indicator.setSelectedIndex(1);
        indicator.setSegmentProgress(0.5);

        Label log = new Label("Hover or click a segment");
        indicator.setOnSegmentEntered(event ->
                log.setText("Entered segment " + event.getSegmentIndex()));
        indicator.setOnSegmentClicked(event -> {
            indicator.setSelectedIndex(event.getSegmentIndex());
            indicator.setSegmentProgress(1.0);
            log.setText("Clicked segment " + event.getSegmentIndex());
        });

        VBox root = new VBox(16.0, indicator, log);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 420.0, 180.0));
        primaryStage.setTitle("RXSegmentedStepIndicator Demo");
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

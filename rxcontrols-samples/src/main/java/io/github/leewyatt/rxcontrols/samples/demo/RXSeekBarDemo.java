package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSeekBarShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXSeekBar}.
 *
 * <p>Shows the default dual-layer appearance with the primary progress ahead
 * of the secondary progress. For the property explorer see
 * {@link RXSeekBarShowcase}.</p>
 */
public class RXSeekBarDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXSeekBar seekBar = new RXSeekBar(0.68);
        seekBar.setSecondaryProgress(0.42);
        seekBar.setPrefWidth(320.0);

        StackPane root = new StackPane(seekBar);
        root.setPadding(new Insets(42.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXSeekBar Demo");
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

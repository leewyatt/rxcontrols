package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDotPulseShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
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
        RXDotPulse indicator1 = new RXDotPulse();

        RXDotPulse indicator2 = new RXDotPulse();
        indicator2.setPulseStyle(RXDotPulse.PulseStyle.PULSE);

        RXDotPulse indicator3 = new RXDotPulse();
        indicator3.setPulseStyle(RXDotPulse.PulseStyle.FADE);

        VBox root = new VBox(25, indicator1, new Separator(), indicator2, new Separator(), indicator3);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));

        primaryStage.setScene(new Scene(root, 260, 225));
        primaryStage.setTitle("RXDotPulse Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

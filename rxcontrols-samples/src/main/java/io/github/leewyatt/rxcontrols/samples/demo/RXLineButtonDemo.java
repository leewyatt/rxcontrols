package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Minimal {@link RXLineButton} demo.
 */
public class RXLineButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        RXLineButton button = new RXLineButton("Line Button / Text");
        button.setOnAction(event -> System.out.println("LineButton onAction"));
        // Line effect; CSS can also set -rx-line-animation: top-bottom-converge.
        button.setLineAnimation(LineAnimation.TOP_BOTTOM_CONVERGE);
        // Parameterized effects use constructors, for example:
        // setLineAnimation(new LineAnimSlide(LineEdges.BOTTOM, 20)).
        // The default trigger is HOVER; use setAnimationTrigger(RXAnimationTrigger.PRESSED) for press.
        // The default duration is 200ms; use setAnimationDuration(Duration.millis(300)) to override it.

        root.setCenter(button);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXLineButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

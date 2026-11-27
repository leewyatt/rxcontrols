package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXColorView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXColorViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXColorView}: an inline panel that recolors a shape
 * while the user drags. For the property explorer see
 * {@link RXColorViewShowcase}.
 */
public class RXColorViewDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXColorView view = new RXColorView(Color.web("#F57C00"));
        Circle shape = new Circle(60.0);
        // The value changes live, so the shape follows every drag step.
        shape.fillProperty().bind(view.valueProperty());

        HBox root = new HBox(36.0, view, shape);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXColorView Demo");
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

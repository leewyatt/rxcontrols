package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTransitionButton;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal usage of {@link RXTransitionButton}: the button's own text and
 * graphic form the normal face, and hovering slides in an alternate face
 * detailing what the action will produce. The graphic is a shape-backed
 * {@code Region}, so its size and color are set from CSS alongside the
 * button's own.
 */
public class RXTransitionButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Region icon = new Region();
        icon.getStyleClass().add("download-icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        RXTransitionButton button = new RXTransitionButton("Download", icon);
        button.getStyleClass().add("download-button");

        Label detail = new Label("12.4 MB · ZIP");
        detail.getStyleClass().add("download-detail");
        button.setAlternateContent(detail);

        button.setOnAction(event -> System.out.println("Download started"));

        StackPane root = new StackPane(button);
        Scene scene = new Scene(root, 500.0, 320.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-transition-button-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXTransitionButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTransitionButton;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * Minimal usage of {@link RXTransitionButton}: the button's own text and
 * graphic form the normal face, the alternate content slides in on hover.
 */
public class RXTransitionButtonDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        ImageView icon = new ImageView(new Image(
                getClass().getResource("/image/email.png").toExternalForm(), true));
        icon.setFitWidth(25.0);
        icon.setPreserveRatio(true);

        RXTransitionButton button = new RXTransitionButton("Email", icon);

        Label address = new Label("hello@example.com");
        address.setAlignment(Pos.CENTER);
        button.setAlternateContent(address);

        button.setOnAction(event -> System.out.println("Email button fired"));

        BorderPane root = new BorderPane(button);
        primaryStage.setScene(new Scene(root, 500.0, 320.0));
        primaryStage.setTitle("RXTransitionButton Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

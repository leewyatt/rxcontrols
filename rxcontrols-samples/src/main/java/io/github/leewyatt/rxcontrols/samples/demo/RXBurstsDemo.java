package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXBursts;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal {@link RXBursts} demo: ordinary buttons that burst on every click,
 * each with its own preset, and an area that bursts wherever it is clicked.
 * No control is subclassed or restyled; one line wires each burst.
 */
public class RXBurstsDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Button save = burstingButton("Save", BurstAnimation.CLASSIC);
        Button send = burstingButton("Send", BurstAnimation.FIREWORKS);
        Button celebrate = burstingButton("Celebrate", BurstAnimation.CONFETTI);
        HBox buttons = new HBox(12.0, save, send, celebrate);
        buttons.setAlignment(Pos.CENTER);

        Label hint = new Label("Click anywhere in here");
        StackPane area = new StackPane(hint);
        area.setStyle("-fx-background-color: rgba(128, 128, 128, 0.08); -fx-background-radius: 12;"
                + " -fx-border-color: rgba(128, 128, 128, 0.35); -fx-border-radius: 12;");
        area.setOnMouseClicked(event -> RXBursts.playAt(area, event.getX(), event.getY()));
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox root = new VBox(20.0, buttons, area);
        root.setPadding(new Insets(28.0));
        primaryStage.setScene(new Scene(root, 520.0, 380.0));
        primaryStage.setTitle("RXBursts Demo");
        primaryStage.show();
    }

    private static Button burstingButton(String text, BurstAnimation animation) {
        Button button = new Button(text);
        button.addEventHandler(ActionEvent.ACTION, event -> RXBursts.play(button, animation));
        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

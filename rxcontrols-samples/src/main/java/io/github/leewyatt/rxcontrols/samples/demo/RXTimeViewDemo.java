package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTimeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalTime;

/**
 * Minimal sample of {@link RXTimeView} used on its own, without a field or a popup: a
 * reminder time in steps of five minutes.
 *
 * <p>Every option of the control is in {@code RXTimeViewShowcase}.
 */
public class RXTimeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXTimeView reminder = new RXTimeView(LocalTime.of(8, 0));
        reminder.setMinuteStep(5);

        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> reminder.getValue() == null ? "No reminder" : "Remind me at " + reminder.getValue(),
                reminder.valueProperty()));

        VBox root = new VBox(12.0, reminder, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 280, 330));
        stage.setTitle("RXTimeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

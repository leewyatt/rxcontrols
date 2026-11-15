package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateTimeRangeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Minimal sample of {@link RXDateTimeRangeView} used on its own, without a field or a
 * popup: the start and the end of an event in the future, with how long it lasts.
 *
 * <p>Every option of the control is in {@code RXDateTimeRangeViewShowcase}.
 */
public class RXDateTimeRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateTimeRangeView event = new RXDateTimeRangeView();
        event.setMinDateTime(LocalDateTime.now());
        event.setMinuteStep(15);

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(() -> {
            if (event.getValue() == null) {
                return "Pick the start and the end";
            }
            Duration duration = event.getValue().duration();
            return event.getValue() + ", " + duration.toHours() + " h " + duration.toMinutesPart() + " min";
        }, event.valueProperty()));

        VBox root = new VBox(12.0, event, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 720, 360));
        stage.setTitle("RXDateTimeRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

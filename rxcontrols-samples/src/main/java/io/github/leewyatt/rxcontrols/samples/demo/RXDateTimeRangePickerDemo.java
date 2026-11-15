package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
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
 * Minimal sample of {@link RXDateTimeRangePicker}: a meeting-room booking in quarter
 * hours that starts in the future and shows how long it lasts.
 *
 * <p>Every option of the control is in {@code RXDateTimeRangePickerShowcase}.
 */
public class RXDateTimeRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateTimeRangePicker booking = new RXDateTimeRangePicker();
        booking.setStartPromptText("From");
        booking.setEndPromptText("Until");
        booking.setMinDateTime(LocalDateTime.now());
        booking.setMinuteStep(15);

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(() -> {
            if (booking.getValue() == null) {
                return "No booking yet";
            }
            Duration duration = booking.getValue().duration();
            return "Booked for " + duration.toHours() + " h " + duration.toMinutesPart() + " min";
        }, booking.valueProperty()));

        VBox root = new VBox(12.0, new Label("Room booking"), booking, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 520, 200));
        stage.setTitle("RXDateTimeRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

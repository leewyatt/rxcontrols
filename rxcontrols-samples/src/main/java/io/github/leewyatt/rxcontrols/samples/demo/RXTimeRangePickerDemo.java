package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTimeRange;
import io.github.leewyatt.rxcontrols.RXTimeRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Duration;
import java.time.LocalTime;

/**
 * Minimal sample of {@link RXTimeRangePicker}: the working hours of a day in quarter
 * hours, with how long they last.
 *
 * <p>Every option of the control is in {@code RXTimeRangePickerShowcase}.
 */
public class RXTimeRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXTimeRangePicker hours = new RXTimeRangePicker(RXTimeRange.of(LocalTime.of(9, 0), LocalTime.of(17, 30)));
        hours.setStartPromptText("From");
        hours.setEndPromptText("Until");
        hours.setMinuteStep(15);

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(() -> {
            if (hours.getValue() == null) {
                return "No working hours yet";
            }
            Duration duration = hours.getValue().duration();
            return "Working for " + duration.toHours() + " h " + duration.toMinutesPart() + " min";
        }, hours.valueProperty()));

        VBox root = new VBox(12.0, new Label("Working hours"), hours, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 400, 200));
        stage.setTitle("RXTimeRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

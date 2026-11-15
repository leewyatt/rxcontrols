package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Minimal sample of {@link RXDateTimePicker}: a meeting start that lies in the future,
 * on a weekday, in half hours within office hours.
 *
 * <p>Every option of the control is in {@code RXDateTimePickerShowcase}.
 */
public class RXDateTimePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateTimePicker meeting = new RXDateTimePicker();
        meeting.setPromptText("Meeting start");
        meeting.setMinDateTime(LocalDateTime.now());
        meeting.setDateFilter(date -> date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue());
        meeting.setTimeFilter(time -> time.getHour() >= 9 && time.getHour() < 18);
        meeting.setMinuteStep(30);

        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT);
        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> meeting.getValue() == null ? "No meeting yet" : "Meeting on " + format.format(meeting.getValue()),
                meeting.valueProperty()));

        VBox root = new VBox(12.0, new Label("Meeting"), meeting, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXDateTimePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

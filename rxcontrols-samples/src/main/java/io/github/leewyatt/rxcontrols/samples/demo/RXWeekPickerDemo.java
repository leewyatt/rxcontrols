package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWeekPicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXWeekPicker}: the week of a timesheet, up to the current week,
 * with the days it covers. The value is the first day of the week.
 *
 * <p>Every option of the control is in {@code RXWeekPickerShowcase}.
 */
public class RXWeekPickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXWeekPicker timesheet = new RXWeekPicker();
        timesheet.setPromptText("Week");
        timesheet.setMaxWeek(LocalDate.now());
        timesheet.setClearable(true);

        Label days = new Label();
        days.textProperty().bind(Bindings.createStringBinding(
                () -> timesheet.getValue() == null ? "No week yet"
                        : "From " + timesheet.getValue() + " to " + timesheet.getValue().plusDays(6),
                timesheet.valueProperty()));

        VBox root = new VBox(12.0, new Label("Timesheet week"), timesheet, days);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXWeekPicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

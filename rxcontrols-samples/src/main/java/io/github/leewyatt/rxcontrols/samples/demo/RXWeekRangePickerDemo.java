package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWeekRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXWeekRangePicker}: the weeks of a sprint, this week at the
 * earliest, with the days they cover. The value runs from the first day of the first
 * week to the last day of the last week.
 *
 * <p>Every option of the control is in {@code RXWeekRangePickerShowcase}.
 */
public class RXWeekRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXWeekRangePicker sprint = new RXWeekRangePicker();
        sprint.setMinWeek(LocalDate.now());

        Label days = new Label();
        days.textProperty().bind(Bindings.createStringBinding(
                () -> sprint.getValue() == null ? "No weeks yet"
                        : sprint.getValue().lengthInDays() / 7 + " weeks, from "
                        + sprint.getValue().start() + " to " + sprint.getValue().end(),
                sprint.valueProperty()));

        VBox root = new VBox(12.0, new Label("Sprint weeks"), sprint, days);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXWeekRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

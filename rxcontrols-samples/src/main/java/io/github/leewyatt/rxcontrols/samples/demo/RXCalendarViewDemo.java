package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Minimal sample of {@link RXCalendarView} used on its own, without a field or a popup:
 * an appointment calendar that offers the weekdays of the next two months.
 *
 * <p>Every option of the control is in {@code RXCalendarViewShowcase}.
 */
public class RXCalendarViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXCalendarView calendar = new RXCalendarView();
        calendar.setMinDate(LocalDate.now());
        calendar.setMaxDate(LocalDate.now().plusMonths(2));
        calendar.setDateFilter(date -> date.getDayOfWeek().getValue() <= DayOfWeek.FRIDAY.getValue());

        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL);
        Label appointment = new Label();
        appointment.textProperty().bind(Bindings.createStringBinding(
                () -> calendar.getValue() == null ? "Pick a day" : format.format(calendar.getValue()),
                calendar.valueProperty()));

        VBox root = new VBox(12.0, calendar, appointment);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 320, 360));
        stage.setTitle("RXCalendarView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

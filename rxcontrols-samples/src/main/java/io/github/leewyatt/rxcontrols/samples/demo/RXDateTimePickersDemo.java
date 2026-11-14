package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDatePicker;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXTimePicker;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Minimal booking form built from the date / time pickers: a stay as an
 * {@link RXDateRangePicker} that starts today at the earliest, an arrival time as an
 * {@link RXTimePicker} limited to quarter hours within the reception's opening
 * hours, and a birth date as a clearable {@link RXDatePicker} in the past.
 */
public class RXDateTimePickersDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateRangePicker stay = new RXDateRangePicker();
        stay.setMinDate(LocalDate.now());
        stay.setStartPromptText("Check-in");
        stay.setEndPromptText("Check-out");

        RXTimePicker arrival = new RXTimePicker(LocalTime.of(15, 0));
        arrival.setMinuteStep(15);
        arrival.setMinTime(LocalTime.of(14, 0));
        arrival.setMaxTime(LocalTime.of(22, 0));

        RXDatePicker birthDate = new RXDatePicker();
        birthDate.setPromptText("Birth date");
        birthDate.setMaxDate(LocalDate.now().minusDays(1));
        birthDate.setClearable(true);

        Label summary = new Label();
        Button book = new Button("Book");
        book.setDefaultButton(true);
        book.setOnAction(event -> summary.setText("Stay " + stay.getValue() + ", arriving at "
                + arrival.getValue() + ", born " + birthDate.getValue()));

        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(12.0);
        form.setPadding(new Insets(24.0));
        form.addRow(0, new Label("Stay"), stay);
        form.addRow(1, new Label("Arrival"), arrival);
        form.addRow(2, new Label("Birth date"), birthDate);
        form.add(book, 1, 3);
        form.add(summary, 0, 4, 2, 1);

        stage.setScene(new Scene(form, 520, 300));
        stage.setTitle("RX Date / Time Pickers Demo");
        stage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

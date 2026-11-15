package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMonthView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Minimal sample of {@link RXMonthView} used on its own, without a field or a popup: a
 * month of the past two years with the number of days in it.
 *
 * <p>Every option of the control is in {@code RXMonthViewShowcase}.
 */
public class RXMonthViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXMonthView months = new RXMonthView(YearMonth.now());
        months.setMinMonth(YearMonth.now().minusYears(2));
        months.setMaxMonth(YearMonth.now());

        DateTimeFormatter format = DateTimeFormatter.ofPattern("MMMM yyyy");
        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> months.getValue() == null ? "Pick a month"
                        : format.format(months.getValue()) + ", " + months.getValue().lengthOfMonth() + " days",
                months.valueProperty()));

        VBox root = new VBox(12.0, months, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 320, 360));
        stage.setTitle("RXMonthView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

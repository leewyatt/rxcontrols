package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMonthRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.YearMonth;

/**
 * Minimal sample of {@link RXMonthRangePicker}: the months of a sales report, up to the
 * current month, with the days they cover.
 *
 * <p>Every option of the control is in {@code RXMonthRangePickerShowcase}.
 */
public class RXMonthRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXMonthRangePicker report = new RXMonthRangePicker();
        report.setMaxMonth(YearMonth.now());

        Label period = new Label();
        period.textProperty().bind(Bindings.createStringBinding(
                () -> report.getValue() == null ? "No period yet"
                        : report.getValue().lengthInMonths() + " months, from "
                        + report.getValue().toDateRange().start() + " to " + report.getValue().toDateRange().end(),
                report.valueProperty()));

        VBox root = new VBox(12.0, new Label("Report period"), report, period);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXMonthRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

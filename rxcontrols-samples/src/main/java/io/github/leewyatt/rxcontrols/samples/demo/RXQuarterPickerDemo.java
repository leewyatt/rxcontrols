package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXQuarterPicker;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXQuarterPicker}: the quarter of a sales report, up to the
 * current quarter, with the dates it covers.
 *
 * <p>Every option of the control is in {@code RXQuarterPickerShowcase}.
 */
public class RXQuarterPickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearQuarter current = RXYearQuarter.from(LocalDate.now());
        RXQuarterPicker report = new RXQuarterPicker(current);
        report.setMaxQuarter(current);

        Label period = new Label();
        period.textProperty().bind(Bindings.createStringBinding(
                () -> report.getValue() == null ? "No quarter yet"
                        : "From " + report.getValue().firstDay() + " to " + report.getValue().lastDay(),
                report.valueProperty()));

        VBox root = new VBox(12.0, new Label("Report quarter"), report, period);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXQuarterPicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

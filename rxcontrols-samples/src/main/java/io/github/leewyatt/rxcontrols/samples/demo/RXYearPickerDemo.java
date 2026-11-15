package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXYearPicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Year;

/**
 * Minimal sample of {@link RXYearPicker}: the year of an annual report, from 2015 up to
 * the current year.
 *
 * <p>Every option of the control is in {@code RXYearPickerShowcase}.
 */
public class RXYearPickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearPicker report = new RXYearPicker(Year.now());
        report.setMinYear(Year.of(2015));
        report.setMaxYear(Year.now());

        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> report.getValue() == null ? "No year yet"
                        : "Annual report " + report.getValue() + ", " + report.getValue().length() + " days",
                report.valueProperty()));

        VBox root = new VBox(12.0, new Label("Report year"), report, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXYearPicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

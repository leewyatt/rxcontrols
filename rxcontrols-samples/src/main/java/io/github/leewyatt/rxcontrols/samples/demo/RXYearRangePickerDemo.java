package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXYearRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Year;

/**
 * Minimal sample of {@link RXYearRangePicker}: the model years of a used-car search, up
 * to the current year.
 *
 * <p>Every option of the control is in {@code RXYearRangePickerShowcase}.
 */
public class RXYearRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearRangePicker modelYears = new RXYearRangePicker();
        modelYears.setStartPromptText("From");
        modelYears.setEndPromptText("To");
        modelYears.setMaxYear(Year.now());

        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> modelYears.getValue() == null ? "Any model year"
                        : "Built from " + modelYears.getValue().start() + " to " + modelYears.getValue().end(),
                modelYears.valueProperty()));

        VBox root = new VBox(12.0, new Label("Model year"), modelYears, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXYearRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

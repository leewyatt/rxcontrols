package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDatePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.Period;

/**
 * Minimal sample of {@link RXDatePicker}: a clearable birth-date field that accepts no
 * future date and shows the age for the picked date.
 *
 * <p>Every option of the control is in {@code RXDatePickerShowcase}.
 */
public class RXDatePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDatePicker birthDate = new RXDatePicker();
        birthDate.setPromptText("Birth date");
        birthDate.setMaxDate(LocalDate.now());
        birthDate.setClearable(true);

        Label age = new Label();
        age.textProperty().bind(Bindings.createStringBinding(
                () -> birthDate.getValue() == null ? "No birth date yet"
                        : "Age: " + Period.between(birthDate.getValue(), LocalDate.now()).getYears(),
                birthDate.valueProperty()));

        VBox root = new VBox(12.0, new Label("Birth date"), birthDate, age);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXDatePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

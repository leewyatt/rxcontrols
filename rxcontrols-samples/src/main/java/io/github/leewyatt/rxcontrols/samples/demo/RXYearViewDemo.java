package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXYearView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Year;

/**
 * Minimal sample of {@link RXYearView} used on its own, without a field or a popup: a
 * year up to the current one, with whether it is a leap year.
 *
 * <p>Every option of the control is in {@code RXYearViewShowcase}.
 */
public class RXYearViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearView years = new RXYearView(Year.now());
        years.setMaxYear(Year.now());

        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> years.getValue() == null ? "Pick a year"
                        : years.getValue() + (years.getValue().isLeap() ? " is a leap year" : " is not a leap year"),
                years.valueProperty()));

        VBox root = new VBox(12.0, years, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 320, 360));
        stage.setTitle("RXYearView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXTimePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalTime;

/**
 * Minimal sample of {@link RXTimePicker}: a pick-up time in quarter hours within the
 * opening hours of a shop.
 *
 * <p>Every option of the control is in {@code RXTimePickerShowcase}.
 */
public class RXTimePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXTimePicker pickUp = new RXTimePicker(LocalTime.of(12, 0));
        pickUp.setMinuteStep(15);
        pickUp.setMinTime(LocalTime.of(10, 0));
        pickUp.setMaxTime(LocalTime.of(21, 0));

        Label summary = new Label();
        summary.textProperty().bind(Bindings.createStringBinding(
                () -> pickUp.getValue() == null ? "No pick-up time yet" : "Ready at " + pickUp.getValue(),
                pickUp.valueProperty()));

        VBox root = new VBox(12.0, new Label("Pick-up time"), pickUp, summary);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXTimePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

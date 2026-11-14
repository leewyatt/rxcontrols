package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXQuarterRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal sample of {@link RXQuarterRangePicker}: the quarters of a budget plan, with
 * how many of them it spans.
 *
 * <p>Every option of the control is in {@code RXQuarterRangePickerShowcase}.
 */
public class RXQuarterRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXQuarterRangePicker plan = new RXQuarterRangePicker();
        plan.setStartPromptText("First quarter");
        plan.setEndPromptText("Last quarter");

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(
                () -> plan.getValue() == null ? "No plan yet" : plan.getValue().lengthInQuarters() + " quarters",
                plan.valueProperty()));

        VBox root = new VBox(12.0, new Label("Budget plan"), plan, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXQuarterRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

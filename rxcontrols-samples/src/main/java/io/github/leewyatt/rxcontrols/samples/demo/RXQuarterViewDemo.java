package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXQuarterView;
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
 * Minimal sample of {@link RXQuarterView} used on its own, without a field or a popup: a
 * quarter with the dates it covers.
 *
 * <p>Every option of the control is in {@code RXQuarterViewShowcase}.
 */
public class RXQuarterViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXQuarterView quarters = new RXQuarterView(RXYearQuarter.from(LocalDate.now()));

        Label period = new Label();
        period.textProperty().bind(Bindings.createStringBinding(
                () -> quarters.getValue() == null ? "Pick a quarter"
                        : "From " + quarters.getValue().firstDay() + " to " + quarters.getValue().lastDay(),
                quarters.valueProperty()));

        VBox root = new VBox(12.0, quarters, period);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 320, 210));
        stage.setTitle("RXQuarterView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

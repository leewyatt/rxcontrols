package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXYearRange;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.Year;

/**
 * Minimal sample of {@link RXYearRangeView} used on its own, without fields or a popup:
 * a range of years with its length.
 *
 * <p>Every option of the control is in {@code RXYearRangeViewShowcase}.
 */
public class RXYearRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXYearRangeView years = new RXYearRangeView(RXYearRange.of(Year.now().minusYears(3), Year.now()));

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(
                () -> years.getValue() == null ? "Pick the first and the last year"
                        : years.getValue().lengthInYears() + " years",
                years.valueProperty()));

        VBox root = new VBox(12.0, years, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 560, 360));
        stage.setTitle("RXYearRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

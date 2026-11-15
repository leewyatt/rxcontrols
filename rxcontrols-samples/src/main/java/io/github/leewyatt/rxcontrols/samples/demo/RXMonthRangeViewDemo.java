package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMonthRange;
import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.YearMonth;

/**
 * Minimal sample of {@link RXMonthRangeView} used on its own, without fields or a popup:
 * a range of months with its length.
 *
 * <p>Every option of the control is in {@code RXMonthRangeViewShowcase}.
 */
public class RXMonthRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXMonthRangeView months = new RXMonthRangeView(
                RXMonthRange.of(YearMonth.now(), YearMonth.now().plusMonths(5)));

        Label length = new Label();
        length.textProperty().bind(Bindings.createStringBinding(
                () -> months.getValue() == null ? "Pick the first and the last month"
                        : months.getValue().lengthInMonths() + " months",
                months.valueProperty()));

        VBox root = new VBox(12.0, months, length);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 560, 360));
        stage.setTitle("RXMonthRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

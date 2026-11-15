package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMonthPicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.YearMonth;

/**
 * Minimal sample of {@link RXMonthPicker}: the month of an account statement, up to the
 * current month, with the period it covers.
 *
 * <p>Every option of the control is in {@code RXMonthPickerShowcase}.
 */
public class RXMonthPickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXMonthPicker statement = new RXMonthPicker(YearMonth.now().minusMonths(1));
        statement.setMaxMonth(YearMonth.now());

        Label period = new Label();
        period.textProperty().bind(Bindings.createStringBinding(
                () -> statement.getValue() == null ? "No month yet"
                        : "From " + statement.getValue().atDay(1) + " to " + statement.getValue().atEndOfMonth(),
                statement.valueProperty()));

        VBox root = new VBox(12.0, new Label("Statement month"), statement, period);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 200));
        stage.setTitle("RXMonthPicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

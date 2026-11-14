package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateRangeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXDateRangeView} used on its own, without a field or a popup:
 * a leave request that starts today at the earliest and shows the number of days.
 *
 * <p>Every option of the control is in {@code RXDateRangeViewShowcase}.
 */
public class RXDateRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateRangeView leave = new RXDateRangeView();
        leave.setMinDate(LocalDate.now());

        Label days = new Label();
        days.textProperty().bind(Bindings.createStringBinding(
                () -> leave.getValue() == null ? "Pick the first and the last day"
                        : leave.getValue() + ", " + leave.getValue().lengthInDays() + " days",
                leave.valueProperty()));

        VBox root = new VBox(12.0, leave, days);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 600, 360));
        stage.setTitle("RXDateRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

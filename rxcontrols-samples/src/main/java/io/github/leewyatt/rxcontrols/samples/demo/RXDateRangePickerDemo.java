package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Minimal sample of {@link RXDateRangePicker}: a hotel stay that starts today at the
 * earliest and shows the number of nights.
 *
 * <p>Every option of the control is in {@code RXDateRangePickerShowcase}.
 */
public class RXDateRangePickerDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateRangePicker stay = new RXDateRangePicker();
        stay.setStartPromptText("Check-in");
        stay.setEndPromptText("Check-out");
        stay.setMinDate(LocalDate.now());

        Label nights = new Label();
        nights.textProperty().bind(Bindings.createStringBinding(
                () -> stay.getValue() == null ? "No stay yet"
                        : ChronoUnit.DAYS.between(stay.getValue().start(), stay.getValue().end()) + " nights",
                stay.valueProperty()));

        VBox root = new VBox(12.0, new Label("Stay"), stay, nights);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 420, 200));
        stage.setTitle("RXDateRangePicker Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

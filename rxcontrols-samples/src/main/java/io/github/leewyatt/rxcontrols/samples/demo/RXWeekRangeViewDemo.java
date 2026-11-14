package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXWeekRangeView} used on its own, without a field or a popup:
 * the weeks a rental runs, this week at the earliest, with the days they cover.
 *
 * <p>Every option of the control is in {@code RXWeekRangeViewShowcase}.
 */
public class RXWeekRangeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXWeekRangeView rental = new RXWeekRangeView();
        rental.setMinWeek(LocalDate.now());

        Label days = new Label();
        days.textProperty().bind(Bindings.createStringBinding(
                () -> rental.getValue() == null ? "Pick the first and the last week"
                        : rental.getValue().lengthInDays() / 7 + " weeks, from "
                        + rental.getValue().start() + " to " + rental.getValue().end(),
                rental.valueProperty()));

        VBox root = new VBox(12.0, rental, days);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 640, 360));
        stage.setTitle("RXWeekRangeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

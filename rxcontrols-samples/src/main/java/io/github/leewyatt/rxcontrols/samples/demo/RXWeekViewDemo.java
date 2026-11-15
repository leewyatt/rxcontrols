package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXWeekView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Minimal sample of {@link RXWeekView} used on its own, without a field or a popup: a
 * delivery week, this week at the earliest, with the days it covers.
 *
 * <p>Every option of the control is in {@code RXWeekViewShowcase}.
 */
public class RXWeekViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXWeekView delivery = new RXWeekView();
        delivery.setMinWeek(LocalDate.now());

        Label days = new Label();
        days.textProperty().bind(Bindings.createStringBinding(
                () -> delivery.getValue() == null ? "Pick the delivery week"
                        : "From " + delivery.getValue() + " to " + delivery.getValue().plusDays(6),
                delivery.valueProperty()));

        VBox root = new VBox(12.0, delivery, days);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 360, 360));
        stage.setTitle("RXWeekView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

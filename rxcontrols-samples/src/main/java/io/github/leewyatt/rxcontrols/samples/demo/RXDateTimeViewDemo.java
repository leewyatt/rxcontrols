package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDateTimeView;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Minimal sample of {@link RXDateTimeView} used on its own, without a field or a popup:
 * a delivery slot from tomorrow on, in half hours between 8:00 and 20:00.
 *
 * <p>Every option of the control is in {@code RXDateTimeViewShowcase}.
 */
public class RXDateTimeViewDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXDateTimeView delivery = new RXDateTimeView();
        delivery.setMinDateTime(LocalDate.now().plusDays(1).atStartOfDay());
        delivery.setTimeFilter(time -> time.getHour() >= 8 && time.getHour() < 20);
        delivery.setMinuteStep(30);

        DateTimeFormatter format = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT);
        Label slot = new Label();
        slot.textProperty().bind(Bindings.createStringBinding(
                () -> delivery.getValue() == null ? "Pick a delivery slot" : format.format(delivery.getValue()),
                delivery.valueProperty()));

        VBox root = new VBox(12.0, delivery, slot);
        root.setPadding(new Insets(24.0));
        root.setFillWidth(false);
        stage.setScene(new Scene(root, 460, 360));
        stage.setTitle("RXDateTimeView Demo");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

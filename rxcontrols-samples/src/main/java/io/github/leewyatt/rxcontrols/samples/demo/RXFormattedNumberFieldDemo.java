package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXFormattedNumberFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;

/**
 * Minimal "out-of-the-box" demo for {@link RXFormattedNumberField}.
 *
 * <p>With its default {@code NumberFormat} the field renders grouped digits
 * while keeping the exact {@link BigDecimal} value. For a full property
 * explorer (locale / currency / percent formats, min/max bounds, slots) see
 * {@link RXFormattedNumberFieldShowcase}.
 */
public class RXFormattedNumberFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXFormattedNumberField field = new RXFormattedNumberField(new BigDecimal("1234567.89"));
        field.setPrefColumnCount(16);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + (field.getValue() == null
                        ? "null" : field.getValue().toPlainString()),
                field.valueProperty()));

        VBox root = new VBox(16.0, field, valueLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 380.0, 200.0));
        primaryStage.setTitle("RXFormattedNumberField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialDecimalField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialDecimalFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Minimal money-entry demo for {@link RXMaterialDecimalField}.
 *
 * <p>A US currency format drives the display while the committed
 * {@link BigDecimal} keeps its exact value and scale; the Material surface
 * contributes the floating "Amount" label and the helper row. For a full
 * property explorer (formats, min/max bounds, the Material knobs) see
 * {@link RXMaterialDecimalFieldShowcase}.
 */
public class RXMaterialDecimalFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXMaterialDecimalField field = new RXMaterialDecimalField(new BigDecimal("1234.50"));
        field.setLabelText("Amount");
        field.setHelperText("US dollars, exact to the cent");
        field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
        field.setPrefColumnCount(16);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + (field.getValue() == null
                        ? "null" : field.getValue().toPlainString()),
                field.valueProperty()));

        Label tipsLabel = new Label("Tip: Press Enter or move focus away from the field to commit the typed value.");
        tipsLabel.setFocusTraversable(true);
        tipsLabel.setWrapText(true);
        tipsLabel.setMaxWidth(300.0);

        VBox root = new VBox(20.0, field, valueLabel, tipsLabel);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-padding: 32; -fx-background-color: -fx-background;");

        primaryStage.setScene(new Scene(root, 380.0, 260.0));
        primaryStage.setTitle("RXMaterialDecimalField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

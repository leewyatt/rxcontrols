package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDecimalField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDecimalFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Minimal "out-of-the-box" demo for {@link RXDecimalField}.
 *
 * <p>Shows the exact-decimal money scenario: a currency format drives the
 * display while the committed {@link BigDecimal} keeps its exact value and
 * scale. For a full property explorer (formats, min/max bounds, decoration
 * slots) see {@link RXDecimalFieldShowcase}.
 */
public class RXDecimalFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXDecimalField field = new RXDecimalField(new BigDecimal("1234.50"));
        field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
        field.setPromptText("Amount");
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

        VBox root = new VBox(16.0, field, valueLabel, tipsLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 380.0, 240.0));
        primaryStage.setTitle("RXDecimalField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

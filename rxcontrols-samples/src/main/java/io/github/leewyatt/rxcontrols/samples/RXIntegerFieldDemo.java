package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXIntegerField;
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
 * Minimal "out-of-the-box" demo for {@link RXIntegerField}.
 *
 * <p>The field rejects the decimal point as you type, so only whole numbers
 * can be entered. For a full property explorer (min/max bounds, slots and the
 * strict rejection of fractional programmatic values) see
 * {@link RXIntegerFieldShowcase}.
 */
public class RXIntegerFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXIntegerField field = new RXIntegerField(new BigDecimal("42"));
        field.setPromptText("Whole numbers only");
        field.setPrefColumnCount(16);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + (field.getValue() == null
                        ? "null" : field.getValue().toPlainString()),
                field.valueProperty()));

        VBox root = new VBox(16.0, field, valueLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 360.0, 200.0));
        primaryStage.setTitle("RXIntegerField Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

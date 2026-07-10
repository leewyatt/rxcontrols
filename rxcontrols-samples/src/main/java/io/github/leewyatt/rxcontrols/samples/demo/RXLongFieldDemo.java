package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXLongField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLongFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXLongField}.
 *
 * <p>The field rejects the decimal point as you type and exposes the committed
 * {@link Long} through {@link RXLongField#valueProperty()} — IDs, timestamps,
 * and counters beyond the 32-bit range stay exact. For a full property
 * explorer (min/max bounds, slots, the 64-bit overflow rollback) see
 * {@link RXLongFieldShowcase}.
 */
public class RXLongFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXLongField field = new RXLongField(9007199254740993L);
        field.setPromptText("Whole numbers only");
        field.setPrefColumnCount(16);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + field.getValue(),
                field.valueProperty()));

        Label tipsLabel = new Label("Tip: Press Enter or move focus away from the field to commit the typed value.");
        tipsLabel.setFocusTraversable(true);
        tipsLabel.setWrapText(true);
        tipsLabel.setMaxWidth(280.0);

        VBox root = new VBox(16.0, field, valueLabel, tipsLabel);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root, 360.0, 240.0));
        primaryStage.setTitle("RXLongField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialIntegerField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialIntegerFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal quantity-entry demo for {@link RXMaterialIntegerField}.
 *
 * <p>The field rejects the decimal point as you type and clamps the committed
 * {@link Integer} into the inclusive 0..999 range; the Material surface
 * contributes the floating "Quantity" label and the helper row. For a full
 * property explorer (bounds, overflow rollback, the Material knobs) see
 * {@link RXMaterialIntegerFieldShowcase}.
 */
public class RXMaterialIntegerFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXMaterialIntegerField field = new RXMaterialIntegerField(25);
        field.setLabelText("Quantity");
        field.setHelperText("Between 0 and 999");
        field.setMin(0);
        field.setMax(999);
        field.setPrefColumnCount(16);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + field.getValue(),
                field.valueProperty()));

        Label tipsLabel = new Label("Tip: Press Enter or move focus away from the field to commit the typed value.");
        tipsLabel.setFocusTraversable(true);
        tipsLabel.setWrapText(true);
        tipsLabel.setMaxWidth(300.0);

        VBox root = new VBox(20.0, field, valueLabel, tipsLabel);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setStyle("-fx-padding: 32; -fx-background-color: -fx-background;");

        primaryStage.setScene(new Scene(root, 380.0, 260.0));
        primaryStage.setTitle("RXMaterialIntegerField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

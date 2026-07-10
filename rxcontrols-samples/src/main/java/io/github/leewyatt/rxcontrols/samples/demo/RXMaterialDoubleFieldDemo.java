package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialDoubleField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialDoubleFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal ratio-entry demo for {@link RXMaterialDoubleField}.
 *
 * <p>Shows a scale-factor scenario: the field accepts plain decimal text and
 * exposes the committed {@link Double}; the Material surface contributes the
 * floating "Scale factor" label and the helper row. Double is binary floating
 * point — for money and other exact decimal quantities use
 * {@code RXMaterialDecimalField}. For a full property explorer (bounds, the
 * finiteness policy, the Material knobs) see
 * {@link RXMaterialDoubleFieldShowcase}.
 */
public class RXMaterialDoubleFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXMaterialDoubleField field = new RXMaterialDoubleField(0.75);
        field.setLabelText("Scale factor");
        field.setHelperText("1.0 renders at the original size");
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
        primaryStage.setTitle("RXMaterialDoubleField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

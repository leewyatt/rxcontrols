package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXMaterialLongField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialLongFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal ID-entry demo for {@link RXMaterialLongField}.
 *
 * <p>The initial value 9007199254740993 (2^53 + 1) is beyond the double-safe
 * integer range — a double-based field would silently round it to ...992,
 * while the committed {@link Long} stays exact. The Material surface
 * contributes the floating "Snowflake ID" label and the helper row. For a
 * full property explorer (bounds, 64-bit policies, the Material knobs) see
 * {@link RXMaterialLongFieldShowcase}.
 */
public class RXMaterialLongFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXMaterialLongField field = new RXMaterialLongField(9007199254740993L);
        field.setLabelText("Snowflake ID");
        field.setHelperText("64-bit IDs stay exact");
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
        primaryStage.setTitle("RXMaterialLongField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

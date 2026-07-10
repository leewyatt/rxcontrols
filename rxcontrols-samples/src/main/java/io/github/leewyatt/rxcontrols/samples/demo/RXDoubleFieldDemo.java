package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXDoubleField;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDoubleFieldShowcase;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXDoubleField}.
 *
 * <p>Shows the few lines needed to drop the control into a scene: the field
 * accepts plain decimal text and exposes the parsed {@link Double} through
 * {@link RXDoubleField#valueProperty()}. For a full property explorer
 * (min/max bounds, finiteness policy, decoration slots) see
 * {@link RXDoubleFieldShowcase}.
 */
public class RXDoubleFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXDoubleField field = new RXDoubleField(3.14);
        field.setPromptText("Enter a number");
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
        primaryStage.setTitle("RXDoubleField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.RXColorPresetGroup;
import io.github.leewyatt.rxcontrols.samples.showcase.RXColorPickerShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Minimal demo for {@link RXColorPicker}: a brand color setting with the brand
 * palette as presets, saved once per completed edit through {@code onAction}.
 * For the property explorer see {@link RXColorPickerShowcase}.
 */
public class RXColorPickerDemo extends Application {

    /** {@inheritDoc} */
    @Override
    public void start(Stage primaryStage) {
        RXColorPicker picker = new RXColorPicker(Color.web("#1769AA"));
        picker.setShowText(true);
        picker.setAccessibleText("Brand color");
        picker.getPresets().add(new RXColorPresetGroup("Brand",
                Color.web("#1769AA"), Color.web("#F57C00"), Color.web("#2E7D32"), Color.web("#C62828")));

        // onAction fires once per completed edit, not on every drag step: the place to save.
        Label saved = new Label("Nothing saved yet");
        picker.setOnAction(event -> saved.setText("Saved " + picker.getValue()));

        HBox row = new HBox(12.0, new Label("Brand color"), picker);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(12.0, row, saved);
        StackPane root = new StackPane(card);
        root.setPadding(new Insets(36.0));

        primaryStage.setScene(new Scene(root, 420.0, 200.0));
        primaryStage.setTitle("RXColorPicker Demo");
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

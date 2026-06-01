package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Compact demo for {@link RXBox} that exposes the main layout properties
 * and per-child grow / margin constraints.
 */
public class RXBoxDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Button leadingButton = themedButton("Leading", "demo-button-blue");
        Button growingButton = themedButton("Grow", "demo-button-green");
        RXBox.setGrow(growingButton, Priority.ALWAYS);
        growingButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Label staticLabel = new Label("Static cell");
        staticLabel.getStyleClass().add("demo-label");
        Button trailingButton = themedButton("Trailing", "demo-button-pink");
        RXBox.setMargin(trailingButton, new Insets(0.0, 0.0, 0.0, 12.0));

        RXBox box = new RXBox(Orientation.HORIZONTAL, 8.0,
                leadingButton, growingButton, staticLabel, trailingButton);
        box.getStyleClass().add("demo-box");
        box.setPadding(new Insets(16.0));

        ComboBox<Orientation> orientationBox =
                new ComboBox<>(FXCollections.observableArrayList(Orientation.values()));
        orientationBox.setValue(Orientation.HORIZONTAL);
        box.orientationProperty().bind(orientationBox.valueProperty());

        Slider spacingSlider = new Slider(0.0, 48.0, 8.0);
        box.spacingProperty().bind(spacingSlider.valueProperty());

        ComboBox<Pos> alignmentBox =
                new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignmentBox.setValue(Pos.TOP_LEFT);
        box.alignmentProperty().bind(alignmentBox.valueProperty());

        CheckBox fillCrossAxisBox = new CheckBox("Fill cross axis");
        fillCrossAxisBox.setSelected(true);
        box.fillCrossAxisProperty().bind(fillCrossAxisBox.selectedProperty());

        CheckBox growMiddleBox = new CheckBox("Middle button grows");
        growMiddleBox.setSelected(true);
        growMiddleBox.selectedProperty().addListener((obs, oldV, newV) ->
                RXBox.setGrow(growingButton, newV ? Priority.ALWAYS : null));

        Slider widthSlider = new Slider(240.0, 760.0, 520.0);
        Slider heightSlider = new Slider(120.0, 520.0, 260.0);

        StackPane frame = new StackPane(box);
        frame.getStyleClass().add("demo-frame");
        frame.setAlignment(Pos.TOP_LEFT);
        frame.prefWidthProperty().bind(widthSlider.valueProperty());
        frame.prefHeightProperty().bind(heightSlider.valueProperty());
        frame.setMinWidth(Region.USE_PREF_SIZE);
        frame.setMinHeight(Region.USE_PREF_SIZE);
        frame.setMaxWidth(Region.USE_PREF_SIZE);
        frame.setMaxHeight(Region.USE_PREF_SIZE);

        VBox controls = new VBox(10.0,
                row("Orientation", orientationBox, new Label()),
                row("Spacing", spacingSlider, valueLabel(spacingSlider, "%.0f px")),
                row("Alignment", alignmentBox, new Label()),
                row("Width", widthSlider, valueLabel(widthSlider, "%.0f px")),
                row("Height", heightSlider, valueLabel(heightSlider, "%.0f px")),
                row("Cross axis", fillCrossAxisBox, growMiddleBox));
        controls.getStyleClass().add("toolbar");

        VBox root = new VBox(14.0, controls, frame);
        root.getStyleClass().add("root");
        VBox.setVgrow(frame, Priority.ALWAYS);

        Scene scene = new Scene(root, 920.0, 620.0);
        scene.getStylesheets().add(
                getClass().getResource("rx-box-demo.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setTitle("RXBox Demo");
        primaryStage.show();
    }

    private Button themedButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("demo-button", styleClass);
        return button;
    }

    private Node row(String label, Node control, Node trailing) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        HBox row = new HBox(10.0, fieldLabel, control, trailing);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private Label valueLabel(Slider slider, String format) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.textProperty().bind(Bindings.format(format, slider.valueProperty()));
        return label;
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

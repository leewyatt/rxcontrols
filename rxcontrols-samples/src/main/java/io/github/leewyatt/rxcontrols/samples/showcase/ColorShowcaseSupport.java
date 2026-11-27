package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXColorPresetGroup;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Controls shared by the {@code RXColorView} and {@code RXColorPicker}
 * showcases: value buttons, a binding switch that shows a bound value taking no
 * input, the format choice, option check boxes and sample preset groups.
 */
final class ColorShowcaseSupport {

    /** Sample groups: a labeled brand palette, a collapsed one and an unlabeled row. */
    static final List<RXColorPresetGroup> PRESETS = List.of(
            new RXColorPresetGroup("Brand", Color.web("#1769AA"), Color.web("#F57C00"),
                    Color.web("#2E7D32"), Color.web("#C62828"), Color.web("#6A1B9A")),
            new RXColorPresetGroup("Translucent", List.of(Color.rgb(22, 119, 255, 0.25),
                    Color.rgb(22, 119, 255, 0.5), Color.rgb(22, 119, 255, 0.75), Color.rgb(0, 0, 0, 0.1)), false),
            new RXColorPresetGroup(null, Color.WHITE, Color.LIGHTGRAY, Color.GRAY, Color.BLACK));

    private ColorShowcaseSupport() {
    }

    /** Buttons that set the value from code; none of them fires onAction. */
    static Node valueButtons(ObjectProperty<Color> value) {
        Button blue = new Button("#1677FF");
        blue.setOnAction(event -> setIfFree(value, Color.web("#1677ff")));
        Button translucent = new Button("50% red");
        translucent.setOnAction(event -> setIfFree(value, Color.rgb(220, 38, 38, 0.5)));
        Button gray = new Button("Gray");
        gray.setOnAction(event -> setIfFree(value, Color.GRAY));
        Button none = new Button("null");
        none.setOnAction(event -> setIfFree(value, null));
        FlowPane buttons = new FlowPane(6.0, 6.0, blue, translucent, gray, none);
        buttons.setPrefWrapLength(220.0);
        return buttons;
    }

    private static void setIfFree(ObjectProperty<Color> value, Color color) {
        if (!value.isBound()) {
            value.set(color);
        }
    }

    /** A check box binding the value to a fixed color; while bound the control takes no input. */
    static CheckBox bindSwitch(ObjectProperty<Color> value) {
        ObjectProperty<Color> source = new SimpleObjectProperty<>(Color.web("#2E7D32"));
        CheckBox bound = new CheckBox("Bound (no input)");
        bound.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                value.bind(source);
            } else {
                value.unbind();
            }
        });
        return bound;
    }

    /** A combo box editing the format both ways, so a switch in the panel shows here. */
    static ComboBox<RXColorFormat> formatBox(ObjectProperty<RXColorFormat> format) {
        ComboBox<RXColorFormat> box = new ComboBox<>();
        box.getItems().setAll(RXColorFormat.values());
        box.valueProperty().bindBidirectional(format);
        return box;
    }

    /** A check box editing a boolean property both ways. */
    static CheckBox check(String text, BooleanProperty property) {
        CheckBox box = new CheckBox(text);
        box.selectedProperty().bindBidirectional(property);
        return box;
    }

    /** A check box that fills or empties a preset list with {@link #PRESETS}. */
    static CheckBox presetsSwitch(ObservableList<RXColorPresetGroup> presets) {
        CheckBox box = new CheckBox("Show presets");
        box.setSelected(!presets.isEmpty());
        box.selectedProperty().addListener((obs, was, is) -> {
            if (is) {
                presets.setAll(PRESETS);
            } else {
                presets.clear();
            }
        });
        return box;
    }

    /** A label for the preview's state lines. */
    static Label stateLabel() {
        Label label = new Label();
        label.getStyleClass().add("state-label");
        return label;
    }

    /** Formats a value for the state lines. */
    static String describe(Color color) {
        return color == null ? "null" : color.toString();
    }
}

package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.samples.demo.RXColorPickerDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Showcase for {@link RXColorPicker}: the picker with its value, whether the
 * popup is showing and how often {@code onAction} fired, so edits in the popup
 * can be seen to write the value live and act once each. The control panel
 * sets the value from code (no action), binds it (the popup takes no input),
 * and switches the trigger text, the copy button, the prompt text, the format, opacity editing,
 * the clear button, the presets and the disabled state. For the smallest
 * working example see {@link RXColorPickerDemo}.
 */
public class RXColorPickerShowcase extends RXShowcaseApplication {

    private final RXColorPicker picker = new RXColorPicker(Color.web("#1677ff"));
    private int actions;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXColorPicker";
    }

    @Override
    protected String subtitle() {
        return "Color swatch that opens an RXColorView in a popup";
    }

    @Override
    protected String windowTitle() {
        return "RXColorPicker Showcase";
    }

    @Override
    protected Node createPreview() {
        picker.getPresets().setAll(ColorShowcaseSupport.PRESETS);
        picker.setShowText(true);
        picker.setCopyButtonVisible(true);
        picker.setClearable(true);
        picker.setPromptText("No color");

        Label valueLabel = ColorShowcaseSupport.stateLabel();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value: " + ColorShowcaseSupport.describe(picker.getValue()), picker.valueProperty()));
        Label showingLabel = ColorShowcaseSupport.stateLabel();
        showingLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "showing: " + picker.isShowing(), picker.showingProperty()));
        Label actionLabel = ColorShowcaseSupport.stateLabel();
        actionLabel.setText("onAction fired: 0");
        picker.setOnAction(event -> actionLabel.setText("onAction fired: " + ++actions));

        ColorPicker nativePicker = new ColorPicker();
        nativePicker.valueProperty().bindBidirectional(picker.valueProperty());

        VBox box = new VBox(12.0, picker,nativePicker, valueLabel, showingLabel, actionLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        TextField prompt = new TextField();
        prompt.textProperty().bindBidirectional(picker.promptTextProperty());
        CheckBox disabled = new CheckBox("Disabled");
        disabled.selectedProperty().bindBidirectional(picker.disableProperty());
        return List.of(
                section("Value", createGrid(
                        row("Set from code", ColorShowcaseSupport.valueButtons(picker.valueProperty())),
                        row(ColorShowcaseSupport.bindSwitch(picker.valueProperty())))),
                section("Trigger", createGrid(
                        row(ColorShowcaseSupport.check("Show text", picker.showTextProperty())),
                        row(ColorShowcaseSupport.check("Copy button", picker.copyButtonVisibleProperty())),
                        row("Prompt text", prompt))),
                section("Popup", createGrid(
                        row("Format", ColorShowcaseSupport.formatBox(picker.formatProperty())),
                        row(ColorShowcaseSupport.check("Opacity editable", picker.alphaEnabledProperty())),
                        row(ColorShowcaseSupport.check("Clear button", picker.clearableProperty())),
                        row(ColorShowcaseSupport.presetsSwitch(picker.getPresets())),
                        row(disabled))));
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

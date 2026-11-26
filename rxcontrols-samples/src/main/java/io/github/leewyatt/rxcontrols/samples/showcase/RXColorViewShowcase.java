package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXColorView;
import io.github.leewyatt.rxcontrols.samples.demo.RXColorViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Showcase for {@link RXColorView}: the panel with its value, the
 * {@code valueChanging} flag and how often {@code onAction} fired, so a drag
 * can be seen to write the value live but act once. The control panel sets the
 * value from code (no action), binds it (no input), and switches the format,
 * opacity editing, the clear button, the presets and the disabled state. For
 * the smallest working example see {@link RXColorViewDemo}.
 */
public class RXColorViewShowcase extends RXShowcaseApplication {

    private final RXColorView view = new RXColorView(Color.web("#1677ff"));
    private int actions;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXColorView";
    }

    @Override
    protected String subtitle() {
        return "Color panel: area, hue and opacity sliders, inputs and presets";
    }

    @Override
    protected String windowTitle() {
        return "RXColorView Showcase";
    }

    @Override
    protected Node createPreview() {
        view.getPresets().setAll(ColorShowcaseSupport.PRESETS);
        view.setClearable(true);

        Label valueLabel = ColorShowcaseSupport.stateLabel();
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value: " + ColorShowcaseSupport.describe(view.getValue()), view.valueProperty()));
        Label changingLabel = ColorShowcaseSupport.stateLabel();
        changingLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "valueChanging: " + view.isValueChanging(), view.valueChangingProperty()));
        Label actionLabel = ColorShowcaseSupport.stateLabel();
        actionLabel.setText("onAction fired: 0");
        view.setOnAction(event -> actionLabel.setText("onAction fired: " + ++actions));

        VBox box = new VBox(12.0, view, valueLabel, changingLabel, actionLabel);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        CheckBox disabled = new CheckBox("Disabled");
        disabled.selectedProperty().bindBidirectional(view.disableProperty());
        return List.of(
                section("Value", createGrid(
                        row("Set from code", ColorShowcaseSupport.valueButtons(view.valueProperty())),
                        row(ColorShowcaseSupport.bindSwitch(view.valueProperty())))),
                section("Options", createGrid(
                        row("Format", ColorShowcaseSupport.formatBox(view.formatProperty())),
                        row(ColorShowcaseSupport.check("Opacity editable", view.alphaEnabledProperty())),
                        row(ColorShowcaseSupport.check("Clear button", view.clearableProperty())),
                        row(ColorShowcaseSupport.presetsSwitch(view.getPresets())),
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

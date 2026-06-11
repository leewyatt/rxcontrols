package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Showcase application for {@link RXButton}.
 *
 * <p>Exercises the armed-driven ripple (pointer press, SPACE activation,
 * drag-out cancel), the four ripple properties and standard button states.
 * Programmatic {@code fire()} and the default-button ENTER accelerator show
 * no ripple by design.</p>
 */
public class RXButtonShowcase extends RXShowcaseApplication {

    private static final String PILL_STYLE_CLASS = "pill";

    private RXButton button;
    private Label firedLabel;
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXButton";
    }

    @Override
    protected String subtitle() {
        return "Standard button with armed-driven ripple feedback";
    }

    @Override
    protected String windowTitle() {
        return "RXButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXButton("Assign incident");
        button.getStyleClass().add("showcase-button");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("action fired " + firedCount + "x");
        });

        firedLabel = new Label("action fired 0x");
        firedLabel.getStyleClass().add("fired-label");

        Label hint = new Label("Click, hold, drag out; focus it and press SPACE.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, button, firedLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Ripple", buildRippleGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Sections ====================

    private Node buildRippleGrid() {
        ColorPicker fillPicker = new ColorPicker((Color) button.getRippleFill());
        fillPicker.setMaxWidth(Double.MAX_VALUE);
        fillPicker.setOnAction(event -> button.setRippleFill(fillPicker.getValue()));

        Slider opacitySlider = createSlider(0.0, 0.5, button.getRippleOpacity());
        button.rippleOpacityProperty().bind(opacitySlider.valueProperty());

        CheckBox enabledBox = new CheckBox();
        enabledBox.setSelected(button.isRippleEnabled());
        button.rippleEnabledProperty().bind(enabledBox.selectedProperty());

        CheckBox centeredBox = new CheckBox();
        centeredBox.setSelected(button.isRippleCentered());
        button.rippleCenteredProperty().bind(centeredBox.selectedProperty());

        return createGrid(
                row("Fill", fillPicker),
                row("Opacity", opacitySlider, createValueLabel(opacitySlider, "%.2f")),
                row("Enabled", enabledBox),
                row("Centered", centeredBox));
    }

    private Node buildButtonGrid() {
        CheckBox defaultBox = new CheckBox();
        button.defaultButtonProperty().bind(defaultBox.selectedProperty());

        CheckBox cancelBox = new CheckBox();
        button.cancelButtonProperty().bind(cancelBox.selectedProperty());

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        CheckBox pillBox = new CheckBox();
        pillBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                button.getStyleClass().add(PILL_STYLE_CLASS);
            } else {
                button.getStyleClass().remove(PILL_STYLE_CLASS);
            }
        });

        return createGrid(
                row("Default (ENTER)", defaultBox),
                row("Cancel (ESC)", cancelBox),
                row("Disabled", disableBox),
                row("Pill shape", pillBox));
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

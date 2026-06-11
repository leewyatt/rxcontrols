package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillButton.FillMode;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXFillButton}.
 *
 * <p>Exercises the six fill modes, the hover/pressed trigger, the reversible
 * progress animation, the mirrored caption recoloring, bounded rounded
 * clipping and the coexistence with the inherited ripple.</p>
 */
public class RXFillButtonShowcase extends RXShowcaseApplication {

    private RXFillButton button;
    private Label firedLabel;
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXFillButton";
    }

    @Override
    protected String subtitle() {
        return "Animated background fill with caption recoloring";
    }

    @Override
    protected String windowTitle() {
        return "RXFillButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-fill-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXFillButton("Deploy to production");
        button.getStyleClass().add("showcase-button");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("action fired " + firedCount + "x");
        });

        firedLabel = new Label("action fired 0x");
        firedLabel.getStyleClass().add("fired-label");

        Label hint = new Label("Hover to fill; quick in-and-out reverses from current progress.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, button, firedLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Fill", buildFillGrid()),
                section("Animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Sections ====================

    private Node buildFillGrid() {
        ComboBox<FillMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(FillMode.values());
        modeBox.setValue(button.getFillMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        button.fillModeProperty().bind(modeBox.valueProperty());

        ColorPicker fillPicker = new ColorPicker(Color.web("#616dff"));
        fillPicker.setMaxWidth(Double.MAX_VALUE);
        fillPicker.setOnAction(event -> updateFillColor(fillPicker.getValue()));

        ColorPicker hoverTextPicker = new ColorPicker((Color) button.getHoverTextFill());
        hoverTextPicker.setMaxWidth(Double.MAX_VALUE);
        hoverTextPicker.setOnAction(event -> button.setHoverTextFill(hoverTextPicker.getValue()));

        return createGrid(
                row("Mode", modeBox),
                row("Fill color", fillPicker),
                row("Hover text", hoverTextPicker));
    }

    private Node buildAnimationGrid() {
        ComboBox<RXAnimationTrigger> triggerBox = new ComboBox<>();
        triggerBox.getItems().setAll(RXAnimationTrigger.values());
        triggerBox.setValue(button.getAnimationTrigger());
        triggerBox.setMaxWidth(Double.MAX_VALUE);
        button.animationTriggerProperty().bind(triggerBox.valueProperty());

        Slider durationSlider = createSlider(0.0, 800.0,
                button.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimationDuration(Duration.millis(newV.doubleValue())));

        return createGrid(
                row("Trigger", triggerBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildButtonGrid() {
        Slider radiusSlider = createSlider(0.0, 24.0, 4.0);
        radiusSlider.valueProperty().addListener((obs, oldV, newV) ->
                updateRadius(newV.doubleValue()));

        CheckBox rippleBox = new CheckBox();
        rippleBox.setSelected(button.isRippleEnabled());
        button.rippleEnabledProperty().bind(rippleBox.selectedProperty());

        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                Region icon = new Region();
                icon.getStyleClass().add("rocket-icon");
                icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                button.setGraphic(icon);
            } else {
                button.setGraphic(null);
            }
        });

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        return createGrid(
                row("Radius", radiusSlider, createValueLabel(radiusSlider, "%.0f px")),
                row("Ripple", rippleBox),
                row("Graphic", graphicBox),
                row("Disabled", disableBox));
    }

    // ==================== Helpers ====================

    private Color lastFill = Color.web("#616dff");
    private double lastRadius = 4.0;

    private void updateFillColor(Color color) {
        lastFill = color;
        applyInlineStyle();
    }

    private void updateRadius(double radius) {
        lastRadius = radius;
        applyInlineStyle();
    }

    private void applyInlineStyle() {
        button.setStyle(String.format(Locale.ROOT,
                "-rx-fill: #%02x%02x%02x; -fx-background-radius: %.0fpx; -fx-border-radius: %.0fpx;",
                Math.round(lastFill.getRed() * 255),
                Math.round(lastFill.getGreen() * 255),
                Math.round(lastFill.getBlue() * 255),
                lastRadius, lastRadius));
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

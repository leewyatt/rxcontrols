package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorDisplay;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorPosition;
import io.github.leewyatt.rxcontrols.samples.demo.RXSliderDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Showcase application for {@link RXSlider}.
 *
 * <p>Exposes every configurable property — value model, indicator display and
 * position, orientation, size variant, the tick scale, and the Material feedback
 * (ripple / state overlay / animation / ripple color). For a minimal example see
 * {@link RXSliderDemo}.</p>
 */
public class RXSliderShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_MAIN = 360.0;
    private static final double PREVIEW_VERTICAL_MAIN = 240.0;
    private static final double PREVIEW_CROSS_MIN = 280.0;

    private RXSlider slider;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSlider";
    }

    @Override
    protected String subtitle() {
        return "Material single-value slider";
    }

    @Override
    protected String windowTitle() {
        return "RXSlider Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-slider-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        slider = new RXSlider(0.0, 100.0, 60.0);
        slider.setShowTickMarks(true);
        slider.setShowTickLabels(true);
        slider.setMajorTickUnit(25.0);
        slider.setMinorTickCount(4);
        applyOrientation(Orientation.HORIZONTAL);

        StackPane pane = new StackPane(slider);
        pane.getStyleClass().add("slider-preview");
        pane.setMinHeight(PREVIEW_CROSS_MIN);
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Model", buildModelGrid()),
                section("Indicator", buildIndicatorGrid()),
                section("Layout", buildLayoutGrid()),
                section("Ticks", buildTicksGrid()),
                section("Feedback", buildFeedbackGrid()));
    }

    // ==================== Sections ====================

    private Node buildModelGrid() {
        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("value-label");
        valueLabel.textProperty().bind(Bindings.format("%.1f", slider.valueProperty()));

        Slider minSlider = createSlider(-50.0, 0.0, slider.getMin());
        slider.minProperty().bind(minSlider.valueProperty());
        Slider maxSlider = createSlider(100.0, 200.0, slider.getMax());
        slider.maxProperty().bind(maxSlider.valueProperty());
        Slider blockSlider = createSlider(1.0, 25.0, slider.getBlockIncrement());
        slider.blockIncrementProperty().bind(blockSlider.valueProperty());

        return createGrid(
                row("Value", valueLabel),
                row("Min", minSlider, createValueLabel(minSlider, "%.0f")),
                row("Max", maxSlider, createValueLabel(maxSlider, "%.0f")),
                row("Block incr", blockSlider, createValueLabel(blockSlider, "%.0f")));
    }

    private Node buildIndicatorGrid() {
        ComboBox<RXSliderIndicatorDisplay> display = new ComboBox<>();
        display.getItems().setAll(RXSliderIndicatorDisplay.values());
        display.setValue(slider.getIndicatorDisplay());
        display.setMaxWidth(Double.MAX_VALUE);
        display.valueProperty().addListener((obs, old, value) -> slider.setIndicatorDisplay(value));

        ComboBox<RXSliderIndicatorPosition> position = new ComboBox<>();
        position.getItems().setAll(RXSliderIndicatorPosition.values());
        position.setValue(slider.getIndicatorPosition());
        position.setMaxWidth(Double.MAX_VALUE);
        position.valueProperty().addListener((obs, old, value) -> slider.setIndicatorPosition(value));

        return createGrid(
                row("Display", display),
                row("Position", position));
    }

    private Node buildLayoutGrid() {
        ComboBox<Orientation> orientation = new ComboBox<>();
        orientation.getItems().setAll(Orientation.values());
        orientation.setValue(slider.getOrientation());
        orientation.setMaxWidth(Double.MAX_VALUE);
        orientation.valueProperty().addListener((obs, old, value) -> applyOrientation(value));

        ComboBox<String> size = new ComboBox<>();
        size.getItems().setAll("Default", "Small", "Large");
        size.setValue("Default");
        size.setMaxWidth(Double.MAX_VALUE);
        size.valueProperty().addListener((obs, old, value) -> applySize(value));

        return createGrid(
                row("Orientation", orientation),
                row("Size", size));
    }

    private Node buildTicksGrid() {
        CheckBox marks = new CheckBox("Show tick marks");
        marks.setSelected(slider.isShowTickMarks());
        slider.showTickMarksProperty().bind(marks.selectedProperty());

        CheckBox labels = new CheckBox("Show tick labels");
        labels.setSelected(slider.isShowTickLabels());
        slider.showTickLabelsProperty().bind(labels.selectedProperty());

        CheckBox snap = new CheckBox("Snap to ticks");
        snap.setSelected(slider.isSnapToTicks());
        slider.snapToTicksProperty().bind(snap.selectedProperty());

        Slider major = createSlider(10.0, 50.0, slider.getMajorTickUnit());
        slider.majorTickUnitProperty().bind(major.valueProperty());

        Slider minor = createSlider(0.0, 8.0, slider.getMinorTickCount());
        minor.setMajorTickUnit(1.0);
        minor.setMinorTickCount(0);
        minor.setSnapToTicks(true);
        minor.valueProperty().addListener((obs, old, value) -> slider.setMinorTickCount(value.intValue()));

        return createGrid(
                row(marks),
                row(labels),
                row(snap),
                row("Major unit", major, createValueLabel(major, "%.0f")),
                row("Minor count", minor, createValueLabel(minor, "%.0f")));
    }

    private Node buildFeedbackGrid() {
        CheckBox ripple = new CheckBox("Ripple enabled");
        ripple.setSelected(slider.isRippleEnabled());
        slider.rippleEnabledProperty().bind(ripple.selectedProperty());

        CheckBox overlay = new CheckBox("State overlay enabled");
        overlay.setSelected(slider.isStateOverlayEnabled());
        slider.stateOverlayEnabledProperty().bind(overlay.selectedProperty());

        CheckBox animated = new CheckBox("Animated");
        animated.setSelected(slider.isAnimated());
        slider.animatedProperty().bind(animated.selectedProperty());

        Color initial = slider.getRippleFill() instanceof Color
                ? (Color) slider.getRippleFill() : Color.web("#1976d2");
        ColorPicker rippleFill = new ColorPicker(initial);
        rippleFill.setMaxWidth(Double.MAX_VALUE);
        rippleFill.setOnAction(e -> slider.setRippleFill(rippleFill.getValue()));

        return createGrid(
                row(ripple),
                row(overlay),
                row(animated),
                row("Ripple fill", rippleFill));
    }

    // ==================== Preview helpers ====================

    private void applyOrientation(Orientation value) {
        slider.setOrientation(value);
        if (value == Orientation.VERTICAL) {
            slider.setPrefWidth(Region.USE_COMPUTED_SIZE);
            slider.setMaxWidth(Region.USE_PREF_SIZE);
            slider.setPrefHeight(PREVIEW_VERTICAL_MAIN);
            slider.setMaxHeight(PREVIEW_VERTICAL_MAIN);
        } else {
            slider.setPrefHeight(Region.USE_COMPUTED_SIZE);
            slider.setMaxHeight(Region.USE_PREF_SIZE);
            slider.setPrefWidth(PREVIEW_MAIN);
            slider.setMaxWidth(PREVIEW_MAIN);
        }
    }

    private void applySize(String value) {
        slider.getStyleClass().removeAll("small", "large");
        if ("Small".equals(value)) {
            slider.getStyleClass().add("small");
        } else if ("Large".equals(value)) {
            slider.getStyleClass().add("large");
        }
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

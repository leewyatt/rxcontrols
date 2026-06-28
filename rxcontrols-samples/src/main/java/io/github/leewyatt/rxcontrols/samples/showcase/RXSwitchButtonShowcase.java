package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.samples.demo.RXSwitchButtonDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase application for {@link RXSwitchButton}.
 *
 * <p>Exposes the {@code selected} / {@code disabled} state, the styleable
 * {@code switchPosition} and {@code animationDuration}, the plain
 * {@code animationInterpolator}, and the {@code .small} density variant. For a
 * minimal example see {@link RXSwitchButtonDemo}.</p>
 */
public class RXSwitchButtonShowcase extends RXShowcaseApplication {

    private static final String SMALL_STYLE_CLASS = "small";

    private final Map<String, Interpolator> interpolators = new LinkedHashMap<>();

    private RXSwitchButton switchButton;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSwitchButton";
    }

    @Override
    protected String subtitle() {
        return "Material on / off switch";
    }

    @Override
    protected String windowTitle() {
        return "RXSwitchButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-switch-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        switchButton = new RXSwitchButton("Wi-Fi");
        switchButton.setSelected(true);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "selected: " + switchButton.isSelected(),
                switchButton.selectedProperty()));

        VBox box = new VBox(20.0, switchButton, stateLabel);
        box.getStyleClass().add("switch-preview");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("State", buildStateGrid()),
                section("Layout", buildLayoutGrid()),
                section("Animation", buildAnimationGrid()));
    }

    // ==================== Sections ====================

    private Node buildStateGrid() {
        CheckBox selected = new CheckBox("selected");
        selected.setSelected(switchButton.isSelected());
        selected.selectedProperty().bindBidirectional(switchButton.selectedProperty());

        CheckBox disabled = new CheckBox("disabled");
        switchButton.disableProperty().bind(disabled.selectedProperty());

        return createGrid(row(selected), row(disabled));
    }

    private Node buildLayoutGrid() {
        ComboBox<HorizontalDirection> position = new ComboBox<>();
        position.getItems().setAll(HorizontalDirection.RIGHT, HorizontalDirection.LEFT);
        position.setMaxWidth(Double.MAX_VALUE);
        position.valueProperty().bindBidirectional(switchButton.switchPositionProperty());

        ComboBox<String> size = new ComboBox<>();
        size.getItems().setAll("Default", "Small");
        size.setValue("Default");
        size.setMaxWidth(Double.MAX_VALUE);
        size.valueProperty().addListener((obs, old, value) -> {
            switchButton.getStyleClass().remove(SMALL_STYLE_CLASS);
            if ("Small".equals(value)) {
                switchButton.getStyleClass().add(SMALL_STYLE_CLASS);
            }
        });

        return createGrid(
                row("Position", position),
                row("Size", size));
    }

    private Node buildAnimationGrid() {
        Slider duration = createSlider(0.0, 600.0,
                switchButton.getAnimationDuration().toMillis());
        switchButton.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(duration.getValue()), duration.valueProperty()));
        Label durationValue = createValueLabel(duration, "%.0f ms");

        interpolators.put("EASE_BOTH", Interpolator.EASE_BOTH);
        interpolators.put("EASE_IN", Interpolator.EASE_IN);
        interpolators.put("EASE_OUT", Interpolator.EASE_OUT);
        interpolators.put("LINEAR", Interpolator.LINEAR);
        ComboBox<String> interpolator = new ComboBox<>();
        interpolator.getItems().setAll(interpolators.keySet());
        interpolator.setValue("EASE_BOTH");
        interpolator.setMaxWidth(Double.MAX_VALUE);
        interpolator.valueProperty().addListener((obs, old, value) ->
                switchButton.setAnimationInterpolator(interpolators.get(value)));

        return createGrid(
                row("Duration", duration, durationValue),
                row("Easing", interpolator));
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

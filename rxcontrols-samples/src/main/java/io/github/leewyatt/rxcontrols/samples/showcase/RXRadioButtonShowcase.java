package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.samples.demo.RXRadioButtonDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase application for {@link RXRadioButton}.
 *
 * <p>Exposes a mutually exclusive group, the {@code disabled} state, the styleable
 * {@code radioPosition} and {@code animationDuration}, and the plain
 * {@code animationInterpolator}. For a minimal example see
 * {@link RXRadioButtonDemo}.</p>
 */
public class RXRadioButtonShowcase extends RXShowcaseApplication {

    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, Interpolator> interpolators = new LinkedHashMap<>();

    private RXRadioButton first;
    private RXRadioButton second;
    private RXRadioButton third;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXRadioButton";
    }

    @Override
    protected String subtitle() {
        return "Material single-choice control";
    }

    @Override
    protected String windowTitle() {
        return "RXRadioButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-radio-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        first = radio("Credit card");
        second = radio("Alipay");
        third = radio("WeChat Pay");
        first.setSelected(true);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    RadioButton selected = (RadioButton) group.getSelectedToggle();
                    return "selected: " + (selected == null ? "none" : selected.getText());
                },
                group.selectedToggleProperty()));

        VBox box = new VBox(16.0, first, second, third, stateLabel);
        box.getStyleClass().add("radio-button-preview");
        box.setAlignment(Pos.CENTER_LEFT);
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
        CheckBox disabled = new CheckBox("disabled");
        first.disableProperty().bind(disabled.selectedProperty());
        second.disableProperty().bind(disabled.selectedProperty());
        third.disableProperty().bind(disabled.selectedProperty());

        return createGrid(row(disabled));
    }

    private Node buildLayoutGrid() {
        ComboBox<HorizontalDirection> radioPosition = new ComboBox<>();
        radioPosition.getItems().setAll(HorizontalDirection.LEFT, HorizontalDirection.RIGHT);
        radioPosition.setValue(HorizontalDirection.LEFT);
        radioPosition.setMaxWidth(Double.MAX_VALUE);
        radioPosition.valueProperty().addListener((obs, old, value) -> {
            first.setRadioPosition(value);
            second.setRadioPosition(value);
            third.setRadioPosition(value);
        });

        return createGrid(row("Radio position", radioPosition));
    }

    private Node buildAnimationGrid() {
        Slider duration = createSlider(0.0, 600.0,
                first.getAnimationDuration().toMillis());
        duration.valueProperty().addListener((obs, old, value) -> {
            Duration millis = Duration.millis(value.doubleValue());
            first.setAnimationDuration(millis);
            second.setAnimationDuration(millis);
            third.setAnimationDuration(millis);
        });
        Label durationValue = createValueLabel(duration, "%.0f ms");

        interpolators.put("EASE_OUT", Interpolator.EASE_OUT);
        interpolators.put("EASE_BOTH", Interpolator.EASE_BOTH);
        interpolators.put("EASE_IN", Interpolator.EASE_IN);
        interpolators.put("LINEAR", Interpolator.LINEAR);
        ComboBox<String> interpolator = new ComboBox<>();
        interpolator.getItems().setAll(interpolators.keySet());
        interpolator.setValue("EASE_OUT");
        interpolator.setMaxWidth(Double.MAX_VALUE);
        interpolator.valueProperty().addListener((obs, old, value) -> {
            Interpolator easing = interpolators.get(value);
            first.setAnimationInterpolator(easing);
            second.setAnimationInterpolator(easing);
            third.setAnimationInterpolator(easing);
        });

        return createGrid(
                row("Duration", duration, durationValue),
                row("Easing", interpolator));
    }

    private RXRadioButton radio(String text) {
        RXRadioButton control = new RXRadioButton(text);
        control.setToggleGroup(group);
        return control;
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

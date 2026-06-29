package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.samples.demo.RXCheckBoxDemo;
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
 * Showcase application for {@link RXCheckBox}.
 *
 * <p>Exposes the tri-state {@code selected} / {@code indeterminate} /
 * {@code allowIndeterminate} and {@code disabled} state, the styleable
 * {@code boxSide} and {@code animationDuration}, and the plain
 * {@code animationInterpolator}. For a minimal example see
 * {@link RXCheckBoxDemo}.</p>
 */
public class RXCheckBoxShowcase extends RXShowcaseApplication {

    private final Map<String, Interpolator> interpolators = new LinkedHashMap<>();

    private RXCheckBox checkBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCheckBox";
    }

    @Override
    protected String subtitle() {
        return "Material tri-state check box";
    }

    @Override
    protected String windowTitle() {
        return "RXCheckBox Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-check-box-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        checkBox = new RXCheckBox("I agree");
        checkBox.setSelected(true);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "selected: " + checkBox.isSelected()
                        + "   indeterminate: " + checkBox.isIndeterminate(),
                checkBox.selectedProperty(), checkBox.indeterminateProperty()));

        VBox box = new VBox(20.0, checkBox, stateLabel);
        box.getStyleClass().add("check-box-preview");
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
        selected.selectedProperty().bindBidirectional(checkBox.selectedProperty());

        CheckBox indeterminate = new CheckBox("indeterminate");
        indeterminate.selectedProperty().bindBidirectional(checkBox.indeterminateProperty());

        CheckBox allowIndeterminate = new CheckBox("allowIndeterminate");
        allowIndeterminate.selectedProperty().bindBidirectional(checkBox.allowIndeterminateProperty());

        CheckBox disabled = new CheckBox("disabled");
        checkBox.disableProperty().bind(disabled.selectedProperty());

        return createGrid(
                row(selected),
                row(indeterminate),
                row(allowIndeterminate),
                row(disabled));
    }

    private Node buildLayoutGrid() {
        ComboBox<HorizontalDirection> boxSide = new ComboBox<>();
        boxSide.getItems().setAll(HorizontalDirection.LEFT, HorizontalDirection.RIGHT);
        boxSide.setMaxWidth(Double.MAX_VALUE);
        boxSide.valueProperty().bindBidirectional(checkBox.boxSideProperty());

        return createGrid(row("Box side", boxSide));
    }

    private Node buildAnimationGrid() {
        Slider duration = createSlider(0.0, 600.0,
                checkBox.getAnimationDuration().toMillis());
        checkBox.animationDurationProperty().bind(Bindings.createObjectBinding(
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
                checkBox.setAnimationInterpolator(interpolators.get(value)));

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

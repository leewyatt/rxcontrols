package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXChip}.
 *
 * <p>Drives a single configurable chip across every axis — the {@code selectable}
 * toggle (a filter chip) and {@code removable} affordance that compose the assist /
 * filter / input / suggestion personas, the {@code selected} and {@code disabled}
 * states, the ripple and state-overlay toggles with their opacity, and the
 * {@code maxLabelWidth} cap — and shows one static chip of each persona for
 * comparison. For the everyday tag-entry use see {@link RXChipInputShowcase}.</p>
 */
public class RXChipShowcase extends RXShowcaseApplication {

    private RXChip chip;

    @Override
    protected String title() {
        return "RXChip";
    }

    @Override
    protected String subtitle() {
        return "The chip atom: assist / filter / input / suggestion personas with selected, removable and ripple states.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-chip-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        chip = new RXChip("Configurable chip");
        // Selectable by default so the selected-state primary tint is reachable straight
        // from the Selected toggle.
        chip.setSelectable(true);
        chip.setMaxLabelWidth(320.0);

        HBox personas = new HBox(10.0,
                new RXChip("Assist"),
                filter("Filter", true),
                removableChip("Input"),
                new RXChip("Suggestion"));
        personas.setAlignment(Pos.CENTER);

        Label personaCaption = new Label("The four chip personas");
        personaCaption.getStyleClass().add("preview-caption");

        VBox box = new VBox(28.0, chip, new VBox(8.0, personas, personaCaption));
        ((VBox) box.getChildren().get(1)).setAlignment(Pos.CENTER);
        box.setAlignment(Pos.CENTER);

        StackPane host = new StackPane(box);
        host.getStyleClass().add("chip-preview");
        return host;
    }

    private static RXChip filter(String text, boolean selected) {
        RXChip filterChip = new RXChip(text);
        filterChip.setSelectable(true);
        filterChip.setSelected(selected);
        return filterChip;
    }

    private static RXChip removableChip(String text) {
        RXChip removable = new RXChip(text);
        removable.setRemovable(true);
        return removable;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Persona & text", personaGrid()),
                section("State", stateGrid()),
                section("Ripple & overlay", rippleGrid()),
                section("Sizing", sizingGrid()));
    }

    private Node personaGrid() {
        TextField text = new TextField(chip.getText());
        text.textProperty().addListener((obs, old, value) -> chip.setText(value));

        return createGrid(
                row(checkBox("Selectable (toggle / filter chip)", chip.isSelectable(), chip::setSelectable)),
                row("Text", text));
    }

    private Node stateGrid() {
        Label state = new Label();
        state.getStyleClass().add("value-label");
        state.textProperty().bind(Bindings.createStringBinding(
                () -> chip.isSelected() ? "selected" : "unselected", chip.selectedProperty()));

        CheckBox selected = checkBox("Selected (a selectable chip tints primary)",
                chip.isSelected(), chip::setSelected);
        // Selected only renders (the primary tint) on a selectable chip; grey the toggle
        // out while the chip is not selectable so the dependency is visible.
        selected.disableProperty().bind(chip.selectableProperty().not());

        return createGrid(
                row(selected),
                row(checkBox("Removable (trailing close button)", chip.isRemovable(), chip::setRemovable)),
                row(checkBox("Disabled", chip.isDisabled(), chip::setDisable)),
                row(state));
    }

    private Node rippleGrid() {
        Slider opacity = createSlider(0.0, 0.5, chip.getRippleOpacity());
        opacity.valueProperty().addListener((obs, old, value) -> chip.setRippleOpacity(value.doubleValue()));

        return createGrid(
                row(checkBox("Ripple enabled", chip.isRippleEnabled(), chip::setRippleEnabled)),
                row(checkBox("State overlay enabled", chip.isStateOverlayEnabled(), chip::setStateOverlayEnabled)),
                row("Ripple opacity", opacity, createValueLabel(opacity, "%.2f")));
    }

    private Node sizingGrid() {
        Slider maxLabel = createSlider(40.0, 320.0, chip.getMaxLabelWidth());
        maxLabel.valueProperty().addListener((obs, old, value) -> chip.setMaxLabelWidth(value.doubleValue()));

        return createGrid(
                row("Max label width", maxLabel, createValueLabel(maxLabel, "%.0f px")));
    }

    private CheckBox checkBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.selectedProperty().addListener((obs, old, value) -> onChange.accept(value));
        return box;
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}

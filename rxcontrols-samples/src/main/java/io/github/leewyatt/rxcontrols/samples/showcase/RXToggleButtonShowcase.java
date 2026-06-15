package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRadioToggleButton;
import io.github.leewyatt.rxcontrols.RXToggleButton;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Showcase application for {@link RXToggleButton} and
 * {@link RXRadioToggleButton}.
 *
 * <p>Two groups sit side by side so the behavioural contrast is obvious: the
 * standard {@code RXToggleButton} group can be emptied by re-clicking the
 * selected button, while the {@code RXRadioToggleButton} group always keeps one
 * selection (radio-like). The right panel drives the shared ripple properties
 * (fill, opacity, enabled, centered) plus the disabled state across every
 * toggle, and a {@code playRipple()} button fires the programmatic ripple.</p>
 */
public class RXToggleButtonShowcase extends RXShowcaseApplication {

    private final List<RXToggleButton> allToggles = new ArrayList<>();

    private Label standardStatus;
    private Label radioStatus;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXToggleButton / RXRadioToggleButton";
    }

    @Override
    protected String subtitle() {
        return "Standard toggle vs radio-like toggle, both with ripple feedback";
    }

    @Override
    protected String windowTitle() {
        return "RXToggleButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-toggle-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        ToggleGroup standardGroup = new ToggleGroup();
        HBox standardRow = new HBox(8.0,
                standardToggle("List", standardGroup),
                standardToggle("Grid", standardGroup),
                standardToggle("Gallery", standardGroup));
        standardRow.setAlignment(Pos.CENTER);
        standardStatus = new Label();
        standardStatus.getStyleClass().add("status-label");
        standardGroup.selectedToggleProperty().addListener(
                (obs, oldToggle, newToggle) -> standardStatus.setText(standardStatusText(newToggle)));
        ((ToggleButton) standardGroup.getToggles().get(0)).setSelected(true);

        ToggleGroup radioGroup = new ToggleGroup();
        HBox radioRow = new HBox(8.0,
                radioToggle("Day", radioGroup),
                radioToggle("Week", radioGroup),
                radioToggle("Month", radioGroup));
        radioRow.setAlignment(Pos.CENTER);
        radioStatus = new Label();
        radioStatus.getStyleClass().add("status-label");
        radioGroup.selectedToggleProperty().addListener(
                (obs, oldToggle, newToggle) -> radioStatus.setText(radioStatusText(newToggle)));
        ((ToggleButton) radioGroup.getToggles().get(0)).setSelected(true);

        VBox standardBlock = new VBox(8.0,
                blockTitle("RXToggleButton (re-click clears it)"), standardRow, standardStatus);
        standardBlock.setAlignment(Pos.CENTER);
        VBox radioBlock = new VBox(8.0,
                blockTitle("RXRadioToggleButton (always one selected)"), radioRow, radioStatus);
        radioBlock.setAlignment(Pos.CENTER);

        VBox preview = new VBox(28.0, standardBlock, radioBlock);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Ripple", buildRippleGrid()),
                section("State", buildStateGrid()));
    }

    // ==================== Sections ====================

    private Node buildRippleGrid() {
        ColorPicker fillPicker = new ColorPicker((Color) allToggles.get(0).getRippleFill());
        fillPicker.setMaxWidth(Double.MAX_VALUE);
        fillPicker.setOnAction(event ->
                allToggles.forEach(toggle -> toggle.setRippleFill(fillPicker.getValue())));

        Slider opacitySlider = createSlider(0.0, 0.5, allToggles.get(0).getRippleOpacity());
        opacitySlider.valueProperty().addListener((obs, oldValue, newValue) ->
                allToggles.forEach(toggle -> toggle.setRippleOpacity(newValue.doubleValue())));

        CheckBox enabledBox = new CheckBox();
        enabledBox.setSelected(allToggles.get(0).isRippleEnabled());
        enabledBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                allToggles.forEach(toggle -> toggle.setRippleEnabled(newValue)));

        CheckBox centeredBox = new CheckBox();
        centeredBox.setSelected(allToggles.get(0).isRippleCentered());
        centeredBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                allToggles.forEach(toggle -> toggle.setRippleCentered(newValue)));

        return createGrid(
                row("Fill", fillPicker),
                row("Opacity", opacitySlider, createValueLabel(opacitySlider, "%.2f")),
                row("Enabled", enabledBox),
                row("Centered", centeredBox));
    }

    private Node buildStateGrid() {
        CheckBox disableBox = new CheckBox();
        disableBox.selectedProperty().addListener((obs, oldValue, newValue) ->
                allToggles.forEach(toggle -> toggle.setDisable(newValue)));

        Button playRippleButton = new Button("playRipple() (all)");
        playRippleButton.setMaxWidth(Double.MAX_VALUE);
        playRippleButton.setOnAction(event -> allToggles.forEach(RXToggleButton::playRipple));

        return createGrid(
                row("Disabled", disableBox),
                row("Play ripple", playRippleButton));
    }

    // ==================== Helpers ====================

    private RXToggleButton standardToggle(String text, ToggleGroup group) {
        RXToggleButton toggle = new RXToggleButton(text);
        toggle.setToggleGroup(group);
        toggle.getStyleClass().add("demo-toggle");
        allToggles.add(toggle);
        return toggle;
    }

    private RXRadioToggleButton radioToggle(String text, ToggleGroup group) {
        RXRadioToggleButton toggle = new RXRadioToggleButton(text);
        toggle.setToggleGroup(group);
        toggle.getStyleClass().add("demo-toggle");
        allToggles.add(toggle);
        return toggle;
    }

    private Label blockTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("block-title");
        return label;
    }

    private static String standardStatusText(Toggle toggle) {
        if (toggle == null) {
            return "selection: none (re-click cleared it)";
        }
        return "selection: " + ((ToggleButton) toggle).getText();
    }

    private static String radioStatusText(Toggle toggle) {
        if (toggle == null) {
            return "selection: none";
        }
        return "selection: " + ((ToggleButton) toggle).getText();
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

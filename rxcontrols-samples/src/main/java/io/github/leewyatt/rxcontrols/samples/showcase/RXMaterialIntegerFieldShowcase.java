package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMaterialIntegerField;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialIntegerFieldDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXMaterialIntegerField}.
 *
 * <p>Exercises the Material surface inherited from RXMaterialTextField
 * (floating label, helper / error supporting text, the invalid state, the
 * animation toggle, and the built-in clear button) together with the typed
 * knobs shared with the plain RXIntegerField: the committed {@link Integer}
 * value, the inclusive primitive min / max bounds with Slider-style
 * convergence, and the 32-bit overflow rollback. Alignment and the editable
 * flag round out the layout section.
 *
 * <p>For a minimal "few lines of code" example see
 * {@link RXMaterialIntegerFieldDemo}.
 */
public class RXMaterialIntegerFieldShowcase extends RXShowcaseApplication {

    private static final double BOUND_MIN = -100.0;
    private static final double BOUND_MAX = 100.0;

    private RXMaterialIntegerField field;
    private Slider minSlider;
    private Slider maxSlider;
    private CheckBox minEnabled;
    private CheckBox maxEnabled;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMaterialIntegerField";
    }

    @Override
    protected String subtitle() {
        return "Material integer-only numeric field";
    }

    @Override
    protected String windowTitle() {
        return "RXMaterialIntegerField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-material-integer-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXMaterialIntegerField(25);
        field.setLabelText("Quantity");
        field.setHelperText("Whole numbers only");
        field.setPrefColumnCount(14);

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> describe(field),
                field.valueProperty(), field.minProperty(), field.maxProperty()));

        VBox box = new VBox(22.0, field, readout);
        box.getStyleClass().add("live-preview");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Material surface", buildSurfaceGrid()),
                section("Range", buildRangeGrid()),
                section("Overflow policy", buildPolicyGrid()),
                section("Layout & state", buildLayoutGrid()));
    }

    // ==================== Sections ====================

    private Node buildSurfaceGrid() {
        TextField labelBox = new TextField(field.getLabelText());
        labelBox.setMaxWidth(Double.MAX_VALUE);
        field.labelTextProperty().bind(labelBox.textProperty());

        TextField helperBox = new TextField(field.getHelperText());
        helperBox.setMaxWidth(Double.MAX_VALUE);
        field.helperTextProperty().bind(helperBox.textProperty());

        TextField errorBox = new TextField();
        errorBox.setMaxWidth(Double.MAX_VALUE);
        errorBox.setPromptText("shown when invalid");
        field.errorTextProperty().bind(errorBox.textProperty());

        CheckBox invalidBox = new CheckBox("Invalid");
        field.invalidProperty().bind(invalidBox.selectedProperty());

        CheckBox floatingBox = new CheckBox("Floating label");
        floatingBox.setSelected(field.isFloatingLabel());
        field.floatingLabelProperty().bind(floatingBox.selectedProperty());

        CheckBox animatedBox = new CheckBox("Animated");
        animatedBox.setSelected(field.isAnimated());
        field.animatedProperty().bind(animatedBox.selectedProperty());

        CheckBox clearBox = new CheckBox("Clear button");
        clearBox.setSelected(field.isShowClearButton());
        field.showClearButtonProperty().bind(clearBox.selectedProperty());

        HBox stateRow = new HBox(18.0, invalidBox, floatingBox);
        stateRow.getStyleClass().add("toggle-row");
        HBox chromeRow = new HBox(18.0, animatedBox, clearBox);
        chromeRow.getStyleClass().add("toggle-row");

        Label hint = new Label("The clear button clears the committed value, not just the "
                + "text — clicking it commits null immediately.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Label", labelBox),
                row("Helper", helperBox),
                row("Error", errorBox),
                row(stateRow),
                row(chromeRow),
                row(hint));
    }

    private Node buildRangeGrid() {
        minSlider = createSlider(BOUND_MIN, BOUND_MAX, 0.0);
        minSlider.setDisable(true);
        minSlider.valueProperty().addListener((obs, oldV, newV) -> applyMin());
        Label minValue = createValueLabel(minSlider, "%.0f");

        maxSlider = createSlider(BOUND_MIN, BOUND_MAX, 0.0);
        maxSlider.setDisable(true);
        maxSlider.valueProperty().addListener((obs, oldV, newV) -> applyMax());
        Label maxValue = createValueLabel(maxSlider, "%.0f");

        minEnabled = new CheckBox("Enable min");
        minEnabled.selectedProperty().addListener((obs, oldV, on) -> {
            minSlider.setDisable(!on);
            applyMin();
        });
        maxEnabled = new CheckBox("Enable max");
        maxEnabled.selectedProperty().addListener((obs, oldV, on) -> {
            maxSlider.setDisable(!on);
            applyMax();
        });
        HBox toggleRow = new HBox(18.0, minEnabled, maxEnabled);
        toggleRow.getStyleClass().add("toggle-row");

        Label hint = new Label("Out-of-range edits clamp to the active bound. "
                + "Dragging one bound past the other converges the opposite bound "
                + "(Slider-style, min <= max). Disabling a bound resets it to the "
                + "full int domain.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Min", minSlider, minValue),
                row("Max", maxSlider, maxValue),
                row(toggleRow),
                row(hint));
    }

    private Node buildPolicyGrid() {
        Label result = new Label("Press the button to feed 2147483648 (Integer.MAX_VALUE + 1) into the text.");
        result.getStyleClass().add("policy-result");
        result.setWrapText(true);

        Button overflow = new Button("Commit 2147483648");
        overflow.setOnAction(e -> {
            field.setText("2147483648");
            field.commitValue();
            result.setText("Overflow rolled the text back; value = " + field.getValue()
                    + ". Whole numbers beyond 32-bit belong in RXMaterialDecimalField.");
        });

        return createGrid(row(overflow), row(result));
    }

    private Node buildLayoutGrid() {
        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().addAll(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT);
        alignmentBox.setValue(field.getAlignment());
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        field.alignmentProperty().bind(alignmentBox.valueProperty());

        CheckBox editableBox = new CheckBox("Editable");
        editableBox.selectedProperty().bindBidirectional(field.editableProperty());

        return createGrid(
                row("Alignment", alignmentBox),
                row(editableBox));
    }

    // ==================== Behaviour ====================

    private void applyMin() {
        field.setMin(minEnabled.isSelected()
                ? (int) Math.round(minSlider.getValue())
                : Integer.MIN_VALUE);
    }

    private void applyMax() {
        field.setMax(maxEnabled.isSelected()
                ? (int) Math.round(maxSlider.getValue())
                : Integer.MAX_VALUE);
    }

    private static String describe(RXMaterialIntegerField field) {
        return "value = " + field.getValue()
                + "\nmin = " + bound(field.getMin(), Integer.MIN_VALUE)
                + "      max = " + bound(field.getMax(), Integer.MAX_VALUE);
    }

    private static String bound(int value, int unboundedSentinel) {
        return value == unboundedSentinel ? "unbounded" : Integer.toString(value);
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

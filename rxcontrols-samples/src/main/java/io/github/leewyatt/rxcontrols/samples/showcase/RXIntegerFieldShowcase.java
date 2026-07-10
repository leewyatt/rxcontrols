package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.samples.demo.RXIntegerFieldDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXIntegerField}.
 *
 * <p>Exercises the main public knobs: the committed {@link Integer} value
 * (edited in the preview field, which rejects the decimal point), the
 * inclusive primitive {@code min} / {@code max} bounds with Slider-style
 * convergence, the inherited leading / trailing decoration slots, alignment,
 * and the editable flag (text padding is deliberately not showcased: a panel
 * slider would take USER origin and permanently disable the UA side-node
 * defaults). A dedicated section demonstrates the 32-bit overflow policy —
 * an out-of-range magnitude rolls the text back instead of corrupting the
 * value.
 *
 * <p>For a minimal "few lines of code" example see {@link RXIntegerFieldDemo}.
 */
public class RXIntegerFieldShowcase extends RXShowcaseApplication {

    private static final double BOUND_MIN = -100.0;
    private static final double BOUND_MAX = 100.0;

    private RXIntegerField field;
    private Slider minSlider;
    private Slider maxSlider;
    private CheckBox minEnabled;
    private CheckBox maxEnabled;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXIntegerField";
    }

    @Override
    protected String subtitle() {
        return "Integer-only numeric text field";
    }

    @Override
    protected String windowTitle() {
        return "RXIntegerField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-integer-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXIntegerField(25);
        field.setPromptText("Whole numbers only");
        field.setPrefColumnCount(14);

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> describe(field),
                field.valueProperty(), field.minProperty(), field.maxProperty()));

        VBox box = new VBox(16.0, field, readout);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Range", buildRangeGrid()),
                section("Overflow policy", buildPolicyGrid()),
                section("Decoration slots", buildSlotGrid()),
                section("Layout & state", buildLayoutGrid()));
    }

    // ==================== Sections ====================

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
                    + ". Whole numbers beyond 32-bit belong in RXDecimalField.");
        });

        return createGrid(row(overflow), row(result));
    }

    private Node buildSlotGrid() {
        ComboBox<SlotPreset> slotBox = new ComboBox<>();
        slotBox.getItems().addAll(SlotPreset.values());
        slotBox.setValue(SlotPreset.NONE);
        slotBox.setMaxWidth(Double.MAX_VALUE);
        slotBox.valueProperty().addListener((obs, oldV, newV) -> applySlots(newV));

        return createGrid(row("Slot preset", slotBox));
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

    private void applySlots(SlotPreset preset) {
        switch (preset) {
            case NONE -> {
                field.setLeading(null);
                field.setTrailing(null);
            }
            case DECORATION -> {
                field.setLeading(slotLabel("#", "slot-badge"));
                field.setTrailing(slotLabel("pcs", "slot-unit"));
            }
            case STEPPER -> {
                field.setLeading(stepButton("−", -1));
                field.setTrailing(stepButton("+", 1));
            }
        }
    }

    private void step(int delta) {
        field.commitValue();
        int current = field.getValue() == null ? 0 : field.getValue();
        field.setValue(current + delta);
    }

    private Node stepButton(String text, int delta) {
        Button button = new Button(text);
        button.getStyleClass().add("slot-button");
        button.setFocusTraversable(false);
        button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        button.setOnAction(e -> step(delta));

        StackPane box = new StackPane(button);
        box.getStyleClass().add("slot-button-box");
        box.setMaxHeight(Double.MAX_VALUE);
        return box;
    }

    private static Label slotLabel(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static String describe(RXIntegerField field) {
        return "value = " + field.getValue()
                + "\nmin = " + bound(field.getMin(), Integer.MIN_VALUE)
                + "      max = " + bound(field.getMax(), Integer.MAX_VALUE);
    }

    private static String bound(int value, int unboundedSentinel) {
        return value == unboundedSentinel ? "unbounded" : Integer.toString(value);
    }

    // ==================== Slot preset ====================

    private enum SlotPreset {
        NONE("None"),
        DECORATION("Badge + unit"),
        STEPPER("± stepper buttons");

        private final String label;

        SlotPreset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
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

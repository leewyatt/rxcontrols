package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * Showcase application for {@link RXIntegerField}.
 *
 * <p>Exercises every public knob: the committed integer value (edited in the
 * preview field, which rejects the decimal point), the inclusive {@code min} /
 * {@code max} bounds, the inherited left / right decoration slots, text
 * padding, alignment, and the editable flag. A dedicated section demonstrates
 * the strict integer-domain policy — a fractional programmatic value is
 * rejected, while a whole value carrying a non-zero scale is normalized.
 *
 * <p>For a minimal "few lines of code" example see {@link RXIntegerFieldDemo}.
 */
public class RXIntegerFieldShowcase extends RXShowcaseApplication {

    private static final double BOUND_MIN = -100.0;
    private static final double BOUND_MAX = 100.0;
    private static final double MAX_TEXT_PADDING = 24.0;
    private static final BigDecimal STEP = BigDecimal.ONE;

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
        return getClass().getResource("rx_integer_field_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXIntegerField(new BigDecimal("25"));
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
                section("Integer policy", buildPolicyGrid()),
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
                + "min is kept <= max; dragging one past the other is rejected.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Min", minSlider, minValue),
                row("Max", maxSlider, maxValue),
                row(toggleRow),
                row(hint));
    }

    private Node buildPolicyGrid() {
        Label result = new Label("Press a button to test the integer-domain check.");
        result.getStyleClass().add("policy-result");
        result.setWrapText(true);

        Button fractional = new Button("setValue(3.14)");
        fractional.setOnAction(e -> {
            try {
                field.setValue(new BigDecimal("3.14"));
                result.setText("Unexpected: 3.14 was accepted.");
            } catch (IllegalArgumentException ex) {
                result.setText("Rejected — " + ex.getMessage());
            }
        });

        Button scaledWhole = new Button("setValue(3.0)");
        scaledWhole.setOnAction(e -> {
            field.setValue(new BigDecimal("3.0"));
            BigDecimal v = field.getValue();
            result.setText("Accepted — 3.0 normalized to " + v.toPlainString()
                    + " (scale " + v.scale() + ").");
        });

        HBox buttons = new HBox(8.0, fractional, scaledWhole);
        buttons.setAlignment(Pos.CENTER_LEFT);

        return createGrid(row(buttons), row(result));
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
        Slider paddingSlider = createSlider(0.0, MAX_TEXT_PADDING, 0.0);
        paddingSlider.valueProperty().addListener((obs, oldV, newV) ->
                field.setTextPadding(new Insets(0, newV.doubleValue(), 0, newV.doubleValue())));
        Label paddingValue = createValueLabel(paddingSlider, "%.0f px");

        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().addAll(Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT);
        alignmentBox.setValue(field.getAlignment());
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        field.alignmentProperty().bind(alignmentBox.valueProperty());

        CheckBox editableBox = new CheckBox("Editable");
        editableBox.selectedProperty().bindBidirectional(field.editableProperty());

        return createGrid(
                row("Text padding", paddingSlider, paddingValue),
                row("Alignment", alignmentBox),
                row(editableBox));
    }

    // ==================== Behaviour ====================

    private void applyMin() {
        applyBound(minEnabled, minSlider, true);
    }

    private void applyMax() {
        applyBound(maxEnabled, maxSlider, false);
    }

    private void applyBound(CheckBox enabled, Slider slider, boolean isMin) {
        BigDecimal value = enabled.isSelected()
                ? BigDecimal.valueOf(Math.round(slider.getValue()))
                : null;
        try {
            if (isMin) {
                field.setMin(value);
            } else {
                field.setMax(value);
            }
        } catch (IllegalArgumentException ignored) {
            // min must stay <= max; a violating bound is rejected and the
            // property keeps its previous value.
        }
    }

    private void applySlots(SlotPreset preset) {
        switch (preset) {
            case NONE -> {
                field.setLeft(null);
                field.setRight(null);
            }
            case DECORATION -> {
                field.setLeft(slotLabel("#", "slot-badge"));
                field.setRight(slotLabel("pcs", "slot-unit"));
            }
            case STEPPER -> {
                field.setLeft(stepButton("−", STEP.negate()));
                field.setRight(stepButton("+", STEP));
            }
        }
    }

    private void step(BigDecimal delta) {
        field.commitValue();
        BigDecimal current = field.getValue() == null ? BigDecimal.ZERO : field.getValue();
        field.setValue(current.add(delta));
    }

    private Node stepButton(String text, BigDecimal delta) {
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
        BigDecimal v = field.getValue();
        String value = (v == null) ? "null" : v.toPlainString();
        return "value = " + value
                + "\nmin = " + bound(field.getMin()) + "      max = " + bound(field.getMax());
    }

    private static String bound(BigDecimal value) {
        return value == null ? "unbounded" : value.toPlainString();
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

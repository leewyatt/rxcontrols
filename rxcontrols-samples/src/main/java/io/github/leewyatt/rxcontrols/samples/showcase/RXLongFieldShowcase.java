package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXLongField;
import io.github.leewyatt.rxcontrols.samples.demo.RXLongFieldDemo;
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
 * Showcase application for {@link RXLongField}.
 *
 * <p>Exercises the main public knobs: the committed {@link Long} value
 * (edited in the preview field, which rejects the decimal point), the
 * inclusive primitive {@code min} / {@code max} bounds with Slider-style
 * convergence, the inherited leading / trailing decoration slots, alignment,
 * and the editable flag (text padding is deliberately not showcased: a panel
 * slider would take USER origin and permanently disable the UA side-node
 * defaults). A dedicated section demonstrates the 64-bit policies — an
 * overflowing magnitude rolls the text back, while a value beyond the
 * double-safe 2^53 range commits exactly.
 *
 * <p>For a minimal "few lines of code" example see {@link RXLongFieldDemo}.
 */
public class RXLongFieldShowcase extends RXShowcaseApplication {

    private static final double BOUND_MIN = -100.0;
    private static final double BOUND_MAX = 100.0;

    private RXLongField field;
    private Slider minSlider;
    private Slider maxSlider;
    private CheckBox minEnabled;
    private CheckBox maxEnabled;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXLongField";
    }

    @Override
    protected String subtitle() {
        return "64-bit integer numeric text field";
    }

    @Override
    protected String windowTitle() {
        return "RXLongField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-long-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXLongField(25L);
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
                section("64-bit policy", buildPolicyGrid()),
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
                + "full long domain.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Min", minSlider, minValue),
                row("Max", maxSlider, maxValue),
                row(toggleRow),
                row(hint));
    }

    private Node buildPolicyGrid() {
        Label result = new Label("Press a button to test the 64-bit policies.");
        result.getStyleClass().add("policy-result");
        result.setWrapText(true);

        Button overflow = new Button("Commit 2^63");
        overflow.setOnAction(e -> {
            field.setText("9223372036854775808");   // Long.MAX_VALUE + 1
            field.commitValue();
            result.setText("Overflow rolled the text back; value = " + field.getValue()
                    + ". Whole numbers beyond 64-bit belong in RXDecimalField.");
        });

        Button exact = new Button("Commit 2^53 + 1");
        exact.setOnAction(e -> {
            field.setText("9007199254740993");      // beyond the double-safe range
            field.commitValue();
            result.setText("Committed exactly: " + field.getValue()
                    + " — a double would silently round this to ...992.");
        });

        HBox buttons = new HBox(8.0, overflow, exact);
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
                ? Math.round(minSlider.getValue())
                : Long.MIN_VALUE);
    }

    private void applyMax() {
        field.setMax(maxEnabled.isSelected()
                ? Math.round(maxSlider.getValue())
                : Long.MAX_VALUE);
    }

    private void applySlots(SlotPreset preset) {
        switch (preset) {
            case NONE -> {
                field.setLeading(null);
                field.setTrailing(null);
            }
            case DECORATION -> {
                field.setLeading(slotLabel("#", "slot-badge"));
                field.setTrailing(slotLabel("id", "slot-unit"));
            }
            case STEPPER -> {
                field.setLeading(stepButton("−", -1L));
                field.setTrailing(stepButton("+", 1L));
            }
        }
    }

    private void step(long delta) {
        field.commitValue();
        long current = field.getValue() == null ? 0L : field.getValue();
        long next;
        try {
            next = Math.addExact(current, delta);
        } catch (ArithmeticException overflow) {
            // Saturate at the domain edge; wrapping to the opposite sign in a
            // showcase that demonstrates 64-bit edge policy would be absurd.
            next = delta > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        field.setValue(next);
    }

    private Node stepButton(String text, long delta) {
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

    private static String describe(RXLongField field) {
        return "value = " + field.getValue()
                + "\nmin = " + bound(field.getMin(), Long.MIN_VALUE)
                + "      max = " + bound(field.getMax(), Long.MAX_VALUE);
    }

    private static String bound(long value, long unboundedSentinel) {
        return value == unboundedSentinel ? "unbounded" : Long.toString(value);
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

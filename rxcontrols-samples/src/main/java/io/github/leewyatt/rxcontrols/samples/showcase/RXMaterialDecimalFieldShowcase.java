package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMaterialDecimalField;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialDecimalFieldDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXMaterialDecimalField}.
 *
 * <p>Exercises the Material surface inherited from RXMaterialTextField
 * (floating label, helper / error supporting text, the invalid state, the
 * animation toggle, and the built-in clear button) together with the typed
 * knobs shared with the plain RXDecimalField: the committed {@link BigDecimal}
 * value, the {@code numberFormat} that drives both rendering and commit
 * parsing ({@code null} = plain {@code toPlainString}), and the inclusive
 * nullable min / max bounds with Slider-style convergence. Alignment and the
 * editable flag round out the layout section.
 *
 * <p>For a minimal "few lines of code" example see
 * {@link RXMaterialDecimalFieldDemo}.
 */
public class RXMaterialDecimalFieldShowcase extends RXShowcaseApplication {

    private static final double BOUND_MIN = -10000.0;
    private static final double BOUND_MAX = 10000.0;

    private RXMaterialDecimalField field;
    private Slider minSlider;
    private Slider maxSlider;
    private CheckBox minEnabled;
    private CheckBox maxEnabled;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMaterialDecimalField";
    }

    @Override
    protected String subtitle() {
        return "Material exact-decimal (BigDecimal) numeric field";
    }

    @Override
    protected String windowTitle() {
        return "RXMaterialDecimalField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-material-decimal-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXMaterialDecimalField(new BigDecimal("1234.50"));
        field.setLabelText("Amount");
        field.setHelperText("Exact decimal, money-safe");
        field.setPrefColumnCount(14);

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> describe(field),
                field.valueProperty(), field.minProperty(),
                field.maxProperty(), field.numberFormatProperty()));

        VBox box = new VBox(22.0, field, readout);
        box.getStyleClass().add("live-preview");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Material surface", buildSurfaceGrid()),
                section("Number format", buildFormatGrid()),
                section("Range", buildRangeGrid()),
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

    private Node buildFormatGrid() {
        ComboBox<FormatPreset> formatBox = new ComboBox<>();
        formatBox.getItems().addAll(FormatPreset.values());
        formatBox.setValue(FormatPreset.PLAIN);
        formatBox.setMaxWidth(Double.MAX_VALUE);
        formatBox.valueProperty().addListener((obs, oldV, newV) ->
                field.setNumberFormat(newV.create()));

        Label hint = new Label("The format drives display and commit parsing; "
                + "null renders plain toPlainString. The stored value keeps its "
                + "full precision across switches.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Format", formatBox),
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
                + "(Slider-style, min <= max). Disabling a bound resets it to "
                + "null (unbounded).");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Min", minSlider, minValue),
                row("Max", maxSlider, maxValue),
                row(toggleRow),
                row(hint));
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
        applyBound(minEnabled, minSlider, true);
    }

    private void applyMax() {
        applyBound(maxEnabled, maxSlider, false);
    }

    private void applyBound(CheckBox enabled, Slider slider, boolean isMin) {
        BigDecimal value = enabled.isSelected()
                ? BigDecimal.valueOf(Math.round(slider.getValue()))
                : null;
        if (isMin) {
            field.setMin(value);
        } else {
            field.setMax(value);
        }
    }

    private static String describe(RXMaterialDecimalField field) {
        BigDecimal v = field.getValue();
        String value = (v == null) ? "null" : v.toPlainString() + "  (scale " + v.scale() + ")";
        return "value = " + value
                + "\nmin = " + bound(field.getMin()) + "      max = " + bound(field.getMax())
                + "\nformat = " + describeFormat(field.getNumberFormat());
    }

    private static String bound(BigDecimal value) {
        return value == null ? "unbounded" : value.toPlainString();
    }

    private static String describeFormat(NumberFormat format) {
        if (format == null) {
            return "null (plain toPlainString)";
        }
        if (format instanceof DecimalFormat decimalFormat) {
            return "DecimalFormat \"" + decimalFormat.toPattern() + "\"";
        }
        return format.getClass().getSimpleName();
    }

    // ==================== Format preset ====================

    private enum FormatPreset {
        PLAIN("Plain (null format)") {
            @Override
            NumberFormat create() {
                return null;
            }
        },
        NUMBER("US grouping") {
            @Override
            NumberFormat create() {
                return NumberFormat.getNumberInstance(Locale.US);
            }
        },
        CURRENCY("US dollar currency") {
            @Override
            NumberFormat create() {
                return NumberFormat.getCurrencyInstance(Locale.US);
            }
        },
        PERCENT("Percent (note the multiplier)") {
            @Override
            NumberFormat create() {
                return NumberFormat.getPercentInstance(Locale.US);
            }
        };

        private final String label;

        FormatPreset(String label) {
            this.label = label;
        }

        abstract NumberFormat create();

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

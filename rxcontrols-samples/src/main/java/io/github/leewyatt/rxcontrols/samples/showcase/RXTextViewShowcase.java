package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXTextView}.
 *
 * <p>Exercises the editable source text, the selection API (select-all, deselect, select
 * a range, copy), the read-only selection-state readout, the {@code selectable} toggle,
 * text alignment, line spacing, preview width, and the CSS color hooks for the text, the
 * selection background, and the caret.</p>
 */
public class RXTextViewShowcase extends RXShowcaseApplication {

    private static final String DEFAULT_TEXT =
            "RXTextView is a non-editable, wrapping block of text the user can select "
                    + "and copy.\nDrag to select, double-click a word, triple-click a line, then "
                    + "press Ctrl or Cmd + C to copy.\nThe selection, caret and selected text are "
                    + "observable read-only properties.";
    private static final double DEFAULT_PREVIEW_WIDTH = 520.0;
    private static final int SELECTED_TEXT_ABBREVIATION_LIMIT = 24;
    private static final double SELECTION_FILL_ALPHA = 0.30;

    private RXTextView selectableText;
    private ColorPicker textFillPicker;
    private ColorPicker selectionFillPicker;
    private ColorPicker caretFillPicker;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTextView";
    }

    @Override
    protected String subtitle() {
        return "Selectable, copyable non-editable text";
    }

    @Override
    protected String windowTitle() {
        return "RXTextView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 680.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 430.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-text-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        selectableText = new RXTextView(DEFAULT_TEXT);
        selectableText.getStyleClass().add("showcase-text-view");
        selectableText.setLineSpacing(7.0);
        selectableText.setPrefWidth(DEFAULT_PREVIEW_WIDTH);
        selectableText.setMaxWidth(Region.USE_PREF_SIZE);

        Label status = new Label();
        status.getStyleClass().add("status-label");
        status.textProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.ROOT, "anchor %d  ·  caret %d  ·  selected \"%s\"",
                        selectableText.getAnchor(), selectableText.getCaretPosition(),
                        abbreviate(selectableText.getSelectedText())),
                selectableText.anchorProperty(), selectableText.caretPositionProperty(),
                selectableText.selectedTextProperty()));

        VBox preview = new VBox(14.0, selectableText, status);
        preview.getStyleClass().add("text-view-preview");
        preview.setAlignment(Pos.CENTER_LEFT);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Text", buildTextGrid()),
                section("Selection", buildSelectionGrid()),
                section("Layout", buildLayoutGrid()),
                section("Colors", buildColorGrid()));
    }

    // ==================== Sections ====================

    private Node buildTextGrid() {
        TextArea textArea = new TextArea(DEFAULT_TEXT);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(6);
        textArea.setMaxWidth(Double.MAX_VALUE);
        selectableText.textProperty().bind(textArea.textProperty());
        return createGrid(row("Source", textArea));
    }

    private Node buildSelectionGrid() {
        CheckBox selectableCheck = new CheckBox("selectable");
        selectableCheck.setSelected(true);
        selectableText.selectableProperty().bind(selectableCheck.selectedProperty());

        Button selectAll = new Button("Select All");
        selectAll.setOnAction(event -> selectableText.selectAll());
        Button selectRange = new Button("Select [0, 16)");
        selectRange.setOnAction(event -> selectableText.selectRange(0, 16));
        Button deselect = new Button("Deselect");
        deselect.setOnAction(event -> selectableText.deselect());
        Button copy = new Button("Copy");
        copy.setOnAction(event -> selectableText.copy());
        FlowPane buttons = new FlowPane(8.0, 8.0, selectAll, selectRange, deselect, copy);

        Label selectedLength = new Label();
        selectedLength.getStyleClass().add("value-label");
        selectedLength.textProperty().bind(Bindings.createStringBinding(
                () -> selectableText.getSelectedText().length() + " chars",
                selectableText.selectedTextProperty()));

        return createGrid(
                row(selectableCheck),
                row(buttons),
                row("Selected", selectedLength));
    }

    private Node buildLayoutGrid() {
        ComboBox<TextAlignment> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(TextAlignment.values());
        alignmentBox.setValue(TextAlignment.LEFT);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        selectableText.textAlignmentProperty().bind(alignmentBox.valueProperty());

        Slider spacingSlider = createSlider(0.0, 24.0, selectableText.getLineSpacing());
        selectableText.lineSpacingProperty().bind(spacingSlider.valueProperty());

        Slider widthSlider = createSlider(300.0, 680.0, DEFAULT_PREVIEW_WIDTH);
        selectableText.prefWidthProperty().bind(widthSlider.valueProperty());

        return createGrid(
                row("Alignment", alignmentBox),
                row("Line spacing", spacingSlider, createValueLabel(spacingSlider, "%.0f px")),
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")));
    }

    private Node buildColorGrid() {
        Color defaultInk = Color.web("#1b1f2a");
        textFillPicker = createColorPicker(defaultInk);
        selectionFillPicker = createColorPicker(Color.web("#0078d7"));
        caretFillPicker = createColorPicker(defaultInk);
        textFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateColors());
        selectionFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateColors());
        caretFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateColors());
        updateColors();
        return createGrid(
                row("Text", textFillPicker),
                row("Selection", selectionFillPicker),
                row("Caret", caretFillPicker));
    }

    // ==================== Helpers ====================

    private ColorPicker createColorPicker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private void updateColors() {
        if (selectableText == null) {
            return;
        }
        // Selection fill kept semi-transparent so the selected glyphs stay readable.
        selectableText.setStyle(String.format(Locale.ROOT,
                "-rx-text-fill: %s; -rx-selection-fill: %s; -rx-caret-fill: %s;",
                toCssColor(textFillPicker.getValue(), 1.0),
                toCssColor(selectionFillPicker.getValue(), SELECTION_FILL_ALPHA),
                toCssColor(caretFillPicker.getValue(), 1.0)));
    }

    private String toCssColor(Color color, double alpha) {
        int red = (int) Math.round(color.getRed() * 255.0);
        int green = (int) Math.round(color.getGreen() * 255.0);
        int blue = (int) Math.round(color.getBlue() * 255.0);
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", red, green, blue, alpha);
    }

    private String abbreviate(String text) {
        String oneLine = text.replace('\n', ' ');
        if (oneLine.length() <= SELECTED_TEXT_ABBREVIATION_LIMIT) {
            return oneLine;
        }
        return oneLine.substring(0, SELECTED_TEXT_ABBREVIATION_LIMIT) + "…";
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

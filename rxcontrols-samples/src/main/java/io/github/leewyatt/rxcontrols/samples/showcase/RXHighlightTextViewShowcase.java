package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXHighlightTextView.MatchRules;
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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXHighlightTextView}.
 *
 * <p>Exercises editable source text, keyword lists, literal / regex matching,
 * matched state reporting, text alignment, line spacing, preview width, and
 * the CSS color hooks for highlighted and plain runs.</p>
 */
public class RXHighlightTextViewShowcase extends RXShowcaseApplication {

    private static final TextPreset DEFAULT_TEXT = TextPreset.ARTICLE;
    private static final KeywordPreset DEFAULT_KEYWORDS = KeywordPreset.LITERAL_WORDS;
    private static final double DEFAULT_PREVIEW_WIDTH = 520.0;
    private static final double SELECTION_FILL_ALPHA = 0.30;

    private RXHighlightTextView highlightTextView;
    private ColorPicker highlightColorPicker;
    private ColorPicker highlightFillPicker;
    private ColorPicker plainFillPicker;
    private ColorPicker selectionFillPicker;
    private ColorPicker caretFillPicker;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXHighlightTextView";
    }

    @Override
    protected String subtitle() {
        return "Keyword highlighting for non-editable rich text";
    }

    @Override
    protected String windowTitle() {
        return "RXHighlightTextView Showcase";
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
        return getClass().getResource("rx-highlight-text-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        highlightTextView = new RXHighlightTextView(DEFAULT_TEXT.text());
        highlightTextView.getStyleClass().add("showcase-highlight-text-view");
        highlightTextView.getKeywords().setAll(parseKeywordLines(DEFAULT_KEYWORDS.keywords()));
        highlightTextView.setMatchRules(RXHighlightTextView.DEFAULT_MATCH_RULES);
        highlightTextView.setLineSpacing(7.0);
        highlightTextView.setPrefWidth(DEFAULT_PREVIEW_WIDTH);
        highlightTextView.setMaxWidth(Region.USE_PREF_SIZE);

        Label statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");
        statusLabel.textProperty().bind(Bindings.when(highlightTextView.matchedProperty())
                .then("Matched").otherwise("No match"));

        VBox preview = new VBox(14.0, highlightTextView, statusLabel);
        preview.getStyleClass().add("highlight-text-view-preview");
        preview.setAlignment(Pos.CENTER_LEFT);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Text", buildTextGrid()),
                section("Keywords", buildKeywordGrid()),
                section("Matching", buildMatchingGrid()),
                section("Selection", buildSelectionGrid()),
                section("Layout", buildLayoutGrid()),
                section("Colors", buildColorGrid()));
    }

    // ==================== Sections ====================

    private Node buildTextGrid() {
        TextArea textArea = new TextArea(DEFAULT_TEXT.text());
        textArea.setWrapText(true);
        textArea.setPrefRowCount(7);
        textArea.setMaxWidth(Double.MAX_VALUE);
        highlightTextView.textProperty().bind(textArea.textProperty());

        ComboBox<TextPreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(TextPreset.values());
        presetBox.setValue(DEFAULT_TEXT);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                textArea.setText(newValue.text());
            }
        });

        return createGrid(
                row("Preset", presetBox),
                row("Source", textArea));
    }

    private Node buildKeywordGrid() {
        TextArea keywordArea = new TextArea(DEFAULT_KEYWORDS.keywords());
        keywordArea.setWrapText(false);
        keywordArea.setPrefRowCount(5);
        keywordArea.setMaxWidth(Double.MAX_VALUE);
        keywordArea.textProperty().addListener((obs, oldValue, newValue) ->
                highlightTextView.getKeywords().setAll(parseKeywordLines(newValue)));

        ComboBox<KeywordPreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(KeywordPreset.values());
        presetBox.setValue(DEFAULT_KEYWORDS);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                keywordArea.setText(newValue.keywords());
            }
        });

        return createGrid(
                row("Preset", presetBox),
                row("One per line", keywordArea));
    }

    private Node buildMatchingGrid() {
        ComboBox<MatchRules> rulesBox = new ComboBox<>();
        rulesBox.getItems().setAll(MatchRules.values());
        rulesBox.setValue(RXHighlightTextView.DEFAULT_MATCH_RULES);
        rulesBox.setMaxWidth(Double.MAX_VALUE);
        highlightTextView.matchRulesProperty().bind(rulesBox.valueProperty());

        Label matchedLabel = new Label();
        matchedLabel.getStyleClass().add("matched-label");
        matchedLabel.textProperty().bind(Bindings.when(highlightTextView.matchedProperty())
                .then("true").otherwise("false"));

        return createGrid(
                row("Rules", rulesBox),
                row("matched", matchedLabel));
    }

    private Node buildSelectionGrid() {
        CheckBox selectableCheck = new CheckBox("selectable");
        selectableCheck.setSelected(true);
        highlightTextView.selectableProperty().bind(selectableCheck.selectedProperty());

        Button selectAll = new Button("Select All");
        selectAll.setOnAction(event -> highlightTextView.selectAll());
        Button selectRange = new Button("Select [0, 16)");
        selectRange.setOnAction(event -> highlightTextView.selectRange(0, 16));
        Button deselect = new Button("Deselect");
        deselect.setOnAction(event -> highlightTextView.deselect());
        Button copy = new Button("Copy");
        copy.setOnAction(event -> highlightTextView.copy());
        FlowPane buttons = new FlowPane(8.0, 8.0, selectAll, selectRange, deselect, copy);

        Label selectionInfo = new Label();
        selectionInfo.getStyleClass().add("matched-label");
        selectionInfo.textProperty().bind(Bindings.createStringBinding(
                () -> String.format(Locale.ROOT, "anchor %d  caret %d  (%d chars)",
                        highlightTextView.getAnchor(), highlightTextView.getCaretPosition(),
                        highlightTextView.getSelectedText().length()),
                highlightTextView.anchorProperty(), highlightTextView.caretPositionProperty(),
                highlightTextView.selectedTextProperty()));

        return createGrid(
                row(selectableCheck),
                row(buttons),
                row("Selection", selectionInfo));
    }

    private Node buildLayoutGrid() {
        ComboBox<TextAlignment> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(TextAlignment.values());
        alignmentBox.setValue(TextAlignment.LEFT);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        highlightTextView.textAlignmentProperty().bind(alignmentBox.valueProperty());

        Slider spacingSlider = createSlider(0.0, 24.0, highlightTextView.getLineSpacing());
        highlightTextView.lineSpacingProperty().bind(spacingSlider.valueProperty());

        Slider widthSlider = createSlider(300.0, 680.0, DEFAULT_PREVIEW_WIDTH);
        highlightTextView.prefWidthProperty().bind(widthSlider.valueProperty());

        return createGrid(
                row("Alignment", alignmentBox),
                row("Line spacing", spacingSlider, createValueLabel(spacingSlider, "%.0f px")),
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")));
    }

    private Node buildColorGrid() {
        Color defaultInk = Color.web("#1b1f2a");
        highlightColorPicker = createColorPicker(Color.web("#fff1a8"));
        highlightFillPicker = createColorPicker(defaultInk);
        plainFillPicker = createColorPicker(Color.web("#344054"));
        selectionFillPicker = createColorPicker(Color.web("#0078d7"));
        caretFillPicker = createColorPicker(defaultInk);
        highlightColorPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHighlightStyle());
        highlightFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHighlightStyle());
        plainFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHighlightStyle());
        selectionFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHighlightStyle());
        caretFillPicker.valueProperty().addListener((obs, oldValue, newValue) -> updateHighlightStyle());
        updateHighlightStyle();

        return createGrid(
                row("Highlight", highlightColorPicker),
                row("Highlight text", highlightFillPicker),
                row("Plain text", plainFillPicker),
                row("Selection", selectionFillPicker),
                row("Caret", caretFillPicker));
    }

    // ==================== Helpers ====================

    private ColorPicker createColorPicker(Color color) {
        ColorPicker picker = new ColorPicker(color);
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private void updateHighlightStyle() {
        if (highlightTextView == null || highlightColorPicker == null || highlightFillPicker == null
                || plainFillPicker == null || selectionFillPicker == null || caretFillPicker == null) {
            return;
        }
        highlightTextView.setStyle(String.format(Locale.ROOT,
                "-rx-highlight-fill: %s; -rx-highlight-text-fill: %s; -rx-text-fill: %s; "
                        + "-rx-selection-fill: %s; -rx-caret-fill: %s;",
                toCssColor(highlightColorPicker.getValue()),
                toCssColor(highlightFillPicker.getValue()),
                toCssColor(plainFillPicker.getValue()),
                toCssColorAlpha(selectionFillPicker.getValue(), SELECTION_FILL_ALPHA),
                toCssColor(caretFillPicker.getValue())));
    }

    private String toCssColor(Color color) {
        return toCssColorAlpha(color, color.getOpacity());
    }

    private String toCssColorAlpha(Color color, double alpha) {
        int red = (int) Math.round(color.getRed() * 255.0);
        int green = (int) Math.round(color.getGreen() * 255.0);
        int blue = (int) Math.round(color.getBlue() * 255.0);
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", red, green, blue, alpha);
    }

    private List<String> parseKeywordLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(keyword -> !keyword.isEmpty())
                .toList();
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    private enum TextPreset {
        ARTICLE("Article", """
                JavaFX is a modern UI toolkit for desktop and rich client applications.
                It includes controls, CSS styling, property binding, scene graph rendering,
                and a skin architecture for building reusable user interface components.
                RXHighlightTextView highlights literal words or regular expression matches
                while preserving the surrounding text flow."""),

        RELEASE_NOTES("Release Notes", """
                Version 2.4 improves keyboard handling, CSS metadata, and skin disposal.
                Search for CSS, skin, binding, or version numbers such as 2.4 and 17.
                Invalid regular expressions are ignored so the remaining keywords still work."""),

        CODE_REVIEW("Code Review", """
                Review focus:
                - property setters stay pass-through
                - Skin owns rendering and listeners
                - CSS substructure names remain short
                - tests cover empty keywords, invalid regex, and overlapping matches""");

        private final String displayName;
        private final String text;

        TextPreset(String displayName, String text) {
            this.displayName = displayName;
            this.text = text;
        }

        private String text() {
            return text;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private enum KeywordPreset {
        LITERAL_WORDS("Literal Words", """
                JavaFX
                CSS
                skin
                binding"""),

        CASE_CHECK("Case Check", """
                javafx
                css
                Skin"""),

        REGEX_PATTERNS("Regex Patterns", """
                \\b[A-Z]{2,}\\b
                \\d+(?:\\.\\d+)?
                property|keywords|matches"""),

        OVERLAP("Overlap", """
                property
                setters
                property setters
                skin"""),

        INVALID_REGEX("Invalid Regex", """
                [A-Z
                JavaFX
                \\d+""");

        private final String displayName;
        private final String keywords;

        KeywordPreset(String displayName, String keywords) {
            this.displayName = displayName;
            this.keywords = keywords;
        }

        private String keywords() {
            return keywords;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}

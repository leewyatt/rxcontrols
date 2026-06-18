package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXSegmentedControl}.
 *
 * <p>Exercises the sliding indicator animation, the three sizing modes (content
 * width / equal width / block) with segment spacing, the selection contract
 * (allow-empty, whole-control and per-segment disabled), dynamic item editing,
 * the text / text+icon / custom-content / long-text content variants, the
 * CSS-driven height (font size), and a theme override.
 */
public class RXSegmentedControlShowcase extends RXShowcaseApplication {

    private static final String TYPE_TEXT = "Text";
    private static final String TYPE_ICON = "Text + icon";
    private static final String TYPE_CUSTOM = "Custom content";
    private static final String TYPE_LONG = "Long labels";

    private RXSegmentedControl<String> segmented;
    private Label valueLabel;
    private CheckBox disableSegmentBox;
    private int extraCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSegmentedControl";
    }

    @Override
    protected String subtitle() {
        return "One-of-many selector with a sliding indicator";
    }

    @Override
    protected String windowTitle() {
        return "RXSegmentedControl Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-segmented-control-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        segmented = new RXSegmentedControl<>();
        segmented.getItems().setAll(textItems());

        valueLabel = new Label();
        valueLabel.getStyleClass().add("value-readout");
        valueLabel.textProperty().bind(segmented.valueProperty().asString("value = %s"));

        Label hint = new Label("Click a segment; the pill slides and stretches between them.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(18.0, segmented, valueLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        preview.setMaxWidth(Double.MAX_VALUE);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Layout", buildLayoutGrid()),
                section("Animation", buildAnimationGrid()),
                section("Selection", buildSelectionGrid()),
                section("Items", buildItemsGrid()),
                section("Appearance", buildAppearanceGrid()));
    }

    // ==================== Sections ====================

    private Node buildLayoutGrid() {
        CheckBox blockBox = new CheckBox();
        segmented.blockProperty().bind(blockBox.selectedProperty());

        CheckBox equalBox = new CheckBox();
        segmented.equalSegmentWidthProperty().bind(equalBox.selectedProperty());

        Slider spacingSlider = createSlider(0.0, 24.0, 0.0);
        segmented.segmentSpacingProperty().bind(spacingSlider.valueProperty());

        return createGrid(
                row("Block (fill)", blockBox),
                row("Equal width", equalBox),
                row("Spacing", spacingSlider, createValueLabel(spacingSlider, "%.0f px")));
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox();
        animatedBox.setSelected(segmented.isAnimated());
        segmented.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider = createSlider(0.0, 600.0, segmented.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                segmented.setAnimationDuration(Duration.millis(newV.doubleValue())));

        return createGrid(
                row("Animated", animatedBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildSelectionGrid() {
        CheckBox emptyBox = new CheckBox();
        segmented.allowEmptySelectionProperty().bind(emptyBox.selectedProperty());

        CheckBox disableControlBox = new CheckBox();
        segmented.disableProperty().bind(disableControlBox.selectedProperty());

        disableSegmentBox = new CheckBox();
        disableSegmentBox.selectedProperty().addListener((obs, oldV, newV) -> applySegmentDisable());

        return createGrid(
                row("Allow empty", emptyBox),
                row("Disable control", disableControlBox),
                row("Disable 3rd seg", disableSegmentBox));
    }

    private Node buildItemsGrid() {
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().setAll(TYPE_TEXT, TYPE_ICON, TYPE_CUSTOM, TYPE_LONG);
        typeBox.setValue(TYPE_TEXT);
        typeBox.setMaxWidth(Double.MAX_VALUE);
        typeBox.valueProperty().addListener((obs, oldV, newV) -> applyContentType(newV));

        Button addButton = new Button("Add");
        addButton.setMaxWidth(Double.MAX_VALUE);
        addButton.setOnAction(event -> {
            extraCount++;
            segmented.getItems().add(RXSegmentedItem.of("extra-" + extraCount, "Extra " + extraCount));
        });

        Button removeButton = new Button("Remove last");
        removeButton.setMaxWidth(Double.MAX_VALUE);
        removeButton.setOnAction(event -> {
            List<RXSegmentedItem<String>> items = segmented.getItems();
            if (!items.isEmpty()) {
                items.remove(items.size() - 1);
            }
        });

        Button clearButton = new Button("Clear");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(event -> segmented.getItems().clear());

        return createGrid(
                row("Content", typeBox),
                row(new HBox(8.0, addButton, removeButton, clearButton)));
    }

    private Node buildAppearanceGrid() {
        Slider fontSlider = createSlider(11.0, 28.0, 13.0);
        fontSlider.valueProperty().addListener((obs, oldV, newV) -> segmented.setStyle(
                String.format(Locale.ROOT, "-fx-font-size: %.0fpx;", newV.doubleValue())));

        CheckBox themeBox = new CheckBox();
        themeBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                segmented.getStyleClass().add("ocean");
            } else {
                segmented.getStyleClass().remove("ocean");
            }
        });

        return createGrid(
                row("Font size", fontSlider, createValueLabel(fontSlider, "%.0f px")),
                row("Ocean theme", themeBox));
    }

    // ==================== Item building ====================

    private void applyContentType(String type) {
        switch (type) {
            case TYPE_ICON:
                segmented.getItems().setAll(iconItems());
                break;
            case TYPE_CUSTOM:
                segmented.getItems().setAll(customItems());
                break;
            case TYPE_LONG:
                segmented.getItems().setAll(longItems());
                break;
            default:
                segmented.getItems().setAll(textItems());
        }
        applySegmentDisable();
    }

    private void applySegmentDisable() {
        if (disableSegmentBox != null && segmented.getItems().size() > 2) {
            segmented.getItems().get(2).setDisable(disableSegmentBox.isSelected());
        }
    }

    private List<RXSegmentedItem<String>> textItems() {
        return new ArrayList<>(List.of(
                RXSegmentedItem.of("daily", "Daily"),
                RXSegmentedItem.of("weekly", "Weekly"),
                RXSegmentedItem.of("monthly", "Monthly"),
                RXSegmentedItem.of("quarterly", "Quarterly"),
                RXSegmentedItem.of("yearly", "Yearly")));
    }

    private List<RXSegmentedItem<String>> iconItems() {
        List<RXSegmentedItem<String>> items = new ArrayList<>();
        items.add(RXSegmentedItem.of("list", "List", icon()));
        items.add(RXSegmentedItem.of("grid", "Grid", icon()));
        items.add(RXSegmentedItem.of("board", "Board", icon()));
        return items;
    }

    private List<RXSegmentedItem<String>> customItems() {
        List<RXSegmentedItem<String>> items = new ArrayList<>();
        items.add(customItem("low", "Low", "#2ecc71"));
        items.add(customItem("medium", "Medium", "#f39c12"));
        items.add(customItem("high", "High", "#e74c3c"));
        return items;
    }

    private List<RXSegmentedItem<String>> longItems() {
        return new ArrayList<>(List.of(
                RXSegmentedItem.of("a", "Short"),
                RXSegmentedItem.of("b", "A considerably longer option label"),
                RXSegmentedItem.of("c", "Medium length")));
    }

    private Region icon() {
        Region icon = new Region();
        icon.getStyleClass().add("seg-icon");
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMouseTransparent(true);
        return icon;
    }

    private RXSegmentedItem<String> customItem(String value, String text, String color) {
        Region dot = new Region();
        dot.getStyleClass().add("seg-dot");
        dot.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        dot.setStyle("-fx-background-color: " + color + ";");
        Label label = new Label(text);
        HBox content = new HBox(6.0, dot, label);
        content.setAlignment(Pos.CENTER);
        content.setMouseTransparent(true);
        RXSegmentedItem<String> item = new RXSegmentedItem<>(value, text);
        item.setContent(content);
        return item;
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

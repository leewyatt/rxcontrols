package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.RXTileCell;
import io.github.leewyatt.rxcontrols.RXTileSection;
import io.github.leewyatt.rxcontrols.RXTileSectionCell;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXTileVisibleRange;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase for {@link RXTileView}. Renders a virtualized wall of {@value
 * #ITEM_COUNT} colored tiles, grouped into sections, and exposes every V1 knob —
 * cell size, spacing, max columns, layout, section grouping and header
 * height, selection mode and reorder animation — plus scroll-to-item and
 * scroll-to-section controls and a live readout of the resolved column count,
 * row count, visible item range and top section, so virtualization, grouping and
 * the reorder glide can be exercised at scale.
 */
public class RXTileViewShowcase extends RXShowcaseApplication {

    private static final int ITEM_COUNT = 10_000;
    private static final String CELL_STYLE = "-fx-text-fill: white; -fx-font-weight: bold;";

    private RXTileView<Integer> tile;
    private ObservableList<Integer> items;

    @Override
    protected String title() {
        return "RXTileView";
    }

    @Override
    protected String subtitle() {
        return ITEM_COUNT + " virtualized, grouped tiles — change the columns to see them glide";
    }

    @Override
    protected Node createPreview() {
        items = FXCollections.observableArrayList();
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(i);
        }

        tile = new RXTileView<>(items);
        tile.setCellFactory(view -> new TileCell());
        tile.setSectionHeaderFactory(view -> new SectionHeader());
        applyGrouping("By 500s");
        logSelectionChanges();

        Label placeholder = new Label("No items");
        placeholder.getStyleClass().add("tile-placeholder");
        tile.setPlaceholder(placeholder);
        return tile;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Cell size", cellSizeGrid()),
                section("Spacing", spacingGrid()),
                section("Columns", columnsGrid()),
                section("Layout", layoutGrid()),
                section("Sections", sectionsGrid()),
                section("Selection", selectionGrid()),
                section("Animation", animationGrid()),
                section("Scroll", scrollGrid()),
                section("Metrics", metricsGrid()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-tile-view-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node cellSizeGrid() {
        Slider width = createSlider(40, 280, tile.getCellWidth());
        width.valueProperty().addListener((obs, old, value) -> tile.setCellWidth(value.doubleValue()));
        Slider height = createSlider(40, 280, tile.getCellHeight());
        height.valueProperty().addListener((obs, old, value) -> tile.setCellHeight(value.doubleValue()));
        return createGrid(
                row("Width", width, createValueLabel(width, "%.0f px")),
                row("Height", height, createValueLabel(height, "%.0f px")));
    }

    private Node spacingGrid() {
        Slider hgap = createSlider(0, 40, tile.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> tile.setHgap(value.doubleValue()));
        Slider vgap = createSlider(0, 40, tile.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> tile.setVgap(value.doubleValue()));
        return createGrid(
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")));
    }

    private Node columnsGrid() {
        Slider maxColumns = intSlider(0, 12, tile.getMaxColumns());
        maxColumns.valueProperty().addListener((obs, old, value) -> tile.setMaxColumns(value.intValue()));
        return createGrid(
                row("Max", maxColumns, sentinelLabel(maxColumns, "none")));
    }

    private Node layoutGrid() {
        // maxCellWidth is a low-frequency, STRETCH-only cap (kept in the API / docs);
        // it is intentionally not shown here so it does not read as a core layout knob.
        ChoiceBox<ItemsJustify> justify = new ChoiceBox<>(
                FXCollections.observableArrayList(ItemsJustify.values()));
        justify.setValue(tile.getItemsJustify());
        justify.valueProperty().addListener((obs, old, value) -> tile.setItemsJustify(value));

        return createGrid(
                row("Justify", justify));
    }

    private Node sectionsGrid() {
        ChoiceBox<String> grouping = new ChoiceBox<>(
                FXCollections.observableArrayList("None", "By 500s", "By 50s"));
        grouping.setValue("By 500s");
        grouping.valueProperty().addListener((obs, old, value) -> applyGrouping(value));

        Slider headerHeight = createSlider(0, 64, tile.getSectionHeaderHeight());
        headerHeight.valueProperty().addListener(
                (obs, old, value) -> tile.setSectionHeaderHeight(value.doubleValue()));

        CheckBox show = new CheckBox("Show section headers");
        show.setSelected(tile.isShowSectionHeaders());
        show.selectedProperty().addListener((obs, old, value) -> tile.setShowSectionHeaders(value));

        return createGrid(
                row("Grouping", grouping),
                row("Header H", headerHeight, createValueLabel(headerHeight, "%.0f px")),
                row(show));
    }

    private void applyGrouping(String mode) {
        switch (mode) {
            case "By 500s" -> tile.setSectionKeyFactory(index -> index / 500);
            case "By 50s" -> tile.setSectionKeyFactory(index -> index / 50);
            default -> tile.setSectionKeyFactory(null);
        }
    }

    private Node selectionGrid() {
        ChoiceBox<SelectionMode> mode = new ChoiceBox<>(
                FXCollections.observableArrayList(SelectionMode.SINGLE, SelectionMode.MULTIPLE));
        mode.setValue(tile.getSelectionModel().getSelectionMode());
        mode.valueProperty().addListener((obs, old, value) -> tile.getSelectionModel().setSelectionMode(value));
        return createGrid(
                row("Mode", mode),
                row(hint("Click, arrow-navigate, Shift/Ctrl-extend; Enter or double-click activates.")));
    }

    private Node animationGrid() {
        CheckBox animated = new CheckBox("Animate reorder");
        animated.setSelected(tile.isAnimated());
        animated.selectedProperty().addListener((obs, old, value) -> tile.setAnimated(value));

        Slider duration = createSlider(0, 600, tile.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> tile.setAnimationDuration(Duration.millis(value.doubleValue())));

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node scrollGrid() {
        TextField index = new TextField("5000");
        index.setPrefColumnCount(6);
        ChoiceBox<ScrollAlignment> alignment = new ChoiceBox<>(
                FXCollections.observableArrayList(ScrollAlignment.values()));
        alignment.setValue(ScrollAlignment.START);
        Button goItem = new Button("Scroll to item");
        goItem.setOnAction(e -> scrollToItem(index.getText(), alignment.getValue()));
        HBox itemBox = new HBox(8.0, index, alignment, goItem);
        itemBox.setAlignment(Pos.CENTER_LEFT);

        TextField sectionKey = new TextField("7");
        sectionKey.setPrefColumnCount(4);
        Button goSection = new Button("Scroll to section");
        goSection.setOnAction(e -> scrollToSection(sectionKey.getText()));
        HBox sectionBox = new HBox(8.0, new Label("key"), sectionKey, goSection);
        sectionBox.setAlignment(Pos.CENTER_LEFT);

        return createGrid(row(itemBox), row(sectionBox));
    }

    private void scrollToItem(String text, ScrollAlignment alignment) {
        try {
            tile.scrollTo(Integer.parseInt(text.trim()), alignment);
        } catch (NumberFormatException ignored) {
            // Leave an unparseable index alone rather than disrupting the view.
        }
    }

    private void scrollToSection(String text) {
        try {
            tile.scrollToSection(Integer.parseInt(text.trim()));
        } catch (NumberFormatException ignored) {
            // Leave an unparseable key alone.
        }
    }

    private Node metricsGrid() {
        Label columns = new Label();
        columns.textProperty().bind(tile.actualColumnCountProperty().asString());
        Label rows = new Label();
        rows.textProperty().bind(tile.rowCountProperty().asString());
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(
                () -> describe(tile.getVisibleRange()), tile.visibleRangeProperty()));
        range.setWrapText(true);
        Label section = new Label();
        section.textProperty().bind(Bindings.createStringBinding(
                () -> describeSection(tile.getVisibleSection()), tile.visibleSectionProperty()));
        section.setWrapText(true);
        return createGrid(
                row("Columns", columns),
                row("Rows", rows),
                row("Visible", range),
                row("Top section", section));
    }

    private static String describe(RXTileVisibleRange range) {
        if (range == null || range.isEmpty()) {
            return "—";
        }
        return "items " + range.firstIndex() + ".." + range.lastIndex()
                + "  (rows " + range.firstRow() + ".." + range.lastRow() + ")";
    }

    private static String describeSection(RXTileSection section) {
        if (section == null) {
            return "—";
        }
        return "Group " + section.key() + "  (" + section.itemCount() + " items)";
    }

    private void logSelectionChanges() {
        tile.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change ->
                System.out.println("RXTileView selected indices: "
                        + List.copyOf(tile.getSelectionModel().getSelectedIndices())));
    }

    // ==================== Helpers ====================

    private Slider intSlider(int min, int max, int value) {
        Slider slider = createSlider(min, max, value);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);
        return slider;
    }

    private Label sentinelLabel(Slider slider, String zeroText) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.textProperty().bind(Bindings.createStringBinding(() -> {
            int v = (int) Math.round(slider.getValue());
            return v <= 0 ? zeroText : Integer.toString(v);
        }, slider.valueProperty()));
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    /**
     * A tile cell that paints a rounded color swatch and shows its index.
     */
    private static final class TileCell extends RXTileCell<Integer> {

        private TileCell() {
            setAlignment(Pos.CENTER);
            setStyle(CELL_STYLE);
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(CELL_STYLE);
            } else {
                setText(Integer.toString(item));
                double hue = (item * 23) % 360;
                // Round the border radius too so the CSS focus / selection ring follows
                // the tile's rounded edge instead of drawing a sharp rectangle.
                setStyle(CELL_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 80%);"
                        + " -fx-background-radius: 8; -fx-border-radius: 8;");
            }
        }
    }

    /**
     * A section header that shows the group key and its item count.
     */
    private static final class SectionHeader extends RXTileSectionCell {

        @Override
        protected void updateItem(RXTileSection section, boolean empty) {
            super.updateItem(section, empty);
            setText(empty || section == null
                    ? null
                    : "Group " + section.key() + "  ·  " + section.itemCount() + " items");
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

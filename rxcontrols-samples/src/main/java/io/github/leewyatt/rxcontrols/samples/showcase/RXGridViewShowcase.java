package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXGridCell;
import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
import io.github.leewyatt.rxcontrols.RXGridView;
import io.github.leewyatt.rxcontrols.RXGridVisibleRange;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * Showcase for {@link RXGridView}. Renders a virtualized wall of {@value
 * #ITEM_COUNT} colored tiles and exposes every V1 knob — cell size, spacing,
 * forced / max columns, stretch, justification and the placeholder — plus a
 * scroll-to control and a live readout of the resolved column count, row count
 * and visible range, so the virtualization and layout can be exercised at scale.
 */
public class RXGridViewShowcase extends RXShowcaseApplication {

    private static final int ITEM_COUNT = 10_000;

    private RXGridView<Integer> grid;
    private ObservableList<Integer> items;

    @Override
    protected String title() {
        return "RXGridView";
    }

    @Override
    protected String subtitle() {
        return ITEM_COUNT + " virtualized tiles — only visible rows hold live cells";
    }

    @Override
    protected Node createPreview() {
        items = FXCollections.observableArrayList();
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(i);
        }

        grid = new RXGridView<>(items);
        grid.setCellFactory(view -> new TileCell());

        Label placeholder = new Label("No items");
        placeholder.getStyleClass().add("grid-placeholder");
        grid.setPlaceholder(placeholder);
        return grid;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Cell size", cellSizeGrid()),
                section("Spacing", spacingGrid()),
                section("Columns", columnsGrid()),
                section("Layout", layoutGrid()),
                section("Data", dataGrid()),
                section("Scroll", scrollGrid()),
                section("Metrics", metricsGrid()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-grid-view-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node cellSizeGrid() {
        Slider width = createSlider(40, 280, grid.getCellWidth());
        width.valueProperty().addListener((obs, old, value) -> grid.setCellWidth(value.doubleValue()));
        Slider height = createSlider(40, 280, grid.getCellHeight());
        height.valueProperty().addListener((obs, old, value) -> grid.setCellHeight(value.doubleValue()));
        return createGrid(
                row("Width", width, createValueLabel(width, "%.0f px")),
                row("Height", height, createValueLabel(height, "%.0f px")));
    }

    private Node spacingGrid() {
        Slider hgap = createSlider(0, 40, grid.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> grid.setHgap(value.doubleValue()));
        Slider vgap = createSlider(0, 40, grid.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> grid.setVgap(value.doubleValue()));
        return createGrid(
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")));
    }

    private Node columnsGrid() {
        Slider columnCount = intSlider(0, 12, grid.getColumnCount());
        columnCount.valueProperty().addListener(
                (obs, old, value) -> grid.setColumnCount(value.intValue()));
        Slider maxColumns = intSlider(0, 12, grid.getMaxColumns());
        maxColumns.valueProperty().addListener(
                (obs, old, value) -> grid.setMaxColumns(value.intValue()));
        return createGrid(
                row("Forced", columnCount, sentinelLabel(columnCount, "auto")),
                row("Max", maxColumns, sentinelLabel(maxColumns, "none")),
                row(hint("Forced count is still capped by Max — e.g. forced 5 + max 2 shows 2.")));
    }

    private Node layoutGrid() {
        ChoiceBox<ItemsJustify> justify = new ChoiceBox<>(
                FXCollections.observableArrayList(ItemsJustify.values()));
        justify.setValue(grid.getItemsJustify());
        justify.valueProperty().addListener((obs, old, value) -> grid.setItemsJustify(value));

        Slider maxCell = createSlider(0, 400, grid.getMaxCellWidth());
        maxCell.valueProperty().addListener((obs, old, value) -> grid.setMaxCellWidth(value.doubleValue()));

        return createGrid(
                row("Justify", justify),
                // maxCellWidth caps growth in STRETCH mode; 0 = unbounded.
                row("Max cell W", maxCell, sentinelLabel(maxCell, "none")));
    }

    private Node dataGrid() {
        CheckBox empty = new CheckBox("Empty (show placeholder)");
        empty.selectedProperty().addListener((obs, old, value) ->
                grid.setItems(value ? FXCollections.observableArrayList() : items));
        return createGrid(row(empty));
    }

    private Node scrollGrid() {
        TextField index = new TextField("5000");
        index.setPrefColumnCount(6);
        ChoiceBox<ScrollAlignment> alignment = new ChoiceBox<>(
                FXCollections.observableArrayList(ScrollAlignment.values()));
        alignment.setValue(ScrollAlignment.START);
        Button go = new Button("Scroll to");
        go.setOnAction(e -> scrollTo(index.getText(), alignment.getValue()));

        HBox box = new HBox(8.0, index, alignment, go);
        box.setAlignment(Pos.CENTER_LEFT);
        return createGrid(row(box));
    }

    private void scrollTo(String text, ScrollAlignment alignment) {
        try {
            grid.scrollTo(Integer.parseInt(text.trim()), alignment);
        } catch (NumberFormatException ignored) {
            // Leave an unparseable index alone rather than disrupting the view.
        }
    }

    private Node metricsGrid() {
        Label columns = new Label();
        columns.textProperty().bind(grid.actualColumnCountProperty().asString());
        Label rows = new Label();
        rows.textProperty().bind(grid.rowCountProperty().asString());
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(
                () -> describe(grid.getVisibleRange()), grid.visibleRangeProperty()));
        range.setWrapText(true);
        return createGrid(
                row("Columns", columns),
                row("Rows", rows),
                row("Visible", range));
    }

    private static String describe(RXGridVisibleRange range) {
        if (range == null || range.isEmpty()) {
            return "—";
        }
        return "items " + range.firstIndex() + ".." + range.lastIndex()
                + "  (rows " + range.firstRow() + ".." + range.lastRow() + ")";
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
     * A grid cell that paints a rounded color tile and shows its index.
     */
    private static final class TileCell extends RXGridCell<Integer> {

        private static final String BASE_STYLE =
                "-fx-text-fill: white; -fx-font-weight: bold;";

        private TileCell() {
            setAlignment(Pos.CENTER);
            setStyle(BASE_STYLE);
        }

        @Override
        protected void updateItem(Integer item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(BASE_STYLE);
            } else {
                setText(Integer.toString(item));
                double hue = (item * 23) % 360;
                setStyle(BASE_STYLE + " -fx-background-color: hsb(" + hue + ", 55%, 80%);"
                        + " -fx-background-radius: 10;");
            }
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

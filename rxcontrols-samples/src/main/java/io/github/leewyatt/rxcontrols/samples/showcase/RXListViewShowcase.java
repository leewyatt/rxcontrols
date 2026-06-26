package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXListSection;
import io.github.leewyatt.rxcontrols.RXListSelectionVisualMode;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXListVisibleRange;
import io.github.leewyatt.rxcontrols.ScrollAlignment;
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
import javafx.util.StringConverter;

import java.util.List;

/**
 * Showcase for {@link RXListView}. Renders a virtualized list of {@value
 * #ITEM_COUNT} rows and exposes the M1 knobs — selection mode and visual mode
 * (row / checkmark / checkbox / auto, the appearance of the single selection) and
 * fixed cell size — plus scroll-to-item / scroll-by controls and a live readout of
 * the row count, visible range and selected count, so virtualization and the
 * selection across visual modes can be exercised at scale.
 */
public class RXListViewShowcase extends RXShowcaseApplication {

    private static final int ITEM_COUNT = 10_000;

    private RXListView<Integer> list;
    private ObservableList<Integer> items;

    @Override
    protected String title() {
        return "RXListView";
    }

    @Override
    protected String subtitle() {
        return ITEM_COUNT + " virtualized rows — flip selection mode / visual mode to see row vs checkbox selection";
    }

    @Override
    protected Node createPreview() {
        items = FXCollections.observableArrayList();
        for (int i = 0; i < ITEM_COUNT; i++) {
            items.add(i);
        }
        list = new RXListView<>(items);
        list.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : "Item " + value;
            }

            @Override
            public Integer fromString(String string) {
                return Integer.valueOf(string.replace("Item ", "").trim());
            }
        });
        Label placeholder = new Label("No items");
        list.setPlaceholder(placeholder);
        logSelectionChanges();
        return list;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", selectionGrid()),
                section("Row height", cellSizeGrid()),
                section("Sections", sectionsGrid()),
                section("Scroll", scrollGrid()),
                section("Metrics", metricsGrid()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-list-view-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node selectionGrid() {
        ChoiceBox<SelectionMode> mode = new ChoiceBox<>(
                FXCollections.observableArrayList(SelectionMode.SINGLE, SelectionMode.MULTIPLE));
        mode.setValue(list.getSelectionMode());
        mode.valueProperty().addListener((obs, old, value) -> list.setSelectionMode(value));

        ChoiceBox<RXListSelectionVisualMode> visual = new ChoiceBox<>(
                FXCollections.observableArrayList(RXListSelectionVisualMode.values()));
        visual.setValue(list.getSelectionVisualMode());
        visual.valueProperty().addListener((obs, old, value) -> list.setSelectionVisualMode(value));

        return createGrid(
                row("Mode", mode),
                row("Visual", visual),
                row(hint("Visual mode is purely the appearance of one selection — switch it freely without losing "
                        + "state. ROW/CHECKMARK: click replaces, Ctrl toggles, arrows select. CHECKBOX: click "
                        + "toggles, arrows move the cursor, Space toggles. Shift extends a range; Enter or "
                        + "double-click activates.")));
    }

    private Node cellSizeGrid() {
        Slider cellSize = createSlider(20, 72, list.getFixedCellSize());
        cellSize.valueProperty().addListener((obs, old, value) -> list.setFixedCellSize(value.doubleValue()));
        return createGrid(
                row("Cell size", cellSize, createValueLabel(cellSize, "%.0f px")));
    }

    private Node sectionsGrid() {
        CheckBox grouped = new CheckBox("Group by hundreds");
        grouped.setSelected(false);
        // On: derive a section per hundred (10000 items -> 100 sections). Off: flat.
        grouped.selectedProperty().addListener((obs, old, on) ->
                list.setSectionKeyFactory(on ? value -> value / 100 : null));

        CheckBox headers = new CheckBox("Show headers");
        headers.setSelected(list.isShowSectionHeaders());
        headers.selectedProperty().addListener((obs, old, on) -> list.setShowSectionHeaders(on));

        Slider headerHeight = createSlider(20, 64, list.getSectionHeaderHeight());
        headerHeight.valueProperty().addListener((obs, old, value) ->
                list.setSectionHeaderHeight(value.doubleValue()));

        Slider spacing = createSlider(0, 40, list.getSectionSpacing());
        spacing.valueProperty().addListener((obs, old, value) -> list.setSectionSpacing(value.doubleValue()));

        TextField sectionKey = new TextField("50");
        sectionKey.setPrefColumnCount(5);
        Button goSection = new Button("Scroll to section");
        goSection.setOnAction(e -> scrollToSection(sectionKey.getText()));
        HBox sectionBox = new HBox(8.0, sectionKey, goSection);
        sectionBox.setAlignment(Pos.CENTER_LEFT);

        Label topSection = new Label();
        topSection.textProperty().bind(Bindings.createStringBinding(
                () -> describeSection(list.getVisibleSection()), list.visibleSectionProperty()));

        return createGrid(
                row("Grouping", grouped),
                row("Headers", headers),
                row("Header h", headerHeight, createValueLabel(headerHeight, "%.0f px")),
                row("Spacing", spacing, createValueLabel(spacing, "%.0f px")),
                row(sectionBox),
                row("Top section", topSection),
                row(hint("Grouping derives sections from the items (no item is moved). Headers are a separate "
                        + "pooled row type — arrow keys skip them and they are never selected. With headers off, "
                        + "sections are still computed so scroll-to-section keeps working.")));
    }

    private void scrollToSection(String text) {
        try {
            list.scrollToSection(Integer.parseInt(text.trim()), ScrollAlignment.START);
        } catch (NumberFormatException ignored) {
            // Leave an unparseable key alone rather than disrupting the view.
        }
    }

    private static String describeSection(RXListSection section) {
        return section == null ? "—" : "key " + section.key() + " (" + section.itemCount() + " items)";
    }

    private Node scrollGrid() {
        TextField index = new TextField("5000");
        index.setPrefColumnCount(6);
        ChoiceBox<ScrollAlignment> alignment = new ChoiceBox<>(
                FXCollections.observableArrayList(ScrollAlignment.values()));
        alignment.setValue(ScrollAlignment.NEAREST);
        Button goItem = new Button("Scroll to item");
        goItem.setOnAction(e -> scrollToItem(index.getText(), alignment.getValue()));
        HBox itemBox = new HBox(8.0, index, alignment, goItem);
        itemBox.setAlignment(Pos.CENTER_LEFT);

        Button up = new Button("- 400 px");
        up.setOnAction(e -> list.scrollBy(-400));
        Button down = new Button("+ 400 px");
        down.setOnAction(e -> list.scrollBy(400));
        HBox byBox = new HBox(8.0, up, down);
        byBox.setAlignment(Pos.CENTER_LEFT);

        return createGrid(row(itemBox), row(byBox));
    }

    private void scrollToItem(String text, ScrollAlignment alignment) {
        try {
            list.scrollTo(Integer.parseInt(text.trim()), alignment);
        } catch (NumberFormatException ignored) {
            // Leave an unparseable index alone rather than disrupting the view.
        }
    }

    private Node metricsGrid() {
        Label rows = new Label();
        rows.textProperty().bind(list.rowCountProperty().asString());
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(
                () -> describe(list.getVisibleRange()), list.visibleRangeProperty()));
        range.setWrapText(true);
        Label selected = new Label();
        selected.textProperty().bind(Bindings.size(list.getSelectionModel().getSelectedIndices()).asString());
        return createGrid(
                row("Rows", rows),
                row("Visible", range),
                row("Selected", selected));
    }

    private static String describe(RXListVisibleRange range) {
        if (range == null || range.isEmpty()) {
            return "—";
        }
        return "items " + range.firstIndex() + ".." + range.lastIndex();
    }

    private void logSelectionChanges() {
        list.getSelectionModel().getSelectedIndices().addListener((ListChangeListener<Integer>) change ->
                System.out.println("RXListView selected indices: "
                        + List.copyOf(list.getSelectionModel().getSelectedIndices())));
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
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

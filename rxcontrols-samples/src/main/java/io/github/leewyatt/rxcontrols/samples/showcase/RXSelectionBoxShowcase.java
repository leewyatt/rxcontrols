package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSelectionBox;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXSelectionBox}.
 *
 * <p>Exposes the selection mode (single / multiple), the searchable popup,
 * the built-in Clear / Select All actions, {@code maxVisibleRows}, the entrance
 * animation toggle, and the {@code readOnly} / {@code disabled} states over a
 * virtualized list of items.</p>
 */
public class RXSelectionBoxShowcase extends RXShowcaseApplication {

    private static final List<String> LANGUAGES = List.of(
            "Java", "Kotlin", "Scala", "Groovy", "Clojure", "JavaScript", "TypeScript",
            "Python", "Ruby", "Go", "Rust", "C", "C++", "C#", "Swift", "Objective-C",
            "PHP", "Perl", "Haskell", "Elixir", "Erlang", "Dart", "Lua", "R",
            "Julia", "F#", "OCaml", "Zig", "Nim", "Crystal");

    private RXSelectionBox<String> selectionBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSelectionBox";
    }

    @Override
    protected String subtitle() {
        return "Searchable, virtualized single / multiple selector";
    }

    @Override
    protected String windowTitle() {
        return "RXSelectionBox Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-selection-box-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        selectionBox = new RXSelectionBox<>(FXCollections.observableArrayList(LANGUAGES));
        selectionBox.setPromptText("Choose a language");
        selectionBox.setSearchPromptText("Search languages");
        selectionBox.setPrefWidth(260.0);
        selectionBox.setMaxWidth(260.0);

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.setWrapText(true);
        stateLabel.textProperty().bind(Bindings.createStringBinding(
                this::describeSelection, selectionBox.getSelectionModel().getSelectedItems()));

        VBox box = new VBox(20.0, selectionBox, stateLabel);
        box.getStyleClass().add("selection-box-preview");
        box.setAlignment(Pos.TOP_CENTER);
        return box;
    }

    private String describeSelection() {
        List<String> selected = selectionBox.getSelectedItems();
        if (selected.isEmpty()) {
            return "Nothing selected";
        }
        return "Selected: " + String.join(", ", selected);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", buildSelectionGrid()),
                section("Search", buildSearchGrid()),
                section("Actions", buildActionsGrid()),
                section("Popup", buildPopupGrid()),
                section("State", buildStateGrid()));
    }

    // ==================== Sections ====================

    private Node buildSelectionGrid() {
        ComboBox<SelectionMode> mode = new ComboBox<>();
        mode.getItems().setAll(SelectionMode.SINGLE, SelectionMode.MULTIPLE);
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.valueProperty().bindBidirectional(selectionBox.selectionModeProperty());

        CheckBox autoHide = new CheckBox("autoHideOnSelection");
        autoHide.selectedProperty().bindBidirectional(selectionBox.autoHideOnSelectionProperty());

        return createGrid(
                row("Mode", mode),
                row(autoHide));
    }

    private Node buildSearchGrid() {
        CheckBox searchable = new CheckBox("searchable");
        searchable.selectedProperty().bindBidirectional(selectionBox.searchableProperty());

        CheckBox clearOnHide = new CheckBox("clearSearchOnHide");
        clearOnHide.selectedProperty().bindBidirectional(selectionBox.clearSearchOnHideProperty());

        return createGrid(
                row(searchable),
                row(clearOnHide));
    }

    private Node buildActionsGrid() {
        CheckBox showClear = new CheckBox("showClearButton");
        showClear.selectedProperty().bindBidirectional(selectionBox.showClearButtonProperty());

        CheckBox showSelectAll = new CheckBox("showSelectAllButton (multiple only)");
        showSelectAll.selectedProperty().bindBidirectional(selectionBox.showSelectAllButtonProperty());

        return createGrid(
                row(showClear),
                row(showSelectAll));
    }

    private Node buildPopupGrid() {
        Slider rows = createSlider(2.0, 15.0, selectionBox.getMaxVisibleRows());
        selectionBox.maxVisibleRowsProperty().bind(Bindings.createIntegerBinding(
                () -> (int) Math.round(rows.getValue()), rows.valueProperty()));
        Label rowsValue = createValueLabel(rows, "%.0f");

        CheckBox animated = new CheckBox("animationEnabled");
        animated.selectedProperty().bindBidirectional(selectionBox.animationEnabledProperty());

        return createGrid(
                row("Max rows", rows, rowsValue),
                row(animated));
    }

    private Node buildStateGrid() {
        CheckBox readOnly = new CheckBox("readOnly");
        readOnly.selectedProperty().bindBidirectional(selectionBox.readOnlyProperty());

        CheckBox disabled = new CheckBox("disabled");
        selectionBox.disableProperty().bind(disabled.selectedProperty());

        return createGrid(
                row(readOnly),
                row(disabled));
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

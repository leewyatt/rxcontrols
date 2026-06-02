package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.List;
import java.util.StringJoiner;

/**
 * Showcase application for {@link RXCascader}.
 *
 * <p>Exercises the public knobs: single vs multiple selection, the clear
 * affordance, the path-to-text factory (full path / last level / first-to-last),
 * and the column-width / row-height / visible-row-count dimensions. The sample
 * tree contains a disabled leaf so the locked tri-state rollup is directly
 * observable: checking its enabled siblings leaves the ancestors indeterminate.
 *
 * <p>For a minimal "few lines of code" example see {@link RXCascaderDemo}.
 */
public class RXCascaderShowcase extends RXShowcaseApplication {

    private static final double MIN_COLUMN_WIDTH = 120.0;
    private static final double MAX_COLUMN_WIDTH = 260.0;
    private static final double MIN_ROW_HEIGHT = 24.0;
    private static final double MAX_ROW_HEIGHT = 48.0;
    private static final double MIN_VISIBLE_ROWS = 3.0;
    private static final double MAX_VISIBLE_ROWS = 10.0;
    private static final double READOUT_HEIGHT = 112.0;
    private static final String SEPARATOR = " / ";

    private RXCascader<String> cascader;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCascader";
    }

    @Override
    protected String subtitle() {
        return "Cascading multi-column selector";
    }

    @Override
    protected String windowTitle() {
        return "RXCascader Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-cascader-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        cascader = new RXCascader<>();
        cascader.setPromptText("Choose a location");
        cascader.setClearable(true);
        cascader.setPathTextFactory(PathFormat.FULL_PATH.factory());
        cascader.getRootItems().setAll(sampleOptions());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        // A fixed-height readout keeps the preview block a constant size as
        // checked paths accumulate, so the field does not bob (and the popup
        // does not chase it) while the user toggles checkboxes.
        readout.setAlignment(Pos.TOP_LEFT);
        readout.setMinHeight(READOUT_HEIGHT);
        readout.setPrefHeight(READOUT_HEIGHT);
        readout.setMaxHeight(READOUT_HEIGHT);
        readout.textProperty().bind(Bindings.createStringBinding(
                this::describeSelection,
                cascader.selectedPathProperty(),
                cascader.getCheckedPaths(),
                cascader.selectionModeProperty()));

        VBox box = new VBox(16.0, cascader, readout);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", buildSelectionGrid()),
                section("Display text", buildDisplayGrid()),
                section("Dimensions", buildDimensionGrid()));
    }

    // ==================== Sections ====================

    private Node buildSelectionGrid() {
        ComboBox<RXCascaderSelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(RXCascaderSelectionMode.values());
        modeBox.setValue(cascader.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        cascader.selectionModeProperty().bind(modeBox.valueProperty());

        CheckBox clearableBox = new CheckBox("Show clear button");
        clearableBox.selectedProperty().bindBidirectional(cascader.clearableProperty());

        Label hint = new Label("\"Disabled City\" under Asia / China is a locked "
                + "leaf. In multiple mode it keeps China and Asia indeterminate "
                + "even when every enabled sibling is checked.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(clearableBox),
                row(hint));
    }

    private Node buildDisplayGrid() {
        ComboBox<PathFormat> formatBox = new ComboBox<>();
        formatBox.getItems().setAll(PathFormat.values());
        formatBox.setValue(PathFormat.FULL_PATH);
        formatBox.setMaxWidth(Double.MAX_VALUE);
        formatBox.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setPathTextFactory(newV == null ? null : newV.factory()));

        return createGrid(row("Path text", formatBox));
    }

    private Node buildDimensionGrid() {
        Slider columnWidth = createSlider(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH, cascader.getColumnWidth());
        cascader.columnWidthProperty().bind(columnWidth.valueProperty());
        Label columnValue = createValueLabel(columnWidth, "%.0f px");

        Slider rowHeight = createSlider(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT, cascader.getRowHeight());
        cascader.rowHeightProperty().bind(rowHeight.valueProperty());
        Label rowValue = createValueLabel(rowHeight, "%.0f px");

        Slider visibleRows = createSlider(MIN_VISIBLE_ROWS, MAX_VISIBLE_ROWS, cascader.getVisibleRowCount());
        visibleRows.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setVisibleRowCount((int) Math.round(newV.doubleValue())));
        Label visibleValue = createValueLabel(visibleRows, "%.0f");

        return createGrid(
                row("Column width", columnWidth, columnValue),
                row("Row height", rowHeight, rowValue),
                row("Visible rows", visibleRows, visibleValue));
    }

    // ==================== Readout ====================

    private String describeSelection() {
        if (cascader.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
            List<RXCascaderPath<String>> checked = cascader.getCheckedPaths();
            if (checked.isEmpty()) {
                return "checked: (none)";
            }
            StringJoiner joiner = new StringJoiner("\n");
            for (RXCascaderPath<String> path : checked) {
                joiner.add("- " + String.join(SEPARATOR, path.getTexts()));
            }
            return "checked (" + checked.size() + "):\n" + joiner;
        }
        RXCascaderPath<String> path = cascader.getSelectedPath();
        if (path == null) {
            return "selected: (none)";
        }
        return "selected: " + String.join(SEPARATOR, path.getTexts());
    }

    // ==================== Sample data ====================

    private static List<RXCascaderItem<String>> sampleOptions() {
        RXCascaderItem<String> disabledCity = item("disabled", "Disabled City");
        disabledCity.setDisabled(true);

        RXCascaderItem<String> china = item("china", "China");
        china.getChildren().setAll(List.of(
                item("shanghai", "Shanghai"),
                item("hangzhou", "Hangzhou"),
                disabledCity));

        RXCascaderItem<String> japan = item("japan", "Japan");
        japan.getChildren().setAll(List.of(
                item("tokyo", "Tokyo"),
                item("osaka", "Osaka")));

        RXCascaderItem<String> asia = item("asia", "Asia");
        asia.getChildren().setAll(List.of(china, japan));

        RXCascaderItem<String> germany = item("germany", "Germany");
        germany.getChildren().setAll(List.of(
                item("berlin", "Berlin"),
                item("munich", "Munich")));

        RXCascaderItem<String> europe = item("europe", "Europe");
        europe.getChildren().setAll(List.of(germany));

        return List.of(asia, europe);
    }

    private static RXCascaderItem<String> item(String value, String text) {
        return new RXCascaderItem<>(value, text);
    }

    // ==================== Path format ====================

    private enum PathFormat {
        FULL_PATH("Full path (A / B / C)") {
            @Override
            Callback<RXCascaderPath<String>, String> factory() {
                return path -> String.join(SEPARATOR, path.getTexts());
            }
        },
        LAST_LEVEL("Last level only (C)") {
            @Override
            Callback<RXCascaderPath<String>, String> factory() {
                return path -> path.getLeaf() == null ? "" : path.getLeaf().getText();
            }
        },
        FIRST_TO_LAST("First -> last (A -> C)") {
            @Override
            Callback<RXCascaderPath<String>, String> factory() {
                return path -> {
                    List<String> texts = path.getTexts();
                    if (texts.isEmpty()) {
                        return "";
                    }
                    String first = texts.get(0);
                    String last = texts.get(texts.size() - 1);
                    return first.equals(last) ? first : first + " -> " + last;
                };
            }
        };

        private final String label;

        PathFormat(String label) {
            this.label = label;
        }

        abstract Callback<RXCascaderPath<String>, String> factory();

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

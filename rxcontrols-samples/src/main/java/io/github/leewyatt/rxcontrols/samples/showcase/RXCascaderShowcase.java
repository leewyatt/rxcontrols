package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.List;

/**
 * Showcase application for {@link RXCascader}.
 *
 * <p>Exercises the popup field wrapper: single vs multiple selection, clearable
 * state, custom cells, path text formatting, and popup sizing knobs. Lazy loading
 * has a separate focused showcase because it is an asynchronous interaction
 * scenario rather than a field-display property.
 *
 * <p>The value type is a {@link CascaderOption} record carrying id + label; the
 * visible node text comes from {@code setItemTextFactory(CascaderOption::label)}.
 *
 * <p>For a minimal "few lines of code" example see {@link RXCascaderDemo}. For
 * the lazy-loading scenario see {@link RXCascaderLazyShowcase}.
 */
public class RXCascaderShowcase extends RXShowcaseApplication {

    private static final double MIN_VISIBLE_ROWS = 3.0;
    private static final double MAX_VISIBLE_ROWS = 10.0;
    private static final double MIN_COLUMN_WIDTH = 140.0;
    private static final double MAX_COLUMN_WIDTH = 320.0;
    private static final double MIN_ROW_HEIGHT = 26.0;
    private static final double MAX_ROW_HEIGHT = 52.0;

    private RXCascader<CascaderOption> cascader;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCascader";
    }

    @Override
    protected String subtitle() {
        return "Popup field wrapper for cascading selection";
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
        cascader.setMaxWidth(Double.MAX_VALUE);
        cascader.setPromptText("Choose a location");
        cascader.setClearable(true);
        cascader.setItemTextFactory(CascaderOption::label);
        cascader.setPathTextFactory(pathFactory(PathFormat.FULL_PATH));
        cascader.getRootItems().setAll(CascaderShowcaseSupport.sampleOptions());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> CascaderShowcaseSupport.describeSelection(cascader),
                cascader.selectedPathProperty(),
                cascader.getCheckedPaths(),
                cascader.selectionModeProperty()));

        VBox box = new VBox(16.0, cascader, readout);
        box.getStyleClass().add("cascader-preview");
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
        ComboBox<SelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(SelectionMode.values());
        modeBox.setValue(cascader.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        cascader.selectionModeProperty().bind(modeBox.valueProperty());

        CheckBox clearableBox = new CheckBox("Show clear button");
        clearableBox.selectedProperty().bindBidirectional(cascader.clearableProperty());

        CheckBox customCell = new CheckBox("Custom cell (colored dot + text)");
        customCell.selectedProperty().addListener((obs, was, on) ->
                cascader.setCellFactory(on ? CascaderShowcaseSupport.DotCell::new : null));

        Label hint = new Label("\"Disabled City\" under Asia / China is a locked "
                + "leaf. In multiple mode it keeps China and Asia indeterminate "
                + "even when every enabled sibling is checked.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        Label keysHint = new Label("Keyboard: Space / F4 / Alt+Down opens the popup; "
                + "arrows then navigate the columns (Right expands, Left steps back, "
                + "disabled rows are skipped), Enter picks the focused item, Escape "
                + "closes.");
        keysHint.getStyleClass().add("hint");
        keysHint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(clearableBox),
                row(customCell),
                row(hint),
                row(keysHint));
    }

    private Node buildDisplayGrid() {
        ComboBox<PathFormat> formatBox = new ComboBox<>();
        formatBox.getItems().setAll(PathFormat.values());
        formatBox.setValue(PathFormat.FULL_PATH);
        formatBox.setMaxWidth(Double.MAX_VALUE);
        formatBox.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setPathTextFactory(newV == null ? null : pathFactory(newV)));

        return createGrid(row("Path text", formatBox));
    }

    private Callback<RXCascaderPath<CascaderOption>, String> pathFactory(PathFormat format) {
        return path -> format.format(CascaderShowcaseSupport.pathTexts(cascader.getItemTextFactory(), path));
    }

    private Node buildDimensionGrid() {
        Slider visibleRows = createSlider(MIN_VISIBLE_ROWS, MAX_VISIBLE_ROWS, cascader.getVisibleRowCount());
        visibleRows.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setVisibleRowCount((int) Math.round(newV.doubleValue())));
        Label visibleValue = createValueLabel(visibleRows, "%.0f");

        Slider columnWidth = createSlider(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH, cascader.getColumnWidth());
        columnWidth.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setColumnWidth(newV.doubleValue()));
        Label columnValue = createValueLabel(columnWidth, "%.0f px");

        Slider rowHeight = createSlider(MIN_ROW_HEIGHT, MAX_ROW_HEIGHT, cascader.getRowHeight());
        rowHeight.valueProperty().addListener((obs, oldV, newV) ->
                cascader.setRowHeight(newV.doubleValue()));
        Label rowValue = createValueLabel(rowHeight, "%.0f px");

        Label hint = new Label("Column width and row height are forwarded to the "
                + "embedded popup view; authors can also style popup columns with CSS.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Visible rows", visibleRows, visibleValue),
                row("Column width", columnWidth, columnValue),
                row("Row height", rowHeight, rowValue),
                row(hint));
    }

    // ==================== Path format ====================

    private enum PathFormat {
        FULL_PATH("Full path (A / B / C)") {
            @Override
            String format(List<String> texts) {
                return String.join(CascaderShowcaseSupport.SEPARATOR, texts);
            }
        },
        LAST_LEVEL("Last level only (C)") {
            @Override
            String format(List<String> texts) {
                return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
            }
        },
        FIRST_TO_LAST("First -> last (A -> C)") {
            @Override
            String format(List<String> texts) {
                if (texts.isEmpty()) {
                    return "";
                }
                String first = texts.get(0);
                String last = texts.get(texts.size() - 1);
                return first.equals(last) ? first : first + " -> " + last;
            }
        };

        private final String label;

        PathFormat(String label) {
            this.label = label;
        }

        abstract String format(List<String> texts);

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

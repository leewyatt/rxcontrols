package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCascaderCell;
import io.github.leewyatt.rxcontrols.RXCascaderItem;
import io.github.leewyatt.rxcontrols.RXCascaderPath;
import io.github.leewyatt.rxcontrols.RXCascaderSelectionMode;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
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

import java.util.List;
import java.util.StringJoiner;

/**
 * Showcase application for {@link RXCascaderView} — the standalone inline
 * cascader view (no input field, no popup). Exercises selection mode, the
 * visible-row-count, CSS sizing presets (column width / row height via
 * {@code .rx-cascader-column} CSS, not Java properties), and a clear action.
 * The sample tree contains a disabled leaf so the locked tri-state rollup is
 * directly observable.
 *
 * <p>For a minimal "few lines of code" example see {@link RXCascaderViewDemo}.
 * For the popup/input-field wrapper, see {@link RXCascaderShowcase}.
 */
public class RXCascaderViewShowcase extends RXShowcaseApplication {

    private static final double MIN_VISIBLE_ROWS = 3.0;
    private static final double MAX_VISIBLE_ROWS = 10.0;
    private static final String SEPARATOR = " / ";

    private RXCascaderView<String> view;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCascaderView";
    }

    @Override
    protected String subtitle() {
        return "Standalone inline cascader panel";
    }

    @Override
    protected String windowTitle() {
        return "RXCascaderView Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-cascader-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        view = new RXCascaderView<>();
        view.getRootItems().setAll(sampleOptions());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                this::describeSelection,
                view.selectedPathProperty(),
                view.getCheckedPaths(),
                view.selectionModeProperty()));

        VBox box = new VBox(16.0, view, readout);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", buildSelectionGrid()),
                section("Dimensions", buildDimensionGrid()));
    }

    // ==================== Sections ====================

    private Node buildSelectionGrid() {
        ComboBox<RXCascaderSelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(RXCascaderSelectionMode.values());
        modeBox.setValue(view.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        view.selectionModeProperty().bind(modeBox.valueProperty());

        Button clearButton = new Button("Clear selection");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(event -> view.clearSelection());

        CheckBox customCell = new CheckBox("Custom cell (colored dot + text)");
        customCell.selectedProperty().addListener((obs, was, on) ->
                view.setCellFactory(on ? DotCell::new : null));

        Label hint = new Label("\"Disabled City\" under Asia / China is a locked "
                + "leaf. In multiple mode it keeps China and Asia indeterminate "
                + "even when every enabled sibling is checked.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(clearButton),
                row(customCell),
                row(hint));
    }

    private Node buildDimensionGrid() {
        Slider visibleRows = createSlider(MIN_VISIBLE_ROWS, MAX_VISIBLE_ROWS, view.getVisibleRowCount());
        visibleRows.valueProperty().addListener((obs, oldV, newV) ->
                view.setVisibleRowCount((int) Math.round(newV.doubleValue())));
        Label visibleValue = createValueLabel(visibleRows, "%.0f");

        ComboBox<SizePreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(SizePreset.values());
        presetBox.setValue(SizePreset.DEFAULT);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> applyPreset(newV));

        Label hint = new Label("Column width / row height are controlled by CSS "
                + "(.rx-cascader-column / .rx-cascader-column-N). The preset toggles "
                + "demo style classes that override them.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(
                row("Visible rows", visibleRows, visibleValue),
                row("CSS preset", presetBox),
                row(hint));
    }

    private void applyPreset(SizePreset preset) {
        view.getStyleClass().removeAll(SizePreset.WIDE_COL2.styleClass(), SizePreset.TALL_ROWS.styleClass());
        if (preset != null && !preset.styleClass().isEmpty()) {
            view.getStyleClass().add(preset.styleClass());
        }
    }

    // ==================== Readout ====================

    private String describeSelection() {
        if (view.getSelectionMode() == RXCascaderSelectionMode.MULTIPLE) {
            List<RXCascaderPath<String>> checked = view.getCheckedPaths();
            if (checked.isEmpty()) {
                return "checked: (none)";
            }
            StringJoiner joiner = new StringJoiner("\n");
            for (RXCascaderPath<String> path : checked) {
                joiner.add("- " + String.join(SEPARATOR, path.getTexts()));
            }
            return "checked (" + checked.size() + "):\n" + joiner;
        }
        RXCascaderPath<String> path = view.getSelectedPath();
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

    // ==================== Custom cell ====================

    /**
     * Cell that overrides only the content area with a colored dot plus the item
     * text, keeping the built-in check box / arrow / loading and interaction.
     *
     * @param <T> application value type
     */
    private static final class DotCell<T> extends RXCascaderCell<T> {

        private DotCell(RXCascaderView<T> view) {
            super(view);
        }

        @Override
        protected Node createContent(RXCascaderItem<T> item) {
            Region dot = new Region();
            dot.getStyleClass().add("demo-cell-dot");
            dot.setMinSize(8.0, 8.0);
            dot.setPrefSize(8.0, 8.0);
            dot.setMaxSize(8.0, 8.0);
            HBox box = new HBox(8.0, dot, new Label(item.getText()));
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }
    }

    // ==================== Size preset ====================

    private enum SizePreset {
        DEFAULT("Default", ""),
        WIDE_COL2("Second column 300px", "demo-wide-col2"),
        TALL_ROWS("Row height 44px", "demo-tall");

        private final String label;
        private final String styleClass;

        SizePreset(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }

        String styleClass() {
            return styleClass;
        }

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

package io.github.leewyatt.rxcontrols.samples.showcase;

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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXCascaderView} — the standalone inline
 * cascader view (no input field, no popup). Exercises selection mode, the
 * visible-row-count, CSS sizing presets (author CSS on the skin's
 * {@code .column} / {@code .columnN} style classes overriding the view's
 * columnWidth / rowHeight defaults), and a clear action. The sample tree
 * contains a disabled leaf so the locked tri-state rollup is directly
 * observable.
 *
 * <p>The value type is a {@link CascaderOption} record; visible node text comes
 * from {@code setItemTextFactory(CascaderOption::label)} and path text from the
 * shared showcase helper.
 *
 * <p>For a minimal "few lines of code" example see {@link RXCascaderViewDemo}.
 * For the popup/input-field wrapper, see {@link RXCascaderShowcase}.
 */
public class RXCascaderViewShowcase extends RXShowcaseApplication {

    private static final double MIN_VISIBLE_ROWS = 3.0;
    private static final double MAX_VISIBLE_ROWS = 10.0;

    private RXCascaderView<CascaderOption> view;

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
        view.setItemTextFactory(CascaderOption::label);
        view.getRootItems().setAll(CascaderShowcaseSupport.sampleOptions());

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> CascaderShowcaseSupport.describeSelection(view),
                view.selectedPathProperty(),
                view.getCheckedPaths(),
                view.selectionModeProperty()));

        VBox box = new VBox(16.0, view, readout);
        box.setAlignment(Pos.TOP_CENTER);
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
        ComboBox<SelectionMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(SelectionMode.values());
        modeBox.setValue(view.getSelectionMode());
        modeBox.setMaxWidth(Double.MAX_VALUE);
        view.selectionModeProperty().bind(modeBox.valueProperty());

        Button clearButton = new Button("Clear selection");
        clearButton.setMaxWidth(Double.MAX_VALUE);
        clearButton.setOnAction(event -> view.clearSelection());

        CheckBox customCell = new CheckBox("Custom cell (colored dot + text)");
        customCell.selectedProperty().addListener((obs, was, on) ->
                view.setCellFactory(on ? CascaderShowcaseSupport.DotCell::new : null));

        Label hint = new Label("\"Disabled City\" under Asia / China is a locked "
                + "leaf. In multiple mode it keeps China and Asia indeterminate "
                + "even when every enabled sibling is checked.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        Label keysHint = new Label("Keyboard: click or Tab to focus the panel "
                + "(focused border turns accent); arrows navigate (Right expands, "
                + "Left steps back, disabled rows are skipped), Enter or Space "
                + "activates the focused item.");
        keysHint.getStyleClass().add("hint");
        keysHint.setWrapText(true);

        return createGrid(
                row("Mode", modeBox),
                row(clearButton),
                row(customCell),
                row(hint),
                row(keysHint));
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

        Label hint = new Label("Column width / row height default from the view's "
                + "columnWidth / rowHeight; author CSS targeting the skin's .column "
                + "/ .columnN classes overrides them. The preset toggles demo style "
                + "classes that do exactly that.");
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

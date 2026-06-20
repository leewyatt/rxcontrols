package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Showcase application for {@link RXFlowPane}.
 *
 * <p>Fills a flow pane with chips of varied size and exposes every property: the
 * orientation, the whole-block {@code alignment}, the per-run / per-item alignment for
 * each direction (rowHalignment / rowValignment when horizontal, columnValignment /
 * columnHalignment when vertical), the gaps, the preferred wrap length, and live add /
 * remove of chips. The pane is given far more room than its content so the whole-block
 * alignment is visible, while the per-run alignment governs the short last run.</p>
 */
public class RXFlowPaneShowcase extends RXShowcaseApplication {

    // ==================== Constants ====================

    private static final int INITIAL_CHIP_COUNT = 11;

    private static final String[] PALETTE = {
            "#6366f1", "#0ea5e9", "#14b8a6", "#22c55e", "#84cc16",
            "#eab308", "#f97316", "#ef4444", "#ec4899", "#a855f7"
    };

    // ==================== Fields ====================

    private RXFlowPane flow;
    private int nextChipIndex;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXFlowPane";
    }

    @Override
    protected String subtitle() {
        return "Bidirectional wrapping flow with whole-block + per-run / per-item alignment";
    }

    @Override
    protected String windowTitle() {
        return "RXFlowPane Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-flow-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        flow = new RXFlowPane();
        flow.getStyleClass().add("showcase-flow");
        flow.setHgap(10.0);
        flow.setVgap(10.0);
        for (int i = 0; i < INITIAL_CHIP_COUNT; i++) {
            flow.getChildren().add(createChip(nextChipIndex++));
        }
        return flow;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Alignment", buildAlignmentGrid()),
                section("Spacing", buildSpacingGrid()),
                section("Preferred size", buildSizingGrid()),
                section("Content", buildContentGrid()));
    }

    // ==================== Sections ====================

    private Node buildAlignmentGrid() {
        ComboBox<Orientation> orientationBox =
                new ComboBox<>(FXCollections.observableArrayList(Orientation.values()));
        orientationBox.setValue(flow.getOrientation());
        orientationBox.setMaxWidth(Double.MAX_VALUE);
        flow.orientationProperty().bind(orientationBox.valueProperty());

        ComboBox<Pos> alignmentBox = new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignmentBox.setValue(flow.getAlignment());
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        flow.alignmentProperty().bind(alignmentBox.valueProperty());

        ComboBox<HPos> rowHBox = new ComboBox<>(FXCollections.observableArrayList(HPos.values()));
        rowHBox.setValue(flow.getRowHalignment());
        rowHBox.setMaxWidth(Double.MAX_VALUE);
        flow.rowHalignmentProperty().bind(rowHBox.valueProperty());

        ComboBox<VPos> rowVBox = new ComboBox<>(FXCollections.observableArrayList(VPos.values()));
        rowVBox.setValue(flow.getRowValignment());
        rowVBox.setMaxWidth(Double.MAX_VALUE);
        flow.rowValignmentProperty().bind(rowVBox.valueProperty());

        ComboBox<VPos> columnVBox = new ComboBox<>(FXCollections.observableArrayList(VPos.values()));
        columnVBox.setValue(flow.getColumnValignment());
        columnVBox.setMaxWidth(Double.MAX_VALUE);
        flow.columnValignmentProperty().bind(columnVBox.valueProperty());

        ComboBox<HPos> columnHBox = new ComboBox<>(FXCollections.observableArrayList(HPos.values()));
        columnHBox.setValue(flow.getColumnHalignment());
        columnHBox.setMaxWidth(Double.MAX_VALUE);
        flow.columnHalignmentProperty().bind(columnHBox.valueProperty());

        return createGrid(
                row("Orientation", orientationBox),
                row("Alignment (block)", alignmentBox),
                row("Row halignment (H only)", rowHBox),
                row("Row valignment (H only)", rowVBox),
                row("Column valignment (V only)", columnVBox),
                row("Column halignment (V only)", columnHBox));
    }

    private Node buildSpacingGrid() {
        Slider hgapSlider = createSlider(0.0, 40.0, flow.getHgap());
        flow.hgapProperty().bind(hgapSlider.valueProperty());

        Slider vgapSlider = createSlider(0.0, 40.0, flow.getVgap());
        flow.vgapProperty().bind(vgapSlider.valueProperty());

        return createGrid(
                row("Hgap", hgapSlider, createValueLabel(hgapSlider, "%.0f px")),
                row("Vgap", vgapSlider, createValueLabel(vgapSlider, "%.0f px")));
    }

    private Node buildSizingGrid() {
        Slider wrapSlider = createSlider(100.0, 800.0, flow.getPrefWrapLength());
        flow.prefWrapLengthProperty().bind(wrapSlider.valueProperty());

        Label prefLabel = new Label();
        prefLabel.getStyleClass().add("resolved-label");
        prefLabel.textProperty().bind(Bindings.createStringBinding(
                () -> flow.getOrientation() == Orientation.VERTICAL
                        ? String.format("prefHeight(-1) = %.0f px", flow.prefHeight(-1))
                        : String.format("prefWidth(-1) = %.0f px", flow.prefWidth(-1)),
                flow.orientationProperty(), flow.prefWrapLengthProperty(),
                flow.hgapProperty(), flow.vgapProperty(), flow.getChildren()));

        Label note = new Label(
                "prefWrapLength drives the preferred size along the flow (main) axis only "
                        + "— width when horizontal, height when vertical. The live wrap "
                        + "follows the pane's actual size, not this value.");
        note.getStyleClass().add("note-label");
        note.setWrapText(true);

        return createGrid(
                row("Pref wrap length", wrapSlider, createValueLabel(wrapSlider, "%.0f px")),
                row(prefLabel),
                row(note));
    }

    private Node buildContentGrid() {
        Button addButton = new Button("Add chip");
        addButton.setOnAction(e -> flow.getChildren().add(createChip(nextChipIndex++)));

        Button removeButton = new Button("Remove last");
        removeButton.setOnAction(e -> {
            if (!flow.getChildren().isEmpty()) {
                flow.getChildren().remove(flow.getChildren().size() - 1);
            }
        });

        Button resetButton = new Button("Reset");
        resetButton.setOnAction(e -> {
            flow.getChildren().clear();
            nextChipIndex = 0;
            for (int i = 0; i < INITIAL_CHIP_COUNT; i++) {
                flow.getChildren().add(createChip(nextChipIndex++));
            }
        });

        return createGrid(
                row("Chips", addButton, removeButton),
                row(resetButton));
    }

    // ==================== Helpers ====================

    private Region createChip(int index) {
        // Deterministic variety so line/row alignment differences are visible.
        double width = 64.0 + (index % 5) * 24.0;
        double height = 44.0 + (index % 3) * 22.0;

        Label label = new Label("#" + (index + 1));
        label.getStyleClass().add("chip-label");

        StackPane chip = new StackPane(label);
        chip.getStyleClass().add("chip");
        chip.setStyle("-fx-background-color: " + PALETTE[index % PALETTE.length] + ";");
        chip.setPrefSize(width, height);
        chip.setMinSize(width, height);
        return chip;
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

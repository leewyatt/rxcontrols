package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;

import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.util.StringConverter;

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
                section("Animation", buildAnimationGrid()),
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

        // Grey out the pair that has no effect in the current orientation.
        rowHBox.disableProperty().bind(orientationBox.valueProperty().isEqualTo(Orientation.VERTICAL));
        rowVBox.disableProperty().bind(orientationBox.valueProperty().isEqualTo(Orientation.VERTICAL));
        columnVBox.disableProperty().bind(orientationBox.valueProperty().isEqualTo(Orientation.HORIZONTAL));
        columnHBox.disableProperty().bind(orientationBox.valueProperty().isEqualTo(Orientation.HORIZONTAL));

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

        Slider wrapLengthSlider = createSlider(100.0, 1000.0, flow.getPrefWrapLength());
        flow.prefWrapLengthProperty().bind(wrapLengthSlider.valueProperty());

        Label wrapNote = new Label(
                "Pref wrap length only drives the pane's preferred size; the actual "
                        + "wrapping follows the width the parent gives it.");
        wrapNote.getStyleClass().add("note-label");
        wrapNote.setWrapText(true);

        return createGrid(
                row("Hgap", hgapSlider, createValueLabel(hgapSlider, "%.0f px")),
                row("Vgap", vgapSlider, createValueLabel(vgapSlider, "%.0f px")),
                row("Pref wrap length", wrapLengthSlider, createValueLabel(wrapLengthSlider, "%.0f px")),
                row(wrapNote));
    }

    private Node buildAnimationGrid() {
        CheckBox animate = new CheckBox("Animate relayout");
        animate.setSelected(flow.isAnimated());
        flow.animatedProperty().bind(animate.selectedProperty());

        Slider durationSlider = createSlider(0.0, 600.0, flow.getAnimationDuration().toMillis());
        flow.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(durationSlider.getValue()), durationSlider.valueProperty()));

        ComboBox<Interpolator> interpolatorBox = new ComboBox<>(FXCollections.observableArrayList(
                Interpolator.EASE_BOTH, Interpolator.EASE_OUT, Interpolator.EASE_IN, Interpolator.LINEAR));
        interpolatorBox.setValue(flow.getAnimationInterpolator());
        interpolatorBox.setMaxWidth(Double.MAX_VALUE);
        interpolatorBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Interpolator interpolator) {
                return interpolatorName(interpolator);
            }

            @Override
            public Interpolator fromString(String string) {
                return null;
            }
        });
        flow.animationInterpolatorProperty().bind(interpolatorBox.valueProperty());

        Label note = new Label(
                "On by default — change the alignment / gaps or add a chip and the "
                        + "existing chips glide to their new positions while a newly added chip "
                        + "snaps in. Untick to snap every reflow.");
        note.getStyleClass().add("note-label");
        note.setWrapText(true);

        return createGrid(
                row(animate),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Interpolator", interpolatorBox, new Label()),
                row(note));
    }

    private String interpolatorName(Interpolator interpolator) {
        if (interpolator == Interpolator.EASE_BOTH) {
            return "EASE_BOTH";
        }
        if (interpolator == Interpolator.EASE_OUT) {
            return "EASE_OUT";
        }
        if (interpolator == Interpolator.EASE_IN) {
            return "EASE_IN";
        }
        if (interpolator == Interpolator.LINEAR) {
            return "LINEAR";
        }
        return String.valueOf(interpolator);
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

        return createGrid(
                row("Chips", addButton, removeButton));
    }

    // ==================== Helpers ====================

    private Region createChip(int index) {
        // Deterministic variety so the per-run and per-item alignment differences show.
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

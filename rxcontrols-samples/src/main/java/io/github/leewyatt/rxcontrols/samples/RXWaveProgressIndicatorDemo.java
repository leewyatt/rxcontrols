package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Demo for {@link RXWaveProgressIndicator}.
 *
 * <p>Exercises every public knob: progress, indeterminate, custom centre
 * graphic, container/wave/border colours, wave amplitude / wavelength /
 * cycle / back-wave ratios, progress and indeterminate timings, and the
 * optional outer ring. Boundary values (cycle = 0, transition = 0) are
 * reachable via the sliders so the "non-positive disables animation" semantic
 * is directly observable.
 */
public class RXWaveProgressIndicatorDemo extends Application {

    private static final double PREVIEW_SIZE = 140.0;
    private static final double VALUE_LABEL_MIN_WIDTH = 60.0;
    private static final String BORDERED_STYLE_CLASS = "bordered";

    private static final StringConverter<Double> STATE_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(Double progress) {
            if (progress == null || progress < 0.0) {
                return "Loading…";
            }
            if (progress >= 1.0) {
                return "Done!";
            }
            return "Step " + Math.round(progress * 100.0) + "%";
        }

        @Override
        public Double fromString(String value) {
            return null;
        }
    };

    private RXWaveProgressIndicator indicator;
    private Region stateIcon;
    private Slider progressSlider;
    private CheckBox indeterminateBox;

    @Override
    public void start(Stage primaryStage) {
        indicator = new RXWaveProgressIndicator(0.6);
        indicator.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);

        stateIcon = new Region();
        stateIcon.getStyleClass().add("state-icon");
        stateIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(createPreviewPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 1000.0, 680.0);
        scene.getStylesheets().add(
                RXWaveProgressIndicatorDemo.class.getResource("rx_wave_progress_indicator_demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXWaveProgressIndicator Demo");
        primaryStage.show();
    }

    private Node createPreviewPane() {
        StackPane pane = new StackPane(indicator);
        pane.getStyleClass().add("preview-pane");
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private Node createControlPane() {
        Label title = new Label("RXWaveProgressIndicator");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Liquid-fill progress control sample");
        subtitle.getStyleClass().add("subtitle-label");

        // ==================== Progress ====================
        progressSlider = createSlider(0.0, 1.0, indicator.getProgress());
        progressSlider.setMajorTickUnit(0.25);
        progressSlider.setShowTickMarks(true);
        progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!indeterminateBox.isSelected()) {
                indicator.setProgress(newV.doubleValue());
            }
        });
        Label progressValue = new Label();
        progressValue.getStyleClass().add("value-label");
        progressValue.textProperty().bind(
                Bindings.format("%.0f%%", progressSlider.valueProperty().multiply(100.0)));
        progressValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        progressValue.setAlignment(Pos.CENTER_RIGHT);

        HBox jumpButtons = new HBox(8.0,
                jumpBtn("0%", 0.0),
                jumpBtn("50%", 0.5),
                jumpBtn("100%", 1.0));
        jumpButtons.getStyleClass().add("segmented-row");
        jumpButtons.setAlignment(Pos.CENTER_LEFT);

        indeterminateBox = new CheckBox("Indeterminate");
        indeterminateBox.selectedProperty().addListener((obs, oldV, selected) -> {
            progressSlider.setDisable(selected);
            if (selected) {
                indicator.setProgress(RXWaveProgressIndicator.INDETERMINATE_PROGRESS);
            } else {
                indicator.setProgress(progressSlider.getValue());
            }
        });

        CheckBox borderedBox = new CheckBox("Bordered preset");
        borderedBox.selectedProperty().addListener((obs, oldV, selected) -> {
            if (selected) {
                if (!indicator.getStyleClass().contains(BORDERED_STYLE_CLASS)) {
                    indicator.getStyleClass().add(BORDERED_STYLE_CLASS);
                }
            } else {
                indicator.getStyleClass().remove(BORDERED_STYLE_CLASS);
            }
        });

        // ==================== Centre slot toggles ====================
        CheckBox graphicBox = new CheckBox("Custom graphic");
        graphicBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setGraphic(selected ? stateIcon : null));

        CheckBox converterBox = new CheckBox("Custom text");
        converterBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setConverter(selected ? STATE_CONVERTER : null));

        HBox toggleRow = new HBox(18.0, indeterminateBox, borderedBox);
        toggleRow.getStyleClass().add("toggle-row");
        HBox graphicRow = new HBox(18.0, graphicBox, converterBox);
        graphicRow.getStyleClass().add("toggle-row");

        // ==================== Sizing ====================
        Slider sizeSlider = createSlider(40.0, 320.0, PREVIEW_SIZE);
        indicator.prefWidthProperty().bind(sizeSlider.valueProperty());
        indicator.prefHeightProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = createValueLabel(sizeSlider, "%.0f px");

        // ==================== Wave shape ====================
        Slider amplitudeSlider = createSlider(0.0, 20.0,
                RXWaveProgressIndicator.DEFAULT_WAVE_AMPLITUDE);
        indicator.waveAmplitudeProperty().bind(amplitudeSlider.valueProperty());
        Label amplitudeValue = createValueLabel(amplitudeSlider, "%.0f");

        Slider waveLengthSlider = createSlider(0.0, 240.0,
                RXWaveProgressIndicator.DEFAULT_WAVE_LENGTH);
        indicator.waveLengthProperty().bind(waveLengthSlider.valueProperty());
        Label waveLengthValue = createValueLabel(waveLengthSlider, "%.0f");

        Slider waveCycleSlider = createSlider(0.0, 5000.0,
                RXWaveProgressIndicator.DEFAULT_WAVE_CYCLE_DURATION.toMillis());
        waveCycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setWaveCycleDuration(Duration.millis(newV.doubleValue())));
        Label waveCycleValue = createValueLabel(waveCycleSlider, "%.0f ms");

        Slider backSpeedSlider = createSlider(0.5, 3.0,
                RXWaveProgressIndicator.DEFAULT_BACK_WAVE_SPEED_RATIO);
        indicator.backWaveSpeedRatioProperty().bind(backSpeedSlider.valueProperty());
        Label backSpeedValue = createValueLabel(backSpeedSlider, "%.2f×");

        Slider backAmpSlider = createSlider(0.0, 1.5,
                RXWaveProgressIndicator.DEFAULT_BACK_WAVE_AMPLITUDE_RATIO);
        indicator.backWaveAmplitudeRatioProperty().bind(backAmpSlider.valueProperty());
        Label backAmpValue = createValueLabel(backAmpSlider, "%.2f");

        // ==================== Colours ====================
        ColorPicker containerColor = new ColorPicker((Color) RXWaveProgressIndicator.DEFAULT_CONTAINER_FILL);
        containerColor.setMaxWidth(Double.MAX_VALUE);
        indicator.containerFillProperty().bind(containerColor.valueProperty());

        ColorPicker frontColor = new ColorPicker((Color) RXWaveProgressIndicator.DEFAULT_FRONT_WAVE_FILL);
        frontColor.setMaxWidth(Double.MAX_VALUE);
        indicator.frontWaveFillProperty().bind(frontColor.valueProperty());

        ColorPicker backColor = new ColorPicker((Color) RXWaveProgressIndicator.DEFAULT_BACK_WAVE_FILL);
        backColor.setMaxWidth(Double.MAX_VALUE);
        indicator.backWaveFillProperty().bind(backColor.valueProperty());

        // ==================== Border ====================
        ColorPicker borderColor = new ColorPicker(Color.web("#1e90ff"));
        borderColor.setMaxWidth(Double.MAX_VALUE);
        indicator.borderStrokeProperty().bind(borderColor.valueProperty());

        Slider borderWidthSlider = createSlider(0.0, 20.0,
                RXWaveProgressIndicator.DEFAULT_BORDER_STROKE_WIDTH);
        indicator.borderStrokeWidthProperty().bind(borderWidthSlider.valueProperty());
        Label borderWidthValue = createValueLabel(borderWidthSlider, "%.0f");

        Slider borderPaddingSlider = createSlider(0.0, 20.0,
                RXWaveProgressIndicator.DEFAULT_BORDER_PADDING);
        indicator.borderPaddingProperty().bind(borderPaddingSlider.valueProperty());
        Label borderPaddingValue = createValueLabel(borderPaddingSlider, "%.0f");

        // ==================== Timing ====================
        // Both sliders reach 0 to demonstrate the "non-positive disables animation" semantic:
        // wave cycle = 0  → horizontal scroll stops (waves freeze)
        // indeterminate cycle = 0  → breathing stops (level pinned to mid-range)
        // tween = 0  → determinate progress jumps instead of tweening
        Slider tweenSlider = createSlider(0.0, 1000.0,
                RXWaveProgressIndicator.DEFAULT_PROGRESS_TRANSITION_DURATION.toMillis());
        tweenSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setProgressTransitionDuration(Duration.millis(newV.doubleValue())));
        Label tweenValue = createValueLabel(tweenSlider, "%.0f ms");

        Slider indeterminateSlider = createSlider(0.0, 6000.0,
                RXWaveProgressIndicator.DEFAULT_INDETERMINATE_CYCLE_DURATION.toMillis());
        indeterminateSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setIndeterminateCycleDuration(Duration.millis(newV.doubleValue())));
        Label indeterminateValue = createValueLabel(indeterminateSlider, "%.0f ms");

        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("header-block");

        VBox panel = new VBox(14.0,
                header,
                createSection("Progress",
                        createGrid(
                                row("Value", progressSlider, progressValue),
                                row("Jump to", jumpButtons),
                                row(toggleRow))),
                createSection("Centre slot",
                        createGrid(
                                row(graphicRow))),
                createSection("Wave",
                        createGrid(
                                row("Size", sizeSlider, sizeValue),
                                row("Amplitude", amplitudeSlider, amplitudeValue),
                                row("Wavelength", waveLengthSlider, waveLengthValue),
                                row("Cycle", waveCycleSlider, waveCycleValue),
                                row("Back speed", backSpeedSlider, backSpeedValue),
                                row("Back amp", backAmpSlider, backAmpValue))),
                createSection("Colours",
                        createGrid(
                                row("Container", containerColor),
                                row("Front", frontColor),
                                row("Back", backColor))),
                createSection("Border",
                        createGrid(
                                row("Stroke", borderColor),
                                row("Width", borderWidthSlider, borderWidthValue),
                                row("Padding", borderPaddingSlider, borderPaddingValue))),
                createSection("Timing",
                        createGrid(
                                row("Tween", tweenSlider, tweenValue),
                                row("Indeterminate", indeterminateSlider, indeterminateValue))));
        panel.setFillWidth(true);
        panel.getStyleClass().add("control-panel");

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("control-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(440.0);
        return scroll;
    }

    private VBox createSection(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("section-label");

        VBox section = new VBox(10.0, label, content);
        section.getStyleClass().add("section");
        section.setFillWidth(true);
        return section;
    }

    private GridPane createGrid(Node[]... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("control-grid");
        grid.setHgap(12.0);
        grid.setVgap(10.0);

        final double labelColWidth = 112.0;
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(labelColWidth);
        labelCol.setPrefWidth(labelColWidth);
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        grid.getColumnConstraints().addAll(labelCol, controlCol, valueCol);

        for (int i = 0; i < rows.length; i++) {
            Node[] row = rows[i];
            if (row.length == 1) {
                grid.add(row[0], 0, i, 3, 1);
            } else if (row.length == 2) {
                grid.add(row[0], 0, i);
                grid.add(row[1], 1, i, 2, 1);
            } else {
                grid.addRow(i, row);
            }
        }
        return grid;
    }

    private Node[] row(String label, Node control, Node value) {
        return new Node[]{createFieldLabel(label), control, value};
    }

    private Node[] row(String label, Node control) {
        return new Node[]{createFieldLabel(label), control};
    }

    private Node[] row(Node control) {
        return new Node[]{control};
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setMaxWidth(Double.MAX_VALUE);
        return slider;
    }

    private Label createValueLabel(Slider slider, String format) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.textProperty().bind(Bindings.format(format, slider.valueProperty()));
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
    }

    private Button jumpBtn(String text, double target) {
        Button button = new Button(text);
        button.setOnAction(e -> {
            if (indeterminateBox.isSelected()) {
                indeterminateBox.setSelected(false);
            }
            progressSlider.setValue(target);
        });
        return button;
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

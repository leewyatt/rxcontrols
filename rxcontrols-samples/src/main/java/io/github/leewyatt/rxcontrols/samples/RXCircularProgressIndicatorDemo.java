package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
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
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

/**
 * Demo for {@link RXCircularProgressIndicator}.
 *
 * <p>Exercises every public knob: progress, clockwise direction, custom centre
 * graphic, sizing, start angle, track/progress stroke colours and widths,
 * line cap, indeterminate cycle duration, progress transition duration.
 * Boundary values (cycle/tween = 0) are reachable via the sliders so the
 * "non-positive disables animation" semantic is directly observable.
 */
public class RXCircularProgressIndicatorDemo extends Application {

    private static final double PREVIEW_SIZE = 100.0;
    private static final double VALUE_LABEL_MIN_WIDTH = 56.0;

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

    private RXCircularProgressIndicator indicator;
    private Region stateIcon;
    private Slider progressSlider;
    private CheckBox indeterminateBox;

    @Override
    public void start(Stage primaryStage) {
        indicator = new RXCircularProgressIndicator(0.35);
        indicator.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);

        stateIcon = new Region();
        stateIcon.getStyleClass().add("state-icon");
        stateIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(createPreviewPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 960.0, 620.0);
        scene.getStylesheets().add(
                RXCircularProgressIndicatorDemo.class.getResource("rx_circular_progress_indicator_demo.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXCircularProgressIndicator Demo");
        primaryStage.show();
    }

    private Node createPreviewPane() {
        StackPane pane = new StackPane(indicator);
        pane.getStyleClass().add("preview-pane");
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    private Node createControlPane() {
        Label title = new Label("RXCircularProgressIndicator");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Circular progress control sample");
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
                indicator.setProgress(RXCircularProgressIndicator.INDETERMINATE_PROGRESS);
            } else {
                indicator.setProgress(progressSlider.getValue());
            }
        });

        // ==================== Toggles ====================
        CheckBox clockwiseBox = new CheckBox("Clockwise");
        clockwiseBox.selectedProperty().bindBidirectional(indicator.clockwiseProperty());

        CheckBox graphicBox = new CheckBox("Custom graphic");
        graphicBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setGraphic(selected ? stateIcon : null));
        graphicBox.setSelected(true);

        CheckBox converterBox = new CheckBox("Custom text");
        converterBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setConverter(selected ? STATE_CONVERTER : null));
        converterBox.setSelected(true);

        HBox toggleRow = new HBox(18.0, indeterminateBox, clockwiseBox);
        toggleRow.getStyleClass().add("toggle-row");
        HBox graphicRow = new HBox(18.0, graphicBox, converterBox);
        graphicRow.getStyleClass().add("toggle-row");

        // ==================== Sizing & strokes ====================
        Slider sizeSlider = createSlider(40.0, 320.0, PREVIEW_SIZE);
        indicator.prefWidthProperty().bind(sizeSlider.valueProperty());
        indicator.prefHeightProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = createValueLabel(sizeSlider, "%.0f px");

        Slider startAngleSlider = createSlider(0.0, 360.0,
                RXCircularProgressIndicator.DEFAULT_START_ANGLE);
        indicator.startAngleProperty().bind(startAngleSlider.valueProperty());
        Label startAngleValue = createValueLabel(startAngleSlider, "%.0f°");

        Slider trackWidthSlider = createSlider(0.0, 32.0,
                RXCircularProgressIndicator.DEFAULT_TRACK_STROKE_WIDTH);
        indicator.trackStrokeWidthProperty().bind(trackWidthSlider.valueProperty());
        Label trackWidthValue = createValueLabel(trackWidthSlider, "%.0f");

        Slider progressWidthSlider = createSlider(0.0, 32.0,
                RXCircularProgressIndicator.DEFAULT_PROGRESS_STROKE_WIDTH);
        indicator.progressStrokeWidthProperty().bind(progressWidthSlider.valueProperty());
        Label progressWidthValue = createValueLabel(progressWidthSlider, "%.0f");

        ComboBox<StrokeLineCap> lineCapBox = new ComboBox<>();
        lineCapBox.getItems().addAll(StrokeLineCap.values());
        lineCapBox.setValue(RXCircularProgressIndicator.DEFAULT_STROKE_LINE_CAP);
        indicator.strokeLineCapProperty().bind(lineCapBox.valueProperty());
        lineCapBox.setMaxWidth(Double.MAX_VALUE);

        ColorPicker trackColor = new ColorPicker((Color) RXCircularProgressIndicator.DEFAULT_TRACK_STROKE);
        trackColor.setMaxWidth(Double.MAX_VALUE);
        indicator.trackStrokeProperty().bind(trackColor.valueProperty());

        ColorPicker progressColor = new ColorPicker((Color) RXCircularProgressIndicator.DEFAULT_PROGRESS_STROKE);
        progressColor.setMaxWidth(Double.MAX_VALUE);
        indicator.progressStrokeProperty().bind(progressColor.valueProperty());

        // ==================== Timing ====================
        // Both sliders go down to 0 to demonstrate the "non-positive disables animation" semantic:
        // cycle = 0  → indeterminate spinner stops (ring stays static)
        // tween = 0  → determinate progress jumps instead of tweening
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXCircularProgressIndicator.DEFAULT_INDETERMINATE_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setIndeterminateCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider tweenSlider = createSlider(0.0, 1000.0,
                RXCircularProgressIndicator.DEFAULT_PROGRESS_TRANSITION_DURATION.toMillis());
        tweenSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setProgressTransitionDuration(Duration.millis(newV.doubleValue())));
        Label tweenValue = createValueLabel(tweenSlider, "%.0f ms");

        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("header-block");

        VBox panel = new VBox(14.0,
                header,
                createSection("Progress",
                        createGrid(
                                row("Value", progressSlider, progressValue),
                                row("Jump to", jumpButtons),
                                row(toggleRow))),
                createSection("Appearance",
                        createGrid(
                                row(graphicRow),
                                row("Size", sizeSlider, sizeValue),
                                row("Start angle", startAngleSlider, startAngleValue),
                                row("Track width", trackWidthSlider, trackWidthValue),
                                row("Progress width", progressWidthSlider, progressWidthValue),
                                row("Line cap", lineCapBox),
                                row("Track color", trackColor),
                                row("Progress color", progressColor))),
                createSection("Timing",
                        createGrid(
                                row("Cycle", cycleSlider, cycleValue),
                                row("Tween", tweenSlider, tweenValue))));
        panel.setFillWidth(true);
        panel.getStyleClass().add("control-panel");

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("control-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(420.0);
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

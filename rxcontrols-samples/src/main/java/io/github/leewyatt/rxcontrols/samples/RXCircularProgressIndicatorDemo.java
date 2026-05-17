package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demo for {@link RXCircularProgressIndicator}.
 *
 * <p>Exercises every public knob: progress, animated tween, clockwise direction,
 * track/progress stroke colours and widths, line cap, track visibility, custom
 * centre graphic, indeterminate cycle duration.
 */
public class RXCircularProgressIndicatorDemo extends Application {

    private static final String STYLESHEET =
            "/css/rx_circular_progress_indicator_demo.css";

    private static final double SCENE_WIDTH = 940.0;
    private static final double SCENE_HEIGHT = 620.0;
    private static final double PREVIEW_SIZE = 200.0;
    private static final double CONTROL_PANEL_WIDTH = 380.0;
    private static final double LABEL_COLUMN_MIN_WIDTH = 130.0;
    private static final double VALUE_LABEL_MIN_WIDTH = 72.0;

    private static final String DOWNLOAD_SVG =
            "M12 3a1 1 0 0 1 1 1v8.59l2.3-2.3a1 1 0 0 1 1.4 1.42l-4 4a1 1 0 0 1-1.4 0l-4-4"
                    + "a1 1 0 1 1 1.4-1.42L11 12.6V4a1 1 0 0 1 1-1Z"
                    + "M5 19a1 1 0 0 1 1-1h12a1 1 0 0 1 0 2H6a1 1 0 0 1-1-1Z";
    private static final double DOWNLOAD_ICON_SCALE = 1.6;

    private RXCircularProgressIndicator indicator;
    private SVGPath downloadIcon;
    private Slider progressSlider;
    private CheckBox indeterminateBox;

    @Override
    public void start(Stage primaryStage) {
        indicator = new RXCircularProgressIndicator(0.35);
        indicator.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);

        downloadIcon = new SVGPath();
        downloadIcon.setContent(DOWNLOAD_SVG);
        downloadIcon.setScaleX(DOWNLOAD_ICON_SCALE);
        downloadIcon.setScaleY(DOWNLOAD_ICON_SCALE);
        downloadIcon.setFill(Color.web("#3a3f4b"));

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(createPreviewPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, SCENE_WIDTH, SCENE_HEIGHT);
        scene.getStylesheets().add(
                RXCircularProgressIndicatorDemo.class.getResource(STYLESHEET).toExternalForm());
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

        indeterminateBox = new CheckBox();
        indeterminateBox.selectedProperty().addListener((obs, oldV, selected) -> {
            progressSlider.setDisable(selected);
            if (selected) {
                indicator.setProgress(RXCircularProgressIndicator.INDETERMINATE_PROGRESS);
            } else {
                indicator.setProgress(progressSlider.getValue());
            }
        });

        // ==================== Toggles ====================
        CheckBox animatedBox = new CheckBox();
        animatedBox.selectedProperty().bindBidirectional(indicator.animatedProperty());

        CheckBox clockwiseBox = new CheckBox();
        clockwiseBox.selectedProperty().bindBidirectional(indicator.clockwiseProperty());

        CheckBox showTextBox = new CheckBox();
        showTextBox.selectedProperty().bindBidirectional(indicator.showProgressTextProperty());

        CheckBox trackVisibleBox = new CheckBox();
        trackVisibleBox.selectedProperty().bindBidirectional(indicator.trackVisibleProperty());

        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setGraphic(selected ? downloadIcon : null));

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
        Slider cycleSlider = createSlider(500.0, 4000.0,
                RXCircularProgressIndicator.DEFAULT_INDETERMINATE_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setIndeterminateCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider tweenSlider = createSlider(0.0, 1000.0,
                RXCircularProgressIndicator.DEFAULT_PROGRESS_TRANSITION_DURATION.toMillis());
        tweenSlider.valueProperty().addListener((obs, oldV, newV) ->
                indicator.setProgressTransitionDuration(Duration.millis(newV.doubleValue())));
        Label tweenValue = createValueLabel(tweenSlider, "%.0f ms");

        // ==================== Grid ====================
        GridPane grid = new GridPane();
        grid.setHgap(8.0);
        grid.setVgap(10.0);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(LABEL_COLUMN_MIN_WIDTH);
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        ColumnConstraints valueCol = new ColumnConstraints();
        grid.getColumnConstraints().addAll(labelCol, controlCol, valueCol);

        int row = 0;
        grid.addRow(row++, new Label("Progress"), progressSlider, progressValue);
        grid.addRow(row++, new Label("Indeterminate"), indeterminateBox);
        addSeparator(grid, row++);
        grid.addRow(row++, new Label("Animated tween"), animatedBox);
        grid.addRow(row++, new Label("Clockwise"), clockwiseBox);
        grid.addRow(row++, new Label("Show text"), showTextBox);
        grid.addRow(row++, new Label("Track visible"), trackVisibleBox);
        grid.addRow(row++, new Label("Centre graphic"), graphicBox);
        addSeparator(grid, row++);
        grid.addRow(row++, new Label("Size"), sizeSlider, sizeValue);
        grid.addRow(row++, new Label("Start angle"), startAngleSlider, startAngleValue);
        grid.addRow(row++, new Label("Track width"), trackWidthSlider, trackWidthValue);
        grid.addRow(row++, new Label("Progress width"), progressWidthSlider, progressWidthValue);
        grid.addRow(row++, new Label("Line cap"), lineCapBox);
        grid.addRow(row++, new Label("Track color"), trackColor);
        grid.addRow(row++, new Label("Progress color"), progressColor);
        addSeparator(grid, row++);
        grid.addRow(row++, new Label("Cycle"), cycleSlider, cycleValue);
        grid.addRow(row, new Label("Tween"), tweenSlider, tweenValue);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label tips = new Label(
                "Drag the Progress slider or toggle Indeterminate to drive the indicator.\n"
                        + "Minimising the window pauses the animation automatically.");
        tips.getStyleClass().add("hint-label");
        tips.setWrapText(true);

        VBox panel = new VBox(10.0, title, new Separator(), grid, spacer, new Separator(), tips);
        panel.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("control-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(CONTROL_PANEL_WIDTH);
        return scroll;
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

    private void addSeparator(GridPane grid, int row) {
        Separator separator = new Separator();
        GridPane.setMargin(separator, new Insets(2.0, 0.0, 2.0, 0.0));
        grid.add(separator, 0, row, 3, 1);
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

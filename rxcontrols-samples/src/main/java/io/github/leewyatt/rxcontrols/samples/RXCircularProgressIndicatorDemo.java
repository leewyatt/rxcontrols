package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final int CAPTURE_FRAMES = 20;

    private RXCircularProgressIndicator indicator;
    private SVGPath downloadIcon;
    private Slider progressSlider;
    private CheckBox indeterminateBox;
    private Button captureButton;
    private Label statusLabel;

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

        HBox jumpButtons = new HBox(8.0,
                jumpBtn("Reset", 0.0),
                jumpBtn("50%", 0.5),
                jumpBtn("100%", 1.0));
        jumpButtons.setAlignment(Pos.CENTER_LEFT);

        indeterminateBox = new CheckBox();
        indeterminateBox.selectedProperty().addListener((obs, oldV, selected) -> {
            progressSlider.setDisable(selected);
            if (selected) {
                indicator.setProgress(RXCircularProgressIndicator.INDETERMINATE_PROGRESS);
            } else {
                indicator.setProgress(progressSlider.getValue());
            }
        });

        captureButton = new Button("Capture cycle (" + CAPTURE_FRAMES + " frames)");
        captureButton.setOnAction(e -> captureIndeterminateCycle());

        HBox indeterminateRow = new HBox(10.0, indeterminateBox, captureButton);
        indeterminateRow.setAlignment(Pos.CENTER_LEFT);

        // ==================== Toggles ====================
        CheckBox clockwiseBox = new CheckBox();
        clockwiseBox.selectedProperty().bindBidirectional(indicator.clockwiseProperty());

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
        grid.addRow(row++, new Label("Jump to"), jumpButtons);
        grid.addRow(row++, new Label("Indeterminate"), indeterminateRow);
        addSeparator(grid, row++);
        grid.addRow(row++, new Label("Clockwise"), clockwiseBox);
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
                "Drag the Progress slider, click Jump-to buttons to see the tween,\n"
                        + "or toggle Indeterminate to drive the spinner.\n"
                        + "Minimising the window pauses the animation automatically.");
        tips.getStyleClass().add("hint-label");
        tips.setWrapText(true);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("hint-label");
        statusLabel.setWrapText(true);

        VBox panel = new VBox(10.0,
                title, new Separator(), grid, spacer, new Separator(), tips, statusLabel);
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

    private void captureIndeterminateCycle() {
        if (!indeterminateBox.isSelected()) {
            indeterminateBox.setSelected(true);
        }

        Duration cycle = indicator.getIndeterminateCycleDuration();
        if (cycle == null || cycle.lessThanOrEqualTo(Duration.ZERO)) {
            // Indeterminate animation is disabled — capturing would just snapshot
            // the same static frame N times, which would be misleading.
            statusLabel.setText("Capture skipped: indeterminate animation is disabled (cycle ≤ 0).");
            return;
        }

        Path outDir;
        try {
            outDir = Files.createTempDirectory("rxcpi-snapshots-");
        } catch (IOException ex) {
            statusLabel.setText("Capture failed: " + ex.getMessage());
            return;
        }

        captureButton.setDisable(true);
        statusLabel.setText("Capturing 0/" + CAPTURE_FRAMES + "...");

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.WHITE);

        Path finalOutDir = outDir;
        Timeline capture = new Timeline();
        for (int i = 0; i < CAPTURE_FRAMES; i++) {
            final int idx = i;
            Duration t = cycle.multiply((double) i / (double) (CAPTURE_FRAMES - 1));
            capture.getKeyFrames().add(new KeyFrame(t,
                    e -> takeFrame(params, finalOutDir, idx)));
        }
        capture.setOnFinished(e -> {
            captureButton.setDisable(false);
            statusLabel.setText("Saved " + CAPTURE_FRAMES + " frames to:\n" + finalOutDir);
            openInFileManager(finalOutDir);
        });
        capture.play();
    }

    private void takeFrame(SnapshotParameters params, Path dir, int idx) {
        WritableImage img = indicator.snapshot(params, null);
        Path file = dir.resolve(String.format("frame_%02d.png", idx));
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", file.toFile());
            statusLabel.setText("Capturing " + (idx + 1) + "/" + CAPTURE_FRAMES + "...");
        } catch (IOException ex) {
            statusLabel.setText("Frame " + idx + " failed: " + ex.getMessage());
        }
    }

    private void openInFileManager(Path dir) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(dir.toFile());
        } catch (IOException ignored) {
            // best-effort only; status label already shows the path
        }
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

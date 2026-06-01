package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.samples.demo.RXCircularProgressIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXCircularProgressIndicator}.
 *
 * <p>Exercises every public knob: progress, clockwise direction, custom centre
 * graphic, sizing, start angle, track/progress stroke colours and widths,
 * line cap, indeterminate cycle duration, progress transition duration.
 * Boundary values (cycle/tween = 0) are reachable via the sliders so the
 * "non-positive disables animation" semantic is directly observable.
 *
 * <p>For a minimal "few lines of code" example see
 * {@link RXCircularProgressIndicatorDemo}.
 */
public class RXCircularProgressIndicatorShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_SIZE = 100.0;

    private static final Callback<Double, String> STATE_TEXT_FACTORY = progress -> {
        if (progress == null || progress < 0.0) {
            return "Loading…";
        }
        if (progress >= 1.0) {
            return "Done!";
        }
        return "Step " + Math.round(progress * 100.0) + "%";
    };

    private RXCircularProgressIndicator indicator;
    private Region stateIcon;
    private Slider progressSlider;
    private CheckBox indeterminateBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCircularProgressIndicator";
    }

    @Override
    protected String subtitle() {
        return "Circular progress control sample";
    }

    @Override
    protected String windowTitle() {
        return "RXCircularProgressIndicator Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 960.0;
    }

    @Override
    protected double sceneHeight() {
        return 620.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-circular-progress-indicator-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        indicator = new RXCircularProgressIndicator(0.35);
        indicator.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);

        stateIcon = new Region();
        stateIcon.getStyleClass().add("state-icon");
        stateIcon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return indicator;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Progress", buildProgressGrid()),
                section("Appearance", buildAppearanceGrid()),
                section("Timing", buildTimingGrid()));
    }

    // ==================== Sections ====================

    private Node buildProgressGrid() {
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

        CheckBox clockwiseBox = new CheckBox("Clockwise");
        clockwiseBox.selectedProperty().bindBidirectional(indicator.clockwiseProperty());

        HBox toggleRow = new HBox(18.0, indeterminateBox, clockwiseBox);
        toggleRow.getStyleClass().add("toggle-row");

        return createGrid(
                row("Value", progressSlider, progressValue),
                row("Jump to", jumpButtons),
                row(toggleRow));
    }

    private Node buildAppearanceGrid() {
        CheckBox graphicBox = new CheckBox("Custom graphic");
        graphicBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setGraphic(selected ? stateIcon : null));
        graphicBox.setSelected(true);

        CheckBox customTextBox = new CheckBox("Custom text");
        customTextBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setTextFactory(selected ? STATE_TEXT_FACTORY : null));
        customTextBox.setSelected(true);

        HBox graphicRow = new HBox(18.0, graphicBox, customTextBox);
        graphicRow.getStyleClass().add("toggle-row");

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

        return createGrid(
                row(graphicRow),
                row("Size", sizeSlider, sizeValue),
                row("Start angle", startAngleSlider, startAngleValue),
                row("Track width", trackWidthSlider, trackWidthValue),
                row("Progress width", progressWidthSlider, progressWidthValue),
                row("Line cap", lineCapBox),
                row("Track color", trackColor),
                row("Progress color", progressColor));
    }

    private Node buildTimingGrid() {
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

        return createGrid(
                row("Cycle", cycleSlider, cycleValue),
                row("Tween", tweenSlider, tweenValue));
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
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

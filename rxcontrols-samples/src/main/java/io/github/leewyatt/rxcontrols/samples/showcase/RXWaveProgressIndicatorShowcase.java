package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.samples.demo.RXWaveProgressIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXWaveProgressIndicator}.
 *
 * <p>Exercises every public knob: progress, indeterminate, custom text
 * factory, wave colours, wave amplitude / wavelength / cycle / back-wave
 * ratios, progress and indeterminate timings. Container background and the
 * outer ring are styled purely via CSS on the skin-internal
 * {@code .wave-container} child node — see the default rules in
 * {@code rx-controls.css}; users override by adding rules like
 * {@code .my-control > .wave-container { -fx-border-color: ...; }}.
 * Boundary values (cycle = 0, transition = 0) are reachable via the sliders
 * so the "non-positive disables animation" semantic is directly observable.
 *
 * <p>For a minimal "few lines of code" example see
 * {@link RXWaveProgressIndicatorDemo}.
 */
public class RXWaveProgressIndicatorShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_SIZE = 140.0;

    private static final Callback<Double, String> STATE_TEXT_FACTORY = progress -> {
        if (progress == null || progress < 0.0) {
            return "Loading…";
        }
        if (progress >= 1.0) {
            return "Done!";
        }
        return "Step " + Math.round(progress * 100.0) + "%";
    };

    private RXWaveProgressIndicator indicator;
    private Slider progressSlider;
    private CheckBox indeterminateBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXWaveProgressIndicator";
    }

    @Override
    protected String subtitle() {
        return "Liquid-fill progress control sample";
    }

    @Override
    protected String windowTitle() {
        return "RXWaveProgressIndicator Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1000.0;
    }

    @Override
    protected double sceneHeight() {
        return 680.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 440.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_wave_progress_indicator_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        indicator = new RXWaveProgressIndicator(0.6);
        indicator.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);
        return indicator;
    }

    @Override
    protected List<Section> createSections() {
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

        CheckBox customTextBox = new CheckBox("Custom text");
        customTextBox.selectedProperty().addListener((obs, oldV, selected) ->
                indicator.setTextFactory(selected ? STATE_TEXT_FACTORY : null));

        HBox toggleRow = new HBox(18.0, indeterminateBox, customTextBox);
        toggleRow.getStyleClass().add("toggle-row");

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
        ColorPicker frontColor = new ColorPicker((Color) RXWaveProgressIndicator.DEFAULT_FRONT_WAVE_FILL);
        frontColor.setMaxWidth(Double.MAX_VALUE);
        indicator.frontWaveFillProperty().bind(frontColor.valueProperty());

        ColorPicker backColor = new ColorPicker((Color) RXWaveProgressIndicator.DEFAULT_BACK_WAVE_FILL);
        backColor.setMaxWidth(Double.MAX_VALUE);
        indicator.backWaveFillProperty().bind(backColor.valueProperty());

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

        return List.of(
                section("Progress",
                        createGrid(
                                row("Value", progressSlider, progressValue),
                                row("Jump to", jumpButtons),
                                row(toggleRow))),
                section("Wave",
                        createGrid(
                                row("Size", sizeSlider, sizeValue),
                                row("Amplitude", amplitudeSlider, amplitudeValue),
                                row("Wavelength", waveLengthSlider, waveLengthValue),
                                row("Cycle", waveCycleSlider, waveCycleValue),
                                row("Back speed", backSpeedSlider, backSpeedValue),
                                row("Back amp", backAmpSlider, backAmpValue))),
                section("Colours",
                        createGrid(
                                row("Front", frontColor),
                                row("Back", backColor))),
                section("Timing",
                        createGrid(
                                row("Tween", tweenSlider, tweenValue),
                                row("Indeterminate", indeterminateSlider, indeterminateValue))));
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

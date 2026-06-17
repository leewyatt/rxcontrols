package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.spectrum.BandLayout;
import io.github.leewyatt.rxcontrols.spectrum.SpectrumVisualization;
import io.github.leewyatt.rxcontrols.spectrum.VisBars;
import io.github.leewyatt.rxcontrols.spectrum.VisBarsMirror;
import io.github.leewyatt.rxcontrols.spectrum.VisLine;
import io.github.leewyatt.rxcontrols.spectrum.VisRadial;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.Animation;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Showcase application for {@code RXAudioSpectrum}. Drives the control with
 * synthetic data sources (sine / noise / sweep / silence, plus a one-shot NaN
 * bad frame) at selectable feed rates, switches between the four V1
 * visualizations with their effect-local parameters, exposes every styleable
 * property, and displays an FPS / frame-time readout from an independent
 * AnimationTimer so render cost and the idle self-suspension are observable.
 */
public class RXAudioSpectrumShowcase extends RXShowcaseApplication {

    private static final int RAW_BANDS = 128;

    private static final double SOURCE_MIN_DB = -60.0;

    private enum Source { SINE, NOISE, SWEEP, SILENCE, NONE }

    private final Random random = new Random();

    private final float[] frame = new float[RAW_BANDS];

    private RXAudioSpectrum spectrum;

    private Source source = Source.SINE;

    private double feedRateHz = 10.0;

    private double sourceTime;

    private Timeline feeder;

    private AnimationTimer fpsMeter;

    private Label fpsLabel;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXAudioSpectrum";
    }

    @Override
    protected String subtitle() {
        return "Canvas audio spectrum with pluggable visualizations";
    }

    @Override
    protected String windowTitle() {
        return "RXAudioSpectrum Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1080.0;
    }

    @Override
    protected double sceneHeight() {
        return 700.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-audio-spectrum-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        spectrum = new RXAudioSpectrum();
        spectrum.setPrefSize(540.0, 240.0);

        StackPane stage = new StackPane(spectrum);
        stage.getStyleClass().add("spectrum-stage");

        Label stateLabel = new Label();
        stateLabel.getStyleClass().add("state-label");
        stateLabel.textProperty().bind(Bindings.when(spectrum.activeProperty())
                .then("state: active").otherwise("state: silent"));

        fpsLabel = new Label("fps: --");
        fpsLabel.getStyleClass().add("fps-label");

        VBox box = new VBox(10.0, stage, stateLabel, fpsLabel);
        box.setAlignment(Pos.CENTER);

        startFeeder();
        startFpsMeter();
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Data source", buildSourceGrid()),
                section("Visualization", buildVisualizationGrid()),
                section("Properties", buildPropertiesGrid()));
    }

    // ==================== Synthetic data source ====================

    private void startFeeder() {
        if (feeder != null) {
            feeder.stop();
        }
        feeder = new Timeline(new KeyFrame(Duration.seconds(1.0 / feedRateHz), e -> feedFrame()));
        feeder.setCycleCount(Animation.INDEFINITE);
        feeder.play();
    }

    private void feedFrame() {
        if (source == Source.NONE) {
            return;
        }
        sourceTime += 1.0 / feedRateHz;
        for (int i = 0; i < RAW_BANDS; i++) {
            frame[i] = (float) (SOURCE_MIN_DB * (1.0 - levelAt(i)));
        }
        spectrum.updateSpectrum(frame);
    }

    private double levelAt(int i) {
        double position = i / (double) RAW_BANDS;
        return switch (source) {
            case SINE -> {
                double wave = Math.max(0.0, Math.sin(sourceTime * 4.0 + i * 0.25));
                double tilt = 1.0 - 0.6 * position;
                double pulse = 0.6 + 0.4 * Math.sin(sourceTime * 2.0 * Math.PI * 0.4);
                yield wave * tilt * pulse;
            }
            case NOISE -> {
                double sample = random.nextDouble();
                yield sample * sample * (1.0 - 0.4 * position);
            }
            case SWEEP -> {
                double center = (sourceTime * 0.25 % 1.0) * RAW_BANDS;
                double sigma = RAW_BANDS * 0.06;
                double distance = i - center;
                yield Math.exp(-(distance * distance) / (2.0 * sigma * sigma));
            }
            case SILENCE -> 0.0;
            case NONE -> 0.0;
        };
    }

    private void feedBadFrame() {
        for (int i = 0; i < RAW_BANDS; i++) {
            frame[i] = i % 3 == 0 ? Float.NaN : -30.0f;
        }
        spectrum.updateSpectrum(frame);
    }

    private void startFpsMeter() {
        fpsMeter = new AnimationTimer() {
            private long windowStart;
            private long lastFrame;
            private int frames;
            private double worstMillis;

            @Override
            public void handle(long now) {
                if (windowStart == 0) {
                    windowStart = now;
                    lastFrame = now;
                    return;
                }
                frames++;
                worstMillis = Math.max(worstMillis, (now - lastFrame) / 1.0e6);
                lastFrame = now;
                if (now - windowStart >= 1_000_000_000L) {
                    double seconds = (now - windowStart) / 1.0e9;
                    fpsLabel.setText(String.format("fps: %.0f · worst frame: %.1f ms",
                            frames / seconds, worstMillis));
                    windowStart = now;
                    frames = 0;
                    worstMillis = 0.0;
                }
            }
        };
        fpsMeter.start();
    }

    // ==================== Sections ====================

    private Node buildSourceGrid() {
        ToggleGroup sourceGroup = new ToggleGroup();
        FlowPane sourcePane = new FlowPane(8.0, 8.0);
        for (Source value : Source.values()) {
            RadioButton button = new RadioButton(value.name().toLowerCase());
            button.setToggleGroup(sourceGroup);
            button.setUserData(value);
            button.setSelected(value == source);
            sourcePane.getChildren().add(button);
        }
        sourceGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                source = (Source) newToggle.getUserData();
            }
        });

        ChoiceBox<Double> rateBox = new ChoiceBox<>();
        rateBox.getItems().addAll(10.0, 20.0, 50.0);
        rateBox.setValue(feedRateHz);
        rateBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                return value == null ? "" : String.format("%.0f Hz", value);
            }

            @Override
            public Double fromString(String string) {
                return null;
            }
        });
        rateBox.valueProperty().addListener((obs, oldRate, newRate) -> {
            feedRateHz = newRate;
            startFeeder();
        });

        Button badFrameButton = new Button("Feed NaN bad frame");
        badFrameButton.setOnAction(e -> feedBadFrame());

        // The meter's AnimationTimer keeps the pulse loop alive; switch it off
        // to observe the idle self-suspension dropping CPU to zero.
        CheckBox meterBox = new CheckBox("FPS meter");
        meterBox.setSelected(true);
        meterBox.selectedProperty().addListener((obs, wasOn, on) -> {
            if (on) {
                startFpsMeter();
            } else {
                fpsMeter.stop();
                fpsLabel.setText("fps: --");
            }
        });

        return createGrid(
                row("Source", sourcePane),
                row("Feed rate", rateBox),
                row("Resilience", badFrameButton),
                row("Stats", meterBox));
    }

    private Node buildVisualizationGrid() {
        VisBars bars = new VisBars();
        VisBarsMirror mirror = new VisBarsMirror();
        VisLine line = new VisLine();
        VisRadial radial = new VisRadial();

        Map<String, SpectrumVisualization> visualizations = new LinkedHashMap<>();
        visualizations.put("Bars", bars);
        visualizations.put("Bars mirror", mirror);
        visualizations.put("Line", line);
        visualizations.put("Radial", radial);

        ChoiceBox<String> visBox = new ChoiceBox<>();
        visBox.getItems().addAll(visualizations.keySet());
        visBox.setValue("Bars");
        visBox.setMaxWidth(Double.MAX_VALUE);
        visBox.valueProperty().addListener((obs, oldName, newName) ->
                spectrum.setVisualization(visualizations.get(newName)));
        spectrum.setVisualization(bars);

        Slider lineWidthSlider = createSlider(0.5, 8.0, line.getLineWidth());
        lineWidthSlider.valueProperty().addListener((obs, oldV, newV) ->
                line.setLineWidth(newV.doubleValue()));
        Label lineWidthValue = createValueLabel(lineWidthSlider, "%.1f px");

        Slider innerRadiusSlider = createSlider(0.0, 0.95, radial.getInnerRadiusRatio());
        innerRadiusSlider.valueProperty().addListener((obs, oldV, newV) ->
                radial.setInnerRadiusRatio(newV.doubleValue()));
        Label innerRadiusValue = createValueLabel(innerRadiusSlider, "%.2f");

        Slider startAngleSlider = createSlider(-180.0, 180.0, radial.getStartAngle());
        startAngleSlider.valueProperty().addListener((obs, oldV, newV) ->
                radial.setStartAngle(newV.doubleValue()));
        Label startAngleValue = createValueLabel(startAngleSlider, "%.0f°");

        return createGrid(
                row("Effect", visBox),
                row("Line width", lineWidthSlider, lineWidthValue),
                row("Inner radius", innerRadiusSlider, innerRadiusValue),
                row("Start angle", startAngleSlider, startAngleValue));
    }

    private Node buildPropertiesGrid() {
        Slider bandCountSlider = createSlider(32.0, 512.0, spectrum.getBandCount());
        bandCountSlider.valueProperty().addListener((obs, oldV, newV) ->
                spectrum.setBandCount(newV.intValue()));
        Label bandCountValue = createValueLabel(bandCountSlider, "%.0f");

        Slider smoothingSlider = createSlider(0.0, RXAudioSpectrum.MAX_SMOOTHING,
                RXAudioSpectrum.DEFAULT_SMOOTHING);
        spectrum.smoothingProperty().bind(smoothingSlider.valueProperty());
        Label smoothingValue = createValueLabel(smoothingSlider, "%.2f");

        Slider gapSlider = createSlider(0.0, RXAudioSpectrum.MAX_BAR_GAP_RATIO,
                RXAudioSpectrum.DEFAULT_BAR_GAP_RATIO);
        spectrum.barGapRatioProperty().bind(gapSlider.valueProperty());
        Label gapValue = createValueLabel(gapSlider, "%.2f");

        Slider minDbSlider = createSlider(-90.0, -10.0, spectrum.getMinDecibels());
        minDbSlider.valueProperty().addListener((obs, oldV, newV) ->
                spectrum.setMinDecibels(newV.doubleValue()));
        Label minDbValue = createValueLabel(minDbSlider, "%.0f dB");

        ChoiceBox<BandLayout> layoutBox = new ChoiceBox<>();
        layoutBox.getItems().addAll(BandLayout.values());
        layoutBox.setValue(spectrum.getBandLayout());
        layoutBox.setMaxWidth(Double.MAX_VALUE);
        spectrum.bandLayoutProperty().bind(layoutBox.valueProperty());

        CheckBox peaksBox = new CheckBox("Show peaks");
        peaksBox.setSelected(spectrum.isShowPeaks());
        spectrum.showPeaksProperty().bind(peaksBox.selectedProperty());

        CheckBox glowBox = new CheckBox("Glow");
        glowBox.setSelected(spectrum.isGlow());
        spectrum.glowProperty().bind(glowBox.selectedProperty());

        return createGrid(
                row("Band count", bandCountSlider, bandCountValue),
                row("Smoothing", smoothingSlider, smoothingValue),
                row("Bar gap", gapSlider, gapValue),
                row("Min decibels", minDbSlider, minDbValue),
                row("Band layout", layoutBox),
                row(peaksBox),
                row(glowBox));
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

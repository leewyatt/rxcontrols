package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXDotPulse.PulseStyle;
import io.github.leewyatt.rxcontrols.samples.demo.RXDotPulseDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXDotPulse}.
 *
 * <p>Exercises every public knob: pulse style, dot count / size / gap, dot
 * color, cycle duration, amplitude. Boundary values (cycle = 0, amplitude = 0)
 * are reachable via the sliders so the "non-positive disables animation"
 * semantic and "amplitude = 0 flattens the pulse" semantic are directly
 * observable.
 *
 * <p>The preview pane also embeds an inline-text usage and a "button graphic"
 * usage so the typical compositions are visible alongside the property panel.
 * For a minimal "few lines of code" example see {@link RXDotPulseDemo}.
 */
public class RXDotPulseShowcase extends RXShowcaseApplication {

    private RXDotPulse mainIndicator;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDotPulse";
    }

    @Override
    protected String subtitle() {
        return "Staggered-phase dots loading indicator";
    }

    @Override
    protected String windowTitle() {
        return "RXDotPulse Showcase";
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
        return getClass().getResource("rx_dot_pulse_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        mainIndicator = new RXDotPulse();

        VBox stack = new VBox(28.0,
                buildCard("Standalone",
                        new StackPane(mainIndicator)),
                buildCard("Inline with text",
                        buildInlineExample()),
                buildCard("Button graphic",
                        buildButtonExample()));
        stack.setAlignment(Pos.CENTER);
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Behaviour", buildBehaviourGrid()),
                section("Geometry", buildGeometryGrid()),
                section("Appearance", buildAppearanceGrid()),
                section("Timing", buildTimingGrid()));
    }

    // ==================== Preview helpers ====================

    private VBox buildCard(String caption, Node body) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("caption");
        StackPane bodyPane = new StackPane(body);
        bodyPane.setAlignment(Pos.CENTER_LEFT);
        bodyPane.setMinHeight(36.0);
        VBox card = new VBox(8.0, captionLabel, bodyPane);
        card.getStyleClass().add("preview-card");
        return card;
    }

    private Node buildInlineExample() {
        RXDotPulse inline = new RXDotPulse();
        inline.dotColorProperty().bind(mainIndicator.dotColorProperty());
        inline.cycleDurationProperty().bind(mainIndicator.cycleDurationProperty());
        inline.pulseStyleProperty().bind(mainIndicator.pulseStyleProperty());
        inline.amplitudeProperty().bind(mainIndicator.amplitudeProperty());
        Label leading = new Label("User is typing");
        leading.getStyleClass().add("inline-text");
        HBox row = new HBox(8.0, leading, inline);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildButtonExample() {
        RXDotPulse inButton = new RXDotPulse();
        inButton.cycleDurationProperty().bind(mainIndicator.cycleDurationProperty());
        inButton.pulseStyleProperty().bind(mainIndicator.pulseStyleProperty());
        inButton.amplitudeProperty().bind(mainIndicator.amplitudeProperty());
        inButton.setDotColor(Color.WHITE);
        inButton.setDotSize(6.0);
        inButton.setDotGap(4.0);

        Button loadingBtn = new Button();
        loadingBtn.getStyleClass().add("loading-button");
        loadingBtn.setGraphic(inButton);
        loadingBtn.setText("Loading");
        loadingBtn.setContentDisplay(ContentDisplay.RIGHT);
        loadingBtn.setGraphicTextGap(10.0);
        return loadingBtn;
    }

    // ==================== Sections ====================

    private Node buildBehaviourGrid() {
        ChoiceBox<PulseStyle> styleBox = new ChoiceBox<>();
        styleBox.getItems().addAll(PulseStyle.values());
        styleBox.setValue(mainIndicator.getPulseStyle());
        styleBox.setMaxWidth(Double.MAX_VALUE);
        mainIndicator.pulseStyleProperty().bind(styleBox.valueProperty());

        Slider amplitudeSlider = createSlider(0.0, 3.0, RXDotPulse.DEFAULT_AMPLITUDE);
        mainIndicator.amplitudeProperty().bind(amplitudeSlider.valueProperty());
        Label amplitudeValue = createValueLabel(amplitudeSlider, "%.2f");

        return createGrid(
                row("Pulse style", styleBox),
                row("Amplitude", amplitudeSlider, amplitudeValue));
    }

    private Node buildGeometryGrid() {
        Slider countSlider = createSlider(RXDotPulse.MIN_DOT_COUNT, RXDotPulse.MAX_DOT_COUNT,
                RXDotPulse.DEFAULT_DOT_COUNT);
        countSlider.setSnapToTicks(true);
        countSlider.setMajorTickUnit(1.0);
        countSlider.setMinorTickCount(0);
        countSlider.setShowTickMarks(true);
        countSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainIndicator.setDotCount(newV.intValue()));
        Label countValue = createValueLabel(countSlider, "%.0f");

        Slider sizeSlider = createSlider(4.0, 32.0, RXDotPulse.DEFAULT_DOT_SIZE);
        mainIndicator.dotSizeProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = createValueLabel(sizeSlider, "%.0f px");

        Slider gapSlider = createSlider(0.0, 32.0, RXDotPulse.DEFAULT_DOT_GAP);
        mainIndicator.dotGapProperty().bind(gapSlider.valueProperty());
        Label gapValue = createValueLabel(gapSlider, "%.0f px");

        return createGrid(
                row("Dot count", countSlider, countValue),
                row("Dot size", sizeSlider, sizeValue),
                row("Dot gap", gapSlider, gapValue));
    }

    private Node buildAppearanceGrid() {
        ColorPicker colorPicker = new ColorPicker((Color) RXDotPulse.DEFAULT_DOT_COLOR);
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        mainIndicator.dotColorProperty().bind(colorPicker.valueProperty());

        return createGrid(
                row("Dot color", colorPicker));
    }

    private Node buildTimingGrid() {
        // The cycle slider reaches 0 to demonstrate the "non-positive disables
        // animation" semantic — the dots snap to their rest pose.
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXDotPulse.DEFAULT_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainIndicator.setCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        return createGrid(
                row("Cycle", cycleSlider, cycleValue));
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

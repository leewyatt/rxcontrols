package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.samples.demo.RXSegmentedProgressBarDemo;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXSegmentedProgressBar}.
 *
 * <p>Exercises every public knob: segment count / gap / height / arc, filled
 * and unfilled colours, indeterminate cycle and band ratio, and the progress
 * transition tween. Boundary values (cycle = 0, transition = 0) are reachable
 * via the sliders so the "non-positive disables animation" semantic is
 * directly observable.
 *
 * <p>The preview pane embeds three usages: a standalone bar, a Stories-style
 * dense bar with many short segments, and an inline composition with a
 * "Step n of N" label. For a minimal "few lines of code" example see
 * {@link RXSegmentedProgressBarDemo}.
 */
public class RXSegmentedProgressBarShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 360.0;

    private RXSegmentedProgressBar mainBar;
    private CheckBox indeterminateBox;
    private Slider progressSlider;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSegmentedProgressBar";
    }

    @Override
    protected String subtitle() {
        return "Stories-style segmented progress bar";
    }

    @Override
    protected String windowTitle() {
        return "RXSegmentedProgressBar Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1000.0;
    }

    @Override
    protected double sceneHeight() {
        return 640.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 430.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_segmented_progress_bar_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        mainBar = new RXSegmentedProgressBar(0.4);
        mainBar.setPrefWidth(PREVIEW_WIDTH);

        VBox stack = new VBox(28.0,
                buildCard("Standalone", new StackPane(mainBar)),
                buildCard("Stories-style (10 segments)", buildStoriesExample()),
                buildCard("Inline with step label", buildInlineExample()));
        stack.setAlignment(Pos.CENTER);
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Progress", buildProgressGrid()),
                section("Geometry", buildGeometryGrid()),
                section("Appearance", buildAppearanceGrid()),
                section("Timing", buildTimingGrid()));
    }

    // ==================== Preview helpers ====================

    private VBox buildCard(String caption, Node body) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("caption");
        StackPane bodyPane = new StackPane(body);
        bodyPane.setAlignment(Pos.CENTER);
        bodyPane.setMinHeight(36.0);
        VBox card = new VBox(8.0, captionLabel, bodyPane);
        card.getStyleClass().add("preview-card");
        return card;
    }

    private Node buildStoriesExample() {
        RXSegmentedProgressBar stories = new RXSegmentedProgressBar();
        stories.setPrefWidth(PREVIEW_WIDTH);
        stories.setSegmentCount(10);
        stories.setSegmentHeight(4.0);
        stories.setSegmentArc(2.0);
        stories.setSegmentGap(3.0);
        // Slave the Stories bar to the main bar so the showcase reads as a
        // single demo, not three independent controls.
        stories.progressProperty().bind(mainBar.progressProperty());
        stories.filledColorProperty().bind(mainBar.filledColorProperty());
        stories.unfilledColorProperty().bind(mainBar.unfilledColorProperty());
        stories.indeterminateCycleDurationProperty().bind(mainBar.indeterminateCycleDurationProperty());
        stories.indeterminateBandRatioProperty().bind(mainBar.indeterminateBandRatioProperty());
        return stories;
    }

    private Node buildInlineExample() {
        RXSegmentedProgressBar inline = new RXSegmentedProgressBar();
        inline.setPrefWidth(220.0);
        inline.setSegmentCount(4);
        inline.setSegmentHeight(6.0);
        inline.setSegmentArc(3.0);
        inline.progressProperty().bind(mainBar.progressProperty());
        inline.filledColorProperty().bind(mainBar.filledColorProperty());
        inline.unfilledColorProperty().bind(mainBar.unfilledColorProperty());
        inline.indeterminateCycleDurationProperty().bind(mainBar.indeterminateCycleDurationProperty());
        inline.indeterminateBandRatioProperty().bind(mainBar.indeterminateBandRatioProperty());

        Label stepLabel = new Label();
        stepLabel.getStyleClass().add("inline-text");
        // Read from mainBar.progress directly — it is the single source of
        // truth, and unlike progressSlider / indeterminateBox it is non-null at
        // the time createPreview() runs (which happens before createSections()).
        // Indeterminate is signalled by progress < 0.
        stepLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            double p = mainBar.getProgress();
            if (p < 0.0) {
                return "Step ? of 4";
            }
            int step = (int) Math.min(4, Math.max(0, Math.ceil(p * 4.0)));
            return "Step " + step + " of 4";
        }, mainBar.progressProperty()));

        HBox row = new HBox(12.0, inline, stepLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Sections ====================

    private Node buildProgressGrid() {
        progressSlider = createSlider(0.0, 1.0, mainBar.getProgress());
        progressSlider.setMajorTickUnit(0.25);
        progressSlider.setShowTickMarks(true);
        progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!indeterminateBox.isSelected()) {
                mainBar.setProgress(newV.doubleValue());
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
        jumpButtons.setAlignment(Pos.CENTER_LEFT);

        indeterminateBox = new CheckBox("Indeterminate");
        indeterminateBox.selectedProperty().addListener((obs, oldV, selected) -> {
            progressSlider.setDisable(selected);
            if (selected) {
                mainBar.setProgress(RXSegmentedProgressBar.INDETERMINATE_PROGRESS);
            } else {
                mainBar.setProgress(progressSlider.getValue());
            }
        });

        return createGrid(
                row("Value", progressSlider, progressValue),
                row("Jump to", jumpButtons),
                row(indeterminateBox));
    }

    private Node buildGeometryGrid() {
        Slider countSlider = createSlider(RXSegmentedProgressBar.MIN_SEGMENT_COUNT,
                RXSegmentedProgressBar.MAX_SEGMENT_COUNT,
                RXSegmentedProgressBar.DEFAULT_SEGMENT_COUNT);
        countSlider.setSnapToTicks(true);
        countSlider.setMajorTickUnit(1.0);
        countSlider.setMinorTickCount(0);
        countSlider.setShowTickMarks(true);
        countSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainBar.setSegmentCount(newV.intValue()));
        Label countValue = createValueLabel(countSlider, "%.0f");

        Slider gapSlider = createSlider(0.0, 24.0,
                RXSegmentedProgressBar.DEFAULT_SEGMENT_GAP);
        mainBar.segmentGapProperty().bind(gapSlider.valueProperty());
        Label gapValue = createValueLabel(gapSlider, "%.0f px");

        Slider heightSlider = createSlider(2.0, 48.0,
                RXSegmentedProgressBar.DEFAULT_SEGMENT_HEIGHT);
        mainBar.segmentHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        Slider arcSlider = createSlider(0.0, 24.0,
                RXSegmentedProgressBar.DEFAULT_SEGMENT_ARC);
        mainBar.segmentArcProperty().bind(arcSlider.valueProperty());
        Label arcValue = createValueLabel(arcSlider, "%.0f px");

        Slider widthSlider = createSlider(120.0, 600.0, PREVIEW_WIDTH);
        mainBar.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        return createGrid(
                row("Segments", countSlider, countValue),
                row("Gap", gapSlider, gapValue),
                row("Height", heightSlider, heightValue),
                row("Arc", arcSlider, arcValue),
                row("Bar width", widthSlider, widthValue));
    }

    private Node buildAppearanceGrid() {
        ColorPicker filledPicker = new ColorPicker((Color) RXSegmentedProgressBar.DEFAULT_FILLED_COLOR);
        filledPicker.setMaxWidth(Double.MAX_VALUE);
        mainBar.filledColorProperty().bind(filledPicker.valueProperty());

        ColorPicker unfilledPicker = new ColorPicker((Color) RXSegmentedProgressBar.DEFAULT_UNFILLED_COLOR);
        unfilledPicker.setMaxWidth(Double.MAX_VALUE);
        mainBar.unfilledColorProperty().bind(unfilledPicker.valueProperty());

        return createGrid(
                row("Filled", filledPicker),
                row("Unfilled", unfilledPicker));
    }

    private Node buildTimingGrid() {
        // Both duration sliders go down to 0 to demonstrate the "non-positive disables animation" semantic:
        // cycle = 0 -> indeterminate band stops (row stays empty)
        // tween = 0 -> determinate progress jumps instead of tweening
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXSegmentedProgressBar.DEFAULT_INDETERMINATE_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainBar.setIndeterminateCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider bandRatioSlider = createSlider(0.05, 1.0,
                RXSegmentedProgressBar.DEFAULT_INDETERMINATE_BAND_RATIO);
        mainBar.indeterminateBandRatioProperty().bind(bandRatioSlider.valueProperty());
        Label bandRatioValue = createValueLabel(bandRatioSlider, "%.2f");

        Slider tweenSlider = createSlider(0.0, 1000.0,
                RXSegmentedProgressBar.DEFAULT_PROGRESS_TRANSITION_DURATION.toMillis());
        tweenSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainBar.setProgressTransitionDuration(Duration.millis(newV.doubleValue())));
        Label tweenValue = createValueLabel(tweenSlider, "%.0f ms");

        return createGrid(
                row("Cycle", cycleSlider, cycleValue),
                row("Band ratio", bandRatioSlider, bandRatioValue),
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

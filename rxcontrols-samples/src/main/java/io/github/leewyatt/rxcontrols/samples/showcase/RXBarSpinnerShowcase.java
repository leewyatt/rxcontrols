package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXBarSpinner.BarStyle;
import io.github.leewyatt.rxcontrols.samples.demo.RXBarSpinnerDemo;
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
 * Showcase application for {@link RXBarSpinner}.
 *
 * <p>Exercises every public knob: bar style, count / width / height / gap /
 * arc, bar color, cycle duration, min-bar-height ratio. Boundary values
 * (cycle = 0, min ratio = 0 / 1) are reachable via the sliders so the
 * "non-positive disables animation" semantic and "ratio = 1 flattens the
 * row" semantic are directly observable.
 *
 * <p>The preview pane also embeds an inline-text usage and a "button graphic"
 * usage so the typical compositions are visible alongside the property panel.
 * For a minimal "few lines of code" example see {@link RXBarSpinnerDemo}.
 */
public class RXBarSpinnerShowcase extends RXShowcaseApplication {

    private RXBarSpinner mainIndicator;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBarSpinner";
    }

    @Override
    protected String subtitle() {
        return "Equalizer-style bar loading indicator";
    }

    @Override
    protected String windowTitle() {
        return "RXBarSpinner Showcase";
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
        return getClass().getResource("rx_bar_spinner_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        mainIndicator = new RXBarSpinner();

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
        RXBarSpinner inline = new RXBarSpinner();
        inline.barColorProperty().bind(mainIndicator.barColorProperty());
        inline.cycleDurationProperty().bind(mainIndicator.cycleDurationProperty());
        inline.barStyleProperty().bind(mainIndicator.barStyleProperty());
        inline.minBarHeightRatioProperty().bind(mainIndicator.minBarHeightRatioProperty());
        inline.setBarHeight(14.0);
        inline.setBarWidth(3.0);
        inline.setBarGap(3.0);
        Label leading = new Label("Now playing");
        leading.getStyleClass().add("inline-text");
        HBox row = new HBox(8.0, leading, inline);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node buildButtonExample() {
        RXBarSpinner inButton = new RXBarSpinner();
        inButton.cycleDurationProperty().bind(mainIndicator.cycleDurationProperty());
        inButton.barStyleProperty().bind(mainIndicator.barStyleProperty());
        inButton.minBarHeightRatioProperty().bind(mainIndicator.minBarHeightRatioProperty());
        inButton.setBarColor(Color.WHITE);
        inButton.setBarWidth(3.0);
        inButton.setBarHeight(14.0);
        inButton.setBarGap(3.0);

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
        ChoiceBox<BarStyle> styleBox = new ChoiceBox<>();
        styleBox.getItems().addAll(BarStyle.values());
        styleBox.setValue(mainIndicator.getBarStyle());
        styleBox.setMaxWidth(Double.MAX_VALUE);
        mainIndicator.barStyleProperty().bind(styleBox.valueProperty());

        Slider minRatioSlider = createSlider(0.0, 1.0, RXBarSpinner.DEFAULT_MIN_BAR_HEIGHT_RATIO);
        mainIndicator.minBarHeightRatioProperty().bind(minRatioSlider.valueProperty());
        Label minRatioValue = createValueLabel(minRatioSlider, "%.2f");

        return createGrid(
                row("Bar style", styleBox),
                row("Min ratio", minRatioSlider, minRatioValue));
    }

    private Node buildGeometryGrid() {
        Slider countSlider = createSlider(RXBarSpinner.MIN_BAR_COUNT, RXBarSpinner.MAX_BAR_COUNT,
                RXBarSpinner.DEFAULT_BAR_COUNT);
        countSlider.setSnapToTicks(true);
        countSlider.setMajorTickUnit(1.0);
        countSlider.setMinorTickCount(0);
        countSlider.setShowTickMarks(true);
        countSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainIndicator.setBarCount(newV.intValue()));
        Label countValue = createValueLabel(countSlider, "%.0f");

        Slider widthSlider = createSlider(2.0, 16.0, RXBarSpinner.DEFAULT_BAR_WIDTH);
        mainIndicator.barWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(8.0, 64.0, RXBarSpinner.DEFAULT_BAR_HEIGHT);
        mainIndicator.barHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        Slider gapSlider = createSlider(0.0, 16.0, RXBarSpinner.DEFAULT_BAR_GAP);
        mainIndicator.barGapProperty().bind(gapSlider.valueProperty());
        Label gapValue = createValueLabel(gapSlider, "%.0f px");

        Slider arcSlider = createSlider(0.0, 8.0, RXBarSpinner.DEFAULT_BAR_ARC);
        mainIndicator.barArcProperty().bind(arcSlider.valueProperty());
        Label arcValue = createValueLabel(arcSlider, "%.0f px");

        return createGrid(
                row("Bar count", countSlider, countValue),
                row("Bar width", widthSlider, widthValue),
                row("Bar height", heightSlider, heightValue),
                row("Bar gap", gapSlider, gapValue),
                row("Bar arc", arcSlider, arcValue));
    }

    private Node buildAppearanceGrid() {
        ColorPicker colorPicker = new ColorPicker((Color) RXBarSpinner.DEFAULT_BAR_COLOR);
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        mainIndicator.barColorProperty().bind(colorPicker.valueProperty());

        return createGrid(
                row("Bar color", colorPicker));
    }

    private Node buildTimingGrid() {
        // The cycle slider reaches 0 to demonstrate the "non-positive disables
        // animation" semantic — the bars snap to their minimum height.
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXBarSpinner.DEFAULT_CYCLE_DURATION.toMillis());
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

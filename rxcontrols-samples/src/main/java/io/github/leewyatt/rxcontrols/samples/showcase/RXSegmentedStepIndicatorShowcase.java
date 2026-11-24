package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator.TitleSide;
import io.github.leewyatt.rxcontrols.samples.demo.RXSegmentedStepIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.List;

/**
 * Showcase application for {@link RXSegmentedStepIndicator}.
 *
 * <p>Exercises every configurable property: step count, selected index,
 * selected-segment progress, content padding, segment gap, segment height,
 * and the segment titles (factory, side, gap, alignment, and truncation of
 * long titles), and shows the read-only completed state. It also logs
 * {@code CLICKED} and {@code ENTERED} events so the indicator can be verified
 * as a passive event source.</p>
 *
 * <p>For a minimal "few lines of code" example see
 * {@link RXSegmentedStepIndicatorDemo}.</p>
 */
public class RXSegmentedStepIndicatorShowcase extends RXShowcaseApplication {

    private static final List<String> TITLES = List.of("Account", "Profile", "Payment", "Review", "Done");

    private final StringProperty eventLog =
            new SimpleStringProperty(this, "eventLog", "No segment event");

    private RXSegmentedStepIndicator mainIndicator;
    private Slider stepSlider;
    private Slider selectedSlider;
    private Slider progressSlider;
    private Slider paddingSlider;
    private Slider gapSlider;
    private Slider heightSlider;
    private Slider titleGapSlider;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSegmentedStepIndicator";
    }

    @Override
    protected String subtitle() {
        return "Segmented step indicator with passive segment events";
    }

    @Override
    protected String windowTitle() {
        return "RXSegmentedStepIndicator Showcase";
    }

    @Override
    protected double sceneHeight() {
        return 620.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-segmented-step-indicator-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        mainIndicator = new RXSegmentedStepIndicator();
        mainIndicator.setPrefWidth(360.0);
        mainIndicator.setSelectedIndex(2);
        mainIndicator.setSegmentProgress(0.45);
        mainIndicator.setTitleFactory(titleFactory(false));
        mainIndicator.setOnSegmentEntered(event ->
                eventLog.set("Entered segment " + event.getSegmentIndex()));
        mainIndicator.setOnSegmentClicked(event -> {
            eventLog.set("Clicked segment " + event.getSegmentIndex());
            if (selectedSlider != null) {
                selectedSlider.setValue(event.getSegmentIndex());
            }
        });

        Label logLabel = new Label();
        logLabel.getStyleClass().add("event-log");
        logLabel.textProperty().bind(eventLog);

        VBox body = new VBox(14.0, new StackPane(mainIndicator), logLabel);
        body.setAlignment(Pos.CENTER);
        return buildCard("Interactive indicator", body);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("State", buildStateGrid()),
                section("Geometry", buildGeometryGrid()),
                section("Titles", buildTitleGrid()),
                section("Events", buildEventGrid()));
    }

    // ==================== Preview helpers ====================

    private VBox buildCard(String caption, Node body) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("caption");
        StackPane bodyPane = new StackPane(body);
        bodyPane.setAlignment(Pos.CENTER);
        bodyPane.setMinHeight(90.0);
        VBox card = new VBox(10.0, captionLabel, bodyPane);
        card.getStyleClass().add("preview-card");
        return card;
    }

    // ==================== Sections ====================

    private Node buildStateGrid() {
        stepSlider = createSlider(0.0, RXSegmentedStepIndicator.MAX_STEP_COUNT,
                mainIndicator.getStepCount());
        configureIntegerSlider(stepSlider);
        stepSlider.valueProperty().addListener((obs, oldV, newV) -> {
            mainIndicator.setStepCount(newV.intValue());
            updateSelectedSliderRange();
        });
        Label stepValue = createValueLabel(stepSlider, "%.0f");

        selectedSlider = createSlider(0.0, mainIndicator.getStepCount() - 1.0,
                mainIndicator.getSelectedIndex());
        configureIntegerSlider(selectedSlider);
        selectedSlider.valueProperty().addListener((obs, oldV, newV) ->
                mainIndicator.setSelectedIndex(newV.intValue()));
        Label selectedValue = createValueLabel(selectedSlider, "%.0f");

        progressSlider = createSlider(0.0, 1.0, mainIndicator.getSegmentProgress());
        progressSlider.setMajorTickUnit(0.25);
        progressSlider.setShowTickMarks(true);
        mainIndicator.segmentProgressProperty().bind(progressSlider.valueProperty());
        Label progressValue = new Label();
        progressValue.getStyleClass().add("value-label");
        progressValue.textProperty().bind(
                Bindings.format("%.0f%%", progressSlider.valueProperty().multiply(100.0)));
        progressValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        progressValue.setAlignment(Pos.CENTER_RIGHT);

        // Read-only: the last step at 100% completes the indicator.
        Label completedValue = new Label();
        completedValue.getStyleClass().add("value-label");
        completedValue.textProperty().bind(mainIndicator.completedProperty().asString());

        updateSelectedSliderRange();

        return createGrid(
                row("Steps", stepSlider, stepValue),
                row("Selected Index", selectedSlider, selectedValue),
                row("Segment Progress", progressSlider, progressValue),
                row("Completed", completedValue));
    }

    private Node buildGeometryGrid() {
        paddingSlider = createSlider(0.0, 18.0, 6.0);
        paddingSlider.valueProperty().addListener((obs, oldV, newV) -> updateInlineStyle());
        Label paddingValue = createValueLabel(paddingSlider, "%.0f px");

        gapSlider = createSlider(0.0, 12.0, mainIndicator.getSegmentGap());
        gapSlider.valueProperty().addListener((obs, oldV, newV) -> updateInlineStyle());
        Label gapValue = createValueLabel(gapSlider, "%.0f px");

        heightSlider = createSlider(2.0, 24.0, mainIndicator.getSegmentHeight());
        heightSlider.valueProperty().addListener((obs, oldV, newV) -> updateInlineStyle());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        updateInlineStyle();

        return createGrid(
                row("Content Padding", paddingSlider, paddingValue),
                row("Gap", gapSlider, gapValue),
                row("Height", heightSlider, heightValue));
    }

    private Node buildTitleGrid() {
        CheckBox showTitles = new CheckBox("Show titles");
        showTitles.setSelected(true);
        CheckBox longTitles = new CheckBox("Long titles");
        longTitles.disableProperty().bind(showTitles.selectedProperty().not());
        Runnable applyTitles = () -> mainIndicator.setTitleFactory(
                showTitles.isSelected() ? titleFactory(longTitles.isSelected()) : null);
        showTitles.selectedProperty().addListener((obs, oldV, newV) -> applyTitles.run());
        longTitles.selectedProperty().addListener((obs, oldV, newV) -> applyTitles.run());

        ComboBox<TitleSide> side = new ComboBox<>();
        side.getItems().setAll(TitleSide.values());
        side.setValue(mainIndicator.getTitleSide());
        side.setMaxWidth(Double.MAX_VALUE);
        side.valueProperty().bindBidirectional(mainIndicator.titleSideProperty());

        titleGapSlider = createSlider(0.0, 16.0, mainIndicator.getTitleGap());
        titleGapSlider.valueProperty().addListener((obs, oldV, newV) -> updateInlineStyle());
        Label titleGapValue = createValueLabel(titleGapSlider, "%.0f px");

        CheckBox centered = new CheckBox("Center titles");
        centered.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                mainIndicator.getStyleClass().add("centered-titles");
            } else {
                mainIndicator.getStyleClass().remove("centered-titles");
            }
        });

        updateInlineStyle();

        return createGrid(
                row(showTitles),
                row(longTitles),
                row("Side", side),
                row("Title Gap", titleGapSlider, titleGapValue),
                row(centered));
    }

    private static Callback<Integer, Node> titleFactory(boolean longTitles) {
        return i -> {
            String name = i < TITLES.size() ? TITLES.get(i) : "Step " + (i + 1);
            return new Label(longTitles ? name + " and a few more words" : name);
        };
    }

    private Node buildEventGrid() {
        Label eventLabel = new Label();
        eventLabel.getStyleClass().add("event-log");
        eventLabel.textProperty().bind(eventLog);
        return createGrid(row(eventLabel));
    }

    private void configureIntegerSlider(Slider slider) {
        slider.setSnapToTicks(true);
        slider.setMajorTickUnit(1.0);
        slider.setMinorTickCount(0);
        slider.setShowTickMarks(true);
    }

    private void updateSelectedSliderRange() {
        if (selectedSlider == null || stepSlider == null) {
            return;
        }
        int steps = stepSlider.valueProperty().intValue();
        double max = Math.max(0, steps - 1);
        selectedSlider.setMax(max);
        selectedSlider.setDisable(steps == 0);
        if (selectedSlider.getValue() > max) {
            selectedSlider.setValue(max);
        }
        mainIndicator.setSelectedIndex((int) selectedSlider.getValue());
    }

    private void updateInlineStyle() {
        if (mainIndicator == null || paddingSlider == null || gapSlider == null || heightSlider == null) {
            return;
        }
        double padding = paddingSlider.getValue();
        double gap = gapSlider.getValue();
        double height = heightSlider.getValue();
        String titleGap = titleGapSlider == null ? "" : "-rx-title-gap: " + titleGapSlider.getValue() + "px;";
        mainIndicator.setStyle("-fx-padding: " + padding + "px 0 " + padding + "px 0;"
                + "-rx-segment-gap: " + gap + "px;"
                + "-rx-segment-height: " + height + "px;"
                + titleGap);
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

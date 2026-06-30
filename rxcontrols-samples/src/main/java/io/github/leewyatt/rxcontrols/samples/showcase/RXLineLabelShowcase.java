package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimSlide;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.animation.line.LineEdges;
import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase application for {@link RXLineLabel}.
 *
 * <p>Exercises the line presets on non-interactive text: link-style entries
 * and a heading, none of which are focus-traversable or fire actions. Lines
 * are a pure overlay that may extend beyond the label bounds.</p>
 */
public class RXLineLabelShowcase extends RXShowcaseApplication {

    private static final Map<String, LineAnimation> ANIMATIONS = new LinkedHashMap<>();

    static {
        ANIMATIONS.put("underline-center-out", LineAnimation.UNDERLINE_CENTER_OUT);
        ANIMATIONS.put("underline-left-to-right", LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        ANIMATIONS.put("underline-right-to-left", LineAnimation.UNDERLINE_RIGHT_TO_LEFT);
        ANIMATIONS.put("underline-edges-in", LineAnimation.UNDERLINE_EDGES_IN);
        ANIMATIONS.put("underline-slide-up", LineAnimation.UNDERLINE_SLIDE_UP);
        ANIMATIONS.put("underline-slide-down", LineAnimation.UNDERLINE_SLIDE_DOWN);
        ANIMATIONS.put("underline-fade", LineAnimation.UNDERLINE_FADE);
        ANIMATIONS.put("top-bottom-center-out", LineAnimation.TOP_BOTTOM_CENTER_OUT);
        ANIMATIONS.put("top-bottom-converge", LineAnimation.TOP_BOTTOM_CONVERGE);
        ANIMATIONS.put("left-right-center-out", LineAnimation.LEFT_RIGHT_CENTER_OUT);
        ANIMATIONS.put("left-right-converge", LineAnimation.LEFT_RIGHT_CONVERGE);
        ANIMATIONS.put("custom: new LineAnimSlide(TOP_BOTTOM, 24)",
                new LineAnimSlide(LineEdges.TOP_BOTTOM, 24.0));
    }

    private final List<RXLineLabel> labels = new ArrayList<>();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXLineLabel";
    }

    @Override
    protected String subtitle() {
        return "Animated line decoration on non-interactive text";
    }

    @Override
    protected String windowTitle() {
        return "RXLineLabel Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-line-label-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        RXLineLabel heading = new RXLineLabel("Hover this heading");
        heading.getStyleClass().add("heading");
        labels.add(heading);

        FlowPane links = new FlowPane(18.0, 8.0);
        links.setAlignment(Pos.CENTER);
        for (String text : List.of("JavaFX", "Animation", "Open Source", "RXControls", "UI")) {
            RXLineLabel link = new RXLineLabel(text);
            link.getStyleClass().add("link");
            labels.add(link);
            links.getChildren().add(link);
        }

        Label hint = new Label("Labels: not focusable, no action — pure decoration.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(18.0, heading, links, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(section("Line", buildLineGrid()));
    }

    // ==================== Sections ====================

    private Node buildLineGrid() {
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(ANIMATIONS.keySet());
        animationBox.setValue("underline-center-out");
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setLineAnimation(ANIMATIONS.get(newV))));

        ComboBox<AnimationTrigger> triggerBox = new ComboBox<>();
        triggerBox.getItems().setAll(AnimationTrigger.values());
        triggerBox.setValue(AnimationTrigger.HOVER);
        triggerBox.setMaxWidth(Double.MAX_VALUE);
        triggerBox.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setAnimationTrigger(newV)));

        Slider thicknessSlider = createSlider(1.0, 8.0, 2.0);
        thicknessSlider.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setLineThickness(newV.doubleValue())));

        Slider gapSlider = createSlider(0.0, 16.0, 2.0);
        gapSlider.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setLineGap(newV.doubleValue())));

        Slider durationSlider = createSlider(0.0, 800.0, 200.0);
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label ->
                        label.setAnimationDuration(Duration.millis(newV.doubleValue()))));

        return createGrid(
                row("Animation", animationBox),
                row("Trigger", triggerBox),
                row("Thickness", thicknessSlider, createValueLabel(thicknessSlider, "%.1f px")),
                row("Gap", gapSlider, createValueLabel(gapSlider, "%.0f px")),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
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

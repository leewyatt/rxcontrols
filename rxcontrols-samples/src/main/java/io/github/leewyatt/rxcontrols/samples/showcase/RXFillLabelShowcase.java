package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
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
 * Showcase application for {@link RXFillLabel}.
 *
 * <p>Exercises the fill sweep on non-interactive text: tag chips with
 * rounded fills and a heading underline-style sweep, none of which are
 * focus-traversable or fire actions.</p>
 */
public class RXFillLabelShowcase extends RXShowcaseApplication {

    private static final Map<String, FillAnimation> ANIMATIONS = new LinkedHashMap<>();

    static {
        ANIMATIONS.put("left-to-right", FillAnimation.LEFT_TO_RIGHT);
        ANIMATIONS.put("right-to-left", FillAnimation.RIGHT_TO_LEFT);
        ANIMATIONS.put("top-to-bottom", FillAnimation.TOP_TO_BOTTOM);
        ANIMATIONS.put("bottom-to-top", FillAnimation.BOTTOM_TO_TOP);
        ANIMATIONS.put("center-out", FillAnimation.CENTER_OUT);
        ANIMATIONS.put("center-out-vertical", FillAnimation.CENTER_OUT_VERTICAL);
        ANIMATIONS.put("edges-in", FillAnimation.EDGES_IN);
        ANIMATIONS.put("edges-in-vertical", FillAnimation.EDGES_IN_VERTICAL);
        ANIMATIONS.put("circle", FillAnimation.CIRCLE);
        ANIMATIONS.put("corners-in", FillAnimation.CORNERS_IN);
        ANIMATIONS.put("zigzag", FillAnimation.ZIGZAG);
        ANIMATIONS.put("zigzag-vertical", FillAnimation.ZIGZAG_VERTICAL);
    }

    private final List<RXFillLabel> labels = new ArrayList<>();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXFillLabel";
    }

    @Override
    protected String subtitle() {
        return "Fill sweep on non-interactive text";
    }

    @Override
    protected String windowTitle() {
        return "RXFillLabel Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-fill-label-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        FlowPane tags = new FlowPane(8.0, 8.0);
        tags.setAlignment(Pos.CENTER);
        for (String text : List.of("JavaFX", "Animation", "Open Source", "RXControls", "UI")) {
            RXFillLabel tag = new RXFillLabel(text);
            tag.getStyleClass().add("tag");
            labels.add(tag);
            tags.getChildren().add(tag);
        }

        RXFillLabel heading = new RXFillLabel("Hover this heading");
        heading.getStyleClass().add("heading");
        labels.add(heading);

        Label hint = new Label("Labels: not focusable, no action — pure decoration.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(18.0, heading, tags, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(section("Fill", buildFillGrid()));
    }

    // ==================== Sections ====================

    private Node buildFillGrid() {
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(ANIMATIONS.keySet());
        modeBox.setValue("left-to-right");
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setFillAnimation(ANIMATIONS.get(newV))));

        ComboBox<AnimationTrigger> triggerBox = new ComboBox<>();
        triggerBox.getItems().setAll(AnimationTrigger.values());
        triggerBox.setValue(AnimationTrigger.HOVER);
        triggerBox.setMaxWidth(Double.MAX_VALUE);
        triggerBox.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label -> label.setAnimationTrigger(newV)));

        Slider durationSlider = createSlider(0.0, 800.0, 200.0);
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                labels.forEach(label ->
                        label.setAnimationDuration(Duration.millis(newV.doubleValue()))));

        return createGrid(
                row("Mode", modeBox),
                row("Trigger", triggerBox),
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

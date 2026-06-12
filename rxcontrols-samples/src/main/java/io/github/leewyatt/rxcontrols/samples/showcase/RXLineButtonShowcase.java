package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimSlide;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.animation.line.LineEdges;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Showcase application for {@link RXLineButton}.
 *
 * <p>Exercises the eleven line presets, a parameterized custom effect, the
 * hover/pressed trigger, the reversible progress animation, line thickness
 * and gap, the {@code :line-showing} pseudo-class and the coexistence with
 * the inherited ripple. Lines are a pure overlay: they may extend beyond the
 * button bounds and never affect its size.</p>
 */
public class RXLineButtonShowcase extends RXShowcaseApplication {

    private RXLineButton button;
    private Label firedLabel;
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXLineButton";
    }

    @Override
    protected String subtitle() {
        return "Animated line decoration around the content";
    }

    @Override
    protected String windowTitle() {
        return "RXLineButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-line-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXLineButton("Explore the docs");
        button.getStyleClass().add("showcase-button");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("action fired " + firedCount + "x");
        });

        firedLabel = new Label("action fired 0x");
        firedLabel.getStyleClass().add("fired-label");

        Label hint = new Label("Hover to draw the lines; quick in-and-out reverses from current progress.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, button, firedLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Line", buildLineGrid()),
                section("Animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Sections ====================

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

    private Node buildLineGrid() {
        ComboBox<String> animationBox = new ComboBox<>();
        animationBox.getItems().setAll(ANIMATIONS.keySet());
        animationBox.setValue("underline-center-out");
        animationBox.setMaxWidth(Double.MAX_VALUE);
        animationBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setLineAnimation(ANIMATIONS.get(newV)));

        ColorPicker colorPicker = new ColorPicker(Color.web("#616dff"));
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        colorPicker.setOnAction(event -> updateLineColor(colorPicker.getValue()));

        Slider thicknessSlider = createSlider(1.0, 8.0, button.getLineThickness());
        thicknessSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setLineThickness(newV.doubleValue()));

        Slider gapSlider = createSlider(0.0, 16.0, button.getLineGap());
        gapSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setLineGap(newV.doubleValue()));

        return createGrid(
                row("Animation", animationBox),
                row("Line color", colorPicker),
                row("Thickness", thicknessSlider, createValueLabel(thicknessSlider, "%.1f px")),
                row("Gap", gapSlider, createValueLabel(gapSlider, "%.0f px")));
    }

    private Node buildAnimationGrid() {
        ComboBox<RXAnimationTrigger> triggerBox = new ComboBox<>();
        triggerBox.getItems().setAll(RXAnimationTrigger.values());
        triggerBox.setValue(button.getAnimationTrigger());
        triggerBox.setMaxWidth(Double.MAX_VALUE);
        button.animationTriggerProperty().bind(triggerBox.valueProperty());

        Slider durationSlider = createSlider(0.0, 800.0,
                button.getAnimationDuration().toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setAnimationDuration(Duration.millis(newV.doubleValue())));

        Button playOnceButton = new Button("playAnimation()");
        playOnceButton.setMaxWidth(Double.MAX_VALUE);
        playOnceButton.setOnAction(event -> button.playAnimation());

        return createGrid(
                row("Trigger", triggerBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Play once", playOnceButton));
    }

    private Node buildButtonGrid() {
        CheckBox rippleBox = new CheckBox();
        rippleBox.setSelected(button.isRippleEnabled());
        button.rippleEnabledProperty().bind(rippleBox.selectedProperty());

        CheckBox graphicBox = new CheckBox();
        graphicBox.selectedProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                Region icon = new Region();
                icon.getStyleClass().add("rocket-icon");
                icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                button.setGraphic(icon);
            } else {
                button.setGraphic(null);
            }
        });

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        return createGrid(
                row("Ripple", rippleBox),
                row("Graphic", graphicBox),
                row("Disabled", disableBox));
    }

    // ==================== Helpers ====================

    private void updateLineColor(Color color) {
        button.setStyle(String.format(Locale.ROOT,
                "-rx-line-color: #%02x%02x%02x;",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255)));
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

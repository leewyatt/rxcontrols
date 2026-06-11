package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimZigzag;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
 * Showcase application for {@link RXFillButton}.
 *
 * <p>Exercises the six fill modes, the hover/pressed trigger, the reversible
 * progress animation, the mirrored caption recoloring, bounded rounded
 * clipping and the coexistence with the inherited ripple.</p>
 */
public class RXFillButtonShowcase extends RXShowcaseApplication {

    private RXFillButton button;
    private Label firedLabel;
    private int firedCount;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXFillButton";
    }

    @Override
    protected String subtitle() {
        return "Animated background fill with caption recoloring";
    }

    @Override
    protected String windowTitle() {
        return "RXFillButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-fill-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXFillButton("Deploy to production");
        button.getStyleClass().add("showcase-button");
        button.setOnAction(event -> {
            firedCount++;
            firedLabel.setText("action fired " + firedCount + "x");
        });

        firedLabel = new Label("action fired 0x");
        firedLabel.getStyleClass().add("fired-label");

        Label hint = new Label("Hover to fill; quick in-and-out reverses from current progress.");
        hint.getStyleClass().add("hint-label");

        VBox preview = new VBox(16.0, button, firedLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Fill", buildFillGrid()),
                section("Animation", buildAnimationGrid()),
                section("Button", buildButtonGrid()));
    }

    // ==================== Sections ====================

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
        ANIMATIONS.put("custom: new FillAnimZigzag(8)", new FillAnimZigzag(8));
    }

    private Node buildFillGrid() {
        ComboBox<String> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(ANIMATIONS.keySet());
        modeBox.setValue("left-to-right");
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldV, newV) ->
                button.setFillAnimation(ANIMATIONS.get(newV)));

        ColorPicker fillPicker = new ColorPicker(Color.web("#616dff"));
        fillPicker.setMaxWidth(Double.MAX_VALUE);
        fillPicker.setOnAction(event -> updateFillColor(fillPicker.getValue()));

        CheckBox autoInsetsBox = new CheckBox();
        autoInsetsBox.setSelected(true);
        Slider insetsSlider = createSlider(-10.0, 20.0, 0.0);
        Runnable applyInsets = () -> button.setFillInsets(
                autoInsetsBox.isSelected() ? null : new Insets(insetsSlider.getValue()));
        autoInsetsBox.selectedProperty().addListener((obs, oldV, newV) -> applyInsets.run());
        insetsSlider.valueProperty().addListener((obs, oldV, newV) -> applyInsets.run());

        Slider fillRadiusSlider = createSlider(-1.0, 24.0, -1.0);
        fillRadiusSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setFillRadius(newV.doubleValue()));

        return createGrid(
                row("Mode", modeBox),
                row("Fill color", fillPicker),
                row("Auto insets", autoInsetsBox),
                row("Insets", insetsSlider, createValueLabel(insetsSlider, "%.0f px")),
                row("Fill radius", fillRadiusSlider, createValueLabel(fillRadiusSlider, "%.0f")));
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

        return createGrid(
                row("Trigger", triggerBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node buildButtonGrid() {
        Slider radiusSlider = createSlider(0.0, 24.0, 4.0);
        radiusSlider.valueProperty().addListener((obs, oldV, newV) ->
                updateRadius(newV.doubleValue()));

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
                row("Radius", radiusSlider, createValueLabel(radiusSlider, "%.0f px")),
                row("Ripple", rippleBox),
                row("Graphic", graphicBox),
                row("Disabled", disableBox));
    }

    // ==================== Helpers ====================

    private Color lastFill = Color.web("#616dff");
    private double lastRadius = 4.0;

    private void updateFillColor(Color color) {
        lastFill = color;
        applyInlineStyle();
    }

    private void updateRadius(double radius) {
        lastRadius = radius;
        applyInlineStyle();
    }

    private void applyInlineStyle() {
        button.setStyle(String.format(Locale.ROOT,
                "-rx-fill: #%02x%02x%02x; -fx-background-radius: %.0fpx; -fx-border-radius: %.0fpx;",
                Math.round(lastFill.getRed() * 255),
                Math.round(lastFill.getGreen() * 255),
                Math.round(lastFill.getBlue() * 255),
                lastRadius, lastRadius));
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

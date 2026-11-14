package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimShapes;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Showcase application for {@link RXReactionButton}.
 *
 * <p>The preview shows a large reaction button with its like count and
 * selection state. The control pane covers every burst preset plus a custom
 * shape, a combined burst and none, the burst duration and scale, a custom
 * burst color and a multi-color palette, the heart, star and bookmark icons
 * and the icon size, no icon at all (the burst then comes from the button's
 * center), the animated switch, quiet selection from code, the disabled
 * state and {@code playBurst()}.</p>
 */
public class RXReactionButtonShowcase extends RXShowcaseApplication {

    // A lightning bolt in a 24 x 24 box, for the custom shape preset.
    private static final String BOLT_PATH = "M13 2 L4 14 H11 L10 22 L20 9 H13 Z";
    private static final List<Paint> RAINBOW = List.of(Color.web("#ff5d73"), Color.web("#ffb703"),
            Color.web("#2ec4b6"), Color.web("#3a86ff"), Color.web("#8338ec"));

    // The count before this user's like; selected adds one.
    private static final int OTHER_LIKES = 127;

    private RXReactionButton button;
    private Node defaultIcon;
    private final Label stateLabel = new Label();
    private final CheckBox countBox = new CheckBox();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXReactionButton";
    }

    @Override
    protected String subtitle() {
        return "A like / favorite toggle that bursts when the user selects it";
    }

    @Override
    protected String windowTitle() {
        return "RXReactionButton Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-reaction-button-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        button = new RXReactionButton();
        button.getStyleClass().add("showcase-large");
        button.setAccessibleText("Like");
        defaultIcon = button.getGraphic();
        // The count follows the selection however it changes, quietly or not.
        button.selectedProperty().addListener((obs, oldV, newV) -> updateState());
        countBox.setSelected(true);
        countBox.selectedProperty().addListener((obs, oldV, newV) -> updateState());
        updateState();
        stateLabel.getStyleClass().add("state-label");

        Label hint = new Label("Click or press SPACE to select: the burst plays. Selecting from code"
                + " (the Selected check box) stays quiet; Play burst plays it on demand.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        hint.setMaxWidth(360.0);

        VBox preview = new VBox(28.0, button, stateLabel, hint);
        preview.getStyleClass().add("live-preview");
        preview.setAlignment(Pos.CENTER);
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Burst", buildBurstGrid()),
                section("Colors", buildColorGrid()),
                section("Button", buildButtonGrid()));
    }

    private void updateState() {
        stateLabel.setText(button.isSelected() ? "Selected" : "Not selected");
        button.setText(countBox.isSelected()
                ? String.valueOf(OTHER_LIKES + (button.isSelected() ? 1 : 0))
                : null);
    }

    // ==================== Sections ====================

    private Node buildBurstGrid() {
        final String classic = "Classic (ring + dots)";
        Map<String, BurstAnimation> presets = new LinkedHashMap<>();
        presets.put(classic, BurstAnimation.CLASSIC);
        presets.put("Ring", BurstAnimation.RING);
        presets.put("Dots", BurstAnimation.DOTS);
        presets.put("Sparkles", BurstAnimation.SPARKLES);
        presets.put("Rays", BurstAnimation.RAYS);
        presets.put("Hearts", BurstAnimation.HEARTS);
        presets.put("Bubbles", BurstAnimation.BUBBLES);
        presets.put("Confetti", BurstAnimation.CONFETTI);
        presets.put("Fireworks", BurstAnimation.FIREWORKS);
        presets.put("Explosion", BurstAnimation.EXPLOSION);
        presets.put("Custom shape (bolt)", new BurstAnimShapes(BOLT_PATH));
        presets.put("Sparkles + confetti", BurstAnimation.combine(BurstAnimation.SPARKLES, BurstAnimation.CONFETTI));
        presets.put("None", null);
        ComboBox<String> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(presets.keySet());
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> {
            button.setBurstAnimation(presets.get(newV));
            button.playBurst();
        });
        presetBox.setValue(classic);

        // 0 ms plays no burst.
        Slider durationSlider = createSlider(0.0, 3000.0, RXBurstOptions.DEFAULT_DURATION.toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                button.setBurstDuration(Duration.millis(Math.round(newV.doubleValue()))));

        Slider scaleSlider = createSlider(0.25, 4.0, RXBurstOptions.DEFAULT_SCALE);
        scaleSlider.valueProperty().addListener((obs, oldV, newV) -> button.setBurstScale(newV.doubleValue()));

        Button playButton = new Button("playBurst()");
        playButton.setMaxWidth(Double.MAX_VALUE);
        playButton.setOnAction(event -> button.playBurst());

        return createGrid(
                row("Preset", presetBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Scale", scaleSlider, createValueLabel(scaleSlider, "%.2f")),
                row("Play", playButton));
    }

    private Node buildColorGrid() {
        ColorPicker colorPicker = new ColorPicker(Color.web("#8338ec"));
        CheckBox customColorBox = new CheckBox();
        colorPicker.disableProperty().bind(customColorBox.selectedProperty().not());
        // An inline style rather than setBurstColor: clearing it hands the
        // color back to the theme, which a value set in code would override.
        Runnable applyColor = () -> button.setStyle(customColorBox.isSelected()
                ? "-rx-burst-color: " + toCss(colorPicker.getValue()) + ";"
                : null);
        customColorBox.selectedProperty().addListener((obs, oldV, newV) -> applyColor.run());
        colorPicker.valueProperty().addListener((obs, oldV, newV) -> applyColor.run());

        CheckBox rainbowBox = new CheckBox();
        rainbowBox.selectedProperty().addListener((obs, oldV, newV) ->
                button.setBurstPalette(newV ? RAINBOW : null));

        Label paletteHint = new Label("Without a palette, the burst derives four colors from the burst color.");
        paletteHint.getStyleClass().add("hint-label");
        paletteHint.setWrapText(true);

        HBox colorRow = new HBox(8.0, customColorBox, colorPicker);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        return createGrid(
                row("Burst color", colorRow),
                row("Rainbow palette", rainbowBox),
                row(paletteHint));
    }

    private Node buildButtonGrid() {
        final String noIcon = "None (burst from the center)";
        ComboBox<String> iconBox = new ComboBox<>();
        iconBox.getItems().setAll("Heart", "Star", "Bookmark", noIcon);
        iconBox.setMaxWidth(Double.MAX_VALUE);
        iconBox.valueProperty().addListener((obs, oldV, newV) -> {
            button.getStyleClass().removeAll(RXReactionButton.STYLE_CLASS_STAR, RXReactionButton.STYLE_CLASS_BOOKMARK);
            button.setGraphic(noIcon.equals(newV) ? null : defaultIcon);
            if ("Star".equals(newV)) {
                button.getStyleClass().add(RXReactionButton.STYLE_CLASS_STAR);
            } else if ("Bookmark".equals(newV)) {
                button.getStyleClass().add(RXReactionButton.STYLE_CLASS_BOOKMARK);
            }
        });
        iconBox.setValue("Heart");

        Slider iconSlider = createSlider(16.0, 64.0, 40.0);
        iconSlider.disableProperty().bind(iconBox.valueProperty().isEqualTo(noIcon));
        Runnable applyIconSize = () -> defaultIcon.setStyle(String.format(
                "-fx-pref-width: %1$.0fpx; -fx-pref-height: %1$.0fpx;", iconSlider.getValue()));
        iconSlider.valueProperty().addListener((obs, oldV, newV) -> applyIconSize.run());
        applyIconSize.run();

        CheckBox animatedBox = new CheckBox();
        animatedBox.selectedProperty().bindBidirectional(button.animatedProperty());

        CheckBox selectedBox = new CheckBox();
        selectedBox.selectedProperty().bindBidirectional(button.selectedProperty());

        CheckBox disableBox = new CheckBox();
        button.disableProperty().bind(disableBox.selectedProperty());

        return createGrid(
                row("Icon", iconBox),
                row("Icon size", iconSlider, createValueLabel(iconSlider, "%.0f px")),
                row("Show count", countBox),
                row("Animated", animatedBox),
                row("Selected", selectedBox),
                row("Disabled", disableBox));
    }

    private static String toCss(Color color) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", Math.round(color.getRed() * 255.0),
                Math.round(color.getGreen() * 255.0), Math.round(color.getBlue() * 255.0), color.getOpacity());
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

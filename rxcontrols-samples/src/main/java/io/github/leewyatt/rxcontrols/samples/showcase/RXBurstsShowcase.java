package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.RXBursts;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimShapes;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase application for {@link RXBursts}.
 *
 * <p>The preview has an area that bursts wherever it is clicked, an ordinary
 * button that bursts from its center and a list whose rows burst when
 * clicked, which shows that the overlay is not clipped by the list. The
 * control pane covers every preset plus a custom shape, a combined burst and
 * none, the duration, scale and anchor size, a custom color and a
 * multi-color palette, all applied through {@link RXBurstOptions}.</p>
 */
public class RXBurstsShowcase extends RXShowcaseApplication {

    // A lightning bolt in a 24 x 24 box, for the custom shape preset.
    private static final String BOLT_PATH = "M13 2 L4 14 H11 L10 22 L20 9 H13 Z";
    private static final List<Paint> RAINBOW = List.of(Color.web("#ff5d73"), Color.web("#ffb703"),
            Color.web("#2ec4b6"), Color.web("#3a86ff"), Color.web("#8338ec"));

    private final ComboBox<String> presetBox = new ComboBox<>();
    private final Map<String, BurstAnimation> presets = new LinkedHashMap<>();
    private final Slider durationSlider = createSlider(0.0, 3000.0, RXBurstOptions.DEFAULT_DURATION.toMillis());
    private final Slider scaleSlider = createSlider(0.25, 4.0, RXBurstOptions.DEFAULT_SCALE);
    private final Slider sizeSlider = createSlider(0.0, 64.0, 0.0);
    private final CheckBox customColorBox = new CheckBox();
    private final ColorPicker colorPicker = new ColorPicker(Color.web("#ff5d73"));
    private final CheckBox rainbowBox = new CheckBox();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBursts";
    }

    @Override
    protected String subtitle() {
        return "Bursts on any node, from a scene overlay";
    }

    @Override
    protected String windowTitle() {
        return "RXBursts Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-bursts-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        Label areaHint = new Label("Click anywhere in this area");
        areaHint.getStyleClass().add("hint-label");
        StackPane area = new StackPane(areaHint);
        area.getStyleClass().add("burst-area");
        area.setOnMouseClicked(event -> RXBursts.playAt(area, event.getX(), event.getY(), options()));
        VBox.setVgrow(area, Priority.ALWAYS);

        Button button = new Button("A plain Button");
        button.addEventHandler(ActionEvent.ACTION, event -> RXBursts.play(button, options()));

        ListView<String> list = new ListView<>();
        list.getItems().setAll("Clicked rows burst", "past the list's edges", "Design", "Engineering",
                "Product", "Marketing");
        list.setPrefSize(220.0, 150.0);
        list.setOnMouseClicked(event -> RXBursts.playAt(list, event.getX(), event.getY(), options()));

        HBox others = new HBox(24.0, button, list);
        others.setAlignment(Pos.CENTER);

        VBox preview = new VBox(20.0, area, others);
        preview.getStyleClass().add("live-preview");
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Burst", buildBurstGrid()),
                section("Colors", buildColorGrid()));
    }

    // Rebuilt per burst so that control changes apply at once; an
    // application builds its options once and reuses them.
    private RXBurstOptions options() {
        return RXBurstOptions.builder()
                .animation(presets.get(presetBox.getValue()))
                .duration(Duration.millis(Math.round(durationSlider.getValue())))
                .scale(scaleSlider.getValue())
                .anchorSize(Math.round(sizeSlider.getValue()))
                .color(customColorBox.isSelected() ? colorPicker.getValue() : null)
                .palette(rainbowBox.isSelected() ? RAINBOW : null)
                .build();
    }

    // ==================== Sections ====================

    private Node buildBurstGrid() {
        final String classic = "Classic (ring + dots)";
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
        presets.put("Fireworks + confetti", BurstAnimation.combine(BurstAnimation.FIREWORKS, BurstAnimation.CONFETTI));
        presets.put("None", null);
        presetBox.getItems().setAll(presets.keySet());
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.setValue(classic);

        Label sizeHint = new Label("Anchor size 0 = automatic: from the node's size within 16-48 px,"
                + " or 24 px at a point. Duration 0 plays nothing.");
        sizeHint.getStyleClass().add("hint-label");
        sizeHint.setWrapText(true);

        return createGrid(
                row("Preset", presetBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Scale", scaleSlider, createValueLabel(scaleSlider, "%.2f")),
                row("Anchor size", sizeSlider, createValueLabel(sizeSlider, "%.0f px")),
                row(sizeHint));
    }

    private Node buildColorGrid() {
        // A palette replaces the color, so the color picker rests meanwhile.
        colorPicker.disableProperty().bind(customColorBox.selectedProperty().not().or(rainbowBox.selectedProperty()));
        Label paletteHint = new Label("Without a color, the colors derive from the theme's accent color;"
                + " a palette, when set, replaces the color.");
        paletteHint.getStyleClass().add("hint-label");
        paletteHint.setWrapText(true);

        HBox colorRow = new HBox(8.0, customColorBox, colorPicker);
        colorRow.setAlignment(Pos.CENTER_LEFT);
        return createGrid(
                row("Color", colorRow),
                row("Rainbow palette", rainbowBox),
                row(paletteHint));
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

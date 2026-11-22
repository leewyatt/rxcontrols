package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.samples.demo.RXRatingDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXRating}.
 *
 * <p>Exposes the value (any fraction, not rated, or bound to the slider), the icon
 * count, the input step (whole, half or any fraction) and the keyboard step, clearing, the read-only and disabled states, and the look:
 * icon shape, size, colors, gap and layout direction. Under the rating it shows the
 * committed value, the hover preview and how often the action fired, so moving over
 * the icons can be seen to commit nothing. For a minimal example see
 * {@link RXRatingDemo}.</p>
 */
public class RXRatingShowcase extends RXShowcaseApplication {

    /** Icon names and the style class that draws each; the star needs none. */
    private static final String[][] ICONS = {
            {"Star", null}, {"Heart", RXRating.STYLE_CLASS_HEART}, {"Smile", "smile"},
            {"Thumb up", "thumb"}, {"Dot", "dot"}, {"Diamond", "diamond"},
            {"Faces (one per position)", "faces"}};
    private static final String[][] SIZES = {
            {"Small", "size-small"}, {"Medium", "size-medium"}, {"Large", "size-large"}};
    private static final Color DEFAULT_FILLED = Color.web("#f59e0b");
    private static final Color DEFAULT_EMPTY = Color.web("#cbd5e1");

    private RXRating rating;
    private int actions;
    private boolean syncingValue;
    /** Kept reachable: a binding made by asObject is otherwise collected. */
    private ObjectProperty<Double> sliderValue;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXRating";
    }

    @Override
    protected String subtitle() {
        return "Star rating with exact display and stepped input";
    }

    @Override
    protected String windowTitle() {
        return "RXRating Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-rating-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        rating = new RXRating(3.7);
        rating.getStyleClass().add("size-large");
        rating.setAccessibleText("Rating");

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("state-label");
        valueLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "value: " + describe(rating.getValue()), rating.valueProperty()));
        Label hoverLabel = new Label();
        hoverLabel.getStyleClass().add("state-label");
        hoverLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "hoverValue: " + describe(rating.getHoverValue()), rating.hoverValueProperty()));
        Label actionLabel = new Label("onAction fired: 0");
        actionLabel.getStyleClass().add("state-label");
        rating.setOnAction(event -> actionLabel.setText("onAction fired: " + ++actions));

        Label description = new Label("The value is drawn exactly, so 3.7 fills 70% of the fourth star. "
                + "Hovering previews what a click would commit; only a click or a key commits and fires "
                + "onAction. Click the current value again to clear it. The icon shapes other than "
                + "the star and the heart come from this page's stylesheet; Faces gives each position "
                + "its own shape through .item-1 to .item-5.");
        description.getStyleClass().add("description");
        description.setWrapText(true);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setMaxWidth(440.0);

        VBox box = new VBox(12.0, new StackPane(rating), valueLabel, hoverLabel, actionLabel, description);
        box.getStyleClass().add("rating-preview");
        box.setAlignment(Pos.CENTER);
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Value", buildValueGrid()),
                section("Input", buildInputGrid()),
                section("Appearance", buildAppearanceGrid()));
    }

    // ==================== Sections ====================

    private Node buildValueGrid() {
        Slider valueSlider = createSlider(0.0, 10.0, rating.getValue());
        valueSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (!syncingValue && !rating.valueProperty().isBound()) {
                rating.setValue(Math.round(newV.doubleValue() * 10.0) / 10.0);
            }
        });
        // A value the user commits on the rating moves the slider, without a write back.
        rating.valueProperty().addListener((obs, oldV, newV) -> {
            syncingValue = true;
            valueSlider.setValue(newV == null ? 0.0 : newV);
            syncingValue = false;
        });
        Label valueLabel = createValueLabel(valueSlider, "%.1f");

        HBox presets = new HBox(8.0,
                preset("Not rated (null)", null), preset("0", 0.0), preset("3.7", 3.7), preset("4.25", 4.25));

        sliderValue = valueSlider.valueProperty().asObject();
        CheckBox bound = new CheckBox("Bind value to the slider (takes no input)");
        bound.selectedProperty().addListener((obs, was, on) -> {
            if (on) {
                rating.valueProperty().bind(sliderValue);
            } else {
                rating.valueProperty().unbind();
            }
            presets.setDisable(on);
        });

        Slider maxSlider = createSlider(1.0, 10.0, rating.getMax());
        maxSlider.setMajorTickUnit(1.0);
        maxSlider.setMinorTickCount(0);
        maxSlider.setSnapToTicks(true);
        maxSlider.valueProperty().addListener((obs, oldV, newV) -> rating.setMax((int) Math.round(newV.doubleValue())));
        Label maxLabel = createValueLabel(maxSlider, "%.0f");

        return createGrid(
                row("Value", valueSlider, valueLabel),
                row(presets),
                row(bound),
                row("Max", maxSlider, maxLabel));
    }

    private Button preset(String text, Double value) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setOnAction(event -> rating.setValue(value));
        return button;
    }

    private Node buildInputGrid() {
        ComboBox<RXRating.Step> step = new ComboBox<>();
        step.getItems().setAll(RXRating.Step.values());
        step.setValue(rating.getStep());
        step.setMaxWidth(Double.MAX_VALUE);
        rating.stepProperty().bind(step.valueProperty());

        ComboBox<String> keyboardStep = new ComboBox<>();
        keyboardStep.getItems().add("Follow step");
        for (RXRating.Step choice : RXRating.Step.values()) {
            keyboardStep.getItems().add(choice.name());
        }
        keyboardStep.getSelectionModel().select(0);
        keyboardStep.setMaxWidth(Double.MAX_VALUE);
        keyboardStep.getSelectionModel().selectedIndexProperty().addListener((obs, was, now) ->
                rating.setKeyboardStep(now.intValue() == 0 ? null : RXRating.Step.values()[now.intValue() - 1]));

        CheckBox clearable = new CheckBox("Clearable");
        clearable.setSelected(rating.isClearable());
        rating.clearableProperty().bind(clearable.selectedProperty());

        CheckBox readOnly = new CheckBox("Read-only");
        rating.readOnlyProperty().bind(readOnly.selectedProperty());

        CheckBox disabled = new CheckBox("Disabled");
        rating.disableProperty().bind(disabled.selectedProperty());

        return createGrid(
                row("Step", step),
                row("Keyboard step", keyboardStep),
                row(clearable),
                row(readOnly),
                row(disabled));
    }

    private Node buildAppearanceGrid() {
        Slider gapSlider = createSlider(0.0, 24.0, rating.getGap());
        rating.gapProperty().bind(gapSlider.valueProperty());
        Label gapLabel = createValueLabel(gapSlider, "%.0f px");

        ComboBox<String> icon = styleClassChoice(ICONS, 0);
        ComboBox<String> size = styleClassChoice(SIZES, 2);

        ColorPicker filled = new ColorPicker(DEFAULT_FILLED);
        ColorPicker empty = new ColorPicker(DEFAULT_EMPTY);
        filled.setMaxWidth(Double.MAX_VALUE);
        empty.setMaxWidth(Double.MAX_VALUE);
        CheckBox customColors = new CheckBox("Custom colors");
        filled.disableProperty().bind(customColors.selectedProperty().not());
        empty.disableProperty().bind(customColors.selectedProperty().not());
        // Both colors are looked-up colors, so an inline style on the rating sets them.
        Runnable applyColors = () -> rating.setStyle(customColors.isSelected()
                ? "-rx-rating-color: " + css(filled.getValue())
                        + "; -rx-rating-empty-color: " + css(empty.getValue()) + ";"
                : null);
        customColors.selectedProperty().addListener((obs, was, on) -> applyColors.run());
        filled.valueProperty().addListener((obs, was, now) -> applyColors.run());
        empty.valueProperty().addListener((obs, was, now) -> applyColors.run());

        CheckBox rightToLeft = new CheckBox("Right-to-left");
        rightToLeft.selectedProperty().addListener((obs, was, on) -> rating.setNodeOrientation(
                on ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Icon", icon),
                row("Size", size),
                row("Gap", gapSlider, gapLabel),
                row(customColors),
                row("Filled", filled),
                row("Empty", empty),
                row(rightToLeft));
    }

    /** A choice of names that swaps the matching style class on the rating. */
    private ComboBox<String> styleClassChoice(String[][] choices, int selected) {
        ComboBox<String> box = new ComboBox<>();
        for (String[] choice : choices) {
            box.getItems().add(choice[0]);
        }
        box.setMaxWidth(Double.MAX_VALUE);
        box.getSelectionModel().select(selected);
        box.getSelectionModel().selectedIndexProperty().addListener((obs, was, now) -> {
            String old = choices[was.intValue()][1];
            String chosen = choices[now.intValue()][1];
            if (old != null) {
                rating.getStyleClass().remove(old);
            }
            if (chosen != null) {
                rating.getStyleClass().add(chosen);
            }
        });
        return box;
    }

    private static String css(Color color) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", Math.round(color.getRed() * 255.0),
                Math.round(color.getGreen() * 255.0), Math.round(color.getBlue() * 255.0), color.getOpacity());
    }

    private static String describe(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.2f", value);
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

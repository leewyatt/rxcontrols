package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.samples.demo.RXRatingDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXRating}.
 *
 * <p>Exposes the value (any fraction, or not rated), the icon count, the input step,
 * clearing, the read-only and disabled states, the gap, the icon and the layout
 * direction. Under the rating it shows the committed value, the hover preview and how
 * often the action fired, so moving over the stars can be seen to commit nothing. For
 * a minimal example see {@link RXRatingDemo}.</p>
 */
public class RXRatingShowcase extends RXShowcaseApplication {

    private RXRating rating;
    private int actions;
    private boolean syncingValue;

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
        rating.getStyleClass().add("showcase-large");
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
                + "onAction. Click the current value again to clear it.");
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
            if (!syncingValue) {
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

        Button notRated = new Button("Set not rated (null)");
        notRated.setMaxWidth(Double.MAX_VALUE);
        notRated.setOnAction(event -> rating.setValue(null));

        Slider maxSlider = createSlider(1.0, 10.0, rating.getMax());
        maxSlider.setMajorTickUnit(1.0);
        maxSlider.setMinorTickCount(0);
        maxSlider.setSnapToTicks(true);
        maxSlider.valueProperty().addListener((obs, oldV, newV) -> rating.setMax((int) Math.round(newV.doubleValue())));
        Label maxLabel = createValueLabel(maxSlider, "%.0f");

        return createGrid(
                row("Value", valueSlider, valueLabel),
                row(notRated),
                row("Max", maxSlider, maxLabel));
    }

    private Node buildInputGrid() {
        ComboBox<RXRating.Step> step = new ComboBox<>();
        step.getItems().setAll(RXRating.Step.values());
        step.setValue(rating.getStep());
        step.setMaxWidth(Double.MAX_VALUE);
        rating.stepProperty().bind(step.valueProperty());

        CheckBox clearable = new CheckBox("Clearable");
        clearable.setSelected(rating.isClearable());
        rating.clearableProperty().bind(clearable.selectedProperty());

        CheckBox readOnly = new CheckBox("Read-only");
        rating.readOnlyProperty().bind(readOnly.selectedProperty());

        CheckBox disabled = new CheckBox("Disabled");
        rating.disableProperty().bind(disabled.selectedProperty());

        return createGrid(
                row("Step", step),
                row(clearable),
                row(readOnly),
                row(disabled));
    }

    private Node buildAppearanceGrid() {
        Slider gapSlider = createSlider(0.0, 24.0, rating.getGap());
        rating.gapProperty().bind(gapSlider.valueProperty());
        Label gapLabel = createValueLabel(gapSlider, "%.0f px");

        CheckBox hearts = new CheckBox("Hearts (heart style class)");
        hearts.selectedProperty().addListener((obs, was, on) -> {
            if (on) {
                rating.getStyleClass().add(RXRating.STYLE_CLASS_HEART);
            } else {
                rating.getStyleClass().remove(RXRating.STYLE_CLASS_HEART);
            }
        });

        CheckBox rightToLeft = new CheckBox("Right-to-left");
        rightToLeft.selectedProperty().addListener((obs, was, on) -> rating.setNodeOrientation(
                on ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Gap", gapSlider, gapLabel),
                row(hearts),
                row(rightToLeft));
    }

    private static String describe(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.1f", value);
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

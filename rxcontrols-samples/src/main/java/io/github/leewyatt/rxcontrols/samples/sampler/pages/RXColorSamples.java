package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.RXColorPresetGroup;
import io.github.leewyatt.rxcontrols.RXColorView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.List;

/**
 * The RXColor page: a brand color field with presets that saves on each
 * completed edit, an optional highlight color that can be cleared, an opaque
 * text color in RGB, and RXColorView as an inline editor recoloring a card.
 */
public final class RXColorSamples {

    private RXColorSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A color swatch that opens a color panel, and the panel as a view: area, hue and opacity "
                        + "sliders, hex / RGB / HSB inputs and preset groups.",
                List.of("Theme and brand settings, chart and label colors, drawing tools.",
                        "Edits write the value live; save in onAction, which fires once per completed edit."),
                List.of(
                        SampleCard.medium("Brand color",
                                "Brand presets and the hex text on the trigger; the label shows the last saved color.",
                                brand()).forType(RXColorPicker.class),
                        SampleCard.medium("Optional highlight",
                                "clearable adds a clear button; no color shows a slashed swatch and the prompt text.",
                                highlight()).forType(RXColorPicker.class),
                        SampleCard.medium("Opaque text color",
                                "alphaEnabled false hides the opacity controls; the format starts in RGB.",
                                textColor()).forType(RXColorPicker.class),
                        SampleCard.large("Inline editor",
                                "An RXColorView beside a card it recolors while you drag.",
                                inline()).forType(RXColorView.class)),
                null);
    }

    private static Node brand() {
        RXColorPicker picker = new RXColorPicker(Color.web("#1769AA"));
        picker.setShowText(true);
        picker.setAccessibleText("Brand color");
        picker.getPresets().add(new RXColorPresetGroup("Brand", Color.web("#1769AA"), Color.web("#F57C00"),
                Color.web("#2E7D32"), Color.web("#C62828"), Color.web("#6A1B9A")));
        Label saved = new Label("Saved: #1769AA");
        picker.setOnAction(event -> saved.setText(picker.getValue() == null ? "Saved: none"
                : "Saved: " + picker.getValue()));
        return column(row("Brand", picker), saved);
    }

    private static Node highlight() {
        RXColorPicker picker = new RXColorPicker(null);
        picker.setClearable(true);
        picker.setShowText(true);
        picker.setPromptText("No highlight");
        picker.setAccessibleText("Highlight color");
        picker.getPresets().add(new RXColorPresetGroup(null, Color.rgb(255, 235, 59, 0.5),
                Color.rgb(129, 199, 132, 0.5), Color.rgb(100, 181, 246, 0.5)));
        return row("Highlight", picker);
    }

    private static Node textColor() {
        RXColorPicker picker = new RXColorPicker(Color.web("#263238"));
        picker.setAlphaEnabled(false);
        picker.setFormat(RXColorFormat.RGB);
        picker.setShowText(true);
        picker.setAccessibleText("Text color");
        return row("Text", picker);
    }

    private static Node inline() {
        RXColorView view = new RXColorView(Color.web("#F57C00"));
        Rectangle card = new Rectangle(160.0, 100.0);
        card.setArcWidth(16.0);
        card.setArcHeight(16.0);
        card.fillProperty().bind(view.valueProperty());
        HBox box = new HBox(24.0, view, card);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox row(String label, Node control) {
        HBox row = new HBox(12.0, new Label(label), control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static VBox column(Node... nodes) {
        return new VBox(12.0, nodes);
    }
}

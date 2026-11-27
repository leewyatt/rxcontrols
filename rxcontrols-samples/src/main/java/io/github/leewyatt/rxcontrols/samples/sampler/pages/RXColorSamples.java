package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXColorFormat;
import io.github.leewyatt.rxcontrols.RXColorPicker;
import io.github.leewyatt.rxcontrols.RXColorPresetGroup;
import io.github.leewyatt.rxcontrols.RXColorView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * The RXColor page: a brand color field with presets and a copy button, an
 * optional highlight color that can be cleared,
 * an inline opaque text color editor in RGB with a clear button, and RXColorView
 * as an inline editor.
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
                                "Brand presets, the hex text and a copy button on the trigger.",
                                brand()).forType(RXColorPicker.class),
                        SampleCard.medium("Optional highlight",
                                "clearable adds a clear button; no color shows a slashed swatch and the prompt text.",
                                highlight()).forType(RXColorPicker.class),
                        SampleCard.medium("Opaque text color",
                                "alphaEnabled false hides the opacity slider; the format starts in RGB "
                                        + "and clearable adds a clear button to the header.",
                                textColor()).forType(RXColorView.class),
                        SampleCard.medium("Inline editor",
                                "RXColorView placed in the page, without a popup.",
                                inline()).forType(RXColorView.class)),
                null);
    }

    private static Node brand() {
        RXColorPicker picker = new RXColorPicker(Color.web("#1769AA"));
        picker.setShowText(true);
        picker.setCopyButtonVisible(true);
        picker.setAccessibleText("Brand color");
        picker.getPresets().add(new RXColorPresetGroup("Brand", Color.web("#1769AA"), Color.web("#F57C00"),
                Color.web("#2E7D32"), Color.web("#C62828"), Color.web("#6A1B9A")));
        return row("Brand", picker);
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
        RXColorView view = new RXColorView(Color.web("#263238"));
        view.setAlphaEnabled(false);
        view.setFormat(RXColorFormat.RGB);
        view.setClearable(true);
        view.setAccessibleText("Text color");
        return view;
    }

    private static Node inline() {
        return new RXColorView(Color.web("#F57C00"));
    }

    private static HBox row(String label, Node control) {
        HBox row = new HBox(12.0, new Label(label), control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}

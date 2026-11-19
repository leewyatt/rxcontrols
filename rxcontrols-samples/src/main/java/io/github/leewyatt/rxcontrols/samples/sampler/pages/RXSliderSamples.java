package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorDisplay;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorPosition;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXSlider page: a volume control with a changing speaker icon, a color mixer,
 * snapping to labeled ticks, the value bubble policies and a vertical equalizer.
 */
public final class RXSliderSamples {

    private static final String[] BANDS = {"60", "230", "910", "3.6k", "14k"};
    private static final double[] PRESET = {4.0, 2.0, -1.0, 3.0, 5.0};
    private static final String[] BAND_COLORS = {"rose", "orange", "amber", "teal", "sky"};

    private RXSliderSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A Material slider for one value in a range, with a value bubble and its own tick marks.",
                List.of("Values where the rough position matters more than the exact number: volume, brightness, zoom.",
                        "For a low and a high value use RXRangeSlider; for exact numbers a number field is faster."),
                List.of(
                        SampleCard.large("Volume",
                                "The speaker icon follows the value; clicking it mutes and restores the last level.",
                                volume()),
                        SampleCard.large("Color mixer",
                                "Three sliders recolored with -rx-primary mix the swatch as they move.",
                                mixer()),
                        SampleCard.large("Ticks and snapping",
                                "showTickLabels with a labelFormatter names each step; snapToTicks lands on them.",
                                ticks()),
                        SampleCard.large("Value bubble",
                                "HOVER and ALWAYS show the value bubble beyond dragging; END puts it below the track.",
                                bubbles()),
                        SampleCard.wide("Equalizer",
                                "Vertical sliders from -12 to +12 dB, each in its own color, with the bubble on hover.",
                                equalizer())),
                SelectionIcons.STYLESHEET);
    }

    private static Node volume() {
        RXSlider slider = new RXSlider(0.0, 100.0, 65.0);
        slider.setPrefWidth(220.0);
        HBox.setHgrow(slider, Priority.ALWAYS);
        Region speaker = icon("volume-up-icon");
        speaker.setMouseTransparent(true);
        HBox mute = new HBox(speaker);
        mute.getStyleClass().add("icon-button");
        mute.setAlignment(Pos.CENTER);
        double[] last = {65.0};
        mute.setOnMouseClicked(event -> {
            if (slider.getValue() > 0.0) {
                last[0] = slider.getValue();
                slider.setValue(0.0);
            } else {
                slider.setValue(last[0]);
            }
        });
        slider.valueProperty().addListener((obs, old, now) -> {
            double v = now.doubleValue();
            speaker.getStyleClass().removeAll("volume-up-icon", "volume-down-icon", "volume-off-icon");
            speaker.getStyleClass().add(v == 0.0 ? "volume-off-icon" : v < 50.0 ? "volume-down-icon" : "volume-up-icon");
        });
        Label value = text("", "value");
        value.setMinWidth(30.0);
        value.textProperty().bind(Bindings.format("%.0f", slider.valueProperty()));
        HBox row = new HBox(10.0, mute, slider, value);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(320.0);
        row.setMaxWidth(Region.USE_PREF_SIZE);
        return row;
    }

    private static Node mixer() {
        RXSlider red = channel("rose", 244.0);
        RXSlider green = channel("lime", 114.0);
        RXSlider blue = channel("sky", 182.0);
        Rectangle swatch = new Rectangle(64.0, 64.0);
        swatch.setArcWidth(16.0);
        swatch.setArcHeight(16.0);
        swatch.fillProperty().bind(Bindings.createObjectBinding(
                () -> Color.rgb((int) red.getValue(), (int) green.getValue(), (int) blue.getValue()),
                red.valueProperty(), green.valueProperty(), blue.valueProperty()));
        Label hex = text("", "value");
        hex.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("#%02X%02X%02X", (int) red.getValue(), (int) green.getValue(),
                        (int) blue.getValue()),
                red.valueProperty(), green.valueProperty(), blue.valueProperty()));
        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(4.0);
        grid.addRow(0, text("R", "hint"), red);
        grid.addRow(1, text("G", "hint"), green);
        grid.addRow(2, text("B", "hint"), blue);
        VBox preview = new VBox(6.0, swatch, hex);
        preview.setAlignment(Pos.CENTER);
        HBox row = new HBox(18.0, grid, preview);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXSlider channel(String color, double value) {
        RXSlider slider = new RXSlider(0.0, 255.0, value);
        slider.getStyleClass().add(color);
        slider.setPrefWidth(170.0);
        return slider;
    }

    private static Node ticks() {
        String[] names = {"XS", "S", "M", "L", "XL"};
        RXSlider size = new RXSlider(0.0, 4.0, 2.0);
        size.setMajorTickUnit(1.0);
        size.setMinorTickCount(0);
        size.setShowTickMarks(true);
        size.setShowTickLabels(true);
        size.setSnapToTicks(true);
        size.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double value) {
                int index = (int) Math.round(value);
                return index >= 0 && index < names.length ? names[index] : "";
            }

            @Override
            public Double fromString(String text) {
                return 0.0;
            }
        });
        size.setPrefWidth(260.0);
        Label caption = text("Text size in the reader", "hint");
        VBox box = new VBox(8.0, caption, size);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node bubbles() {
        RXSlider hover = percent(40.0, RXSliderIndicatorDisplay.HOVER);
        RXSlider always = percent(70.0, RXSliderIndicatorDisplay.ALWAYS);
        always.setIndicatorPosition(RXSliderIndicatorPosition.END);
        always.getStyleClass().add("violet");
        VBox box = new VBox(8.0, text("Brightness, bubble on hover", "hint"), hover,
                text("Opacity, bubble always shown below", "hint"), always);
        VBox.setMargin(always, new Insets(0.0, 0.0, 30.0, 0.0));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXSlider percent(double value, RXSliderIndicatorDisplay display) {
        RXSlider slider = new RXSlider(0.0, 100.0, value);
        slider.setIndicatorDisplay(display);
        slider.setLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Double number) {
                return Math.round(number) + "%";
            }

            @Override
            public Double fromString(String text) {
                return 0.0;
            }
        });
        slider.setPrefWidth(260.0);
        return slider;
    }

    private static Node equalizer() {
        HBox bands = new HBox(28.0);
        bands.setAlignment(Pos.CENTER);
        for (int i = 0; i < BANDS.length; i++) {
            RXSlider band = new RXSlider(-12.0, 12.0, PRESET[i]);
            band.setOrientation(Orientation.VERTICAL);
            band.setIndicatorDisplay(RXSliderIndicatorDisplay.HOVER);
            band.setPrefHeight(150.0);
            band.getStyleClass().add(BAND_COLORS[i]);
            Label gain = text("", "value");
            gain.textProperty().bind(gainText(band));
            VBox column = new VBox(8.0, gain, band, text(BANDS[i] + " Hz", "hint"));
            column.setAlignment(Pos.CENTER);
            // Wide enough for "+12 dB": a column that grows with its gain text would
            // shift every band sideways while one is dragged.
            column.setPrefWidth(60.0);
            column.setMinWidth(Region.USE_PREF_SIZE);
            bands.getChildren().add(column);
        }
        return bands;
    }

    private static ObservableValue<String> gainText(RXSlider band) {
        return Bindings.createStringBinding(
                () -> String.format("%+.0f dB", band.getValue()), band.valueProperty());
    }
}

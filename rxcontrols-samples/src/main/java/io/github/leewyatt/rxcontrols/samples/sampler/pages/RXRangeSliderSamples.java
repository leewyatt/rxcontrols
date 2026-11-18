package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.RXSliderIndicatorDisplay;
import javafx.beans.binding.Bindings;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Function;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXRangeSlider page: a price filter with a live match count, working hours kept
 * apart by a minimum gap, dragging the band, snapping to ticks, sizes and colors, and a
 * vertical range.
 */
public final class RXRangeSliderSamples {

    private static final int[] PRICES = {29, 45, 60, 89, 120, 149, 180, 210, 260, 320, 380, 450, 520, 640, 790, 950};

    private RXRangeSliderSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A slider with two thumbs that picks a low and a high value inside one range.",
                List.of("Filtering by a span of values: a price range, an age group, opening hours.",
                        "For a single value use RXSlider; for exact bounds pair it with two number fields."),
                List.of(
                        SampleCard.large("Price filter",
                                "Moving either thumb updates the price span and how many products fall inside it.",
                                priceFilter()),
                        SampleCard.large("Minimum gap",
                                "minGap keeps the shift at least 4 hours long; the thumbs stop instead of crossing.",
                                workingHours()),
                        SampleCard.large("Drag the band",
                                "Drag the colored band to move the clip as a whole; rangeDraggable is on by default.",
                                trim()),
                        SampleCard.large("Ticks and snapping",
                                "Tick marks and labels every 10 years, with snapToTicks on.",
                                ages()),
                        SampleCard.large("Sizes and colors",
                                "The small and large style classes, recolored with -rx-primary.",
                                sizes()),
                        SampleCard.large("Vertical",
                                "orientation VERTICAL turns the range upright, here a thermostat's comfort band.",
                                thermostat())),
                SelectionIcons.STYLESHEET);
    }

    private static Node priceFilter() {
        RXRangeSlider range = new RXRangeSlider(0.0, 1000.0, 100.0, 600.0);
        range.setLabelFormatter(formatter(v -> "$" + Math.round(v)));
        range.setPrefWidth(320.0);
        Label span = text("", "figure-small");
        span.textProperty().bind(Bindings.createStringBinding(
                () -> "$" + Math.round(range.getLowValue()) + " – $" + Math.round(range.getHighValue()),
                range.lowValueProperty(), range.highValueProperty()));
        Label count = text("", "hint");
        count.textProperty().bind(Bindings.createStringBinding(
                () -> matches(range.getLowValue(), range.getHighValue()) + " of " + PRICES.length + " products",
                range.lowValueProperty(), range.highValueProperty()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8.0, text("Price", "body"), spacer, span);
        header.setAlignment(Pos.BASELINE_LEFT);
        VBox box = new VBox(10.0, header, range, count);
        box.getStyleClass().add("form-panel");
        box.setPrefWidth(360.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static int matches(double low, double high) {
        int count = 0;
        for (int price : PRICES) {
            if (price >= low && price <= high) {
                count++;
            }
        }
        return count;
    }

    private static Node workingHours() {
        RXRangeSlider hours = new RXRangeSlider(0.0, 24.0, 9.0, 17.0);
        hours.setMinGap(4.0);
        hours.setMajorTickUnit(6.0);
        hours.setMinorTickCount(5);
        hours.setSnapToTicks(true);
        hours.setShowTickMarks(true);
        hours.setShowTickLabels(true);
        hours.setLabelFormatter(formatter(RXRangeSliderSamples::clock));
        hours.getStyleClass().add("teal");
        hours.setPrefWidth(320.0);
        Label shift = text("", "hint");
        shift.textProperty().bind(Bindings.createStringBinding(
                () -> "Shift " + clock(hours.getLowValue()) + " to " + clock(hours.getHighValue())
                        + ", " + Math.round(hours.getHighValue() - hours.getLowValue()) + " hours",
                hours.lowValueProperty(), hours.highValueProperty()));
        VBox box = new VBox(10.0, hours, shift);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static String clock(double hours) {
        return String.format("%02d:00", Math.round(hours));
    }

    private static Node trim() {
        RXRangeSlider clip = new RXRangeSlider(0.0, 60.0, 12.0, 27.0);
        clip.setIndicatorDisplay(RXSliderIndicatorDisplay.HOVER);
        clip.setLabelFormatter(formatter(v -> String.format("0:%02d", Math.round(v))));
        clip.getStyleClass().addAll("large", "pink");
        clip.setPrefWidth(280.0);
        Label length = text("", "hint");
        length.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("Clip 0:%02d to 0:%02d, %d s", Math.round(clip.getLowValue()),
                        Math.round(clip.getHighValue()), Math.round(clip.getHighValue() - clip.getLowValue())),
                clip.lowValueProperty(), clip.highValueProperty()));
        VBox box = new VBox(10.0, clip, length);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node ages() {
        RXRangeSlider age = new RXRangeSlider(20.0, 80.0, 30.0, 50.0);
        age.setMajorTickUnit(10.0);
        age.setMinorTickCount(0);
        age.setShowTickMarks(true);
        age.setShowTickLabels(true);
        age.setSnapToTicks(true);
        age.getStyleClass().add("indigo");
        age.setPrefWidth(280.0);
        Label caption = text("Audience age", "hint");
        VBox box = new VBox(8.0, caption, age);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node sizes() {
        GridPane grid = new GridPane();
        grid.setHgap(14.0);
        grid.setVgap(16.0);
        grid.addRow(0, text("Small", "hint"), sized("small", "orange", 20.0, 55.0));
        grid.addRow(1, text("Default", "hint"), sized(null, "lime", 30.0, 70.0));
        grid.addRow(2, text("Large", "hint"), sized("large", "violet", 40.0, 85.0));
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node sized(String size, String color, double low, double high) {
        RXRangeSlider slider = new RXRangeSlider(0.0, 100.0, low, high);
        if (size != null) {
            slider.getStyleClass().add(size);
        }
        slider.getStyleClass().add(color);
        slider.setPrefWidth(240.0);
        return slider;
    }

    private static Node thermostat() {
        RXRangeSlider comfort = new RXRangeSlider(14.0, 30.0, 19.0, 23.0);
        comfort.setOrientation(Orientation.VERTICAL);
        comfort.setMinGap(1.0);
        comfort.setMajorTickUnit(4.0);
        comfort.setMinorTickCount(3);
        comfort.setShowTickMarks(true);
        comfort.setShowTickLabels(true);
        comfort.setLabelFormatter(formatter(v -> Math.round(v) + "°"));
        comfort.getStyleClass().add("orange");
        comfort.setPrefHeight(170.0);
        Label heat = text("", "hint");
        heat.textProperty().bind(Bindings.format("Heat below %.0f °C", comfort.lowValueProperty()));
        Label cool = text("", "hint");
        cool.textProperty().bind(Bindings.format("Cool above %.0f °C", comfort.highValueProperty()));
        VBox notes = new VBox(8.0, cool, heat);
        HBox row = new HBox(24.0, comfort, notes);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static StringConverter<Number> formatter(Function<Double, String> format) {
        return new StringConverter<>() {
            @Override
            public String toString(Number value) {
                return value == null ? "" : format.apply(value.doubleValue());
            }

            @Override
            public Number fromString(String text) {
                return 0.0;
            }
        };
    }
}

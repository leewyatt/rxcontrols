package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXYearPicker;
import io.github.leewyatt.rxcontrols.RXYearView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Year;
import java.util.List;

/**
 * The RXYear page: year pickers for a car's model year and a clearable graduation year,
 * and RXYearView as a photo archive with gaps and as a fiscal-year switcher.
 */
public final class RXYearSamples {

    private static final double FIELD_WIDTH = 180.0;
    private static final int FIRST_PHOTO_YEAR = 2014;

    private RXYearSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field for picking a year from a popup grid of a decade, and the grid as a view.",
                List.of("Model years, graduation years, fiscal years and yearly archives.",
                        "For a span of years use RXYearRangePicker; for a month of a year use RXMonthPicker."),
                List.of(
                        SampleCard.medium("Model year",
                                "minYear and maxYear limit the choice to 1990 through next year's models.",
                                modelYear()).forType(RXYearPicker.class),
                        SampleCard.medium("Graduation year",
                                "clearable shows a clear button on hover for an optional field.",
                                graduation()).forType(RXYearPicker.class),
                        SampleCard.large("Photo archive",
                                "A yearFilter disables the years without photos; the count follows the selected year.",
                                photos()).forType(RXYearView.class),
                        SampleCard.large("Fiscal year",
                                "An inline RXYearView that switches the fiscal year shown in the panel.",
                                fiscalYear()).forType(RXYearView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node modelYear() {
        Year now = Year.now();
        RXYearPicker picker = new RXYearPicker(Year.of(2021));
        picker.setMinYear(Year.of(1990));
        picker.setMaxYear(now.plusYears(1));
        return DateTimeKit.field("Model year", DateTimeKit.icon("car-shape", "sky"), picker, FIELD_WIDTH,
                DateTimeKit.text("1990 – " + now.plusYears(1), "hint"));
    }

    private static Node graduation() {
        RXYearPicker picker = new RXYearPicker(Year.now().plusYears(2));
        picker.setClearable(true);
        picker.setPromptText("Optional");
        return DateTimeKit.field("Graduation year", DateTimeKit.icon("school-shape", "violet"), picker, FIELD_WIDTH);
    }

    private static Node photos() {
        Year now = Year.now();
        RXYearView view = new RXYearView(now.minusYears(1));
        view.getStyleClass().add("accent-pink");
        view.setMinYear(Year.of(FIRST_PHOTO_YEAR));
        view.setMaxYear(now);
        view.setYearFilter(year -> year.getValue() != 2020);
        Label count = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : String.format("%,d photos", photoCount(view.getValue())), view.valueProperty());
        Label note = DateTimeKit.text("No photos from 2020.", "hint");
        VBox panel = DateTimeKit.panel("Lee Wyatt's library", 170.0, count, note);
        return row(view, panel);
    }

    private static Node fiscalYear() {
        Year now = Year.now();
        RXYearView view = new RXYearView(now);
        view.getStyleClass().add("accent-indigo");
        view.setMaxYear(now.plusYears(1));
        Label name = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "FY" + view.getValue().getValue(), view.valueProperty());
        Label span = DateTimeKit.bound("hint", () -> view.getValue() == null ? ""
                : "Apr " + view.getValue().minusYears(1) + " – Mar " + view.getValue(), view.valueProperty());
        VBox panel = DateTimeKit.panel("Fiscal year", 170.0, name, span);
        return row(view, panel);
    }

    private static int photoCount(Year year) {
        return 380 + (year.getValue() * 137) % 1100;
    }

    private static Node row(Node view, Node panel) {
        HBox row = new HBox(16.0, view, panel);
        row.setAlignment(Pos.CENTER);
        return row;
    }
}

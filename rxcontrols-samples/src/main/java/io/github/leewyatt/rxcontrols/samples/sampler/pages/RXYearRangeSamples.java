package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXYearRange;
import io.github.leewyatt.rxcontrols.RXYearRangePicker;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * The RXYearRange page: year range pickers for a job on a résumé and a warranty that
 * starts this year, and RXYearRangeView driving a chart over a decade and spanning two
 * decades.
 */
public final class RXYearRangeSamples {

    private static final double FIELD_WIDTH = 240.0;
    private static final int FIRST_CHART_YEAR = 2016;
    private static final double[] DOWNLOADS = {12, 18, 25, 31, 46, 58, 64, 81, 97, 120};

    private RXYearRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a first and a last year, picked from decade grids, and the grids as a view.",
                List.of("Employment history, warranties, terms and statistics that span whole years.",
                        "For a single year use RXYearPicker; for months use RXMonthRangePicker."),
                List.of(
                        SampleCard.medium("Work experience",
                                "From and To prompts for a résumé entry; maxYear stops at this year.",
                                experience()).forType(RXYearRangePicker.class),
                        SampleCard.medium("Extended warranty",
                                "minYear set to this year; the coverage in years comes from the value.",
                                warranty()).forType(RXYearRangePicker.class),
                        SampleCard.wide("Downloads by year",
                                "decadeCount 1 shows one decade; the chart highlights the years in the range.",
                                downloads()).forType(RXYearRangeView.class),
                        SampleCard.wide("Two decades",
                                "The default two decades side by side, for ranges longer than ten years.",
                                career()).forType(RXYearRangeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node experience() {
        RXYearRangePicker picker = new RXYearRangePicker(RXYearRange.of(Year.of(2019), Year.of(2024)));
        picker.setStartPromptText("From");
        picker.setEndPromptText("To");
        picker.setMaxYear(Year.now());
        return DateTimeKit.field("Senior designer, Northwind", DateTimeKit.icon("work-shape", "amber"), picker,
                FIELD_WIDTH);
    }

    private static Node warranty() {
        Year now = Year.now();
        RXYearRangePicker picker = new RXYearRangePicker(RXYearRange.of(now, now.plusYears(2)));
        picker.setMinYear(now);
        Label years = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No coverage"
                : DateTimeKit.plural(picker.getValue().lengthInYears(), "year") + " of coverage",
                picker.valueProperty());
        return DateTimeKit.field("Laptop warranty", DateTimeKit.icon("doc-shape", "teal"), picker, FIELD_WIDTH, years);
    }

    private static Node[] downloads() {
        RXYearRangeView view = new RXYearRangeView(RXYearRange.of(Year.of(2021), Year.of(2024)));
        view.getStyleClass().add("accent-orange");
        view.setDecadeCount(1);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < DOWNLOADS.length; i++) {
            labels.add("'" + String.valueOf(FIRST_CHART_YEAR + i).substring(2));
        }
        DateTimeKit.Bars bars = new DateTimeKit.Bars("orange", labels, DOWNLOADS);
        Runnable mark = () -> bars.mark(i -> view.getValue() != null
                && view.getValue().contains(Year.of(FIRST_CHART_YEAR + i)));
        view.valueProperty().addListener(observable -> mark.run());
        mark.run();
        Label total = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : total(view.getValue()) + "k downloads", view.valueProperty());
        VBox chart = new VBox(12.0, DateTimeKit.text("Downloads " + FIRST_CHART_YEAR + " – "
                + (FIRST_CHART_YEAR + DOWNLOADS.length - 1), "panel-heading"), total, bars);
        chart.getStyleClass().add("side-panel");
        return new Node[] {view, chart};
    }

    private static Node[] career() {
        RXYearRangeView view = new RXYearRangeView(RXYearRange.of(Year.of(2008), Year.of(2021)));
        view.getStyleClass().add("accent-violet");
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(view.getValue().lengthInYears(), "year"), view.valueProperty());
        Label note = DateTimeKit.text("Lee Wyatt, chess club member.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Membership", 160.0, length, note);
        return new Node[] {view, panel};
    }

    private static long total(RXYearRange range) {
        long sum = 0;
        for (int i = 0; i < DOWNLOADS.length; i++) {
            if (range.contains(Year.of(FIRST_CHART_YEAR + i))) {
                sum += (long) DOWNLOADS[i];
            }
        }
        return sum;
    }
}

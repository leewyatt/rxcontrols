package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMonthRange;
import io.github.leewyatt.rxcontrols.RXMonthRangePicker;
import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The RXMonthRange page: month range pickers for a subscription term and with a custom
 * text, and RXMonthRangeView driving a revenue chart and planning a season across the
 * turn of the year.
 */
public final class RXMonthRangeSamples {

    private static final double FIELD_WIDTH = 250.0;
    private static final double[] REVENUE = {42, 38, 51, 56, 61, 70, 74, 69, 63, 58, 66, 83};

    private RXMonthRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a first and a last month, picked from grids of months, "
                        + "and the grids as a view.",
                List.of("Subscription terms, budgets, seasons and reports that run from month to month.",
                        "For a single month use RXMonthPicker; for exact days use RXDateRangePicker."),
                List.of(
                        SampleCard.medium("Subscription term",
                                "minMonth set to this month; the length in months comes from the value.",
                                subscription()).forType(RXMonthRangePicker.class),
                        SampleCard.medium("Custom format",
                                "A converter with the MMM yyyy pattern writes both fields in English.",
                                customFormat()).forType(RXMonthRangePicker.class),
                        SampleCard.wide("Revenue by month",
                                "yearCount 1 shows a single year; the chart highlights the months in the range.",
                                revenue()).forType(RXMonthRangeView.class),
                        SampleCard.wide("Ski season",
                                "Two years side by side, so a range can cross the turn of the year.",
                                season()).forType(RXMonthRangeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node subscription() {
        YearMonth now = YearMonth.now();
        RXMonthRangePicker picker = new RXMonthRangePicker(RXMonthRange.of(now, now.plusMonths(11)));
        picker.setMinMonth(now);
        Label months = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No term"
                : DateTimeKit.plural(picker.getValue().lengthInMonths(), "month"), picker.valueProperty());
        return DateTimeKit.field("Pro plan", DateTimeKit.icon("card-shape", "teal"), picker, FIELD_WIDTH, months);
    }

    private static Node customFormat() {
        int year = Year.now().getValue();
        RXMonthRangePicker picker = new RXMonthRangePicker(
                RXMonthRange.of(YearMonth.of(year + 1, 3), YearMonth.of(year + 1, 6)));
        picker.setConverter(DateTimeKit.converter("MMM yyyy", YearMonth::from));
        return DateTimeKit.field("Internship", DateTimeKit.icon("school-shape", "amber"), picker, FIELD_WIDTH);
    }

    private static Node[] revenue() {
        int year = Year.now().getValue();
        RXMonthRangeView view = new RXMonthRangeView(RXMonthRange.of(YearMonth.of(year, 4), YearMonth.of(year, 9)));
        view.getStyleClass().add("accent-teal");
        view.setYearCount(1);
        List<String> labels = new ArrayList<>();
        for (Month month : Month.values()) {
            labels.add(month.getDisplayName(TextStyle.NARROW, Locale.ENGLISH));
        }
        DateTimeKit.Bars bars = new DateTimeKit.Bars("teal", labels, REVENUE);
        Runnable mark = () -> bars.mark(i -> view.getValue() != null
                && view.getValue().contains(YearMonth.of(year, i + 1)));
        view.valueProperty().addListener(observable -> mark.run());
        mark.run();
        Label total = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "$" + total(view.getValue(), year) + "k", view.valueProperty());
        VBox chart = new VBox(12.0, DateTimeKit.text("Revenue " + year, "panel-heading"), total, bars);
        chart.getStyleClass().add("side-panel");
        return new Node[] {view, chart};
    }

    private static Node[] season() {
        int year = Year.now().getValue();
        RXMonthRangeView view = new RXMonthRangeView(
                RXMonthRange.of(YearMonth.of(year, 12), YearMonth.of(year + 1, 3)));
        view.getStyleClass().add("accent-indigo");
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(view.getValue().lengthInMonths(), "month"), view.valueProperty());
        Label note = DateTimeKit.text("Lifts open daily, snow permitting.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Season pass", 160.0, length, note);
        return new Node[] {view, panel};
    }

    private static long total(RXMonthRange range, int year) {
        long sum = 0;
        for (int i = 0; i < REVENUE.length; i++) {
            if (range.contains(YearMonth.of(year, i + 1))) {
                sum += (long) REVENUE[i];
            }
        }
        return sum;
    }
}

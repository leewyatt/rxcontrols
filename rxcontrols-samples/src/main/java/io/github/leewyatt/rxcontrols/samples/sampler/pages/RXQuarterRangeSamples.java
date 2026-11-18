package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXQuarterRange;
import io.github.leewyatt.rxcontrols.RXQuarterRangePicker;
import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * The RXQuarterRange page: quarter range pickers for a roadmap phase and a read-only,
 * clearable budget period, and RXQuarterRangeView driving a chart over two years and
 * planning ahead from the current quarter.
 */
public final class RXQuarterRangeSamples {

    private static final double FIELD_WIDTH = 250.0;
    private static final double[] REVENUE = {3.1, 3.4, 3.2, 4.0, 3.8, 4.3, 4.6, 5.2};

    private RXQuarterRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a first and a last quarter, picked from rows of quarters, "
                        + "and the rows as a view.",
                List.of("Roadmaps, budgets and reports that span several quarters.",
                        "For one quarter use RXQuarterPicker; for month precision use RXMonthRangePicker."),
                List.of(
                        SampleCard.medium("Roadmap phase",
                                "minQuarter set to the current quarter; the number of quarters follows the value.",
                                roadmap()).forType(RXQuarterRangePicker.class),
                        SampleCard.medium("Read-only and clearable",
                                "With editable off the fields only open the popup; clearable empties the range on hover.",
                                budget()).forType(RXQuarterRangePicker.class),
                        SampleCard.wide("Revenue by quarter",
                                "Two years of quarters; the chart highlights the quarters in the range.",
                                revenue()).forType(RXQuarterRangeView.class),
                        SampleCard.wide("Plan ahead",
                                "minQuarter set to the current quarter, so the quarters before it are disabled.",
                                planAhead()).forType(RXQuarterRangeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static RXYearQuarter current() {
        return RXYearQuarter.now(Clock.systemDefaultZone());
    }

    private static Node roadmap() {
        RXYearQuarter now = current();
        RXQuarterRangePicker picker = new RXQuarterRangePicker(RXQuarterRange.of(now.plusQuarters(1),
                now.plusQuarters(2)));
        picker.setMinQuarter(now);
        Label length = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No phase"
                : DateTimeKit.plural(picker.getValue().lengthInQuarters(), "quarter"), picker.valueProperty());
        return DateTimeKit.field("Phase 2: mobile app", DateTimeKit.icon("flag-shape", "sky"), picker, FIELD_WIDTH,
                length);
    }

    private static Node budget() {
        RXYearQuarter now = current();
        RXQuarterRangePicker picker = new RXQuarterRangePicker(RXQuarterRange.of(
                RXYearQuarter.of(now.year() + 1, 1), RXYearQuarter.of(now.year() + 1, 4)));
        picker.setEditable(false);
        picker.setClearable(true);
        return DateTimeKit.field("Budget period", DateTimeKit.icon("chart-shape", "rose"), picker, FIELD_WIDTH);
    }

    private static Node[] revenue() {
        int year = current().year() - 1;
        RXQuarterRangeView view = new RXQuarterRangeView(RXQuarterRange.of(RXYearQuarter.of(year, 3),
                RXYearQuarter.of(year + 1, 2)));
        view.getStyleClass().add("accent-pink");
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < REVENUE.length; i++) {
            labels.add("Q" + (i % 4 + 1));
        }
        DateTimeKit.Bars bars = new DateTimeKit.Bars("pink", labels, REVENUE);
        Runnable mark = () -> bars.mark(i -> view.getValue() != null
                && view.getValue().contains(RXYearQuarter.of(year + i / 4, i % 4 + 1)));
        view.valueProperty().addListener(observable -> mark.run());
        mark.run();
        Label total = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "$" + String.format("%.1f", total(view.getValue(), year)) + "M", view.valueProperty());
        VBox chart = new VBox(12.0, DateTimeKit.text("Revenue " + year + " – " + (year + 1), "panel-heading"),
                total, bars);
        chart.getStyleClass().add("side-panel");
        return new Node[] {view, chart};
    }

    private static Node[] planAhead() {
        RXYearQuarter now = current();
        RXQuarterRangeView view = new RXQuarterRangeView(RXQuarterRange.of(now.plusQuarters(1), now.plusQuarters(2)));
        view.getStyleClass().add("accent-teal");
        view.setMinQuarter(now);
        view.setDisplayedYear(Year.of(now.year()));
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(view.getValue().lengthInQuarters(), "quarter"), view.valueProperty());
        Label note = DateTimeKit.text("Hiring plan for the support team.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Hiring", 160.0, length, note);
        return new Node[] {view, panel};
    }

    private static double total(RXQuarterRange range, int year) {
        double sum = 0.0;
        for (int i = 0; i < REVENUE.length; i++) {
            if (range.contains(RXYearQuarter.of(year + i / 4, i % 4 + 1))) {
                sum += REVENUE[i];
            }
        }
        return sum;
    }
}

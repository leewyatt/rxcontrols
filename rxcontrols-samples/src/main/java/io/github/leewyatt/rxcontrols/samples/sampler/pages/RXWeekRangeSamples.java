package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXWeekRangePicker;
import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * The RXWeekRange page: week range pickers for a sprint and for comparing two periods,
 * and RXWeekRangeView as a release plan over two months and as a one-month planner that
 * starts this week.
 */
public final class RXWeekRangeSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXWeekRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a run of whole weeks, picked from calendars whose rows are weeks, "
                        + "and the calendars as a view.",
                List.of("Sprints, campaigns and rotations that start and end on week boundaries.",
                        "For a single week use RXWeekPicker; for spans of single days use RXDateRangePicker."),
                List.of(
                        SampleCard.medium("Sprint",
                                "ISO weekFields start the weeks on Monday; the value runs from the first day of the first week "
                                        + "to the last day of the last.",
                                sprint()).forType(RXWeekRangePicker.class),
                        SampleCard.medium("Compare periods",
                                "Two pickers side by side for a period and the one it is compared with.",
                                compare()).forType(RXWeekRangePicker.class),
                        SampleCard.wide("Release plan",
                                "Two months of week rows; the first pick anchors the range and the second ends it.",
                                releasePlan()).forType(RXWeekRangeView.class),
                        SampleCard.wide("Plan ahead",
                                "monthCount 1 and minWeek set to this week, so past weeks are disabled.",
                                planAhead()).forType(RXWeekRangeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node sprint() {
        LocalDate monday = thisMonday();
        RXWeekRangePicker picker = new RXWeekRangePicker(RXDateRange.of(monday, monday.plusDays(13)));
        picker.setWeekFields(WeekFields.ISO);
        Label weeks = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No sprint"
                : DateTimeKit.plural(weeks(picker.getValue()), "week"), picker.valueProperty());
        return DateTimeKit.field("Sprint 24", DateTimeKit.icon("flag-shape", "orange"), picker, FIELD_WIDTH, weeks);
    }

    private static Node compare() {
        LocalDate monday = thisMonday();
        RXWeekRangePicker current = new RXWeekRangePicker(RXDateRange.of(monday.minusWeeks(3), monday.plusDays(6)));
        RXWeekRangePicker previous = new RXWeekRangePicker(
                RXDateRange.of(monday.minusWeeks(7), monday.minusWeeks(4).plusDays(6)));
        current.setWeekFields(WeekFields.ISO);
        previous.setWeekFields(WeekFields.ISO);
        VBox box = new VBox(14.0,
                DateTimeKit.field("This period", DateTimeKit.icon("chart-shape", "indigo"), current, FIELD_WIDTH),
                DateTimeKit.field("Compared with", DateTimeKit.icon("chart-shape", "pink"), previous, FIELD_WIDTH));
        box.setFillWidth(false);
        return box;
    }

    private static Node[] releasePlan() {
        LocalDate monday = thisMonday().plusWeeks(1);
        RXWeekRangeView view = new RXWeekRangeView(RXDateRange.of(monday, monday.plusWeeks(4).minusDays(1)));
        view.getStyleClass().add("accent-indigo");
        view.setWeekFields(WeekFields.ISO);
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(weeks(view.getValue()), "week"), view.valueProperty());
        Label first = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : "W" + view.getValue().start().get(WeekFields.ISO.weekOfWeekBasedYear()), view.valueProperty());
        Label last = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : "W" + view.getValue().end().get(WeekFields.ISO.weekOfWeekBasedYear()), view.valueProperty());
        VBox panel = DateTimeKit.panel("Release 3.2", 160.0, length,
                DateTimeKit.pair("From", first), DateTimeKit.pair("To", last));
        return new Node[] {view, panel};
    }

    private static Node[] planAhead() {
        LocalDate monday = thisMonday();
        RXWeekRangeView view = new RXWeekRangeView(RXDateRange.of(monday.plusWeeks(1), monday.plusWeeks(3).minusDays(1)));
        view.getStyleClass().add("accent-orange");
        view.setMonthCount(1);
        view.setWeekFields(WeekFields.ISO);
        view.setMinWeek(monday);
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(weeks(view.getValue()), "week"), view.valueProperty());
        Label note = DateTimeKit.text("Holiday campaign: social posts and two newsletters.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Campaign", 180.0, length, note);
        return new Node[] {view, panel};
    }

    private static long weeks(RXDateRange range) {
        return (range.lengthInDays() + 6) / 7;
    }

    private static LocalDate thisMonday() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}

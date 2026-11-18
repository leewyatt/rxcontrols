package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.function.Supplier;

/**
 * The RXDateRange page: range pickers for a hotel stay and a report period with preset
 * buttons, and RXDateRangeView as a leave request that skips weekends and as a
 * one-month sprint planner.
 */
public final class RXDateRangeSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXDateRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field with a popup of side-by-side months for picking a start and an end date, "
                        + "and the same calendar as a view.",
                List.of("Stays, bookings, leave and report periods: anything with a first and a last day.",
                        "For a single day use RXDatePicker; for whole weeks or months use the week or month range."),
                List.of(
                        SampleCard.medium("Hotel stay",
                                "Check-in and check-out prompts, minDate set to today, and the nights counted from the value.",
                                hotelStay()).forType(RXDateRangePicker.class),
                        SampleCard.medium("Report period",
                                "Buttons beside the field set the value to common periods; any other span is one pick away.",
                                reportPeriod()).forType(RXDateRangePicker.class),
                        SampleCard.wide("Leave request",
                                "Two months side by side; weekends cannot start or end the range, "
                                        + "but a range may span them, and only workdays are counted.",
                                leaveRequest()).forType(RXDateRangeView.class),
                        SampleCard.wide("Sprint planner",
                                "monthCount 1 for a compact calendar with week numbers next to the sprint details.",
                                sprintPlanner()).forType(RXDateRangeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node hotelStay() {
        LocalDate today = LocalDate.now();
        RXDateRangePicker picker = new RXDateRangePicker();
        picker.setStartPromptText("Check-in");
        picker.setEndPromptText("Check-out");
        picker.setMinDate(today);
        picker.setValue(RXDateRange.of(today.plusDays(14), today.plusDays(17)));
        Label nights = DateTimeKit.bound("result", () -> picker.getValue() == null ? "Pick your dates"
                        : DateTimeKit.plural(ChronoUnit.DAYS.between(picker.getValue().start(), picker.getValue().end()),
                        "night"),
                picker.valueProperty());
        return DateTimeKit.field("Your stay", DateTimeKit.icon("bed-shape", "teal"), picker, FIELD_WIDTH, nights);
    }

    private static Node reportPeriod() {
        LocalDate today = LocalDate.now();
        RXDateRangePicker picker = new RXDateRangePicker();
        picker.setMaxDate(today);
        picker.setValue(RXDateRange.of(today.minusDays(6), today));
        LocalDate lastMonth = today.minusMonths(1);
        HBox presets = new HBox(6.0,
                preset(picker, "Last 7 days", () -> RXDateRange.of(today.minusDays(6), today)),
                preset(picker, "This month", () -> RXDateRange.of(today.withDayOfMonth(1), today)),
                preset(picker, "Last month", () -> RXDateRange.of(lastMonth.withDayOfMonth(1),
                        lastMonth.with(TemporalAdjusters.lastDayOfMonth()))));
        return DateTimeKit.field("Sales report", DateTimeKit.icon("chart-shape", "indigo"), picker, FIELD_WIDTH,
                presets);
    }

    private static RXButton preset(RXDateRangePicker picker, String text, Supplier<RXDateRange> range) {
        RXButton button = new RXButton(text);
        button.getStyleClass().add("preset");
        button.setOnAction(event -> picker.setValue(range.get()));
        return button;
    }

    private static Node[] leaveRequest() {
        LocalDate start = nextMonday(LocalDate.now().plusWeeks(2));
        RXDateRangeView view = new RXDateRangeView(RXDateRange.of(start, start.plusDays(11)));
        view.getStyleClass().add("accent-violet");
        view.setMinDate(LocalDate.now());
        view.setDateFilter(DateTimeKit::isWeekday);
        Label from = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : DateTimeKit.DAY.format(view.getValue().start()), view.valueProperty());
        Label to = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : DateTimeKit.DAY.format(view.getValue().end()), view.valueProperty());
        Label days = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                        : DateTimeKit.plural(DateTimeKit.workdays(view.getValue().start(), view.getValue().end()),
                        "workday"),
                view.valueProperty());
        VBox panel = DateTimeKit.panel("Leave request", 190.0, days,
                DateTimeKit.pair("From", from), DateTimeKit.pair("To", to),
                DateTimeKit.text("18 days left this year", "hint"));
        return new Node[] {view, panel};
    }

    private static Node[] sprintPlanner() {
        LocalDate start = nextMonday(LocalDate.now());
        RXDateRangeView view = new RXDateRangeView(RXDateRange.of(start, start.plusDays(11)));
        view.getStyleClass().add("accent-teal");
        view.setMonthCount(1);
        view.setShowWeekNumbers(true);
        Label length = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.plural(view.getValue().lengthInDays(), "day"), view.valueProperty());
        Label first = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : DateTimeKit.DAY.format(view.getValue().start()), view.valueProperty());
        Label last = DateTimeKit.bound("body", () -> view.getValue() == null ? "–"
                : DateTimeKit.DAY.format(view.getValue().end()), view.valueProperty());
        VBox panel = DateTimeKit.panel("Sprint 24", 190.0, length,
                DateTimeKit.pair("Starts", first), DateTimeKit.pair("Ends", last),
                DateTimeKit.text("Goal: ship the new checkout", "hint"));
        return new Node[] {view, panel};
    }

    private static LocalDate nextMonday(LocalDate day) {
        return day.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }
}

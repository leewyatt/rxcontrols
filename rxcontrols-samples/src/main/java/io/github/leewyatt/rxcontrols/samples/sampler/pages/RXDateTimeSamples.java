package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * The RXDateTime page: date-time pickers for a meeting on quarter hours and a departure
 * on a 12-hour clock with seconds, and RXDateTimeView for scheduling a post and for
 * booking within business hours.
 */
public final class RXDateTimeSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXDateTimeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field with a popup calendar and time columns for picking a day and a time together, "
                        + "and the same panel as a view.",
                List.of("Meetings, deadlines, departures and scheduled posts: one moment in time.",
                        "For only a day use RXDatePicker; for only a time use RXTimePicker."),
                List.of(
                        SampleCard.medium("Meeting start",
                                "minuteStep 15 keeps the minutes to quarter hours; minDateTime rules out the past.",
                                meeting()).forType(RXDateTimePicker.class),
                        SampleCard.medium("12-hour clock, seconds",
                                "twelveHourClock adds an AM / PM column and showSeconds a seconds column; "
                                        + "locale US formats the field.",
                                departure()).forType(RXDateTimePicker.class),
                        SampleCard.wide("Schedule a post",
                                "An inline RXDateTimeView with five-minute steps; the preview follows the value.",
                                schedulePost()).forType(RXDateTimeView.class),
                        SampleCard.wide("Business hours",
                                "dateFilter rules out weekends and timeFilter keeps the hours between 9:00 and 17:00.",
                                businessHours()).forType(RXDateTimeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node meeting() {
        RXDateTimePicker picker = new RXDateTimePicker(LocalDate.now().plusDays(1).atTime(10, 0));
        picker.setMinuteStep(15);
        picker.setMinDateTime(LocalDateTime.now());
        return DateTimeKit.field("Starts at", DateTimeKit.icon("calendar-shape", "sky"), picker, FIELD_WIDTH);
    }

    private static Node departure() {
        RXDateTimePicker picker = new RXDateTimePicker(LocalDate.now().plusDays(9).atTime(19, 45, 30));
        picker.setLocale(Locale.US);
        picker.setTwelveHourClock(true);
        picker.setShowSeconds(true);
        return DateTimeKit.field("Departure", DateTimeKit.icon("clock-shape", "amber"), picker, FIELD_WIDTH);
    }

    private static Node[] schedulePost() {
        RXDateTimeView view = new RXDateTimeView(LocalDate.now().plusDays(1).atTime(9, 0));
        view.getStyleClass().add("accent-pink");
        view.setMinuteStep(5);
        view.setMinDateTime(LocalDateTime.now());
        Label when = DateTimeKit.bound("result", () -> view.getValue() == null ? "Pick a time"
                : "Goes live " + DateTimeKit.DAY_TIME.format(view.getValue()), view.valueProperty());
        when.setWrapText(true);
        Label post = DateTimeKit.text("Our new date pickers are out: ranges, weeks, quarters and more.",
                "agenda-item");
        post.setWrapText(true);
        VBox panel = DateTimeKit.panel("Lee Wyatt  @LeeWyatt_7788", 220.0, post, when);
        return new Node[] {view, panel};
    }

    private static Node[] businessHours() {
        RXDateTimeView view = new RXDateTimeView(nextWorkday().atTime(14, 0));
        view.getStyleClass().add("accent-teal");
        view.setMinuteStep(30);
        view.setDateFilter(DateTimeKit::isWeekday);
        view.setTimeFilter(time -> !time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(17, 0)));
        Label slot = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : DateTimeKit.DAY_TIME.format(view.getValue()), view.valueProperty());
        slot.setWrapText(true);
        VBox panel = DateTimeKit.panel("Support call", 200.0, slot,
                DateTimeKit.text("Weekdays, 9:00 to 17:00", "hint"),
                DateTimeKit.text("30-minute slots", "hint"));
        return new Node[] {view, panel};
    }

    private static LocalDate nextWorkday() {
        LocalDate day = LocalDate.now().plusDays(1);
        while (!DateTimeKit.isWeekday(day)) {
            day = day.plusDays(1);
        }
        return day;
    }
}

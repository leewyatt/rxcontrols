package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXTimePicker;
import io.github.leewyatt.rxcontrols.RXTimeView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * The RXTime page: time pickers for an alarm on a 12-hour clock and appointment slots
 * within opening hours, and RXTimeView as an inline setting and with a seconds column.
 */
public final class RXTimeSamples {

    private static final double FIELD_WIDTH = 200.0;

    private RXTimeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A time field with a popup of scrolling hour and minute columns, and the columns as a view.",
                List.of("A time of day without a date: alarms, opening hours, reminders, appointment slots.",
                        "When the day matters too use RXDateTimePicker; for a start and an end use RXTimeRangePicker."),
                List.of(
                        SampleCard.medium("Alarm",
                                "twelveHourClock adds an AM / PM column; locale US shows 6:30 AM in the field.",
                                alarm()).forType(RXTimePicker.class),
                        SampleCard.medium("Appointment slots",
                                "minTime, maxTime and minuteStep 30 offer half-hour slots; "
                                        + "a timeFilter takes out the lunch hour.",
                                appointment()).forType(RXTimePicker.class),
                        SampleCard.large("Daily digest",
                                "An inline RXTimeView in a settings panel; minuteStep 15 keeps the list short.",
                                digest()).forType(RXTimeView.class),
                        SampleCard.large("Seconds column",
                                "showSeconds adds a third column for times that need the second, like a race start.",
                                raceStart()).forType(RXTimeView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node alarm() {
        RXTimePicker picker = new RXTimePicker(LocalTime.of(6, 30));
        picker.setLocale(Locale.US);
        picker.setTwelveHourClock(true);
        return DateTimeKit.field("Wake me at", DateTimeKit.icon("alarm-shape", "rose"), picker, FIELD_WIDTH,
                DateTimeKit.text("Weekdays", "hint"));
    }

    private static Node appointment() {
        RXTimePicker picker = new RXTimePicker(LocalTime.of(10, 30));
        picker.setMinTime(LocalTime.of(9, 0));
        picker.setMaxTime(LocalTime.of(17, 0));
        picker.setMinuteStep(30);
        picker.setTimeFilter(time -> time.getHour() != 12);
        return DateTimeKit.field("Appointment", DateTimeKit.icon("clock-shape", "teal"), picker, FIELD_WIDTH,
                DateTimeKit.text("9:00 to 17:00, closed for lunch", "hint"));
    }

    private static Node digest() {
        RXTimeView view = new RXTimeView(LocalTime.of(8, 0));
        view.getStyleClass().add("accent-indigo");
        view.setMinuteStep(15);
        Label next = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "Off"
                : DateTimeKit.TIME.format(view.getValue()), view.valueProperty());
        Label note = DateTimeKit.text("A summary of new comments and mentions, once a day.", "hint");
        note.setWrapText(true);
        VBox panel = DateTimeKit.panel("Daily digest", 170.0, next, note);
        return row(view, panel);
    }

    private static Node raceStart() {
        RXTimeView view = new RXTimeView(LocalTime.of(7, 30, 15));
        view.getStyleClass().add("accent-orange");
        view.setShowSeconds(true);
        Label start = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : view.getValue().toString(), view.valueProperty());
        VBox panel = DateTimeKit.panel("Wave 2 start", 150.0, start,
                DateTimeKit.text("City half marathon", "hint"));
        return row(view, panel);
    }

    private static Node row(Node view, Node panel) {
        HBox row = new HBox(16.0, view, panel);
        row.setAlignment(Pos.CENTER);
        return row;
    }
}

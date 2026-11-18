package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.RXDatePicker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * The RXDate page: date pickers for a birthday, a delivery day, two text formats and a
 * read-only due date, and RXCalendarView inline on the page and with week numbers.
 */
public final class RXDateSamples {

    private static final double FIELD_WIDTH = 220.0;

    private RXDateSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A date field with a popup calendar, and the same calendar as a view on the page.",
                List.of("One day: a birthday, a due date, a delivery day. Keep the month on the page with RXCalendarView.",
                        "For a span of days use RXDateRangePicker; for a day and a time use RXDateTimePicker."),
                List.of(
                        SampleCard.medium("Date of birth",
                                "maxDate keeps future days out; typed text is read in the locale's date format.",
                                birthday()).forType(RXDatePicker.class),
                        SampleCard.medium("Delivery day",
                                "minDate, maxDate and a dateFilter that rules out weekends; disabled days are greyed out.",
                                delivery()).forType(RXDatePicker.class),
                        SampleCard.medium("Locale and converter",
                                "locale sets the default format and the popup's names; a converter replaces the text.",
                                formats()).forType(RXDatePicker.class),
                        SampleCard.medium("Read-only and clearable",
                                "With editable off the field only opens the popup; clearable shows a clear button on hover.",
                                dueDate()).forType(RXDatePicker.class),
                        SampleCard.large("Inline calendar",
                                "An RXCalendarView keeps the month on the page; the label below follows the selected day.",
                                inlineCalendar()).forType(RXCalendarView.class),
                        SampleCard.large("Week numbers",
                                "showWeekNumbers adds a week column, ISO weekFields start on Monday, "
                                        + "and showAdjacentDays off hides the other months' days.",
                                weekNumbers()).forType(RXCalendarView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node birthday() {
        RXDatePicker picker = new RXDatePicker(LocalDate.of(1992, 5, 14));
        picker.setPromptText("Your birthday");
        picker.setMaxDate(LocalDate.now());
        return DateTimeKit.field("Birthday", DateTimeKit.icon("person-shape", "violet"), picker, FIELD_WIDTH);
    }

    private static Node delivery() {
        LocalDate today = LocalDate.now();
        RXDatePicker picker = new RXDatePicker(nextWeekday(today.plusDays(2)));
        picker.setMinDate(today.plusDays(1));
        picker.setMaxDate(today.plusWeeks(3));
        picker.setDateFilter(DateTimeKit::isWeekday);
        return DateTimeKit.field("Delivery day", DateTimeKit.icon("truck-shape", "orange"), picker, FIELD_WIDTH,
                DateTimeKit.text("Weekdays in the next three weeks", "hint"));
    }

    private static Node formats() {
        LocalDate day = LocalDate.now().plusDays(10);
        RXDatePicker german = new RXDatePicker(day);
        german.setLocale(Locale.GERMANY);
        RXDatePicker custom = new RXDatePicker(day);
        custom.setConverter(DateTimeKit.converter("EEE, MMM d, yyyy", LocalDate::from));
        VBox box = new VBox(14.0,
                DateTimeKit.field("German locale", DateTimeKit.icon("globe-shape", "sky"), german, FIELD_WIDTH),
                DateTimeKit.field("Custom converter", DateTimeKit.icon("text-shape", "teal"), custom, FIELD_WIDTH));
        box.setFillWidth(false);
        return box;
    }

    private static Node dueDate() {
        RXDatePicker picker = new RXDatePicker(LocalDate.now().plusDays(5));
        picker.setEditable(false);
        picker.setClearable(true);
        picker.setPromptText("No due date");
        return DateTimeKit.field("Due date", DateTimeKit.icon("flag-shape", "rose"), picker, FIELD_WIDTH);
    }

    private static Node inlineCalendar() {
        RXCalendarView calendar = new RXCalendarView(LocalDate.now());
        Label day = DateTimeKit.bound("panel-value",
                () -> calendar.getValue() == null ? "Pick a day" : DateTimeKit.DAY.format(calendar.getValue()),
                calendar.valueProperty());
        VBox box = new VBox(12.0, calendar, day);
        box.setAlignment(Pos.CENTER);
        return box;
    }


    private static Node weekNumbers() {
        RXCalendarView calendar = new RXCalendarView(LocalDate.now());
        calendar.getStyleClass().add("accent-teal");
        calendar.setShowWeekNumbers(true);
        calendar.setWeekFields(WeekFields.ISO);
        calendar.setShowAdjacentDays(false);
        return calendar;
    }


    private static LocalDate nextWeekday(LocalDate day) {
        LocalDate next = day;
        while (!DateTimeKit.isWeekday(next)) {
            next = next.plusDays(1);
        }
        return next;
    }
}

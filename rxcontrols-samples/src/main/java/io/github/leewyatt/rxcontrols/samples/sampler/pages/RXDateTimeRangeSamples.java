package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXDateTimeRange;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

/**
 * The RXDateTimeRange page: a maintenance window on half hours, a car rental within
 * opening hours, a clearable log search down to the second and an event on a 12-hour clock.
 */
public final class RXDateTimeRangeSamples {

    private static final double FIELD_WIDTH = 400.0;

    private RXDateTimeRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a start and an end moment, each picked in a popup with a calendar "
                        + "and time columns.",
                List.of("Windows that start and end at a time of day: maintenance, rentals, events, log queries.",
                        "When only the days matter use RXDateRangePicker; for times on any day use RXTimeRangePicker."),
                List.of(
                        SampleCard.large("Maintenance window",
                                "minuteStep 30 keeps both ends on the half hour; the length comes from the value.",
                                maintenance()),
                        SampleCard.large("Car rental",
                                "Pick-up and drop-off prompts, minDateTime set to now, "
                                        + "and a timeFilter for the desk's opening hours.",
                                carRental()),
                        SampleCard.large("Log search",
                                "showSeconds adds a seconds column; maxDateTime stops the range at the present; "
                                        + "clearable to search all time.",
                                logSearch()),
                        SampleCard.large("12-hour clock",
                                "twelveHourClock adds an AM / PM column; locale US formats both fields.",
                                event())),
                DateTimeKit.STYLESHEET);
    }

    private static Node maintenance() {
        LocalDate saturday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        RXDateTimeRangePicker picker = new RXDateTimeRangePicker(
                RXDateTimeRange.of(saturday.atTime(22, 0), saturday.plusDays(1).atTime(2, 30)));
        picker.setMinuteStep(30);
        Label length = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No window"
                : "Down for " + DateTimeKit.duration(picker.getValue().duration()), picker.valueProperty());
        return DateTimeKit.field("Planned downtime", DateTimeKit.icon("bell-shape", "rose"), picker, FIELD_WIDTH,
                length);
    }

    private static Node carRental() {
        LocalDate pickUp = LocalDate.now().plusDays(3);
        RXDateTimeRangePicker picker = new RXDateTimeRangePicker(
                RXDateTimeRange.of(pickUp.atTime(9, 0), pickUp.plusDays(4).atTime(18, 0)));
        picker.setStartPromptText("Pick-up");
        picker.setEndPromptText("Drop-off");
        picker.setMinuteStep(30);
        picker.setMinDateTime(LocalDateTime.now());
        picker.setTimeFilter(time -> !time.isBefore(LocalTime.of(8, 0)) && !time.isAfter(LocalTime.of(20, 0)));
        Label days = DateTimeKit.bound("result", () -> picker.getValue() == null ? "Pick your dates"
                        : DateTimeKit.plural(rentalDays(picker.getValue()), "rental day"),
                picker.valueProperty());
        return DateTimeKit.field("Rental period", DateTimeKit.icon("car-shape", "orange"), picker, FIELD_WIDTH,
                days, DateTimeKit.text("Desk open 8:00 to 20:00", "hint"));
    }

    private static Node logSearch() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RXDateTimeRangePicker picker = new RXDateTimeRangePicker(RXDateTimeRange.of(now.minusMinutes(45), now));
        picker.setShowSeconds(true);
        picker.setMaxDateTime(now);
        picker.setClearable(true);
        return DateTimeKit.field("Search logs between", DateTimeKit.icon("search-shape", "indigo"), picker,
                FIELD_WIDTH, DateTimeKit.text("level:error  service:checkout", "hint"));
    }

    private static Node event() {
        LocalDate day = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        RXDateTimeRangePicker picker = new RXDateTimeRangePicker(
                RXDateTimeRange.of(day.atTime(18, 30), day.atTime(22, 0)));
        picker.setLocale(Locale.US);
        picker.setTwelveHourClock(true);
        picker.setMinuteStep(15);
        return DateTimeKit.field("Launch party", DateTimeKit.icon("calendar-shape", "pink"), picker, FIELD_WIDTH);
    }

    private static long rentalDays(RXDateTimeRange range) {
        long hours = ChronoUnit.HOURS.between(range.start(), range.end());
        return Math.max(1, (hours + 23) / 24);
    }
}

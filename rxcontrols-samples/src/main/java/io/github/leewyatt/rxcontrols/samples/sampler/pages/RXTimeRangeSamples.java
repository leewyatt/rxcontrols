package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXTimeRange;
import io.github.leewyatt.rxcontrols.RXTimeRangePicker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * The RXTimeRange page: a shop's opening hours, a night shift past midnight, a lunch
 * break within limits and clearable quiet hours on a 12-hour clock.
 */
public final class RXTimeRangeSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXTimeRangeSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A two-part field for a start and an end time of day, picked in a popup with two sets of columns.",
                List.of("Opening hours, shifts, breaks and quiet hours that repeat on any day.",
                        "When the range has dates too use RXDateTimeRangePicker."),
                List.of(
                        SampleCard.medium("Opening hours",
                                "One clearable picker per day group with minuteStep 30; an empty field reads as closed.",
                                openingHours()),
                        SampleCard.medium("Night shift",
                                "With ordered off the start may come after the end: a range that runs past midnight.",
                                nightShift()),
                        SampleCard.medium("Lunch break",
                                "minTime and maxTime keep both ends between 11:00 and 15:00, in 15-minute steps.",
                                lunchBreak()),
                        SampleCard.medium("Quiet hours",
                                "twelveHourClock with locale US, and clearable to switch the quiet hours off.",
                                quietHours())),
                DateTimeKit.STYLESHEET);
    }

    private static Node openingHours() {
        GridPane grid = new GridPane();
        grid.setHgap(10.0);
        grid.setVgap(10.0);
        grid.setAlignment(Pos.CENTER);
        addDay(grid, 0, "Mon – Fri", RXTimeRange.of(LocalTime.of(9, 0), LocalTime.of(18, 0)));
        addDay(grid, 1, "Saturday", RXTimeRange.of(LocalTime.of(10, 0), LocalTime.of(16, 0)));
        addDay(grid, 2, "Sunday", null);
        HBox title = new HBox(6.0, DateTimeKit.icon("store-shape", "lime"), DateTimeKit.text("Corner Bakery", "body"));
        title.setAlignment(Pos.CENTER_LEFT);
        return new VBox(10.0, title, grid);
    }

    private static void addDay(GridPane grid, int row, String day, RXTimeRange hours) {
        RXTimeRangePicker picker = new RXTimeRangePicker(hours);
        picker.setMinuteStep(30);
        picker.setStartPromptText("Closed");
        picker.setEndPromptText("");
        picker.setClearable(true);
        picker.setPrefWidth(200.0);
        grid.addRow(row, DateTimeKit.text(day, "hint"), picker);
    }

    private static Node nightShift() {
        RXTimeRangePicker picker = new RXTimeRangePicker(RXTimeRange.of(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        picker.setOrdered(false);
        Label length = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No shift"
                        : DateTimeKit.duration(picker.getValue().duration())
                        + (picker.getValue().end().isBefore(picker.getValue().start()) ? ", ends the next day" : ""),
                picker.valueProperty());
        return DateTimeKit.field("Night shift", DateTimeKit.icon("moon-shape", "indigo"), picker, FIELD_WIDTH, length);
    }

    private static Node lunchBreak() {
        RXTimeRangePicker picker = new RXTimeRangePicker(RXTimeRange.of(LocalTime.of(12, 0), LocalTime.of(12, 45)));
        picker.setMinTime(LocalTime.of(11, 0));
        picker.setMaxTime(LocalTime.of(15, 0));
        picker.setMinuteStep(15);
        Label length = DateTimeKit.bound("result", () -> picker.getValue() == null ? "No break"
                : DateTimeKit.duration(picker.getValue().duration()), picker.valueProperty());
        return DateTimeKit.field("Lunch break", DateTimeKit.icon("work-shape", "amber"), picker, FIELD_WIDTH, length);
    }

    private static Node quietHours() {
        RXTimeRangePicker picker = new RXTimeRangePicker(RXTimeRange.of(LocalTime.of(22, 0), LocalTime.of(7, 0)));
        picker.setLocale(Locale.US);
        picker.setTwelveHourClock(true);
        picker.setOrdered(false);
        picker.setClearable(true);
        picker.setStartPromptText("Off");
        picker.setEndPromptText("");
        return DateTimeKit.field("Do not disturb", DateTimeKit.icon("bell-shape", "violet"), picker, FIELD_WIDTH,
                DateTimeKit.text("Notifications are held during these hours", "hint"));
    }
}

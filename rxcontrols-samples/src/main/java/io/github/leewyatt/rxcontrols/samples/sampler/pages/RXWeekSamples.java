package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXWeekPicker;
import io.github.leewyatt.rxcontrols.RXWeekView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;

/**
 * The RXWeek page: week pickers for a timesheet and a sprint with a custom text, and
 * RXWeekView as a weekly report and as a pay calendar that allows every other week.
 */
public final class RXWeekSamples {

    private static final double FIELD_WIDTH = 220.0;

    private RXWeekSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field for picking a whole week from a popup calendar whose rows are weeks, "
                        + "and the calendar as a view.",
                List.of("Timesheets, weekly reports and sprints: anything planned or counted by the week.",
                        "For several weeks in a row use RXWeekRangePicker; for single days use RXDatePicker."),
                List.of(
                        SampleCard.medium("Timesheet",
                                "The value is the first day of the week; the field shows the year and week number.",
                                timesheet()).forType(RXWeekPicker.class),
                        SampleCard.medium("Sprint week",
                                "minWeek and maxWeek allow the next eight weeks; a converter writes the field's text.",
                                sprintWeek()).forType(RXWeekPicker.class),
                        SampleCard.wide("Weekly report",
                                "An inline RXWeekView with ISO weekFields; the report beside it follows the week.",
                                weeklyReport()).forType(RXWeekView.class),
                        SampleCard.wide("Every other week",
                                "A weekFilter keeps the even weeks for a pay calendar; the others are disabled.",
                                payWeeks()).forType(RXWeekView.class)),
                DateTimeKit.STYLESHEET);
    }

    private static Node timesheet() {
        RXWeekPicker picker = new RXWeekPicker(LocalDate.now());
        Label days = DateTimeKit.bound("hint", () -> picker.getValue() == null ? "No week"
                : span(picker.getValue(), WeekFields.of(Locale.getDefault(Locale.Category.FORMAT))),
                picker.valueProperty());
        return DateTimeKit.field("Timesheet for", DateTimeKit.icon("work-shape", "sky"), picker, FIELD_WIDTH, days);
    }

    private static Node sprintWeek() {
        LocalDate today = LocalDate.now();
        RXWeekPicker picker = new RXWeekPicker(today.plusWeeks(2));
        picker.setMinWeek(today);
        picker.setMaxWeek(today.plusWeeks(8));
        picker.setEditable(false);
        picker.setConverter(DateTimeKit.converter("'Week of' MMM d, yyyy", LocalDate::from));
        return DateTimeKit.field("Sprint 25 starts", DateTimeKit.icon("flag-shape", "orange"), picker,
                FIELD_WIDTH);
    }

    private static Node weeklyReport() {
        RXWeekView view = new RXWeekView(LocalDate.now());
        view.getStyleClass().add("accent-orange");
        view.setWeekFields(WeekFields.ISO);
        Label week = DateTimeKit.bound("panel-value", () -> view.getValue() == null ? "–"
                : "Week " + view.getValue().get(WeekFields.ISO.weekOfWeekBasedYear()), view.valueProperty());
        Label days = DateTimeKit.bound("hint", () -> view.getValue() == null ? ""
                : span(view.getValue(), WeekFields.ISO), view.valueProperty());
        Label hours = DateTimeKit.bound("body", () -> view.getValue() == null ? ""
                : hoursLogged(view.getValue()) + " h logged", view.valueProperty());
        days.setWrapText(true);
        VBox panel = DateTimeKit.panel("Report", 160.0, week, days, hours);
        HBox row = new HBox(16.0, view, panel);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node payWeeks() {
        WeekFields rules = WeekFields.ISO;
        LocalDate today = LocalDate.now();
        LocalDate payWeek = today.get(rules.weekOfWeekBasedYear()) % 2 == 0 ? today : today.plusWeeks(1);
        RXWeekView view = new RXWeekView(payWeek);
        view.getStyleClass().add("accent-teal");
        view.setWeekFields(rules);
        view.setWeekFilter(day -> day.get(rules.weekOfWeekBasedYear()) % 2 == 0);
        return view;
    }

    private static String span(LocalDate day, WeekFields rules) {
        LocalDate first = day.with(rules.dayOfWeek(), 1);
        return DateTimeKit.SHORT_DAY.format(first) + " – " + DateTimeKit.SHORT_DAY.format(first.plusDays(6));
    }

    private static String hoursLogged(LocalDate day) {
        int week = day.get(WeekFields.ISO.weekOfWeekBasedYear());
        return String.valueOf(32 + (week * 7) % 9) + (week % 2 == 0 ? ".5" : "");
    }
}

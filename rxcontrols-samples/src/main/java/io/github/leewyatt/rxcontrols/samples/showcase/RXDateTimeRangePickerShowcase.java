package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateTimeRangePicker}: the picker with its committed value and
 * commit count, and a control panel for the shared picker options, the two prompts, the day
 * grid, the time columns with their two tabs, the selectable date-times and the OK button.
 * For the smallest working example see {@code RXDateTimeRangePickerDemo}.
 */
public class RXDateTimeRangePickerShowcase extends DateTimeShowcase {

    private final RXDateTimeRangePicker picker = new RXDateTimeRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateTimeRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Date-time range with start and end times";
    }

    @Override
    protected String windowTitle() {
        return "RXDateTimeRangePicker Showcase";
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected Node createPreview() {
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Start prompt", picker.startPromptTextProperty()),
                        textRow("End prompt", picker.endPromptTextProperty()))),
                section("Calendar", calendarGrid(picker.weekFieldsProperty(), picker.showWeekNumbersProperty(),
                        picker.showAdjacentDaysProperty())),
                section("Time", timeGrid(picker.showSecondsProperty(), picker.twelveHourClockProperty(),
                        picker.hourStepProperty(), picker.minuteStepProperty(), picker.secondStepProperty())),
                section("Selectable date-times", dateTimeLimitGrid(picker.minDateTimeProperty(),
                        picker.maxDateTimeProperty(), picker.dateFilterProperty(), picker.timeFilterProperty())),
                section("Footer", createGrid(
                        textRow("OK text", picker.okTextProperty()))));
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}

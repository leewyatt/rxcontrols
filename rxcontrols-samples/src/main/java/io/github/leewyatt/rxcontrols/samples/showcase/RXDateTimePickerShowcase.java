package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateTimePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the day grid, the time columns,
 * the selectable date-times and the footer buttons. For the smallest working example see
 * {@code RXDateTimePickerDemo}.
 */
public class RXDateTimePickerShowcase extends DateTimeShowcase {

    private final RXDateTimePicker picker = new RXDateTimePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateTimePicker";
    }

    @Override
    protected String subtitle() {
        return "Date and time with a confirmed popup draft";
    }

    @Override
    protected String windowTitle() {
        return "RXDateTimePicker Showcase";
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected Node createPreview() {
        picker.setPromptText("Select date and time");
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt text", picker.promptTextProperty()))),
                section("Calendar", calendarGrid(picker.weekFieldsProperty(), picker.showWeekNumbersProperty(),
                        picker.showAdjacentDaysProperty())),
                section("Time", timeGrid(picker.showSecondsProperty(), picker.twelveHourClockProperty(),
                        picker.hourStepProperty(), picker.minuteStepProperty(), picker.secondStepProperty())),
                section("Selectable date-times", dateTimeLimitGrid(picker.minDateTimeProperty(),
                        picker.maxDateTimeProperty(), picker.dateFilterProperty(), picker.timeFilterProperty())),
                section("Footer", createGrid(
                        flagRow("showNow", picker.showNowProperty()),
                        textRow("Now text", picker.nowTextProperty()),
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

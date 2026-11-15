package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTimeRangePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXTimeRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts, the time
 * columns, the selectable times and the OK button.
 * For the smallest working example see {@code RXTimeRangePickerDemo}.
 */
public class RXTimeRangePickerShowcase extends DateTimeShowcase {

    private final RXTimeRangePicker picker = new RXTimeRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTimeRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Time range with start and end times";
    }

    @Override
    protected String windowTitle() {
        return "RXTimeRangePicker Showcase";
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
                section("Time", timeGrid(picker.showSecondsProperty(), picker.twelveHourClockProperty(),
                        picker.hourStepProperty(), picker.minuteStepProperty(), picker.secondStepProperty())),
                section("Selectable times", timeLimitGrid(picker.minTimeProperty(), picker.maxTimeProperty(),
                        picker.timeFilterProperty())),
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

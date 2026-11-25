package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTimePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXTimePicker}: the picker with its committed value and commit count,
 * and a control panel for the shared picker options, the time columns, the selectable times
 * and the footer buttons. For the smallest working example see {@code RXTimePickerDemo}.
 */
public class RXTimePickerShowcase extends DateTimeShowcase {

    private final RXTimePicker picker = new RXTimePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTimePicker";
    }

    @Override
    protected String subtitle() {
        return "Time picker with scrolling columns";
    }

    @Override
    protected String windowTitle() {
        return "RXTimePicker Showcase";
    }

    @Override
    protected Node createPreview() {
        picker.setPromptText("Select time");
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt text", picker.promptTextProperty()))),
                section("Time", timeGrid(picker.showSecondsProperty(), picker.twelveHourClockProperty(),
                        picker.hourStepProperty(), picker.minuteStepProperty(), picker.secondStepProperty(),
                        picker.wheelMultiplierProperty())),
                section("Selectable times", timeLimitGrid(picker.minTimeProperty(), picker.maxTimeProperty(),
                        picker.timeFilterProperty())),
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

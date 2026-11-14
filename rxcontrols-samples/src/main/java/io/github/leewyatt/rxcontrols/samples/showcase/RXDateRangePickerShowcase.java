package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts, the day grid
 * and the selectable dates. For the smallest working example see
 * {@code RXDateRangePickerDemo}.
 */
public class RXDateRangePickerShowcase extends DateTimeShowcase {

    private final RXDateRangePicker picker = new RXDateRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Start and end dates picked on two linked months";
    }

    @Override
    protected String windowTitle() {
        return "RXDateRangePicker Showcase";
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
                section("Selectable dates", dateLimitGrid(picker.minDateProperty(), picker.maxDateProperty(),
                        picker.dateFilterProperty())));
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

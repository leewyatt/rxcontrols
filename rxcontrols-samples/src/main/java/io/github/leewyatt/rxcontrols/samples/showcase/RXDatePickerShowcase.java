package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDatePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDatePicker}: the picker with its committed value and commit count,
 * and a control panel for the shared picker options, the day grid, the selectable dates and
 * the Today button. For the smallest working example see {@code RXDatePickerDemo}.
 */
public class RXDatePickerShowcase extends DateTimeShowcase {

    private final RXDatePicker picker = new RXDatePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDatePicker";
    }

    @Override
    protected String subtitle() {
        return "Text field with a popup calendar";
    }

    @Override
    protected String windowTitle() {
        return "RXDatePicker Showcase";
    }

    @Override
    protected Node createPreview() {
        picker.setPromptText("Select date");
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt text", picker.promptTextProperty()))),
                section("Calendar", calendarGrid(picker.weekFieldsProperty(), picker.showWeekNumbersProperty(),
                        picker.showAdjacentDaysProperty())),
                section("Selectable dates", dateLimitGrid(picker.minDateProperty(), picker.maxDateProperty(),
                        picker.dateFilterProperty())),
                section("Footer", createGrid(
                        flagRow("showToday", picker.showTodayProperty()),
                        textRow("Today text", picker.todayTextProperty()))));
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

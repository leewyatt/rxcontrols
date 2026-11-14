package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXWeekRangePicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXWeekRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts, the week
 * rules and the selectable weeks. For the smallest working example see
 * {@code RXWeekRangePickerDemo}.
 */
public class RXWeekRangePickerShowcase extends DateTimeShowcase {

    private final RXWeekRangePicker picker = new RXWeekRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXWeekRangePicker";
    }

    @Override
    protected String subtitle() {
        return "First and last weeks picked on two linked months";
    }

    @Override
    protected String windowTitle() {
        return "RXWeekRangePicker Showcase";
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
                section("Weeks", weekGrid(picker.weekFieldsProperty(), picker.showWeekNumbersProperty())),
                section("Selectable weeks", weekLimitGrid(picker.minWeekProperty(), picker.maxWeekProperty(),
                        picker.weekFilterProperty())));
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

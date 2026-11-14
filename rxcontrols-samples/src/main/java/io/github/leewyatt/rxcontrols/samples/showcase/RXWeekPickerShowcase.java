package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXWeekPicker;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXWeekPicker}: the picker with its committed value and commit count,
 * and a control panel for the shared picker options, the prompt, the week rules and the
 * selectable weeks. For the smallest working example see {@code RXWeekPickerDemo}.
 */
public class RXWeekPickerShowcase extends DateTimeShowcase {

    private final RXWeekPicker picker = new RXWeekPicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXWeekPicker";
    }

    @Override
    protected String subtitle() {
        return "Text field with a popup calendar that picks whole weeks";
    }

    @Override
    protected String windowTitle() {
        return "RXWeekPicker Showcase";
    }

    @Override
    protected Node createPreview() {
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt", picker.promptTextProperty()))),
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
